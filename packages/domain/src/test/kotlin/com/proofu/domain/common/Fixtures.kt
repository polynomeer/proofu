package com.proofu.domain.common

import java.util.UUID

/** Deterministic identifiers for tests. */
object Fixtures {
    fun uuid(seed: Int): UUID = UUID.nameUUIDFromBytes(byteArrayOf(seed.toByte()))

    val workspaceA = WorkspaceId(uuid(1))
    val workspaceB = WorkspaceId(uuid(2))
}
