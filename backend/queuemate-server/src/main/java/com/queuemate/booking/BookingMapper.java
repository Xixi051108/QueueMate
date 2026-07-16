package com.queuemate.booking;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingMapper extends BaseMapper<Booking> {

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
}
