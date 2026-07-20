package com.queuemate.merchant;

import com.queuemate.venue.VenueCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantApplicationRequest(
        @NotBlank(message = "请填写商家或品牌名称")
        @Size(max = 100, message = "商家或品牌名称最多100个字符")
        String businessName,

        @NotBlank(message = "请填写联系人姓名")
        @Size(max = 100, message = "联系人姓名最多100个字符")
        String contactName,

        @NotBlank(message = "请填写联系电话")
        @Pattern(regexp = "^1\\d{10}$", message = "联系电话应为11位手机号")
        String contactPhone,

        @NotBlank(message = "请填写拟入驻门店名称")
        @Size(max = 100, message = "门店名称最多100个字符")
        String venueName,

        @NotNull(message = "请选择门店类别")
        VenueCategory venueCategory,

        @NotBlank(message = "请填写门店详细地址")
        @Size(max = 255, message = "门店地址最多255个字符")
        String addressText,

        @Size(max = 500, message = "经营介绍最多500个字符")
        String description
) {
}
