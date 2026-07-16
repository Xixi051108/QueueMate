package com.queuemate.booking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import com.queuemate.venue.VenueStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSlotService {

    private final BookingSlotMapper bookingSlotMapper;
    private final VenueService venueService;

    public BookingSlotService(BookingSlotMapper bookingSlotMapper, VenueService venueService) {
        this.bookingSlotMapper = bookingSlotMapper;
        this.venueService = venueService;
    }

    public List<BookingSlotResponse> list(
            Long venueId,
            LocalDate dateFrom,
            LocalDate dateTo,
            BookingSlotStatus status
    ) {
        venueService.getRequiredVenue(venueId);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PARAM_INVALID", "开始日期不能晚于结束日期");
        }

        LambdaQueryWrapper<BookingSlot> query = Wrappers.<BookingSlot>lambdaQuery()
                .eq(BookingSlot::getVenueId, venueId)
                .ge(dateFrom != null, BookingSlot::getSlotDate, dateFrom)
                .le(dateTo != null, BookingSlot::getSlotDate, dateTo)
                .eq(status != null, BookingSlot::getStatus, status)
                .orderByAsc(BookingSlot::getSlotDate)
                .orderByAsc(BookingSlot::getStartTime)
                .orderByAsc(BookingSlot::getId);
        return bookingSlotMapper.selectList(query).stream()
                .map(BookingSlotResponse::from)
                .toList();
    }

    @Transactional
    public BookingSlotResponse create(
            Long venueId,
            BookingSlotCreateRequest request,
            AuthenticatedUser principal
    ) {
        Venue venue = venueService.getRequiredVenue(venueId);
        venueService.requireOwnerOrAdmin(venue, principal);
        requireBookableVenue(venue);
        validateCreateRequest(request);
        ensureSlotAvailable(venueId, request);

        BookingSlot slot = new BookingSlot();
        slot.setVenueId(venueId);
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setCapacity(request.capacity());
        slot.setReservedCount(0);
        slot.setPrice(request.price());
        slot.setStatus(BookingSlotStatus.OPEN);
        slot.setCreatedBy(principal.id());

        try {
            bookingSlotMapper.insert(slot);
        } catch (DuplicateKeyException ex) {
            throw slotExists();
        }
        return BookingSlotResponse.from(slot);
    }

    @Transactional
    public BookingSlotResponse updateStatus(
            Long venueId,
            Long slotId,
            BookingSlotStatus status,
            AuthenticatedUser principal
    ) {
        Venue venue = venueService.getRequiredVenue(venueId);
        venueService.requireOwnerOrAdmin(venue, principal);
        BookingSlot slot = getRequiredSlot(venueId, slotId);
        if (status == BookingSlotStatus.OPEN) {
            requireBookableVenue(venue);
        }
        slot.setStatus(status);
        bookingSlotMapper.updateById(slot);
        return BookingSlotResponse.from(slot);
    }

    private BookingSlot getRequiredSlot(Long venueId, Long slotId) {
        BookingSlot slot = bookingSlotMapper.selectById(slotId);
        if (slot == null || !slot.getVenueId().equals(venueId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "BOOKING_SLOT_NOT_FOUND", "预约时段不存在");
        }
        return slot;
    }

    private void requireBookableVenue(Venue venue) {
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "VENUE_INACTIVE", "地点已停用");
        }
        if (!Boolean.TRUE.equals(venue.getBookingEnabled())) {
            throw new BusinessException(HttpStatus.CONFLICT, "VENUE_BOOKING_DISABLED", "地点未启用预约");
        }
    }

    private void validateCreateRequest(BookingSlotCreateRequest request) {
        if (request.slotDate().isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PARAM_INVALID", "时段日期不能早于今天");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "BOOKING_SLOT_TIME_INVALID",
                    "开始时间必须早于结束时间"
            );
        }
    }

    private void ensureSlotAvailable(Long venueId, BookingSlotCreateRequest request) {
        long count = bookingSlotMapper.selectCount(
                Wrappers.<BookingSlot>lambdaQuery()
                        .eq(BookingSlot::getVenueId, venueId)
                        .eq(BookingSlot::getSlotDate, request.slotDate())
                        .eq(BookingSlot::getStartTime, request.startTime())
                        .eq(BookingSlot::getEndTime, request.endTime())
        );
        if (count > 0) {
            throw slotExists();
        }
    }

    private BusinessException slotExists() {
        return new BusinessException(HttpStatus.CONFLICT, "BOOKING_SLOT_EXISTS", "相同时段已存在");
    }
}
