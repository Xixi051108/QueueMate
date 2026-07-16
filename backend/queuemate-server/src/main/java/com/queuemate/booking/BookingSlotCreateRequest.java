package com.queuemate.booking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingSlotCreateRequest(
        @NotNull(message = "时段日期不能为空")
        @FutureOrPresent(message = "时段日期不能早于今天")
        LocalDate slotDate,

        @NotNull(message = "开始时间不能为空")
        LocalTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalTime endTime,

        @NotNull(message = "容量不能为空")
        @Positive(message = "容量必须大于0")
        Integer capacity,

        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.00", message = "价格不能小于0")
        @Digits(integer = 8, fraction = 2, message = "价格最多8位整数和2位小数")
        BigDecimal price
) {
}
