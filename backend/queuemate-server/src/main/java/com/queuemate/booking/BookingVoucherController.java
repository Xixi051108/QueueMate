package com.queuemate.booking;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/venues/{venueId}/booking-vouchers")
public class BookingVoucherController {

    private final BookingVoucherService voucherService;

    public BookingVoucherController(BookingVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<BookingVoucherRedeemResponse> redeem(
            @Positive @PathVariable Long venueId,
            @Valid @RequestBody BookingVoucherRedeemRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(voucherService.redeem(venueId, request, principal));
    }
}
