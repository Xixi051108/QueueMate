package com.queuemate.booking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingVoucherResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        String consumptionCode,
        BigDecimal amount,
        BookingVoucherStatus status,
        LocalDateTime validFrom,
        LocalDateTime validUntil,

        @JsonSerialize(using = ToStringSerializer.class)
        Long redeemedBy,

        LocalDateTime redeemedAt
) {
    public static BookingVoucherResponse from(BookingVoucher voucher) {
        if (voucher == null) {
            return null;
        }
        return new BookingVoucherResponse(
                voucher.getId(),
                voucher.getConsumptionCode(),
                voucher.getAmount(),
                voucher.getStatus(),
                voucher.getValidFrom(),
                voucher.getValidUntil(),
                voucher.getRedeemedBy(),
                voucher.getRedeemedAt()
        );
    }
}
