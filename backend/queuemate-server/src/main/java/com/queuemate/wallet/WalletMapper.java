package com.queuemate.wallet;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    @Select("select * from wallets where user_id = #{userId} for update")
    Wallet selectByUserIdForUpdate(@Param("userId") Long userId);

    @Update("""
            update wallets
            set balance = balance - #{amount}
            where id = #{walletId}
              and status = 'ACTIVE'
              and balance >= #{amount}
            """)
    int deductBalance(
            @Param("walletId") Long walletId,
            @Param("amount") BigDecimal amount
    );

    @Update("""
            update wallets
            set balance = balance + #{amount}
            where id = #{walletId}
              and status = 'ACTIVE'
            """)
    int addBalance(
            @Param("walletId") Long walletId,
            @Param("amount") BigDecimal amount
    );
}
