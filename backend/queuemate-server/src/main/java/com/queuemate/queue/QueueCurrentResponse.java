package com.queuemate.queue;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.util.List;

public record QueueCurrentResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long venueId,

        LocalDate queueDate,
        Integer latestCalledNo,
        Integer nextWaitingNo,
        long waitingCount,
        long calledCount,
        List<QueueTicketResponse> tickets
) {
}
