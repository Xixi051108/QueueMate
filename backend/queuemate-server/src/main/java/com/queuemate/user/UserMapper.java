package com.queuemate.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            select u.* from users u
            join user_roles ur on ur.user_id = u.id and ur.role = 'MERCHANT'
            where u.status = 'ACTIVE'
            order by u.display_name, u.id
            """)
    List<User> selectActiveMerchants();
}
