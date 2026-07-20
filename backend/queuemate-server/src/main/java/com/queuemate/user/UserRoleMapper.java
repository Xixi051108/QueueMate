package com.queuemate.user;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper {

    @Select("select role from user_roles where user_id = #{userId} order by role")
    List<UserRole> selectRolesByUserId(@Param("userId") Long userId);

    @Insert("""
            insert ignore into user_roles (user_id, role, granted_by)
            values (#{userId}, #{role}, #{grantedBy})
            """)
    int insertRole(
            @Param("userId") Long userId,
            @Param("role") UserRole role,
            @Param("grantedBy") Long grantedBy
    );
}
