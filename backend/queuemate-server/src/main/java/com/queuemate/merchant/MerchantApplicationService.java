package com.queuemate.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserRoleService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MerchantApplicationService {

    private final MerchantApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final UserRoleService userRoleService;

    public MerchantApplicationService(
            MerchantApplicationMapper applicationMapper,
            UserMapper userMapper,
            UserRoleService userRoleService
    ) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.userRoleService = userRoleService;
    }

    @Transactional
    public MerchantApplicationResponse submit(MerchantApplicationRequest request, AuthenticatedUser principal) {
        requireRole(principal, UserRole.USER);
        if (principal.hasRole(UserRole.MERCHANT)) {
            throw new BusinessException(HttpStatus.CONFLICT, "MERCHANT_ALREADY_GRANTED", "当前账号已经拥有商家身份");
        }
        if (applicationMapper.selectCount(Wrappers.<MerchantApplication>lambdaQuery()
                .eq(MerchantApplication::getApplicantId, principal.id())
                .eq(MerchantApplication::getStatus, MerchantApplicationStatus.PENDING)) > 0) {
            throw pendingExists();
        }

        LocalDateTime now = LocalDateTime.now();
        MerchantApplication application = new MerchantApplication();
        application.setApplicantId(principal.id());
        application.setBusinessName(request.businessName().trim());
        application.setContactName(request.contactName().trim());
        application.setContactPhone(request.contactPhone().trim());
        application.setVenueName(request.venueName().trim());
        application.setVenueCategory(request.venueCategory());
        application.setAddressText(request.addressText().trim());
        application.setDescription(normalize(request.description()));
        application.setStatus(MerchantApplicationStatus.PENDING);
        application.setSubmittedAt(now);
        application.setUpdatedAt(now);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException ex) {
            throw pendingExists();
        }
        return toResponse(application);
    }

    public List<MerchantApplicationResponse> mine(AuthenticatedUser principal) {
        requireRole(principal, UserRole.USER);
        return applicationMapper.selectList(Wrappers.<MerchantApplication>lambdaQuery()
                        .eq(MerchantApplication::getApplicantId, principal.id())
                        .orderByDesc(MerchantApplication::getSubmittedAt, MerchantApplication::getId))
                .stream().map(this::toResponse).toList();
    }

    public List<MerchantApplicationResponse> list(MerchantApplicationStatus status, AuthenticatedUser principal) {
        requireRole(principal, UserRole.ADMIN);
        return applicationMapper.selectList(Wrappers.<MerchantApplication>lambdaQuery()
                        .eq(status != null, MerchantApplication::getStatus, status)
                        .orderByAsc(MerchantApplication::getStatus)
                        .orderByDesc(MerchantApplication::getSubmittedAt, MerchantApplication::getId))
                .stream().map(this::toResponse).toList();
    }

    public MerchantApplicationResponse get(Long id, AuthenticatedUser principal) {
        requireRole(principal, UserRole.ADMIN);
        return toResponse(requiredApplication(applicationMapper.selectById(id)));
    }

    @Transactional
    public MerchantApplicationResponse approve(
            Long id,
            MerchantApplicationReviewRequest request,
            AuthenticatedUser principal
    ) {
        requireRole(principal, UserRole.ADMIN);
        MerchantApplication application = requiredApplication(applicationMapper.selectByIdForUpdate(id));
        requirePending(application);
        User applicant = requiredApplicant(application.getApplicantId());

        userRoleService.grantRole(applicant.getId(), UserRole.MERCHANT, principal.id());
        if (applicant.getRole() == UserRole.USER) {
            applicant.setRole(UserRole.MERCHANT);
            userMapper.updateById(applicant);
        }
        finishReview(application, MerchantApplicationStatus.APPROVED, normalize(request.reviewNote()), principal.id());
        return MerchantApplicationResponse.from(application, applicant);
    }

    @Transactional
    public MerchantApplicationResponse reject(
            Long id,
            MerchantApplicationReviewRequest request,
            AuthenticatedUser principal
    ) {
        requireRole(principal, UserRole.ADMIN);
        if (!StringUtils.hasText(request.reviewNote())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REVIEW_NOTE_REQUIRED", "驳回申请时必须填写原因");
        }
        MerchantApplication application = requiredApplication(applicationMapper.selectByIdForUpdate(id));
        requirePending(application);
        finishReview(application, MerchantApplicationStatus.REJECTED, request.reviewNote().trim(), principal.id());
        return toResponse(application);
    }

    public List<MerchantSummaryResponse> merchants(AuthenticatedUser principal) {
        requireRole(principal, UserRole.ADMIN);
        return userMapper.selectActiveMerchants().stream().map(MerchantSummaryResponse::from).toList();
    }

    private void finishReview(
            MerchantApplication application,
            MerchantApplicationStatus status,
            String note,
            Long reviewerId
    ) {
        LocalDateTime now = LocalDateTime.now();
        application.setStatus(status);
        application.setReviewNote(note);
        application.setReviewerId(reviewerId);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);
    }

    private MerchantApplicationResponse toResponse(MerchantApplication application) {
        return MerchantApplicationResponse.from(application, requiredApplicant(application.getApplicantId()));
    }

    private User requiredApplicant(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "申请账号不存在");
        }
        return user;
    }

    private MerchantApplication requiredApplication(MerchantApplication application) {
        if (application == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "MERCHANT_APPLICATION_NOT_FOUND", "商家入驻申请不存在");
        }
        return application;
    }

    private void requirePending(MerchantApplication application) {
        if (application.getStatus() != MerchantApplicationStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "MERCHANT_APPLICATION_REVIEWED", "该申请已经审核，不能重复处理");
        }
    }

    private void requireRole(AuthenticatedUser principal, UserRole role) {
        if (principal == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        if (!principal.hasRole(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "当前账号无权执行此操作");
        }
    }

    private BusinessException pendingExists() {
        return new BusinessException(HttpStatus.CONFLICT, "MERCHANT_APPLICATION_PENDING", "已有待审核的商家入驻申请");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
