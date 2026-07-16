package com.queuemate.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTransactionMapper transactionMapper;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletMapper, transactionMapper);
    }

    @Test
    void rechargeLocksWalletAndWritesTransaction() {
        Wallet wallet = wallet("20.00", WalletStatus.ACTIVE);
        when(walletMapper.selectByUserIdForUpdate(3001L)).thenReturn(wallet);
        when(walletMapper.addBalance(9001L, new BigDecimal("30.00"))).thenReturn(1);

        WalletResponse response = walletService.recharge(
                new WalletRechargeRequest(new BigDecimal("30.00"), "  test recharge  "),
                user()
        );

        assertThat(response.balance()).isEqualByComparingTo("50.00");
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.RECHARGE);
        assertThat(captor.getValue().getBalanceBefore()).isEqualByComparingTo("20.00");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("50.00");
        assertThat(captor.getValue().getRemark()).isEqualTo("test recharge");
    }

    @Test
    void paidBookingDeductsBalanceAndWritesPayment() {
        Wallet wallet = wallet("80.00", WalletStatus.ACTIVE);
        when(walletMapper.selectByUserIdForUpdate(3001L)).thenReturn(wallet);
        when(walletMapper.deductBalance(9001L, new BigDecimal("30.00"))).thenReturn(1);

        WalletBalanceChange result = walletService.chargeBooking(
                3001L,
                new BigDecimal("30.00"),
                "BKTEST"
        );

        assertThat(result.balanceAfter()).isEqualByComparingTo("50.00");
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.PAYMENT);
        assertThat(captor.getValue().getBizNo()).isEqualTo("BKTEST");
    }

    @Test
    void insufficientBalanceDoesNotWritePayment() {
        when(walletMapper.selectByUserIdForUpdate(3001L))
                .thenReturn(wallet("10.00", WalletStatus.ACTIVE));

        assertBusinessError(
                () -> walletService.chargeBooking(3001L, new BigDecimal("30.00"), "BKTEST"),
                HttpStatus.CONFLICT,
                "WALLET_BALANCE_NOT_ENOUGH"
        );
        verify(walletMapper, never()).deductBalance(any(), any());
        verify(transactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    void frozenWalletCannotPay() {
        when(walletMapper.selectByUserIdForUpdate(3001L))
                .thenReturn(wallet("80.00", WalletStatus.FROZEN));

        assertBusinessError(
                () -> walletService.chargeBooking(3001L, new BigDecimal("30.00"), "BKTEST"),
                HttpStatus.CONFLICT,
                "WALLET_FROZEN"
        );
    }

    @Test
    void refundCreditsBalanceAndWritesRefund() {
        Wallet wallet = wallet("50.00", WalletStatus.ACTIVE);
        when(walletMapper.selectByUserIdForUpdate(3001L)).thenReturn(wallet);
        when(walletMapper.addBalance(9001L, new BigDecimal("30.00"))).thenReturn(1);

        WalletBalanceChange result = walletService.refundBooking(
                3001L,
                new BigDecimal("30.00"),
                "BKTEST"
        );

        assertThat(result.balanceAfter()).isEqualByComparingTo("80.00");
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.REFUND);
    }

    private Wallet wallet(String balance, WalletStatus status) {
        Wallet wallet = new Wallet();
        wallet.setId(9001L);
        wallet.setUserId(3001L);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setStatus(status);
        return wallet;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(3001L, "alice", UserRole.USER);
    }

    private void assertBusinessError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}
