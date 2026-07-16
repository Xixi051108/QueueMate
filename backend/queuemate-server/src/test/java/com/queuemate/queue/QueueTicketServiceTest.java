package com.queuemate.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import com.queuemate.venue.VenueStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QueueTicketServiceTest {

    @Mock
    private QueueTicketMapper ticketMapper;

    @Mock
    private QueueSequenceMapper sequenceMapper;

    @Mock
    private VenueService venueService;

    private QueueTicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new QueueTicketService(ticketMapper, sequenceMapper, venueService);
    }

    @Test
    void authenticatedUserTakesNextDailyNumber() {
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.selectCount(any())).thenReturn(0L);
        when(sequenceMapper.currentConnectionValue()).thenReturn(3);
        when(ticketMapper.insert(any(QueueTicket.class))).thenAnswer(invocation -> {
            QueueTicket ticket = invocation.getArgument(0);
            ticket.setId(7003L);
            return 1;
        });

        QueueTicketResponse response = ticketService.take(4001L, user());

        assertThat(response.id()).isEqualTo(7003L);
        assertThat(response.queueNo()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(QueueTicketStatus.WAITING);
        assertThat(response.ticketNo()).startsWith("QT").hasSize(22);
        verify(sequenceMapper).next(any(), any());
    }

    @Test
    void disabledVenueCannotIssueTicket() {
        Venue venue = queueVenue();
        venue.setQueueEnabled(false);
        when(venueService.getRequiredVenue(4001L)).thenReturn(venue);

        assertBusinessError(
                () -> ticketService.take(4001L, user()),
                HttpStatus.CONFLICT,
                "QUEUE_VENUE_UNAVAILABLE"
        );
        verify(sequenceMapper, never()).next(any(), any());
    }

    @Test
    void duplicateActiveTicketIsRejectedBeforeSequenceIncrement() {
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.selectCount(any())).thenReturn(1L);

        assertBusinessError(
                () -> ticketService.take(4001L, user()),
                HttpStatus.CONFLICT,
                "QUEUE_TICKET_DUPLICATE"
        );
        verify(sequenceMapper, never()).next(any(), any());
    }

    @Test
    void currentQueueSummarizesCalledAndWaitingTickets() {
        QueueTicket called = ticket(7001L, 1, QueueTicketStatus.CALLED);
        called.setCalledAt(LocalDateTime.now().minusMinutes(1));
        QueueTicket waiting = ticket(7002L, 2, QueueTicketStatus.WAITING);
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.selectCurrent(any(), any())).thenReturn(List.of(called, waiting));

        QueueCurrentResponse response = ticketService.current(4001L, LocalDate.now());

        assertThat(response.latestCalledNo()).isEqualTo(1);
        assertThat(response.nextWaitingNo()).isEqualTo(2);
        assertThat(response.waitingCount()).isEqualTo(1);
        assertThat(response.calledCount()).isEqualTo(1);
        assertThat(response.tickets()).hasSize(2);
    }

    @Test
    void merchantCallsWaitingTicket() {
        QueueTicket ticket = ticket(7002L, 2, QueueTicketStatus.WAITING);
        when(ticketMapper.selectById(7002L)).thenReturn(ticket);
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.transition(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1);

        QueueTicketResponse response = ticketService.call(7002L, merchant());

        assertThat(response.status()).isEqualTo(QueueTicketStatus.CALLED);
        assertThat(response.calledAt()).isNotNull();
        verify(venueService).requireOwnerOrAdmin(any(), any());
    }

    @Test
    void calledTicketCanBeCompleted() {
        QueueTicket ticket = ticket(7002L, 2, QueueTicketStatus.CALLED);
        when(ticketMapper.selectById(7002L)).thenReturn(ticket);
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.transition(any(), any(), any(), any())).thenReturn(1);

        QueueTicketResponse response = ticketService.complete(7002L, merchant());

        assertThat(response.status()).isEqualTo(QueueTicketStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    void calledTicketCanBeMissed() {
        QueueTicket ticket = ticket(7002L, 2, QueueTicketStatus.CALLED);
        when(ticketMapper.selectById(7002L)).thenReturn(ticket);
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.transition(any(), any(), any(), any())).thenReturn(1);

        QueueTicketResponse response = ticketService.miss(7002L, merchant());

        assertThat(response.status()).isEqualTo(QueueTicketStatus.MISSED);
        assertThat(response.missedAt()).isNotNull();
    }

    @Test
    void concurrentOrIllegalTransitionIsRejectedByConditionalUpdate() {
        QueueTicket ticket = ticket(7002L, 2, QueueTicketStatus.WAITING);
        when(ticketMapper.selectById(7002L)).thenReturn(ticket);
        when(venueService.getRequiredVenue(4001L)).thenReturn(queueVenue());
        when(ticketMapper.transition(any(), any(), any(), any())).thenReturn(0);

        assertBusinessError(
                () -> ticketService.call(7002L, merchant()),
                HttpStatus.CONFLICT,
                "QUEUE_STATUS_INVALID"
        );
    }

    @Test
    void missingTicketReturnsNotFound() {
        when(ticketMapper.selectById(7999L)).thenReturn(null);

        assertBusinessError(
                () -> ticketService.call(7999L, merchant()),
                HttpStatus.NOT_FOUND,
                "QUEUE_TICKET_NOT_FOUND"
        );
    }

    private QueueTicket ticket(Long id, int queueNo, QueueTicketStatus status) {
        QueueTicket ticket = new QueueTicket();
        ticket.setId(id);
        ticket.setTicketNo("QTTEST" + queueNo);
        ticket.setVenueId(4001L);
        ticket.setUserId(3001L);
        ticket.setQueueDate(LocalDate.now());
        ticket.setQueueNo(queueNo);
        ticket.setStatus(status);
        ticket.setTakenAt(LocalDateTime.now());
        return ticket;
    }

    private Venue queueVenue() {
        Venue venue = new Venue();
        venue.setId(4001L);
        venue.setMerchantId(2001L);
        venue.setQueueEnabled(true);
        venue.setStatus(VenueStatus.ACTIVE);
        return venue;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(3001L, "alice", UserRole.USER);
    }

    private AuthenticatedUser merchant() {
        return new AuthenticatedUser(2001L, "merchant_tea", UserRole.MERCHANT);
    }

    private void assertBusinessError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}
