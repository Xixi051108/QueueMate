package com.queuemate.merchant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.queuemate.venue.VenueCategory;
import java.time.LocalDateTime;

@TableName("merchant_applications")
public class MerchantApplication {

    private Long id;
    private Long applicantId;
    private String businessName;
    private String contactName;
    private String contactPhone;
    private String venueName;
    private VenueCategory venueCategory;
    private String addressText;
    private String description;
    private MerchantApplicationStatus status;
    private String reviewNote;
    private Long reviewerId;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public VenueCategory getVenueCategory() { return venueCategory; }
    public void setVenueCategory(VenueCategory venueCategory) { this.venueCategory = venueCategory; }
    public String getAddressText() { return addressText; }
    public void setAddressText(String addressText) { this.addressText = addressText; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public MerchantApplicationStatus getStatus() { return status; }
    public void setStatus(MerchantApplicationStatus status) { this.status = status; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
