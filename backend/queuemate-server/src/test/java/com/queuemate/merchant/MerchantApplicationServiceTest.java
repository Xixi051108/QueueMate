package com.queuemate.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserRoleService;
import com.queuemate.user.UserStatus;
import com.queuemate.venue.VenueCategory;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MerchantApplicationServiceTest {

    @Mock
    private MerchantApplicationMapper applicationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleService userRoleService;

    private MerchantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantApplicationService(applicationMapper, userMapper, userRoleService);
    }

    @Test
    void userSubmitsApplication() {
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(applicationMapper.insert(any(MerchantApplication.class))).thenAnswer(invocation -> {
            MerchantApplication application = invocation.getArgument(0);
            application.setId(501L);
            return 1;
        });
        when(userMapper.selectById(3001L)).thenReturn(user(3001L, UserRole.USER));

        MerchantApplicationResponse response = service.submit(request(), userPrincipal());

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
        assertThat(response.venueName()).isEqualTo("青禾自习室·滨江店");
        verify(applicationMapper).insert(any(MerchantApplication.class));
    }

    @Test
    void duplicatePendingApplicationIsRejected() {
        when(applicationMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(request(), userPrincipal()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("MERCHANT_APPLICATION_PENDING");
                });
        verify(applicationMapper, never()).insert(any(MerchantApplication.class));
    }

    @Test
    void existingMerchantCannotApplyAgain() {
        AuthenticatedUser principal = new AuthenticatedUser(
                3001L, "alice", UserRole.MERCHANT, Set.of(UserRole.USER, UserRole.MERCHANT)
        );

        assertThatThrownBy(() -> service.submit(request(), principal))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MERCHANT_ALREADY_GRANTED"));
    }

    @Test
    void approveGrantsMerchantRoleAndKeepsApplicationAudit() {
        MerchantApplication application = pendingApplication();
        User applicant = user(3001L, UserRole.USER);
        when(applicationMapper.selectByIdForUpdate(501L)).thenReturn(application);
        when(userMapper.selectById(3001L)).thenReturn(applicant);

        MerchantApplicationResponse response = service.approve(
                501L,
                new MerchantApplicationReviewRequest("资料核对通过"),
                adminPrincipal()
        );

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.APPROVED);
        assertThat(response.reviewerId()).isEqualTo(1001L);
        assertThat(applicant.getRole()).isEqualTo(UserRole.MERCHANT);
        verify(userRoleService).grantRole(3001L, UserRole.MERCHANT, 1001L);
        verify(userMapper).updateById(applicant);
        verify(applicationMapper).updateById(application);
    }

    @Test
    void rejectRequiresRecoveryReason() {
        assertThatThrownBy(() -> service.reject(
                501L,
                new MerchantApplicationReviewRequest("  "),
                adminPrincipal()
        )).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("REVIEW_NOTE_REQUIRED"));
        verify(applicationMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void reviewedApplicationCannotBeReviewedAgain() {
        MerchantApplication application = pendingApplication();
        application.setStatus(MerchantApplicationStatus.APPROVED);
        when(applicationMapper.selectByIdForUpdate(501L)).thenReturn(application);

        assertThatThrownBy(() -> service.approve(
                501L,
                new MerchantApplicationReviewRequest(null),
                adminPrincipal()
        )).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("MERCHANT_APPLICATION_REVIEWED"));
        verify(userRoleService, never()).grantRole(any(), any(), any());
    }

    private MerchantApplicationRequest request() {
        return new MerchantApplicationRequest(
                "青禾空间", "张三", "13800003001", "青禾自习室·滨江店",
                VenueCategory.STUDY_ROOM, "滨江新区云帆路88号3层", "提供安静自习座位"
        );
    }

    private MerchantApplication pendingApplication() {
        MerchantApplication application = new MerchantApplication();
        application.setId(501L);
        application.setApplicantId(3001L);
        application.setBusinessName("青禾空间");
        application.setContactName("张三");
        application.setContactPhone("13800003001");
        application.setVenueName("青禾自习室·滨江店");
        application.setVenueCategory(VenueCategory.STUDY_ROOM);
        application.setAddressText("滨江新区云帆路88号3层");
        application.setStatus(MerchantApplicationStatus.PENDING);
        application.setSubmittedAt(LocalDateTime.now());
        return application;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setPhone("13800003001");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private AuthenticatedUser userPrincipal() {
        return new AuthenticatedUser(3001L, "alice", UserRole.USER);
    }

    private AuthenticatedUser adminPrincipal() {
        return new AuthenticatedUser(1001L, "admin", UserRole.ADMIN);
    }
}
