package com.queuemate.venue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VenueService {

    private final VenueMapper venueMapper;
    private final UserMapper userMapper;

    public VenueService(VenueMapper venueMapper, UserMapper userMapper) {
        this.venueMapper = venueMapper;
        this.userMapper = userMapper;
    }

    public List<VenueResponse> list(VenueCategory category, VenueStatus status, String keyword) {
        LambdaQueryWrapper<Venue> query = Wrappers.lambdaQuery();
        query.eq(category != null, Venue::getCategory, category)
                .eq(status != null, Venue::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(nested -> nested
                    .like(Venue::getName, normalizedKeyword)
                    .or()
                    .like(Venue::getDescription, normalizedKeyword)
                    .or()
                    .like(Venue::getAddressText, normalizedKeyword));
        }
        query.orderByAsc(Venue::getId);
        return venueMapper.selectList(query).stream()
                .map(VenueResponse::from)
                .toList();
    }

    public VenueResponse get(Long id) {
        return VenueResponse.from(getRequiredVenue(id));
    }

    @Transactional
    public VenueResponse create(VenueCreateRequest request, AuthenticatedUser principal) {
        requireManager(principal);
        Long merchantId = resolveMerchantId(request.merchantId(), principal);
        ensureNameAvailable(request.name().trim(), merchantId, null);

        Venue venue = new Venue();
        venue.setMerchantId(merchantId);
        applyWritableFields(
                venue,
                request.name(),
                request.category(),
                request.description(),
                request.addressText(),
                request.queueEnabled(),
                request.bookingEnabled(),
                request.defaultPrice()
        );
        venue.setStatus(VenueStatus.ACTIVE);

        try {
            venueMapper.insert(venue);
        } catch (DuplicateKeyException ex) {
            throw venueNameExists();
        }
        return VenueResponse.from(venue);
    }

    @Transactional
    public VenueResponse update(Long id, VenueUpdateRequest request, AuthenticatedUser principal) {
        Venue venue = getRequiredVenue(id);
        requireOwnerOrAdmin(venue, principal);
        ensureNameAvailable(request.name().trim(), venue.getMerchantId(), venue.getId());

        applyWritableFields(
                venue,
                request.name(),
                request.category(),
                request.description(),
                request.addressText(),
                request.queueEnabled(),
                request.bookingEnabled(),
                request.defaultPrice()
        );
        try {
            venueMapper.updateById(venue);
        } catch (DuplicateKeyException ex) {
            throw venueNameExists();
        }
        return VenueResponse.from(venue);
    }

    @Transactional
    public VenueResponse updateStatus(Long id, VenueStatus status, AuthenticatedUser principal) {
        Venue venue = getRequiredVenue(id);
        requireOwnerOrAdmin(venue, principal);
        venue.setStatus(status);
        venueMapper.updateById(venue);
        return VenueResponse.from(venue);
    }

    public Venue getRequiredVenue(Long id) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "地点不存在");
        }
        return venue;
    }

    private Long resolveMerchantId(Long requestedMerchantId, AuthenticatedUser principal) {
        if (principal.role() == UserRole.MERCHANT) {
            return principal.id();
        }
        if (requestedMerchantId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MERCHANT_INVALID", "管理员创建地点时必须指定商家");
        }
        User merchant = userMapper.selectById(requestedMerchantId);
        if (merchant == null || merchant.getRole() != UserRole.MERCHANT || merchant.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MERCHANT_INVALID", "指定的商家不存在或不可用");
        }
        return merchant.getId();
    }

    private void requireManager(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (principal.role() != UserRole.MERCHANT && principal.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "无权管理地点");
        }
    }

    public void requireOwnerOrAdmin(Venue venue, AuthenticatedUser principal) {
        requireManager(principal);
        if (principal.role() == UserRole.ADMIN) {
            return;
        }
        if (!venue.getMerchantId().equals(principal.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "RESOURCE_NOT_OWNED", "只能操作自己名下的地点");
        }
    }

    private void ensureNameAvailable(String name, Long merchantId, Long excludedId) {
        LambdaQueryWrapper<Venue> query = Wrappers.<Venue>lambdaQuery()
                .eq(Venue::getName, name)
                .eq(Venue::getMerchantId, merchantId)
                .ne(excludedId != null, Venue::getId, excludedId);
        if (venueMapper.selectCount(query) > 0) {
            throw venueNameExists();
        }
    }

    private BusinessException venueNameExists() {
        return new BusinessException(HttpStatus.CONFLICT, "VENUE_NAME_EXISTS", "当前商家已存在同名地点");
    }

    private void applyWritableFields(
            Venue venue,
            String name,
            VenueCategory category,
            String description,
            String addressText,
            Boolean queueEnabled,
            Boolean bookingEnabled,
            java.math.BigDecimal defaultPrice
    ) {
        venue.setName(name.trim());
        venue.setCategory(category);
        venue.setDescription(normalizeOptionalText(description));
        venue.setAddressText(normalizeOptionalText(addressText));
        venue.setQueueEnabled(queueEnabled);
        venue.setBookingEnabled(bookingEnabled);
        venue.setDefaultPrice(defaultPrice);
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
