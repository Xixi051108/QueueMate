package com.queuemate.queue;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record QueueTicketResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        String ticketNo,

        @JsonSerialize(using = ToStringSerializer.class)
        Long venueId,

        Integer queueNo,
        LocalDate queueDate,
        QueueTicketStatus status,
        LocalDateTime takenAt,
        LocalDateTime calledAt,
        LocalDateTime completedAt,
        LocalDateTime missedAt
) {
    public static QueueTicketResponse from(QueueTicket ticket) {
        return new QueueTicketResponse(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getVenueId(),
                ticket.getQueueNo(),
                ticket.getQueueDate(),
                ticket.getStatus(),
                ticket.getTakenAt(),
                ticket.getCalledAt(),
                ticket.getCompletedAt(),
                ticket.getMissedAt()
        );
    }
}
