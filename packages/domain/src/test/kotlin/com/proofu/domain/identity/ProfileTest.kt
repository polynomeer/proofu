package com.proofu.domain.identity

import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProfileTest {
    private val user = UserId(Fixtures.uuid(1))

    @Test
    fun `contact line lists only what exists`() {
        assertThat(Profile(user, "홍길동", email = "hong@example.com", phone = "010-1234-5678").contactLine)
            .isEqualTo("hong@example.com · 010-1234-5678")
        assertThat(Profile(user, "홍길동", location = "서울").contactLine).isEqualTo("서울")
        assertThat(Profile(user, "홍길동").contactLine).isEmpty()
    }

    @Test
    fun `rejects blank names, malformed contact and too many links`() {
        assertThatThrownBy { Profile(user, " ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy {
            Profile(
                user,
                "홍길동",
                email = "not-an-email",
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { Profile(user, "홍길동", phone = "abc") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { Profile(user, "홍길동", links = List(6) { ProfileLink("l$it", "https://x/$it") }) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { ProfileLink("GitHub", "github.com/x") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThat(Profile(user, "홍길동", phone = "+82 10-1234-5678").phone).isEqualTo("+82 10-1234-5678")
    }
}
