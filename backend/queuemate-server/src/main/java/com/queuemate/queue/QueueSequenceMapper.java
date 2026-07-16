package com.queuemate.queue;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QueueSequenceMapper {

    @Insert("""
            insert into queue_daily_sequences (venue_id, queue_date, last_no)
            values (#{venueId}, #{queueDate}, last_insert_id(1))
            on duplicate key update last_no = last_insert_id(last_no + 1)
            """)
    int next(
            @Param("venueId") Long venueId,
            @Param("queueDate") LocalDate queueDate
    );

    @Select("select last_insert_id()")
    Integer currentConnectionValue();
}
