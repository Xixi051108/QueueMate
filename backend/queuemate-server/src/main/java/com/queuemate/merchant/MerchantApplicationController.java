package com.queuemate.merchant;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class MerchantApplicationController {

    private final MerchantApplicationService applicationService;

    public MerchantApplicationController(MerchantApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/api/v1/merchant-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<MerchantApplicationResponse> submit(
            @Valid @RequestBody MerchantApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.submit(request, principal));
    }

    @GetMapping("/api/v1/merchant-applications/my")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<MerchantApplicationResponse>> mine(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.mine(principal));
    }

    @GetMapping("/api/v1/admin/merchant-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MerchantApplicationResponse>> list(
            @RequestParam(required = false) MerchantApplicationStatus status,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.list(status, principal));
    }

    @GetMapping("/api/v1/admin/merchant-applications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MerchantApplicationResponse> get(
            @Positive @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.get(id, principal));
    }

    @PatchMapping("/api/v1/admin/merchant-applications/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MerchantApplicationResponse> approve(
            @Positive @PathVariable Long id,
            @Valid @RequestBody MerchantApplicationReviewRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.approve(id, request, principal));
    }

    @PatchMapping("/api/v1/admin/merchant-applications/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MerchantApplicationResponse> reject(
            @Positive @PathVariable Long id,
            @Valid @RequestBody MerchantApplicationReviewRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.reject(id, request, principal));
    }

    @GetMapping("/api/v1/admin/merchants")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MerchantSummaryResponse>> merchants(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.merchants(principal));
    }
}
