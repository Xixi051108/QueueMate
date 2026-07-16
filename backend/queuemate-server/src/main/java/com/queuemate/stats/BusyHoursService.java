package com.queuemate.stats;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BusyHoursService {

    private static final long MAX_RANGE_DAYS = 366;

    private final BusyHoursMapper busyHoursMapper;
    private final VenueService venueService;

    public BusyHoursService(BusyHoursMapper busyHoursMapper, VenueService venueService) {
        this.busyHoursMapper = busyHoursMapper;
        this.venueService = venueService;
    }

    public List<BusyHourResponse> getBusyHours(
            Long venueId,
            LocalDate dateFrom,
            LocalDate dateTo,
            AuthenticatedUser principal
    ) {
        validateRange(dateFrom, dateTo);
        Venue venue = venueService.getRequiredVenue(venueId);
        venueService.requireOwnerOrAdmin(venue, principal);

        Map<Integer, Long> bookingCounts = toMap(
                busyHoursMapper.countBookingsByHour(venueId, dateFrom, dateTo)
        );
        Map<Integer, Long> queueCounts = toMap(
                busyHoursMapper.countQueueTicketsByHour(venueId, dateFrom, dateTo)
        );
        TreeSet<Integer> hours = new TreeSet<>();
        hours.addAll(bookingCounts.keySet());
        hours.addAll(queueCounts.keySet());
        return hours.stream()
                .map(hour -> {
                    long bookingCount = bookingCounts.getOrDefault(hour, 0L);
                    long queueCount = queueCounts.getOrDefault(hour, 0L);
                    return new BusyHourResponse(
                            "%02d:00".formatted(hour),
                            bookingCount,
                            queueCount,
                            bookingCount + queueCount
                    );
                })
                .toList();
    }

    private Map<Integer, Long> toMap(List<HourlyCountRow> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (HourlyCountRow row : rows) {
            counts.put(row.getHourValue(), row.getCountValue());
        }
        return counts;
    }

    private void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw invalidRange();
        }
        long days = ChronoUnit.DAYS.between(dateFrom, dateTo);
        if (days < 0 || days > MAX_RANGE_DAYS) {
            throw invalidRange();
        }
    }

    private BusinessException invalidRange() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "STATS_DATE_RANGE_INVALID",
                "统计日期范围不合法"
        );
    }
}
