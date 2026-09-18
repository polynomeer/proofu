package com.proofu.api.dashboard

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.domain.career.CareerEntryType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("${ApiPaths.V1}/dashboard")
class DashboardController(
    private val service: DashboardService,
) {
    @GetMapping
    fun summary(
        workspace: WorkspaceContext,
        @RequestParam(required = false) timelineType: CareerEntryType?,
    ): DashboardSummary = service.summary(workspace, timelineType)
}
