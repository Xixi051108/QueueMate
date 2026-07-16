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
        LocalDateTime cancelledAt,
        BookingVoucherResponse voucher
) {
    public static BookingResponse from(Booking booking) {
        return from(booking, null);
    }

    public static BookingResponse from(Booking booking, BookingVoucher voucher) {
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
                booking.getCancelledAt(),
                BookingVoucherResponse.from(voucher)
        );
    }

    public BookingResponse(
            Long id,
            String bookingNo,
            Long userId,
            Long venueId,
            Long slotId,
            BookingStatus status,
            BookingPayStatus payStatus,
            BigDecimal paidAmount,
            String cancelReason,
            LocalDateTime bookedAt,
            LocalDateTime cancelledAt
    ) {
        this(
                id,
                bookingNo,
                userId,
                venueId,
                slotId,
                status,
                payStatus,
                paidAmount,
                cancelReason,
                bookedAt,
                cancelledAt,
                null
        );
    }
}
