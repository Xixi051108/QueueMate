package com.queuemate.venue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VenueCreateRequest(
        @NotBlank(message = "地点名称不能为空")
        @Size(max = 100, message = "地点名称最多100个字符")
        String name,

        @NotNull(message = "地点类别不能为空")
        VenueCategory category,

        @Size(max = 500, message = "地点描述最多500个字符")
        String description,

        @Size(max = 255, message = "地点地址最多255个字符")
        String addressText,

        @NotNull(message = "是否支持排队不能为空")
        Boolean queueEnabled,

        @NotNull(message = "是否支持预约不能为空")
        Boolean bookingEnabled,

        @NotNull(message = "默认价格不能为空")
        @DecimalMin(value = "0.00", message = "默认价格不能小于0")
        @Digits(integer = 8, fraction = 2, message = "默认价格最多8位整数和2位小数")
        BigDecimal defaultPrice,

        @Positive(message = "商家ID必须为正数")
        Long merchantId
) {
}
