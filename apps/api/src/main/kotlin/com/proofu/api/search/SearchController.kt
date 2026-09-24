package com.proofu.api.search

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("${ApiPaths.V1}/search")
class SearchController(
    private val service: SearchService,
) {
    @GetMapping
    fun search(
        workspace: WorkspaceContext,
        @RequestParam @NotBlank @Size(min = 2, max = 120) q: String,
        @RequestParam(defaultValue = "5") @Min(1) @Max(20) limit: Int,
    ): SearchResults = service.search(workspace, q, limit)
}
