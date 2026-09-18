package com.proofu.api.identity

import jakarta.servlet.http.HttpServletRequest

/**
 * Turns an incoming request into a [WorkspaceContext]. Returns null when the caller
 * is not authenticated; the argument resolver then answers 401.
 */
fun interface WorkspaceResolver {
    fun resolve(request: HttpServletRequest): WorkspaceContext?
}
