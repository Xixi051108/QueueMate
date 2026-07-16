package com.queuemate.booking;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/venues/{venueId}/slots")
public class BookingSlotController {

    private final BookingSlotService bookingSlotService;

    public BookingSlotController(BookingSlotService bookingSlotService) {
        this.bookingSlotService = bookingSlotService;
    }

    @GetMapping
    public ApiResponse<List<BookingSlotResponse>> list(
            @Positive @PathVariable Long venueId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BookingSlotStatus status
    ) {
        return ApiResponse.success(bookingSlotService.list(venueId, dateFrom, dateTo, status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<BookingSlotResponse> create(
            @Positive @PathVariable Long venueId,
            @Valid @RequestBody BookingSlotCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(bookingSlotService.create(venueId, request, principal));
    }

    @PatchMapping("/{slotId}/status")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<BookingSlotResponse> updateStatus(
            @Positive @PathVariable Long venueId,
            @Positive @PathVariable Long slotId,
            @Valid @RequestBody BookingSlotStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(
                bookingSlotService.updateStatus(venueId, slotId, request.status(), principal)
        );
    }
}
