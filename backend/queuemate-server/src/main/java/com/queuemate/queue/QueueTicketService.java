package com.queuemate.queue;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import com.queuemate.venue.VenueStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueTicketService {

    private static final DateTimeFormatter TICKET_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final QueueTicketMapper ticketMapper;
    private final QueueSequenceMapper sequenceMapper;
    private final VenueService venueService;

    public QueueTicketService(
            QueueTicketMapper ticketMapper,
            QueueSequenceMapper sequenceMapper,
            VenueService venueService
    ) {
        this.ticketMapper = ticketMapper;
        this.sequenceMapper = sequenceMapper;
        this.venueService = venueService;
    }

    @Transactional
    public QueueTicketResponse take(Long venueId, AuthenticatedUser principal) {
        requireAuthenticated(principal);
        Venue venue = venueService.getRequiredVenue(venueId);
        validateQueueEnabled(venue);
        LocalDate queueDate = LocalDate.now();
        ensureNoActiveTicket(venueId, queueDate, principal.id());

        sequenceMapper.next(venueId, queueDate);
        Integer queueNo = sequenceMapper.currentConnectionValue();
        if (queueNo == null || queueNo <= 0) {
            throw new IllegalStateException("Queue sequence was not generated");
        }

        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNo(generateTicketNo(queueDate));
        ticket.setVenueId(venueId);
        ticket.setUserId(principal.id());
        ticket.setQueueDate(queueDate);
        ticket.setQueueNo(queueNo);
        ticket.setStatus(QueueTicketStatus.WAITING);
        ticket.setTakenAt(LocalDateTime.now());
        try {
            ticketMapper.insert(ticket);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "QUEUE_TICKET_DUPLICATE",
                    "当前地点已有有效排队号码"
            );
        }
        return QueueTicketResponse.from(ticket);
    }

    public QueueCurrentResponse current(Long venueId, LocalDate queueDate) {
        venueService.getRequiredVenue(venueId);
        LocalDate targetDate = queueDate == null ? LocalDate.now() : queueDate;
        List<QueueTicket> tickets = ticketMapper.selectCurrent(venueId, targetDate);
        Integer latestCalledNo = tickets.stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.CALLED)
                .max(Comparator.comparing(QueueTicket::getCalledAt))
                .map(QueueTicket::getQueueNo)
                .orElse(null);
        Integer nextWaitingNo = tickets.stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.WAITING)
                .map(QueueTicket::getQueueNo)
                .findFirst()
                .orElse(null);
        long waitingCount = tickets.stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.WAITING)
                .count();
        long calledCount = tickets.stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.CALLED)
                .count();
        return new QueueCurrentResponse(
                venueId,
                targetDate,
                latestCalledNo,
                nextWaitingNo,
                waitingCount,
                calledCount,
                tickets.stream().map(QueueTicketResponse::from).toList()
        );
    }

    public List<QueueTicketResponse> listMine(
            Long venueId,
            QueueTicketStatus status,
            LocalDate queueDate,
            AuthenticatedUser principal
    ) {
        requireAuthenticated(principal);
        return ticketMapper.selectList(
                        Wrappers.<QueueTicket>lambdaQuery()
                                .eq(QueueTicket::getUserId, principal.id())
                                .eq(venueId != null, QueueTicket::getVenueId, venueId)
                                .eq(status != null, QueueTicket::getStatus, status)
                                .eq(queueDate != null, QueueTicket::getQueueDate, queueDate)
                                .orderByDesc(QueueTicket::getTakenAt)
                                .orderByDesc(QueueTicket::getId)
                ).stream()
                .map(QueueTicketResponse::from)
                .toList();
    }

    @Transactional
    public QueueTicketResponse call(Long ticketId, AuthenticatedUser principal) {
        return transition(
                ticketId,
                QueueTicketStatus.WAITING,
                QueueTicketStatus.CALLED,
                principal
        );
    }

    @Transactional
    public QueueTicketResponse complete(Long ticketId, AuthenticatedUser principal) {
        return transition(
                ticketId,
                QueueTicketStatus.CALLED,
                QueueTicketStatus.COMPLETED,
                principal
        );
    }

    @Transactional
    public QueueTicketResponse miss(Long ticketId, AuthenticatedUser principal) {
        return transition(
                ticketId,
                QueueTicketStatus.CALLED,
                QueueTicketStatus.MISSED,
                principal
        );
    }

    private QueueTicketResponse transition(
            Long ticketId,
            QueueTicketStatus expected,
            QueueTicketStatus target,
            AuthenticatedUser principal
    ) {
        QueueTicket ticket = getRequiredTicket(ticketId);
        Venue venue = venueService.getRequiredVenue(ticket.getVenueId());
        venueService.requireOwnerOrAdmin(venue, principal);
        LocalDateTime changedAt = LocalDateTime.now();
        if (ticketMapper.transition(ticketId, expected, target, changedAt) != 1) {
            throw invalidStatus();
        }
        ticket.setStatus(target);
        if (target == QueueTicketStatus.CALLED) {
            ticket.setCalledAt(changedAt);
        } else if (target == QueueTicketStatus.COMPLETED) {
            ticket.setCompletedAt(changedAt);
        } else if (target == QueueTicketStatus.MISSED) {
            ticket.setMissedAt(changedAt);
        }
        return QueueTicketResponse.from(ticket);
    }

    private QueueTicket getRequiredTicket(Long ticketId) {
        QueueTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "QUEUE_TICKET_NOT_FOUND",
                    "排队号码不存在"
            );
        }
        return ticket;
    }

    private void validateQueueEnabled(Venue venue) {
        if (venue.getStatus() != VenueStatus.ACTIVE || !Boolean.TRUE.equals(venue.getQueueEnabled())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "QUEUE_VENUE_UNAVAILABLE",
                    "当前地点不可取号"
            );
        }
    }

    private void ensureNoActiveTicket(Long venueId, LocalDate queueDate, Long userId) {
        long count = ticketMapper.selectCount(
                Wrappers.<QueueTicket>lambdaQuery()
                        .eq(QueueTicket::getVenueId, venueId)
                        .eq(QueueTicket::getQueueDate, queueDate)
                        .eq(QueueTicket::getUserId, userId)
                        .in(
                                QueueTicket::getStatus,
                                QueueTicketStatus.WAITING,
                                QueueTicketStatus.CALLED
                        )
        );
        if (count > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "QUEUE_TICKET_DUPLICATE",
                    "当前地点已有有效排队号码"
            );
        }
    }

    private void requireAuthenticated(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED",
                    "登录状态无效"
            );
        }
    }

    private BusinessException invalidStatus() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "QUEUE_STATUS_INVALID",
                "当前排队号码状态不允许此操作"
        );
    }

    private String generateTicketNo(LocalDate queueDate) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
        return "QT" + queueDate.format(TICKET_DATE_FORMAT) + suffix;
    }
}
