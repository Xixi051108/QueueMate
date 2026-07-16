package com.queuemate.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingCreateRequest(
        @NotNull(message = "时段ID不能为空")
        @Positive(message = "时段ID必须为正数")
        Long slotId
) {
}
