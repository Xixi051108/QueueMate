package com.queuemate.booking;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingVoucherMapper extends BaseMapper<BookingVoucher> {

    @Select("select * from booking_vouchers where booking_id = #{bookingId} limit 1")
    BookingVoucher selectByBookingId(@Param("bookingId") Long bookingId);

    @Select("select * from booking_vouchers where booking_id = #{bookingId} for update")
    BookingVoucher selectByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    @Select("select * from booking_vouchers where consumption_code = #{code} for update")
    BookingVoucher selectByCodeForUpdate(@Param("code") String code);

    @Update("""
            update booking_vouchers
            set status = 'REDEEMED',
                redeemed_by = #{redeemedBy},
                redeemed_at = #{redeemedAt}
            where id = #{voucherId}
              and status = 'AVAILABLE'
            """)
    int redeemAvailable(
            @Param("voucherId") Long voucherId,
            @Param("redeemedBy") Long redeemedBy,
            @Param("redeemedAt") LocalDateTime redeemedAt
    );

    @Update("""
            update booking_vouchers
            set status = 'VOID',
                voided_at = #{voidedAt}
            where id = #{voucherId}
              and status = 'AVAILABLE'
            """)
    int voidAvailable(
            @Param("voucherId") Long voucherId,
            @Param("voidedAt") LocalDateTime voidedAt
    );

    @Update("""
            update booking_vouchers
            set status = 'EXPIRED',
                expired_at = #{expiredAt}
            where status = 'AVAILABLE'
              and valid_until < #{expiredAt}
            """)
    int expireDue(@Param("expiredAt") LocalDateTime expiredAt);
}
