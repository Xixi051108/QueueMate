package com.queuemate.wallet;

import java.math.BigDecimal;

public record WalletBalanceChange(
        Long walletId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter
) {
}
