package com.queuemate.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import com.queuemate.venue.VenueStatus;
import com.queuemate.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private BookingSlotMapper bookingSlotMapper;

    @Mock
    private VenueService venueService;

    @Mock
    private WalletService walletService;

    @Mock
    private BookingVoucherService voucherService;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingMapper,
                bookingSlotMapper,
                venueService,
                walletService,
                voucherService
        );
    }

    @Test
    void userCreatesFreeBookingAfterAtomicReservation() {
        BookingSlot slot = openFreeSlot();
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(1);
        when(bookingMapper.insert(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(6101L);
            return 1;
        });

        BookingResponse response = bookingService.create(
                new BookingCreateRequest(5001L),
                userPrincipal(3001L)
        );

        assertThat(response.id()).isEqualTo(6101L);
        assertThat(response.bookingNo()).startsWith("BK").hasSize(34);
        assertThat(response.userId()).isEqualTo(3001L);
        assertThat(response.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(response.payStatus()).isEqualTo(BookingPayStatus.NOT_REQUIRED);
        assertThat(response.paidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bookingSlotMapper).reserveCapacity(5001L);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingMapper).insert(captor.capture());
        assertThat(captor.getValue().getVenueId()).isEqualTo(4002L);
        assertThat(captor.getValue().getSlotId()).isEqualTo(5001L);
    }

    @Test
    void duplicateBookingIsRejectedBeforeCapacityReservation() {
        when(bookingSlotMapper.selectById(5001L)).thenReturn(openFreeSlot());
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(1L);

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "BOOKING_DUPLICATE"
        );
        verify(bookingSlotMapper, never()).reserveCapacity(any());
    }

    @Test
    void cancelledHistoryAllowsRebookingSameSlot() {
        when(bookingSlotMapper.selectById(5001L)).thenReturn(openFreeSlot());
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(1);
        when(bookingMapper.insert(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(6103L);
            return 1;
        });

        BookingResponse response = bookingService.create(
                new BookingCreateRequest(5001L),
                userPrincipal(3001L)
        );

        assertThat(response.id()).isEqualTo(6103L);
        assertThat(response.status()).isEqualTo(BookingStatus.BOOKED);
        verify(bookingMapper).countActiveBooking(eq(3001L), eq(5001L));
        verify(bookingSlotMapper).reserveCapacity(5001L);
    }

    @Test
    void paidSlotChargesWalletAndCreatesVoucher() {
        BookingSlot slot = openFreeSlot();
        slot.setPrice(new BigDecimal("20.00"));
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(1);
        when(bookingMapper.insert(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(6102L);
            return 1;
        });
        BookingVoucher voucher = availableVoucher();
        voucher.setBookingId(6102L);
        when(voucherService.createForPaidBooking(any(), any())).thenReturn(voucher);

        BookingResponse response = bookingService.create(
                new BookingCreateRequest(5001L),
                userPrincipal(3001L)
        );

        assertThat(response.payStatus()).isEqualTo(BookingPayStatus.PAID);
        assertThat(response.paidAmount()).isEqualByComparingTo("20.00");
        assertThat(response.voucher()).isNotNull();
        assertThat(response.voucher().status()).isEqualTo(BookingVoucherStatus.AVAILABLE);
        verify(walletService).chargeBooking(
                org.mockito.ArgumentMatchers.eq(3001L),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("20.00")),
                any()
        );
    }

    @Test
    void paidBookingFailureDoesNotInsertBookingOrVoucher() {
        BookingSlot slot = openFreeSlot();
        slot.setPrice(new BigDecimal("20.00"));
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(1);
        when(walletService.chargeBooking(any(), any(), any()))
                .thenThrow(new BusinessException(
                        HttpStatus.CONFLICT,
                        "WALLET_BALANCE_NOT_ENOUGH",
                        "钱包余额不足"
                ));

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "WALLET_BALANCE_NOT_ENOUGH"
        );
        verify(bookingMapper, never()).insert(any(Booking.class));
        verify(voucherService, never()).createForPaidBooking(any(), any());
    }

    @Test
    void closedSlotCannotBeBooked() {
        BookingSlot slot = openFreeSlot();
        slot.setStatus(BookingSlotStatus.CLOSED);
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_CLOSED"
        );
    }

    @Test
    void expiredSlotCannotBeBooked() {
        BookingSlot slot = openFreeSlot();
        slot.setSlotDate(LocalDate.now().minusDays(1));
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_EXPIRED"
        );
    }

    @Test
    void atomicReservationFailureReturnsSlotFull() {
        BookingSlot slot = openFreeSlot();
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot, slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(0);

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_FULL"
        );
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void duplicateKeyRaceReturnsDuplicateAndTransactionCanRollbackCapacity() {
        BookingSlot slot = openFreeSlot();
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);
        when(venueService.getRequiredVenue(4002L)).thenReturn(activeVenue());
        when(bookingMapper.countActiveBooking(3001L, 5001L)).thenReturn(0L);
        when(bookingSlotMapper.reserveCapacity(5001L)).thenReturn(1);
        when(bookingMapper.insert(any(Booking.class)))
                .thenThrow(new DuplicateKeyException("duplicate user and slot"));

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), userPrincipal(3001L)),
                HttpStatus.CONFLICT,
                "BOOKING_DUPLICATE"
        );
    }

    @Test
    void merchantCannotCreateUserBooking() {
        AuthenticatedUser merchant = new AuthenticatedUser(2001L, "merchant_tea", UserRole.MERCHANT);

        assertBusinessError(
                () -> bookingService.create(new BookingCreateRequest(5001L), merchant),
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN"
        );
        verify(bookingSlotMapper, never()).selectById(any());
    }

    @Test
    void userListsOnlyOwnBookings() {
        when(bookingMapper.selectList(any())).thenReturn(List.of(bookedBooking()));

        List<BookingResponse> response = bookingService.listMine(
                BookingStatus.BOOKED,
                userPrincipal(3001L)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().userId()).isEqualTo(3001L);
        assertThat(response.getFirst().status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void ownerCancelsFreeBookingAndReleasesCapacityOnce() {
        Booking booking = bookedBooking();
        when(bookingMapper.selectById(6001L)).thenReturn(booking);
        when(bookingMapper.cancelBooked(any(), any(), any())).thenReturn(1);
        when(bookingSlotMapper.releaseCapacity(5001L)).thenReturn(1);

        BookingResponse response = bookingService.cancel(
                6001L,
                new BookingCancelRequest("  plan changed  "),
                userPrincipal(3001L)
        );

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("plan changed");
        assertThat(response.cancelledAt()).isNotNull();
        verify(bookingSlotMapper).releaseCapacity(5001L);
    }

    @Test
    void anotherUserCannotCancelBooking() {
        when(bookingMapper.selectById(6001L)).thenReturn(bookedBooking());

        assertBusinessError(
                () -> bookingService.cancel(
                        6001L,
                        new BookingCancelRequest(null),
                        userPrincipal(3002L)
                ),
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED"
        );
        verify(bookingMapper, never()).cancelBooked(any(), any(), any());
    }

    @Test
    void alreadyCancelledBookingCannotBeCancelledAgain() {
        Booking booking = bookedBooking();
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingMapper.selectById(6001L)).thenReturn(booking);

        assertBusinessError(
                () -> bookingService.cancel(
                        6001L,
                        new BookingCancelRequest(null),
                        userPrincipal(3001L)
                ),
                HttpStatus.CONFLICT,
                "BOOKING_STATUS_INVALID"
        );
    }

    @Test
    void ownerCancelsPaidBookingBeforeStartAndRefunds() {
        Booking booking = bookedBooking();
        booking.setPayStatus(BookingPayStatus.PAID);
        booking.setPaidAmount(new BigDecimal("10.00"));
        when(bookingMapper.selectById(6001L)).thenReturn(booking);
        when(bookingSlotMapper.selectById(5001L)).thenReturn(openFreeSlot());
        BookingVoucher voucher = availableVoucher();
        when(voucherService.lockRefundable(6001L)).thenReturn(voucher);
        when(bookingMapper.cancelPaidBooking(any(), any(), any())).thenReturn(1);
        when(bookingSlotMapper.releaseCapacity(5001L)).thenReturn(1);

        BookingResponse response = bookingService.cancel(
                6001L,
                new BookingCancelRequest(null),
                userPrincipal(3001L)
        );

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.payStatus()).isEqualTo(BookingPayStatus.REFUNDED);
        verify(walletService).refundBooking(3001L, new BigDecimal("10.00"), "BKTEST0001");
        verify(voucherService).voidRefundedVoucher(any(), any());
    }

    @Test
    void paidBookingCannotBeCancelledAfterStart() {
        Booking booking = bookedBooking();
        booking.setPayStatus(BookingPayStatus.PAID);
        booking.setPaidAmount(new BigDecimal("10.00"));
        BookingSlot slot = openFreeSlot();
        slot.setSlotDate(LocalDate.now());
        slot.setStartTime(LocalTime.now().minusMinutes(1));
        when(bookingMapper.selectById(6001L)).thenReturn(booking);
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);

        assertBusinessError(
                () -> bookingService.cancel(
                        6001L,
                        new BookingCancelRequest(null),
                        userPrincipal(3001L)
                ),
                HttpStatus.CONFLICT,
                "BOOKING_REFUND_WINDOW_CLOSED"
        );
        verify(walletService, never()).refundBooking(any(), any(), any());
    }

    @Test
    void concurrentSecondCancelDoesNotReleaseCapacity() {
        when(bookingMapper.selectById(6001L)).thenReturn(bookedBooking());
        when(bookingMapper.cancelBooked(any(), any(), any())).thenReturn(0);

        assertBusinessError(
                () -> bookingService.cancel(
                        6001L,
                        new BookingCancelRequest(null),
                        userPrincipal(3001L)
                ),
                HttpStatus.CONFLICT,
                "BOOKING_STATUS_INVALID"
        );
        verify(bookingSlotMapper, never()).releaseCapacity(any());
    }

    @Test
    void capacityReleaseFailureRaisesSystemErrorForTransactionRollback() {
        when(bookingMapper.selectById(6001L)).thenReturn(bookedBooking());
        when(bookingMapper.cancelBooked(any(), any(), any())).thenReturn(1);
        when(bookingSlotMapper.releaseCapacity(5001L)).thenReturn(0);

        assertThatThrownBy(() -> bookingService.cancel(
                6001L,
                new BookingCancelRequest(null),
                userPrincipal(3001L)
        )).isInstanceOf(IllegalStateException.class);
    }

    private void assertBusinessError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }

    private BookingSlot openFreeSlot() {
        BookingSlot slot = new BookingSlot();
        slot.setId(5001L);
        slot.setVenueId(4002L);
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(19, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setCapacity(20);
        slot.setReservedCount(0);
        slot.setPrice(BigDecimal.ZERO);
        slot.setStatus(BookingSlotStatus.OPEN);
        return slot;
    }

    private Venue activeVenue() {
        Venue venue = new Venue();
        venue.setId(4002L);
        venue.setMerchantId(2001L);
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setBookingEnabled(true);
        return venue;
    }

    private Booking bookedBooking() {
        Booking booking = new Booking();
        booking.setId(6001L);
        booking.setBookingNo("BKTEST0001");
        booking.setUserId(3001L);
        booking.setVenueId(4002L);
        booking.setSlotId(5001L);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setPayStatus(BookingPayStatus.NOT_REQUIRED);
        booking.setPaidAmount(BigDecimal.ZERO);
        booking.setBookedAt(LocalDateTime.now().minusMinutes(1));
        return booking;
    }

    private BookingVoucher availableVoucher() {
        BookingVoucher voucher = new BookingVoucher();
        voucher.setId(8001L);
        voucher.setBookingId(6001L);
        voucher.setUserId(3001L);
        voucher.setVenueId(4002L);
        voucher.setConsumptionCode("QMTESTCODE01");
        voucher.setAmount(new BigDecimal("10.00"));
        voucher.setStatus(BookingVoucherStatus.AVAILABLE);
        voucher.setValidFrom(LocalDateTime.now().minusMinutes(1));
        voucher.setValidUntil(LocalDateTime.now().plusHours(1));
        return voucher;
    }

    private AuthenticatedUser userPrincipal(Long id) {
        return new AuthenticatedUser(id, "user_" + id, UserRole.USER);
    }
}
