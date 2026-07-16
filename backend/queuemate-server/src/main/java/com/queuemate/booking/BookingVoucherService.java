package com.queuemate.booking;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.venue.Venue;
import com.queuemate.venue.VenueService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingVoucherService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_RANDOM_LENGTH = 10;
    private static final int GENERATE_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final BookingVoucherMapper voucherMapper;
    private final BookingMapper bookingMapper;
    private final VenueService venueService;

    public BookingVoucherService(
            BookingVoucherMapper voucherMapper,
            BookingMapper bookingMapper,
            VenueService venueService
    ) {
        this.voucherMapper = voucherMapper;
        this.bookingMapper = bookingMapper;
        this.venueService = venueService;
    }

    public BookingVoucher createForPaidBooking(Booking booking, BookingSlot slot) {
        LocalDateTime validFrom = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime())
                .minusMinutes(30);
        LocalDateTime validUntil = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());
        for (int attempt = 0; attempt < GENERATE_ATTEMPTS; attempt++) {
            BookingVoucher voucher = new BookingVoucher();
            voucher.setBookingId(booking.getId());
            voucher.setUserId(booking.getUserId());
            voucher.setVenueId(booking.getVenueId());
            voucher.setConsumptionCode(generateConsumptionCode());
            voucher.setAmount(booking.getPaidAmount());
            voucher.setStatus(BookingVoucherStatus.AVAILABLE);
            voucher.setValidFrom(validFrom);
            voucher.setValidUntil(validUntil);
            try {
                voucherMapper.insert(voucher);
                return voucher;
            } catch (DuplicateKeyException ex) {
                if (voucherMapper.selectByBookingId(booking.getId()) != null) {
                    throw new BusinessException(
                            HttpStatus.CONFLICT,
                            "CONSUMPTION_CODE_DUPLICATE",
                            "预约消费凭证已存在"
                    );
                }
            }
        }
        throw new IllegalStateException("Unable to generate unique consumption code");
    }

    public BookingVoucher findByBookingId(Long bookingId) {
        return voucherMapper.selectByBookingId(bookingId);
    }

    public BookingVoucher lockRefundable(Long bookingId) {
        BookingVoucher voucher = voucherMapper.selectByBookingIdForUpdate(bookingId);
        if (voucher == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "CONSUMPTION_CODE_NOT_FOUND",
                    "预约消费凭证不存在"
            );
        }
        if (voucher.getStatus() != BookingVoucherStatus.AVAILABLE) {
            throw invalidVoucherStatus();
        }
        return voucher;
    }

    public void voidRefundedVoucher(BookingVoucher voucher, LocalDateTime voidedAt) {
        if (voucherMapper.voidAvailable(voucher.getId(), voidedAt) != 1) {
            throw invalidVoucherStatus();
        }
        voucher.setStatus(BookingVoucherStatus.VOID);
        voucher.setVoidedAt(voidedAt);
    }

    @Transactional
    public BookingVoucherRedeemResponse redeem(
            Long venueId,
            BookingVoucherRedeemRequest request,
            AuthenticatedUser principal
    ) {
        Venue venue = venueService.getRequiredVenue(venueId);
        venueService.requireOwnerOrAdmin(venue, principal);

        BookingVoucher voucher = voucherMapper.selectByCodeForUpdate(
                request.consumptionCode().trim().toUpperCase()
        );
        if (voucher == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "CONSUMPTION_CODE_NOT_FOUND",
                    "消费码不存在"
            );
        }
        if (!voucher.getVenueId().equals(venueId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "RESOURCE_NOT_OWNED",
                    "消费码不属于当前地点"
            );
        }
        if (voucher.getStatus() != BookingVoucherStatus.AVAILABLE) {
            throw invalidVoucherStatus();
        }

        LocalDateTime redeemedAt = LocalDateTime.now();
        if (redeemedAt.isBefore(voucher.getValidFrom())
                || redeemedAt.isAfter(voucher.getValidUntil())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONSUMPTION_CODE_OUT_OF_WINDOW",
                    "当前不在消费码核销时间窗口"
            );
        }

        Booking booking = bookingMapper.selectById(voucher.getBookingId());
        if (booking == null) {
            throw new IllegalStateException("Voucher booking does not exist");
        }
        if (booking.getStatus() != BookingStatus.BOOKED
                || booking.getPayStatus() != BookingPayStatus.PAID) {
            throw invalidBookingStatus();
        }
        if (voucherMapper.redeemAvailable(voucher.getId(), principal.id(), redeemedAt) != 1) {
            throw invalidVoucherStatus();
        }
        if (bookingMapper.fulfillPaidBooking(booking.getId()) != 1) {
            throw invalidBookingStatus();
        }

        return new BookingVoucherRedeemResponse(
                booking.getId(),
                booking.getBookingNo(),
                BookingStatus.FULFILLED,
                booking.getPayStatus(),
                booking.getPaidAmount(),
                BookingVoucherStatus.REDEEMED,
                principal.id(),
                redeemedAt
        );
    }

    private String generateConsumptionCode() {
        StringBuilder code = new StringBuilder("QM");
        for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
            code.append(CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)]);
        }
        return code.toString();
    }

    private BusinessException invalidVoucherStatus() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "CONSUMPTION_CODE_STATUS_INVALID",
                "消费码当前状态不可用"
        );
    }

    private BusinessException invalidBookingStatus() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "BOOKING_STATUS_INVALID",
                "预约当前状态不允许核销"
        );
    }
}
