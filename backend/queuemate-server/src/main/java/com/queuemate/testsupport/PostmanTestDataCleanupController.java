package com.queuemate.testsupport;

import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e")
@ConditionalOnProperty(
        prefix = "queuemate.test-support",
        name = "enabled",
        havingValue = "true"
)
@RestController
@RequestMapping("/api/v1/test-support/postman-runs")
@PreAuthorize("hasRole('ADMIN')")
public class PostmanTestDataCleanupController {

    private final PostmanTestDataCleanupService cleanupService;

    public PostmanTestDataCleanupController(PostmanTestDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/cleanup")
    public ApiResponse<PostmanCleanupResponse> cleanup(
            @Valid @RequestBody PostmanCleanupRequest request
    ) {
        return ApiResponse.success(cleanupService.cleanup(request));
    }
}
