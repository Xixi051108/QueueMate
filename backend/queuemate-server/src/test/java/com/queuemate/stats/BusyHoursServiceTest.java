package com.queuemate.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.UserRole;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusyHoursServiceTest {

    @Mock
    private BusyHoursMapper busyHoursMapper;

    @Mock
    private VenueService venueService;

    private BusyHoursService busyHoursService;

    @BeforeEach
    void setUp() {
        busyHoursService = new BusyHoursService(busyHoursMapper, venueService);
    }

    @Test
    void mergesBookingAndQueueCountsByHour() {
        Venue venue = new Venue();
        venue.setId(4001L);
        when(venueService.getRequiredVenue(4001L)).thenReturn(venue);
        when(busyHoursMapper.countBookingsByHour(any(), any(), any()))
                .thenReturn(List.of(row(10, 14L), row(11, 2L)));
        when(busyHoursMapper.countQueueTicketsByHour(any(), any(), any()))
                .thenReturn(List.of(row(10, 9L), row(12, 3L)));

        List<BusyHourResponse> response = busyHoursService.getBusyHours(
                4001L,
                LocalDate.now().minusDays(7),
                LocalDate.now(),
                merchant()
        );

        assertThat(response).containsExactly(
                new BusyHourResponse("10:00", 14, 9, 23),
                new BusyHourResponse("11:00", 2, 0, 2),
                new BusyHourResponse("12:00", 0, 3, 3)
        );
        verify(venueService).requireOwnerOrAdmin(venue, merchant());
    }

    @Test
    void reversedDateRangeIsRejected() {
        assertThatThrownBy(() -> busyHoursService.getBusyHours(
                4001L,
                LocalDate.now(),
                LocalDate.now().minusDays(1),
                merchant()
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getCode()).isEqualTo("STATS_DATE_RANGE_INVALID")
        );
    }

    @Test
    void rangeLongerThanOneYearIsRejected() {
        assertThatThrownBy(() -> busyHoursService.getBusyHours(
                4001L,
                LocalDate.now().minusDays(367),
                LocalDate.now(),
                merchant()
        )).isInstanceOf(BusinessException.class);
    }

    private HourlyCountRow row(int hour, long count) {
        HourlyCountRow row = new HourlyCountRow();
        row.setHourValue(hour);
        row.setCountValue(count);
        return row;
    }

    private AuthenticatedUser merchant() {
        return new AuthenticatedUser(2001L, "merchant", UserRole.MERCHANT);
    }
}
