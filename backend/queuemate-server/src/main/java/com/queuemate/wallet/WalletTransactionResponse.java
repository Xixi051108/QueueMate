package com.queuemate.wallet;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        String transactionNo,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String bizType,
        String bizNo,
        WalletTransactionStatus status,
        String remark,
        LocalDateTime createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getTransactionNo(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getBizType(),
                transaction.getBizNo(),
                transaction.getStatus(),
                transaction.getRemark(),
                transaction.getCreatedAt()
        );
    }
}
