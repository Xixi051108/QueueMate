package com.queuemate.wallet;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletAdminAdjustRequest(
        @NotNull(message = "调整金额不能为空")
        @DecimalMin(value = "-100000.00", message = "调整金额不能小于-100000")
        @DecimalMax(value = "100000.00", message = "调整金额不能大于100000")
        @Digits(integer = 8, fraction = 2, message = "调整金额最多保留两位小数")
        BigDecimal amount,

        @Size(max = 255, message = "备注最多255个字符")
        String remark
) {
}
