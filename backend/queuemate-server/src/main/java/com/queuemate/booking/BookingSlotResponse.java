package com.queuemate.booking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingSlotResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        @JsonSerialize(using = ToStringSerializer.class)
        Long venueId,

        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        Integer reservedCount,
        Integer availableCapacity,
        BigDecimal price,
        BookingSlotStatus status,

        @JsonSerialize(using = ToStringSerializer.class)
        Long createdBy
) {
    public static BookingSlotResponse from(BookingSlot slot) {
        int reserved = slot.getReservedCount() == null ? 0 : slot.getReservedCount();
        return new BookingSlotResponse(
                slot.getId(),
                slot.getVenueId(),
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getCapacity(),
                reserved,
                slot.getCapacity() - reserved,
                slot.getPrice(),
                slot.getStatus(),
                slot.getCreatedBy()
        );
    }
}
