package com.queuemate.wallet;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletRechargeRequest(
        @NotNull(message = "充值金额不能为空")
        @DecimalMin(value = "0.01", message = "充值金额必须大于0")
        @DecimalMax(value = "100000.00", message = "单次充值金额不能超过100000")
        @Digits(integer = 8, fraction = 2, message = "充值金额最多保留两位小数")
        BigDecimal amount,

        @Size(max = 255, message = "备注最多255个字符")
        String remark
) {
}
