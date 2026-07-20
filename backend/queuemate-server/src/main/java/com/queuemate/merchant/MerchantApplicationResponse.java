package com.queuemate.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.queuemate.user.User;
import com.queuemate.venue.VenueCategory;
import java.time.LocalDateTime;

public record MerchantApplicationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long applicantId,
        String applicantUsername,
        String applicantDisplayName,
        String businessName,
        String contactName,
        String contactPhone,
        String venueName,
        VenueCategory venueCategory,
        String addressText,
        String description,
        MerchantApplicationStatus status,
        String reviewNote,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewerId,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
    public static MerchantApplicationResponse from(MerchantApplication application, User applicant) {
        return new MerchantApplicationResponse(
                application.getId(), application.getApplicantId(), applicant.getUsername(), applicant.getDisplayName(),
                application.getBusinessName(), application.getContactName(), application.getContactPhone(),
                application.getVenueName(), application.getVenueCategory(), application.getAddressText(),
                application.getDescription(), application.getStatus(), application.getReviewNote(),
                application.getReviewerId(), application.getSubmittedAt(), application.getReviewedAt()
        );
    }
}
