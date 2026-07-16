package com.queuemate.venue;

import jakarta.validation.constraints.NotNull;

public record VenueStatusRequest(
        @NotNull(message = "地点状态不能为空")
        VenueStatus status
) {
}
