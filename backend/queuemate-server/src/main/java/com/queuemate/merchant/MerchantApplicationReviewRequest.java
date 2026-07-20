package com.queuemate.merchant;

import jakarta.validation.constraints.Size;

public record MerchantApplicationReviewRequest(
        @Size(max = 500, message = "审核说明最多500个字符")
        String reviewNote
) {
}
