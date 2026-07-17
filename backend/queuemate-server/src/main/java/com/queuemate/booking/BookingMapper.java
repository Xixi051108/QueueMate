package com.queuemate.booking;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingMapper extends BaseMapper<Booking> {

    @Select("""
            select count(*)
            from bookings
            where user_id = #{userId}
              and slot_id = #{slotId}
              and status = 'BOOKED'
            """)
    long countActiveBooking(
            @Param("userId") Long userId,
            @Param("slotId") Long slotId
    );

    @Update("""
            update bookings
            set status = 'CANCELLED',
                cancel_reason = #{reason},
                cancelled_at = #{cancelledAt}
            where id = #{bookingId}
              and status = 'BOOKED'
            """)
    int cancelBooked(
            @Param("bookingId") Long bookingId,
            @Param("reason") String reason,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );

    @Update("""
            update bookings
            set status = 'CANCELLED',
                pay_status = 'REFUNDED',
                cancel_reason = #{reason},
                cancelled_at = #{cancelledAt},
                refunded_at = #{cancelledAt}
            where id = #{bookingId}
              and status = 'BOOKED'
              and pay_status = 'PAID'
            """)
    int cancelPaidBooking(
            @Param("bookingId") Long bookingId,
            @Param("reason") String reason,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );

    @Update("""
            update bookings
            set status = 'FULFILLED'
            where id = #{bookingId}
              and status = 'BOOKED'
              and pay_status = 'PAID'
            """)
    int fulfillPaidBooking(@Param("bookingId") Long bookingId);

    @Update("""
            update bookings b
            join booking_vouchers v on v.booking_id = b.id
            set b.status = 'NO_SHOW'
            where b.status = 'BOOKED'
              and b.pay_status = 'PAID'
              and v.status = 'EXPIRED'
            """)
    int markExpiredPaidBookingsNoShow();
}
