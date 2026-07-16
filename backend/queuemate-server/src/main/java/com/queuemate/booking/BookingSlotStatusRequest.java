package com.queuemate.booking;

import jakarta.validation.constraints.NotNull;

public record BookingSlotStatusRequest(
        @NotNull(message = "时段状态不能为空")
        BookingSlotStatus status
) {
}
