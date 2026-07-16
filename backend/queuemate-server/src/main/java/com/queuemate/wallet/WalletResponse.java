package com.queuemate.wallet;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;

public record WalletResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,

        BigDecimal balance,
        WalletStatus status
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getStatus()
        );
    }
}
