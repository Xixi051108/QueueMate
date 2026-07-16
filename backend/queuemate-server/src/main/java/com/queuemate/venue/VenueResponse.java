package com.queuemate.venue;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;

public record VenueResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String name,
        VenueCategory category,
        String description,
        @JsonSerialize(using = ToStringSerializer.class)
        Long merchantId,
        String addressText,
        Boolean queueEnabled,
        Boolean bookingEnabled,
        BigDecimal defaultPrice,
        VenueStatus status
) {
    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getCategory(),
                venue.getDescription(),
                venue.getMerchantId(),
                venue.getAddressText(),
                venue.getQueueEnabled(),
                venue.getBookingEnabled(),
                venue.getDefaultPrice(),
                venue.getStatus()
        );
    }
}
