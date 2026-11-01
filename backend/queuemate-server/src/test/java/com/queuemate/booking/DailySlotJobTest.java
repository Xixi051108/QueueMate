package com.queuemate.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueMapper;
import com.queuemate.venue.VenueStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class DailySlotJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    private VenueMapper venueMapper;

    @Mock
    private BookingSlotMapper slotMapper;

    private DailySlotJob job;

    @BeforeEach
    void setUp() {
        job = new DailySlotJob(venueMapper, slotMapper);
    }

    @Test
    void fillsEveryHourFromEightToTwentyTwoThroughFourteenDaysAhead() {
        when(slotMapper.selectOne(any())).thenReturn(slot(TODAY.minusMonths(1), 9, 10));
        when(slotMapper.selectList(any())).thenReturn(List.of());

        assertThat(job.fillMissingSlots(venue(), TODAY)).isEqualTo(210);

        List<BookingSlot> created = insertedSlots(210);
        assertThat(created).allSatisfy(slot -> {
            assertThat(slot.getSlotDate()).isBetween(TODAY, TODAY.plusDays(14));
            assertThat(slot.getStartTime().getHour()).isBetween(8, 21);
            assertThat(slot.getStartTime().getMinute()).isZero();
            assertThat(slot.getEndTime()).isEqualTo(slot.getStartTime().plusHours(1));
            assertThat(slot.getCapacity()).isEqualTo(20);
            assertThat(slot.getReservedCount()).isZero();
            assertThat(slot.getPrice()).isEqualByComparingTo("10.00");
            assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.OPEN);
            assertThat(slot.getCreatedBy()).isEqualTo(2001L);
        });
        assertThat(created.stream().map(slot -> slot.getSlotDate() + " " + slot.getStartTime()).distinct())
                .hasSize(210);
    }

    @Test
    void defaultsToOnePlaceWhenVenueHasNoPreviousSlots() {
        when(slotMapper.selectList(any())).thenReturn(List.of());

        job.fillMissingSlots(venue(), TODAY);

        assertThat(insertedSlots(210)).allSatisfy(slot -> assertThat(slot.getCapacity()).isEqualTo(1));
    }

    @Test
    void preservesMerchantClosuresAndExistingMultiHourBookings() {
        BookingSlot closed = slot(TODAY.plusDays(1), 9, 11);
        closed.setStatus(BookingSlotStatus.CLOSED);
        BookingSlot booked = slot(TODAY.plusDays(1), 13, 15);
        booked.setReservedCount(3);
        when(slotMapper.selectOne(any())).thenReturn(closed);
        when(slotMapper.selectList(any())).thenReturn(List.of(closed, booked));

        assertThat(job.fillMissingSlots(venue(), TODAY)).isEqualTo(206);

        assertThat(insertedSlots(206).stream()
                .filter(slot -> slot.getSlotDate().equals(TODAY.plusDays(1)))
                .map(slot -> slot.getStartTime().getHour()))
                .doesNotContain(9, 10, 13, 14);
        assertThat(closed.getStatus()).isEqualTo(BookingSlotStatus.CLOSED);
        assertThat(booked.getReservedCount()).isEqualTo(3);
        verify(slotMapper, never()).updateById(any(BookingSlot.class));
    }

    @Test
    void doesNotDuplicateSlotsOnRepeatedRuns() {
        List<BookingSlot> existing = fullWindow();
        existing.getFirst().setStatus(BookingSlotStatus.CLOSED);
        when(slotMapper.selectOne(any())).thenReturn(existing.getFirst());
        when(slotMapper.selectList(any())).thenReturn(existing);

        assertThat(job.fillMissingSlots(venue(), TODAY)).isZero();
        assertThat(job.fillMissingSlots(venue(), TODAY)).isZero();

        verify(slotMapper, never()).insert(any(BookingSlot.class));
    }

    @Test
    void skipsClosedVenuesAndVenuesWithoutOnlineBooking() {
        Venue inactive = venue();
        inactive.setStatus(VenueStatus.INACTIVE);
        Venue withoutBooking = venue();
        withoutBooking.setBookingEnabled(false);

        assertThat(job.fillMissingSlots(inactive, TODAY)).isZero();
        assertThat(job.fillMissingSlots(withoutBooking, TODAY)).isZero();

        verifyNoInteractions(slotMapper);
    }

    @Test
    void uniqueKeyHandlesConcurrentCreationWithoutStoppingRemainingSlots() {
        when(slotMapper.selectList(any())).thenReturn(List.of());
        when(slotMapper.insert(any(BookingSlot.class)))
                .thenThrow(new DuplicateKeyException("same venue, date and time"))
                .thenReturn(1);

        assertThat(job.fillMissingSlots(venue(), TODAY)).isEqualTo(209);
        insertedSlots(210);
    }

    @Test
    void nextDayAddsOnlyTheNewLastDay() {
        List<BookingSlot> existing = fullWindow();
        when(slotMapper.selectOne(any())).thenReturn(existing.getFirst());
        when(slotMapper.selectList(any())).thenReturn(existing);

        assertThat(job.fillMissingSlots(venue(), TODAY.plusDays(1))).isEqualTo(14);

        assertThat(insertedSlots(14)).allSatisfy(slot ->
                assertThat(slot.getSlotDate()).isEqualTo(TODAY.plusDays(15)));
    }

    private List<BookingSlot> insertedSlots(int count) {
        ArgumentCaptor<BookingSlot> captor = ArgumentCaptor.forClass(BookingSlot.class);
        verify(slotMapper, org.mockito.Mockito.times(count)).insert(captor.capture());
        return captor.getAllValues();
    }

    private List<BookingSlot> fullWindow() {
        List<BookingSlot> slots = new ArrayList<>();
        for (int day = 0; day <= 14; day++) {
            for (int hour = 8; hour < 22; hour++) {
                slots.add(slot(TODAY.plusDays(day), hour, hour + 1));
            }
        }
        return slots;
    }

    private BookingSlot slot(LocalDate date, int startHour, int endHour) {
        BookingSlot slot = new BookingSlot();
        slot.setVenueId(4002L);
        slot.setSlotDate(date);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        slot.setCapacity(20);
        slot.setReservedCount(0);
        slot.setStatus(BookingSlotStatus.OPEN);
        return slot;
    }

    private Venue venue() {
        Venue venue = new Venue();
        venue.setId(4002L);
        venue.setMerchantId(2001L);
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setBookingEnabled(true);
        venue.setDefaultPrice(new BigDecimal("10.00"));
        return venue;
    }
}
