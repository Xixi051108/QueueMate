package com.queuemate.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WalletService {

    private static final String BOOKING_BIZ_TYPE = "BOOKING";
    private static final String WALLET_BIZ_TYPE = "WALLET";
    private static final String WALLET_ADMIN_BIZ_TYPE = "WALLET_ADMIN";

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;

    public WalletService(
            WalletMapper walletMapper,
            WalletTransactionMapper transactionMapper
    ) {
        this.walletMapper = walletMapper;
        this.transactionMapper = transactionMapper;
    }

    public WalletResponse getMine(AuthenticatedUser principal) {
        requireUser(principal);
        return WalletResponse.from(getRequiredWallet(principal.id()));
    }

    @Transactional
    public WalletResponse recharge(WalletRechargeRequest request, AuthenticatedUser principal) {
        requireUser(principal);
        Wallet wallet = lockRequiredWallet(principal.id());
        requireActive(wallet);

        BigDecimal before = wallet.getBalance();
        BigDecimal after = before.add(request.amount());
        if (walletMapper.addBalance(wallet.getId(), request.amount()) != 1) {
            throw walletUnavailable();
        }
        insertTransaction(
                wallet,
                WalletTransactionType.RECHARGE,
                request.amount(),
                before,
                after,
                WALLET_BIZ_TYPE,
                null,
                normalizeRemark(request.remark(), "模拟充值")
        );
        wallet.setBalance(after);
        return WalletResponse.from(wallet);
    }

    public List<WalletTransactionResponse> listMine(
            WalletTransactionType type,
            AuthenticatedUser principal
    ) {
        requireUser(principal);
        LambdaQueryWrapper<WalletTransaction> query = Wrappers.<WalletTransaction>lambdaQuery()
                .eq(WalletTransaction::getUserId, principal.id())
                .eq(type != null, WalletTransaction::getType, type)
                .orderByDesc(WalletTransaction::getCreatedAt)
                .orderByDesc(WalletTransaction::getId);
        return transactionMapper.selectList(query).stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    public List<WalletTransactionResponse> listAllTransactions(
            Long userId,
            WalletTransactionType type,
            AuthenticatedUser principal
    ) {
        requireAdmin(principal);
        LambdaQueryWrapper<WalletTransaction> query = Wrappers.<WalletTransaction>lambdaQuery()
                .eq(userId != null, WalletTransaction::getUserId, userId)
                .eq(type != null, WalletTransaction::getType, type)
                .orderByDesc(WalletTransaction::getCreatedAt)
                .orderByDesc(WalletTransaction::getId);
        return transactionMapper.selectList(query).stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    @Transactional
    public WalletResponse adjust(
            Long userId,
            WalletAdminAdjustRequest request,
            AuthenticatedUser principal
    ) {
        requireAdmin(principal);
        if (request.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "WALLET_ADJUSTMENT_INVALID",
                    "调整金额不能为0"
            );
        }
        Wallet wallet = lockRequiredWallet(userId);
        requireActive(wallet);
        BigDecimal before = wallet.getBalance();
        BigDecimal absoluteAmount = request.amount().abs();
        BigDecimal after;
        if (request.amount().signum() > 0) {
            if (walletMapper.addBalance(wallet.getId(), absoluteAmount) != 1) {
                throw walletUnavailable();
            }
            after = before.add(absoluteAmount);
        } else {
            if (before.compareTo(absoluteAmount) < 0
                    || walletMapper.deductBalance(wallet.getId(), absoluteAmount) != 1) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "WALLET_BALANCE_NOT_ENOUGH",
                        "钱包余额不足"
                );
            }
            after = before.subtract(absoluteAmount);
        }
        insertTransaction(
                wallet,
                WalletTransactionType.ADJUSTMENT,
                absoluteAmount,
                before,
                after,
                WALLET_ADMIN_BIZ_TYPE,
                generateAdjustmentBizNo(),
                normalizeRemark(request.remark(), "管理员余额调整")
        );
        wallet.setBalance(after);
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletBalanceChange chargeBooking(
            Long userId,
            BigDecimal amount,
            String bookingNo
    ) {
        Wallet wallet = lockRequiredWallet(userId);
        requireActive(wallet);
        BigDecimal before = wallet.getBalance();
        if (before.compareTo(amount) < 0
                || walletMapper.deductBalance(wallet.getId(), amount) != 1) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "WALLET_BALANCE_NOT_ENOUGH",
                    "钱包余额不足"
            );
        }
        BigDecimal after = before.subtract(amount);
        try {
            insertTransaction(
                    wallet,
                    WalletTransactionType.PAYMENT,
                    amount,
                    before,
                    after,
                    BOOKING_BIZ_TYPE,
                    bookingNo,
                    "预约预付消费"
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_DUPLICATE",
                    "预约不能重复扣款"
            );
        }
        return new WalletBalanceChange(wallet.getId(), before, after);
    }

    @Transactional
    public WalletBalanceChange refundBooking(
            Long userId,
            BigDecimal amount,
            String bookingNo
    ) {
        Wallet wallet = lockRequiredWallet(userId);
        requireActive(wallet);
        BigDecimal before = wallet.getBalance();
        if (walletMapper.addBalance(wallet.getId(), amount) != 1) {
            throw walletUnavailable();
        }
        BigDecimal after = before.add(amount);
        try {
            insertTransaction(
                    wallet,
                    WalletTransactionType.REFUND,
                    amount,
                    before,
                    after,
                    BOOKING_BIZ_TYPE,
                    bookingNo,
                    "预约取消退款"
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REFUND_DUPLICATE",
                    "预约不能重复退款"
            );
        }
        return new WalletBalanceChange(wallet.getId(), before, after);
    }

    private Wallet getRequiredWallet(Long userId) {
        Wallet wallet = walletMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery()
                        .eq(Wallet::getUserId, userId)
                        .last("limit 1")
        );
        if (wallet == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "钱包不存在");
        }
        return wallet;
    }

    private Wallet lockRequiredWallet(Long userId) {
        Wallet wallet = walletMapper.selectByUserIdForUpdate(userId);
        if (wallet == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "钱包不存在");
        }
        return wallet;
    }

    private void requireActive(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw walletUnavailable();
        }
    }

    private BusinessException walletUnavailable() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "WALLET_FROZEN",
                "钱包当前不可用"
        );
    }

    private void insertTransaction(
            Wallet wallet,
            WalletTransactionType type,
            BigDecimal amount,
            BigDecimal before,
            BigDecimal after,
            String bizType,
            String bizNo,
            String remark
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setWalletId(wallet.getId());
        transaction.setUserId(wallet.getUserId());
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after);
        transaction.setBizType(bizType);
        transaction.setBizNo(bizNo);
        transaction.setStatus(WalletTransactionStatus.SUCCESS);
        transaction.setRemark(remark);
        transactionMapper.insert(transaction);
    }

    private void requireUser(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (principal.role() != UserRole.USER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "仅普通用户可以使用钱包");
        }
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (principal.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "仅管理员可以执行此操作");
        }
    }

    private String normalizeRemark(String remark, String fallback) {
        return StringUtils.hasText(remark) ? remark.trim() : fallback;
    }

    private String generateTransactionNo() {
        return "WT" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String generateAdjustmentBizNo() {
        return "ADJ" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
