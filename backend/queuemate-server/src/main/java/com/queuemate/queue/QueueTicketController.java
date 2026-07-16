package com.queuemate.queue;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class QueueTicketController {

    private final QueueTicketService ticketService;

    public QueueTicketController(QueueTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/venues/{venueId}/queue/tickets")
    public ApiResponse<QueueTicketResponse> take(
            @Positive @PathVariable Long venueId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(ticketService.take(venueId, principal));
    }

    @GetMapping("/venues/{venueId}/queue/tickets/current")
    public ApiResponse<QueueCurrentResponse> current(
            @Positive @PathVariable Long venueId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate queueDate
    ) {
        return ApiResponse.success(ticketService.current(venueId, queueDate));
    }

    @GetMapping("/queue/tickets/my")
    public ApiResponse<List<QueueTicketResponse>> listMine(
            @RequestParam(required = false) @Positive Long venueId,
            @RequestParam(required = false) QueueTicketStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate queueDate,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(ticketService.listMine(venueId, status, queueDate, principal));
    }

    @PatchMapping("/queue/tickets/{ticketId}/call")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<QueueTicketResponse> call(
            @Positive @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(ticketService.call(ticketId, principal));
    }

    @PatchMapping("/queue/tickets/{ticketId}/complete")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<QueueTicketResponse> complete(
            @Positive @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(ticketService.complete(ticketId, principal));
    }

    @PatchMapping("/queue/tickets/{ticketId}/miss")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ApiResponse<QueueTicketResponse> miss(
            @Positive @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(ticketService.miss(ticketId, principal));
    }
}
