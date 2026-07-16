package com.queuemate.booking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        String bookingNo,

        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,

        @JsonSerialize(using = ToStringSerializer.class)
        Long venueId,

        @JsonSerialize(using = ToStringSerializer.class)
        Long slotId,

        BookingStatus status,
        BookingPayStatus payStatus,
        BigDecimal paidAmount,
        String cancelReason,
        LocalDateTime bookedAt,
        LocalDateTime cancelledAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingNo(),
                booking.getUserId(),
                booking.getVenueId(),
                booking.getSlotId(),
                booking.getStatus(),
                booking.getPayStatus(),
                booking.getPaidAmount(),
                booking.getCancelReason(),
                booking.getBookedAt(),
                booking.getCancelledAt()
        );
    }
}
