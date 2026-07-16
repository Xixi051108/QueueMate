package com.queuemate.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import com.queuemate.venue.VenueStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingVoucherServiceTest {

    @Mock
    private BookingVoucherMapper voucherMapper;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private VenueService venueService;

    private BookingVoucherService voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new BookingVoucherService(voucherMapper, bookingMapper, venueService);
    }

    @Test
    void paidBookingCreatesAvailableVoucher() {
        when(voucherMapper.insert(any(BookingVoucher.class))).thenAnswer(invocation -> {
            BookingVoucher voucher = invocation.getArgument(0);
            voucher.setId(8001L);
            return 1;
        });

        BookingSlot slot = currentSlot();
        BookingVoucher voucher = voucherService.createForPaidBooking(paidBooking(), slot);

        assertThat(voucher.getConsumptionCode()).startsWith("QM").hasSize(12);
        assertThat(voucher.getStatus()).isEqualTo(BookingVoucherStatus.AVAILABLE);
        assertThat(voucher.getAmount()).isEqualByComparingTo("20.00");
        assertThat(voucher.getValidFrom()).isEqualTo(
                LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).minusMinutes(30)
        );
    }

    @Test
    void merchantRedeemsOwnedVoucherInWindow() {
        BookingVoucher voucher = availableVoucher();
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue());
        when(voucherMapper.selectByCodeForUpdate("QMTESTCODE01")).thenReturn(voucher);
        when(bookingMapper.selectById(6001L)).thenReturn(paidBooking());
        when(voucherMapper.redeemAvailable(any(), any(), any())).thenReturn(1);
        when(bookingMapper.fulfillPaidBooking(6001L)).thenReturn(1);

        BookingVoucherRedeemResponse response = voucherService.redeem(
                4002L,
                new BookingVoucherRedeemRequest("qmtestcode01"),
                merchant()
        );

        assertThat(response.status()).isEqualTo(BookingStatus.FULFILLED);
        assertThat(response.voucherStatus()).isEqualTo(BookingVoucherStatus.REDEEMED);
        verify(venueService).requireOwnerOrAdmin(any(), any());
    }

    @Test
    void crossVenueVoucherCannotBeRedeemed() {
        BookingVoucher voucher = availableVoucher();
        voucher.setVenueId(4003L);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue());
        when(voucherMapper.selectByCodeForUpdate("QMTESTCODE01")).thenReturn(voucher);

        assertBusinessError(
                () -> voucherService.redeem(
                        4002L,
                        new BookingVoucherRedeemRequest("QMTESTCODE01"),
                        merchant()
                ),
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED"
        );
        verify(bookingMapper, never()).fulfillPaidBooking(any());
    }

    @Test
    void voucherOutsideWindowCannotBeRedeemed() {
        BookingVoucher voucher = availableVoucher();
        voucher.setValidFrom(LocalDateTime.now().plusMinutes(5));
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue());
        when(voucherMapper.selectByCodeForUpdate("QMTESTCODE01")).thenReturn(voucher);

        assertBusinessError(
                () -> voucherService.redeem(
                        4002L,
                        new BookingVoucherRedeemRequest("QMTESTCODE01"),
                        merchant()
                ),
                HttpStatus.CONFLICT,
                "CONSUMPTION_CODE_OUT_OF_WINDOW"
        );
    }

    @Test
    void repeatedVoucherRedeemIsRejected() {
        BookingVoucher voucher = availableVoucher();
        voucher.setStatus(BookingVoucherStatus.REDEEMED);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue());
        when(voucherMapper.selectByCodeForUpdate("QMTESTCODE01")).thenReturn(voucher);

        assertBusinessError(
                () -> voucherService.redeem(
                        4002L,
                        new BookingVoucherRedeemRequest("QMTESTCODE01"),
                        merchant()
                ),
                HttpStatus.CONFLICT,
                "CONSUMPTION_CODE_STATUS_INVALID"
        );
    }

    @Test
    void concurrentSecondRedeemIsRejectedByConditionalUpdate() {
        BookingVoucher voucher = availableVoucher();
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue());
        when(voucherMapper.selectByCodeForUpdate("QMTESTCODE01")).thenReturn(voucher);
        when(bookingMapper.selectById(6001L)).thenReturn(paidBooking());
        when(voucherMapper.redeemAvailable(any(), any(), any())).thenReturn(0);

        assertBusinessError(
                () -> voucherService.redeem(
                        4002L,
                        new BookingVoucherRedeemRequest("QMTESTCODE01"),
                        merchant()
                ),
                HttpStatus.CONFLICT,
                "CONSUMPTION_CODE_STATUS_INVALID"
        );
        verify(bookingMapper, never()).fulfillPaidBooking(any());
    }

    @Test
    void refundedVoucherIsVoidedOnce() {
        BookingVoucher voucher = availableVoucher();
        when(voucherMapper.voidAvailable(any(), any())).thenReturn(1);

        voucherService.voidRefundedVoucher(voucher, LocalDateTime.now());

        assertThat(voucher.getStatus()).isEqualTo(BookingVoucherStatus.VOID);
    }

    @Test
    void dueVouchersExpireAndBookingsBecomeNoShow() {
        when(voucherMapper.expireDue(any())).thenReturn(2);
        when(bookingMapper.markExpiredPaidBookingsNoShow()).thenReturn(2);

        int expired = voucherService.expireDueVouchers(LocalDateTime.now());

        assertThat(expired).isEqualTo(2);
        verify(bookingMapper).markExpiredPaidBookingsNoShow();
    }

    private Booking paidBooking() {
        Booking booking = new Booking();
        booking.setId(6001L);
        booking.setBookingNo("BKTEST");
        booking.setUserId(3001L);
        booking.setVenueId(4002L);
        booking.setSlotId(5001L);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setPayStatus(BookingPayStatus.PAID);
        booking.setPaidAmount(new BigDecimal("20.00"));
        return booking;
    }

    private BookingSlot currentSlot() {
        BookingSlot slot = new BookingSlot();
        slot.setId(5001L);
        slot.setVenueId(4002L);
        slot.setSlotDate(LocalDate.now());
        slot.setStartTime(LocalTime.now().plusMinutes(10));
        slot.setEndTime(LocalTime.now().plusHours(1));
        slot.setPrice(new BigDecimal("20.00"));
        return slot;
    }

    private BookingVoucher availableVoucher() {
        BookingVoucher voucher = new BookingVoucher();
        voucher.setId(8001L);
        voucher.setBookingId(6001L);
        voucher.setUserId(3001L);
        voucher.setVenueId(4002L);
        voucher.setConsumptionCode("QMTESTCODE01");
        voucher.setAmount(new BigDecimal("20.00"));
        voucher.setStatus(BookingVoucherStatus.AVAILABLE);
        voucher.setValidFrom(LocalDateTime.now().minusMinutes(1));
        voucher.setValidUntil(LocalDateTime.now().plusMinutes(30));
        return voucher;
    }

    private Venue venue() {
        Venue venue = new Venue();
        venue.setId(4002L);
        venue.setMerchantId(2001L);
        venue.setStatus(VenueStatus.ACTIVE);
        return venue;
    }

    private AuthenticatedUser merchant() {
        return new AuthenticatedUser(2001L, "merchant_tea", UserRole.MERCHANT);
    }

    private void assertBusinessError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}
