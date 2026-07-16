package com.queuemate.booking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingVoucherRedeemResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long bookingId,

        String bookingNo,
        BookingStatus status,
        BookingPayStatus payStatus,
        BigDecimal paidAmount,
        BookingVoucherStatus voucherStatus,

        @JsonSerialize(using = ToStringSerializer.class)
        Long redeemedBy,

        LocalDateTime redeemedAt
) {
}
