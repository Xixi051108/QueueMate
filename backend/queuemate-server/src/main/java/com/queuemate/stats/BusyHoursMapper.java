package com.queuemate.stats;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusyHoursMapper {

    @Select("""
            select hour(bs.start_time) as hour_value,
                   count(*) as count_value
            from bookings b
            join booking_slots bs on bs.id = b.slot_id
            where b.venue_id = #{venueId}
              and bs.slot_date between #{dateFrom} and #{dateTo}
              and b.status in ('BOOKED', 'FULFILLED', 'NO_SHOW')
            group by hour(bs.start_time)
            order by hour_value
            """)
    List<HourlyCountRow> countBookingsByHour(
            @Param("venueId") Long venueId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    @Select("""
            select hour(taken_at) as hour_value,
                   count(*) as count_value
            from queue_tickets
            where venue_id = #{venueId}
              and queue_date between #{dateFrom} and #{dateTo}
            group by hour(taken_at)
            order by hour_value
            """)
    List<HourlyCountRow> countQueueTicketsByHour(
            @Param("venueId") Long venueId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );
}
