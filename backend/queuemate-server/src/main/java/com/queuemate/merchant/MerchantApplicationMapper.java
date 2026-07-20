package com.queuemate.merchant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantApplicationMapper extends BaseMapper<MerchantApplication> {

    @Select("select * from merchant_applications where id = #{id} for update")
    MerchantApplication selectByIdForUpdate(@Param("id") Long id);
}
