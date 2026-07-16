package com.queuemate.stats;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/stats/venues")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class BusyHoursController {

    private final BusyHoursService busyHoursService;

    public BusyHoursController(BusyHoursService busyHoursService) {
        this.busyHoursService = busyHoursService;
    }

    @GetMapping("/{venueId}/busy-hours")
    public ApiResponse<List<BusyHourResponse>> getBusyHours(
            @Positive @PathVariable Long venueId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(
                busyHoursService.getBusyHours(venueId, dateFrom, dateTo, principal)
        );
    }
}
