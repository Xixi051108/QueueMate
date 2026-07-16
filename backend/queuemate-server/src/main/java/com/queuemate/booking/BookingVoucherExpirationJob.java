package com.queuemate.booking;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingVoucherExpirationJob {

    private final BookingVoucherService voucherService;

    public BookingVoucherExpirationJob(BookingVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Scheduled(
            fixedDelayString = "${queuemate.voucher-expiration-delay-ms:60000}",
            initialDelayString = "${queuemate.voucher-expiration-initial-delay-ms:60000}"
    )
    public void expireDueVouchers() {
        voucherService.expireDueVouchers(LocalDateTime.now());
    }
}
