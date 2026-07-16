package com.queuemate.booking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingService {

    private final BookingMapper bookingMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final VenueService venueService;

    public BookingService(
            BookingMapper bookingMapper,
            BookingSlotMapper bookingSlotMapper,
            VenueService venueService
    ) {
        this.bookingMapper = bookingMapper;
        this.bookingSlotMapper = bookingSlotMapper;
        this.venueService = venueService;
    }

    @Transactional
    public BookingResponse create(BookingCreateRequest request, AuthenticatedUser principal) {
        requireUser(principal);
        BookingSlot slot = getRequiredSlot(request.slotId());
        Venue venue = venueService.getRequiredVenue(slot.getVenueId());
        validateBookable(venue, slot);
        ensureFreeSlot(slot);
        ensureNotDuplicate(principal.id(), slot.getId());

        int reserved = bookingSlotMapper.reserveCapacity(slot.getId());
        if (reserved == 0) {
            throwCapacityFailure(slot.getId());
        }

        Booking booking = new Booking();
        booking.setBookingNo(generateBookingNo());
        booking.setUserId(principal.id());
        booking.setVenueId(slot.getVenueId());
        booking.setSlotId(slot.getId());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setPayStatus(BookingPayStatus.NOT_REQUIRED);
        booking.setPaidAmount(BigDecimal.ZERO);
        booking.setBookedAt(LocalDateTime.now());

        try {
            bookingMapper.insert(booking);
        } catch (DuplicateKeyException ex) {
            throw duplicateBooking();
        }
        return BookingResponse.from(booking);
    }

    public List<BookingResponse> listMine(BookingStatus status, AuthenticatedUser principal) {
        requireUser(principal);
        LambdaQueryWrapper<Booking> query = Wrappers.<Booking>lambdaQuery()
                .eq(Booking::getUserId, principal.id())
                .eq(status != null, Booking::getStatus, status)
                .orderByDesc(Booking::getBookedAt)
                .orderByDesc(Booking::getId);
        return bookingMapper.selectList(query).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse cancel(
            Long bookingId,
            BookingCancelRequest request,
            AuthenticatedUser principal
    ) {
        Booking booking = getRequiredBooking(bookingId);
        requireOwnerOrAdmin(booking, principal);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw invalidBookingStatus();
        }
        if (booking.getPayStatus() != BookingPayStatus.NOT_REQUIRED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "BOOKING_REFUND_REQUIRED",
                    "收费预约需由退款流程取消"
            );
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        String reason = normalizeReason(request.reason());
        int cancelled = bookingMapper.cancelBooked(bookingId, reason, cancelledAt);
        if (cancelled == 0) {
            throw invalidBookingStatus();
        }
        int released = bookingSlotMapper.releaseCapacity(booking.getSlotId());
        if (released == 0) {
            throw new IllegalStateException("Booking slot capacity cannot be released");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(reason);
        booking.setCancelledAt(cancelledAt);
        return BookingResponse.from(booking);
    }

    private BookingSlot getRequiredSlot(Long slotId) {
        BookingSlot slot = bookingSlotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "BOOKING_SLOT_NOT_FOUND",
                    "预约时段不存在"
            );
        }
        return slot;
    }

    private Booking getRequiredBooking(Long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预约不存在");
        }
        return booking;
    }

    private void validateBookable(Venue venue, BookingSlot slot) {
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "VENUE_INACTIVE", "地点已停用");
        }
        if (!Boolean.TRUE.equals(venue.getBookingEnabled())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "VENUE_BOOKING_DISABLED",
                    "地点未启用预约"
            );
        }
        if (slot.getStatus() != BookingSlotStatus.OPEN) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "BOOKING_SLOT_CLOSED",
                    "预约时段已关闭"
            );
        }
        if (isExpired(slot)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "BOOKING_SLOT_EXPIRED",
                    "预约时段已开始或已过期"
            );
        }
    }

    private boolean isExpired(BookingSlot slot) {
        LocalDate today = LocalDate.now();
        if (slot.getSlotDate().isBefore(today)) {
            return true;
        }
        return slot.getSlotDate().isEqual(today)
                && !slot.getStartTime().isAfter(LocalTime.now());
    }

    private void ensureFreeSlot(BookingSlot slot) {
        if (slot.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "BOOKING_PAYMENT_REQUIRED",
                    "收费时段需在钱包支付模块上线后预约"
            );
        }
    }

    private void ensureNotDuplicate(Long userId, Long slotId) {
        long count = bookingMapper.selectCount(
                Wrappers.<Booking>lambdaQuery()
                        .eq(Booking::getUserId, userId)
                        .eq(Booking::getSlotId, slotId)
        );
        if (count > 0) {
            throw duplicateBooking();
        }
    }

    private void throwCapacityFailure(Long slotId) {
        BookingSlot latest = bookingSlotMapper.selectById(slotId);
        if (latest == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "BOOKING_SLOT_NOT_FOUND",
                    "预约时段不存在"
            );
        }
        Venue venue = venueService.getRequiredVenue(latest.getVenueId());
        validateBookable(venue, latest);
        throw new BusinessException(
                HttpStatus.CONFLICT,
                "BOOKING_SLOT_FULL",
                "当前时段已约满"
        );
    }

    private void requireUser(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (principal.role() != UserRole.USER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "仅普通用户可以预约");
        }
    }

    private void requireOwnerOrAdmin(Booking booking, AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (principal.role() == UserRole.ADMIN) {
            return;
        }
        if (principal.role() != UserRole.USER || !booking.getUserId().equals(principal.id())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "RESOURCE_NOT_OWNED",
                    "只能取消自己的预约"
            );
        }
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private String generateBookingNo() {
        return "BK" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private BusinessException duplicateBooking() {
        return new BusinessException(HttpStatus.CONFLICT, "BOOKING_DUPLICATE", "不能重复预约同一时段");
    }

    private BusinessException invalidBookingStatus() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "BOOKING_STATUS_INVALID",
                "当前预约状态不允许取消"
        );
    }
}
