package com.queuemate.venue;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    public ApiResponse<List<VenueResponse>> list(
            @RequestParam(required = false) VenueCategory category,
            @RequestParam(required = false) VenueStatus status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(venueService.list(category, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<VenueResponse> get(@Positive @PathVariable Long id) {
        return ApiResponse.success(venueService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<VenueResponse> create(
            @Valid @RequestBody VenueCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(venueService.create(request, principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<VenueResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody VenueUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(venueService.update(id, request, principal));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<VenueResponse> updateStatus(
            @Positive @PathVariable Long id,
            @Valid @RequestBody VenueStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(venueService.updateStatus(id, request.status(), principal));
    }
}
