package com.proofu.worker.account

import com.proofu.worker.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipInputStream

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class AccountExportJobHandlerTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `zips every table of the workspace plus the ready export files and records size, hash and expiry`() {
        val s = AccountSeed.seed(jdbc)
        val other = AccountSeed.seed(jdbc)
        val exportId = UUID.randomUUID()
        val job = enqueue(s, exportId)
        awaitStatus(job, "SUCCEEDED")

        val row = jdbc.queryForMap("select * from account_exports where id = ?", exportId)
        assertThat(row["status"]).isEqualTo("READY")
        assertThat(row["object_key"]).isEqualTo("pg:account:$exportId")
        assertThat(row["expires_at"]).isNotNull()
        val bytes =
            jdbc.queryForObject(
                "select content from export_files where object_key = ?",
                ByteArray::class.java,
                "pg:account:$exportId",
            )!!
        assertThat(row["size_bytes"]).isEqualTo(bytes.size.toLong())
        assertThat(row["sha256"]).isEqualTo(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            },
        )

        val entries = unzip(bytes)
        assertThat(entries.keys).contains("README.txt", "data.json")
        assertThat(entries.keys.filter { it.startsWith("exports/") }).hasSize(1)
        assertThat(
            entries.keys.single { it.startsWith("exports/") },
        ).startsWith("exports/${s.exportId}-d").endsWith(".docx")

        val data = mapper.readTree(entries["data.json"])
        for (table in AccountExportJobHandler.TABLES.map { it.first }) {
            assertThat(data.has(table)).describedAs(table).isTrue()
        }
        assertThat(data["claims"].size()).isEqualTo(1)
        assertThat(data["submission_snapshots"].size()).isEqualTo(1)
        assertThat(data["user_profiles"][0]["full_name"].asString()).isEqualTo("홍길동")
        assertThat(data["workspaces"][0]["id"].asString()).isEqualTo(s.workspaceId.toString())
        // Nothing from another workspace leaks in.
        val text = String(entries["data.json"]!!)
        assertThat(text).doesNotContain(other.workspaceId.toString()).doesNotContain(other.userId.toString())

        val counts = mapper.readTree(row["tables"].toString())
        assertThat(counts["claims"].asInt()).isEqualTo(1)
        assertThat(counts["audit_events"].asInt()).isEqualTo(1)

        // Re-delivery of a finished export is a no-op.
        awaitStatus(enqueue(s, exportId), "SUCCEEDED")
        assertThat(
            jdbc.queryForObject(
                "select count(*) from export_files where object_key = ?",
                Long::class.java,
                "pg:account:$exportId",
            ),
        ).isOne()
    }

    @Test
    fun `fails cleanly when the export row does not belong to the workspace`() {
        val s = AccountSeed.seed(jdbc)
        val other = AccountSeed.seed(jdbc)
        val exportId = UUID.randomUUID()
        jdbc.update(
            "insert into account_exports (id, workspace_id, status) values (?, ?, 'REQUESTED')",
            exportId,
            other.workspaceId,
        )
        awaitStatus(enqueue(s, exportId), "FAILED:EXPORT_NOT_FOUND")
        assertThat(
            jdbc.queryForObject("select status from account_exports where id = ?", String::class.java, exportId),
        ).isEqualTo("REQUESTED")
    }

    private fun enqueue(
        s: AccountSeed.Seed,
        exportId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            s.workspaceId,
            AccountExportJobHandler.TYPE,
            """{"exportId":"$exportId","workspaceId":"${s.workspaceId}"}""",
        )
        jdbc.update(
            "insert into account_exports (id, workspace_id, job_id, status) values (?, ?, ?, 'REQUESTED') on conflict (id) do nothing",
            exportId,
            s.workspaceId,
            id,
        )
        return id
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> =
        buildMap {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    put(e.name, zip.readBytes())
                    e = zip.nextEntry
                }
            }
        }

    private fun awaitStatus(
        job: UUID,
        status: String,
    ) = await().atMost(Duration.ofSeconds(15)).untilAsserted {
        assertThat(
            jdbc.queryForObject(
                "select status || coalesce(':' || error_code, '') from jobs where id = ?",
                String::class.java,
                job,
            ),
        ).isEqualTo(status)
    }
}
