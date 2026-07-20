package com.queuemate.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.queuemate.user.User;

public record MerchantSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String displayName,
        String phone
) {
    public static MerchantSummaryResponse from(User user) {
        return new MerchantSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone());
    }
}
