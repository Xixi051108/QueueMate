package com.queuemate.stats;

public record BusyHourResponse(
        String hour,
        long bookingCount,
        long queueCount,
        long heatScore
) {
}
