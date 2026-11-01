package com.queuemate.booking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueMapper;
import com.queuemate.venue.VenueStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!e2e")
public class DailySlotJob {

    private static final Logger log = LoggerFactory.getLogger(DailySlotJob.class);

    private final VenueMapper venueMapper;
    private final BookingSlotMapper slotMapper;

    public DailySlotJob(VenueMapper venueMapper, BookingSlotMapper slotMapper) {
        this.venueMapper = venueMapper;
        this.slotMapper = slotMapper;
    }

    @Scheduled(fixedDelay = 60000)
    public void refresh() {
        LocalDate today = LocalDate.now();
        List<Venue> venues = venueMapper.selectList(Wrappers.<Venue>lambdaQuery()
                .eq(Venue::getStatus, VenueStatus.ACTIVE)
                .eq(Venue::getBookingEnabled, true));
        int created = 0;
        for (Venue venue : venues) {
            created += fillMissingSlots(venue, today);
        }
        if (created > 0) {
            log.info("Created {} daily booking slots", created);
        }
    }

    int fillMissingSlots(Venue venue, LocalDate today) {
        if (venue.getStatus() != VenueStatus.ACTIVE || !Boolean.TRUE.equals(venue.getBookingEnabled())) {
            return 0;
        }

        BookingSlot first = slotMapper.selectOne(Wrappers.<BookingSlot>lambdaQuery()
                .eq(BookingSlot::getVenueId, venue.getId())
                .orderByAsc(BookingSlot::getSlotDate)
                .orderByAsc(BookingSlot::getStartTime)
                .orderByAsc(BookingSlot::getId)
                .last("limit 1"));
        int capacity = first == null ? 1 : first.getCapacity();
        LocalDate lastDay = today.plusDays(14);
        List<BookingSlot> existing = slotMapper.selectList(Wrappers.<BookingSlot>lambdaQuery()
                .eq(BookingSlot::getVenueId, venue.getId())
                .ge(BookingSlot::getSlotDate, today)
                .le(BookingSlot::getSlotDate, lastDay));

        int created = 0;
        for (LocalDate date = today; !date.isAfter(lastDay); date = date.plusDays(1)) {
            for (int hour = 8; hour < 22; hour++) {
                LocalDate slotDate = date;
                LocalTime start = LocalTime.of(hour, 0);
                LocalTime end = start.plusHours(1);
                // Keep existing bookings and merchant closures, including overlapping custom slots.
                boolean occupied = existing.stream().anyMatch(slot -> slot.getSlotDate().equals(slotDate)
                        && slot.getStartTime().isBefore(end)
                        && slot.getEndTime().isAfter(start));
                if (occupied) {
                    continue;
                }

                BookingSlot slot = new BookingSlot();
                slot.setVenueId(venue.getId());
                slot.setSlotDate(date);
                slot.setStartTime(start);
                slot.setEndTime(end);
                slot.setCapacity(capacity);
                slot.setReservedCount(0);
                slot.setPrice(venue.getDefaultPrice());
                slot.setStatus(BookingSlotStatus.OPEN);
                slot.setCreatedBy(venue.getMerchantId());
                try {
                    slotMapper.insert(slot);
                    created++;
                } catch (DuplicateKeyException ex) {
                    // A concurrent generator or merchant already created this exact slot.
                }
            }
        }
        return created;
    }
}
