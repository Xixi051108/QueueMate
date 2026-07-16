package com.queuemate.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingSlotServiceTest {

    @Mock
    private BookingSlotMapper bookingSlotMapper;

    @Mock
    private VenueService venueService;

    private BookingSlotService bookingSlotService;

    @BeforeEach
    void setUp() {
        bookingSlotService = new BookingSlotService(bookingSlotMapper, venueService);
    }

    @Test
    void publicListReturnsAvailableCapacity() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        when(bookingSlotMapper.selectList(any())).thenReturn(List.of(openSlot(5001L, 4002L)));

        List<BookingSlotResponse> response = bookingSlotService.list(
                4002L,
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                BookingSlotStatus.OPEN
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().availableCapacity()).isEqualTo(18);
        assertThat(response.getFirst().status()).isEqualTo(BookingSlotStatus.OPEN);
    }

    @Test
    void listRejectsReversedDateRange() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());

        assertThatThrownBy(() -> bookingSlotService.list(
                4002L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(1),
                null
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getCode()).isEqualTo("PARAM_INVALID");
        });
    }

    @Test
    void merchantCreatesOpenEmptySlot() {
        Venue venue = bookableVenue();
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);
        when(bookingSlotMapper.selectCount(any())).thenReturn(0L);
        when(bookingSlotMapper.insert(any(BookingSlot.class))).thenAnswer(invocation -> {
            BookingSlot slot = invocation.getArgument(0);
            slot.setId(5101L);
            return 1;
        });

        BookingSlotResponse response = bookingSlotService.create(
                4002L,
                validRequest(),
                merchantPrincipal()
        );

        assertThat(response.id()).isEqualTo(5101L);
        assertThat(response.venueId()).isEqualTo(4002L);
        assertThat(response.reservedCount()).isZero();
        assertThat(response.availableCapacity()).isEqualTo(12);
        assertThat(response.status()).isEqualTo(BookingSlotStatus.OPEN);
        assertThat(response.createdBy()).isEqualTo(2001L);
        verify(venueService).requireOwnerOrAdmin(venue, merchantPrincipal());
    }

    @Test
    void createRejectsInactiveVenue() {
        Venue venue = bookableVenue();
        venue.setStatus(VenueStatus.INACTIVE);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);

        assertBusinessError(
                () -> bookingSlotService.create(4002L, validRequest(), merchantPrincipal()),
                HttpStatus.CONFLICT,
                "VENUE_INACTIVE"
        );
        verify(bookingSlotMapper, never()).insert(any(BookingSlot.class));
    }

    @Test
    void createRejectsVenueWithoutBooking() {
        Venue venue = bookableVenue();
        venue.setBookingEnabled(false);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);

        assertBusinessError(
                () -> bookingSlotService.create(4002L, validRequest(), merchantPrincipal()),
                HttpStatus.CONFLICT,
                "VENUE_BOOKING_DISABLED"
        );
    }

    @Test
    void createRejectsInvalidTimeRange() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        BookingSlotCreateRequest request = new BookingSlotCreateRequest(
                LocalDate.now().plusDays(1),
                LocalTime.of(20, 0),
                LocalTime.of(19, 0),
                12,
                BigDecimal.TEN
        );

        assertBusinessError(
                () -> bookingSlotService.create(4002L, request, merchantPrincipal()),
                HttpStatus.BAD_REQUEST,
                "BOOKING_SLOT_TIME_INVALID"
        );
    }

    @Test
    void createRejectsPastDateEvenWhenCalledOutsideController() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        BookingSlotCreateRequest request = new BookingSlotCreateRequest(
                LocalDate.now().minusDays(1),
                LocalTime.of(19, 0),
                LocalTime.of(20, 0),
                12,
                BigDecimal.TEN
        );

        assertBusinessError(
                () -> bookingSlotService.create(4002L, request, merchantPrincipal()),
                HttpStatus.BAD_REQUEST,
                "PARAM_INVALID"
        );
    }

    @Test
    void duplicateSlotIsRejectedBeforeInsert() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        when(bookingSlotMapper.selectCount(any())).thenReturn(1L);

        assertBusinessError(
                () -> bookingSlotService.create(4002L, validRequest(), merchantPrincipal()),
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_EXISTS"
        );
        verify(bookingSlotMapper, never()).insert(any(BookingSlot.class));
    }

    @Test
    void duplicateKeyRaceIsMappedToStableError() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        when(bookingSlotMapper.selectCount(any())).thenReturn(0L);
        when(bookingSlotMapper.insert(any(BookingSlot.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertBusinessError(
                () -> bookingSlotService.create(4002L, validRequest(), merchantPrincipal()),
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_EXISTS"
        );
    }

    @Test
    void ownerCanCloseSlot() {
        Venue venue = bookableVenue();
        BookingSlot slot = openSlot(5001L, 4002L);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);

        BookingSlotResponse response = bookingSlotService.updateStatus(
                4002L,
                5001L,
                BookingSlotStatus.CLOSED,
                merchantPrincipal()
        );

        assertThat(response.status()).isEqualTo(BookingSlotStatus.CLOSED);
        verify(bookingSlotMapper).updateById(slot);
    }

    @Test
    void reopeningSlotRequiresBookableVenue() {
        Venue venue = bookableVenue();
        venue.setStatus(VenueStatus.INACTIVE);
        BookingSlot slot = openSlot(5001L, 4002L);
        slot.setStatus(BookingSlotStatus.CLOSED);
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);
        when(bookingSlotMapper.selectById(5001L)).thenReturn(slot);

        assertBusinessError(
                () -> bookingSlotService.updateStatus(
                        4002L,
                        5001L,
                        BookingSlotStatus.OPEN,
                        merchantPrincipal()
                ),
                HttpStatus.CONFLICT,
                "VENUE_INACTIVE"
        );
        verify(bookingSlotMapper, never()).updateById(any(BookingSlot.class));
    }

    @Test
    void slotFromAnotherVenueIsNotExposed() {
        when(venueService.getRequiredVenue(4002L)).thenReturn(bookableVenue());
        when(bookingSlotMapper.selectById(5003L)).thenReturn(openSlot(5003L, 4003L));

        assertBusinessError(
                () -> bookingSlotService.updateStatus(
                        4002L,
                        5003L,
                        BookingSlotStatus.CLOSED,
                        merchantPrincipal()
                ),
                HttpStatus.NOT_FOUND,
                "BOOKING_SLOT_NOT_FOUND"
        );
    }

    @Test
    void venueOwnershipFailureStopsCreate() {
        Venue venue = bookableVenue();
        when(venueService.getRequiredVenue(4002L)).thenReturn(venue);
        doThrow(new BusinessException(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "只能操作自己名下的地点"
        )).when(venueService).requireOwnerOrAdmin(venue, merchantPrincipal());

        assertBusinessError(
                () -> bookingSlotService.create(4002L, validRequest(), merchantPrincipal()),
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED"
        );
        verify(bookingSlotMapper, never()).selectCount(any());
    }

    private void assertBusinessError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }

    private BookingSlotCreateRequest validRequest() {
        return new BookingSlotCreateRequest(
                LocalDate.now().plusDays(1),
                LocalTime.of(19, 0),
                LocalTime.of(20, 0),
                12,
                new BigDecimal("20.00")
        );
    }

    private BookingSlot openSlot(Long id, Long venueId) {
        BookingSlot slot = new BookingSlot();
        slot.setId(id);
        slot.setVenueId(venueId);
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(19, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setCapacity(20);
        slot.setReservedCount(2);
        slot.setPrice(new BigDecimal("20.00"));
        slot.setStatus(BookingSlotStatus.OPEN);
        slot.setCreatedBy(2001L);
        return slot;
    }

    private Venue bookableVenue() {
        Venue venue = new Venue();
        venue.setId(4002L);
        venue.setMerchantId(2001L);
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setBookingEnabled(true);
        return venue;
    }

    private AuthenticatedUser merchantPrincipal() {
        return new AuthenticatedUser(2001L, "merchant_tea", UserRole.MERCHANT);
    }
}
