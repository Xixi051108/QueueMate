package com.queuemate.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingVoucherRedeemRequest(
        @NotBlank(message = "消费码不能为空")
        @Size(min = 8, max = 32, message = "消费码长度必须为8到32个字符")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "消费码格式不正确")
        String consumptionCode
) {
}
