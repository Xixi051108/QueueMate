package com.queuemate.booking;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingSlotMapper extends BaseMapper<BookingSlot> {

    @Update("""
            update booking_slots bs
            join venues v on v.id = bs.venue_id
            set bs.reserved_count = bs.reserved_count + 1
            where bs.id = #{slotId}
              and bs.status = 'OPEN'
              and bs.reserved_count < bs.capacity
              and v.status = 'ACTIVE'
              and v.booking_enabled = 1
            """)
    int reserveCapacity(@Param("slotId") Long slotId);

    @Update("""
            update booking_slots
            set reserved_count = reserved_count - 1
            where id = #{slotId}
              and reserved_count > 0
            """)
    int releaseCapacity(@Param("slotId") Long slotId);
}
