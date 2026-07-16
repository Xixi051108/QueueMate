package com.queuemate.booking;

import jakarta.validation.constraints.Size;

public record BookingCancelRequest(
        @Size(max = 255, message = "取消原因最多255个字符")
        String reason
) {
}
