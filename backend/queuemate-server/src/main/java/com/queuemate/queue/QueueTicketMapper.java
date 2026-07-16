package com.queuemate.queue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QueueTicketMapper extends BaseMapper<QueueTicket> {

    @Select("""
            select *
            from queue_tickets
            where venue_id = #{venueId}
              and queue_date = #{queueDate}
              and status in ('WAITING', 'CALLED')
            order by queue_no
            """)
    List<QueueTicket> selectCurrent(
            @Param("venueId") Long venueId,
            @Param("queueDate") LocalDate queueDate
    );

    @Update("""
            update queue_tickets
            set status = #{targetStatus},
                called_at = case when #{targetStatus} = 'CALLED' then #{changedAt} else called_at end,
                completed_at = case when #{targetStatus} = 'COMPLETED' then #{changedAt} else completed_at end,
                missed_at = case when #{targetStatus} = 'MISSED' then #{changedAt} else missed_at end
            where id = #{ticketId}
              and status = #{expectedStatus}
            """)
    int transition(
            @Param("ticketId") Long ticketId,
            @Param("expectedStatus") QueueTicketStatus expectedStatus,
            @Param("targetStatus") QueueTicketStatus targetStatus,
            @Param("changedAt") LocalDateTime changedAt
    );
}
