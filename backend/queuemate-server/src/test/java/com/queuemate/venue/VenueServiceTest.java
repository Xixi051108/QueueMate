package com.queuemate.venue;

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
import com.queuemate.user.UserStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueMapper venueMapper;

    @Mock
    private UserMapper userMapper;

    private VenueService venueService;

    @BeforeEach
    void setUp() {
        venueService = new VenueService(venueMapper, userMapper);
    }

    @Test
    void publicListReturnsMappedVenues() {
        when(venueMapper.selectList(any())).thenReturn(List.of(activeVenue(4001L, 2001L)));

        List<VenueResponse> response = venueService.list(
                VenueCategory.TEA_SHOP,
                VenueStatus.ACTIVE,
                "奶茶"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(4001L);
        assertThat(response.getFirst().category()).isEqualTo(VenueCategory.TEA_SHOP);
    }

    @Test
    void merchantCreateAlwaysUsesCurrentMerchant() {
        when(venueMapper.selectCount(any())).thenReturn(0L);
        when(venueMapper.insert(any(Venue.class))).thenAnswer(invocation -> {
            Venue venue = invocation.getArgument(0);
            venue.setId(4101L);
            return 1;
        });

        VenueResponse response = venueService.create(
                createRequest(9999L),
                merchantPrincipal(2001L)
        );

        assertThat(response.merchantId()).isEqualTo(2001L);
        assertThat(response.status()).isEqualTo(VenueStatus.ACTIVE);
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void adminCreateRequiresAnActiveMerchant() {
        User merchant = new User();
        merchant.setId(2002L);
        merchant.setRole(UserRole.MERCHANT);
        merchant.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectById(2002L)).thenReturn(merchant);
        when(venueMapper.selectCount(any())).thenReturn(0L);
        when(venueMapper.insert(any(Venue.class))).thenAnswer(invocation -> {
            Venue venue = invocation.getArgument(0);
            venue.setId(4102L);
            return 1;
        });

        VenueResponse response = venueService.create(createRequest(2002L), adminPrincipal());

        assertThat(response.merchantId()).isEqualTo(2002L);
    }

    @Test
    void adminCreateRejectsMissingMerchantId() {
        assertThatThrownBy(() -> venueService.create(createRequest(null), adminPrincipal()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("MERCHANT_INVALID");
                });
    }

    @Test
    void userCannotCreateVenue() {
        AuthenticatedUser user = new AuthenticatedUser(3001L, "alice", UserRole.USER);

        assertThatThrownBy(() -> venueService.create(createRequest(null), user))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("AUTH_FORBIDDEN");
                });
    }

    @Test
    void merchantCanUpdateOwnedVenue() {
        Venue venue = activeVenue(4001L, 2001L);
        when(venueMapper.selectById(4001L)).thenReturn(venue);
        when(venueMapper.selectCount(any())).thenReturn(0L);

        VenueResponse response = venueService.update(
                4001L,
                updateRequest("更新后的奶茶店"),
                merchantPrincipal(2001L)
        );

        assertThat(response.name()).isEqualTo("更新后的奶茶店");
        verify(venueMapper).updateById(venue);
    }

    @Test
    void merchantCannotUpdateAnotherMerchantsVenue() {
        Venue venue = activeVenue(4003L, 2002L);
        when(venueMapper.selectById(4003L)).thenReturn(venue);

        assertThatThrownBy(() -> venueService.update(
                4003L,
                updateRequest("越权修改"),
                merchantPrincipal(2001L)
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_OWNED");
        });

        verify(venueMapper, never()).updateById(any(Venue.class));
    }

    @Test
    void adminCanDisableAnyVenue() {
        Venue venue = activeVenue(4003L, 2002L);
        when(venueMapper.selectById(4003L)).thenReturn(venue);

        VenueResponse response = venueService.updateStatus(
                4003L,
                VenueStatus.INACTIVE,
                adminPrincipal()
        );

        assertThat(response.status()).isEqualTo(VenueStatus.INACTIVE);
        verify(venueMapper).updateById(venue);
    }

    @Test
    void missingVenueReturnsNotFound() {
        when(venueMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> venueService.get(9999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("VENUE_NOT_FOUND");
                });
    }

    @Test
    void duplicateVenueNameIsRejected() {
        when(venueMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> venueService.create(createRequest(null), merchantPrincipal(2001L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("VENUE_NAME_EXISTS");
                });

        verify(venueMapper, never()).insert(any(Venue.class));
    }

    @Test
    void createNormalizesOptionalText() {
        when(venueMapper.selectCount(any())).thenReturn(0L);
        when(venueMapper.insert(any(Venue.class))).thenReturn(1);

        venueService.create(
                new VenueCreateRequest(
                        "  新地点  ",
                        VenueCategory.STUDY_ROOM,
                        "   ",
                        "  模拟地址  ",
                        false,
                        true,
                        new BigDecimal("10.00"),
                        null
                ),
                merchantPrincipal(2001L)
        );

        ArgumentCaptor<Venue> captor = ArgumentCaptor.forClass(Venue.class);
        verify(venueMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新地点");
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getAddressText()).isEqualTo("模拟地址");
    }

    private VenueCreateRequest createRequest(Long merchantId) {
        return new VenueCreateRequest(
                "QueueMate 新地点",
                VenueCategory.TEA_SHOP,
                "模拟地点",
                "模拟地址",
                true,
                false,
                BigDecimal.ZERO,
                merchantId
        );
    }

    private VenueUpdateRequest updateRequest(String name) {
        return new VenueUpdateRequest(
                name,
                VenueCategory.TEA_SHOP,
                "更新描述",
                "更新地址",
                true,
                false,
                BigDecimal.ZERO
        );
    }

    private Venue activeVenue(Long id, Long merchantId) {
        Venue venue = new Venue();
        venue.setId(id);
        venue.setName("QueueMate 奶茶店 A");
        venue.setCategory(VenueCategory.TEA_SHOP);
        venue.setDescription("模拟奶茶店");
        venue.setMerchantId(merchantId);
        venue.setAddressText("模拟商业街 1 号");
        venue.setQueueEnabled(true);
        venue.setBookingEnabled(false);
        venue.setDefaultPrice(BigDecimal.ZERO);
        venue.setStatus(VenueStatus.ACTIVE);
        return venue;
    }

    private AuthenticatedUser merchantPrincipal(Long id) {
        return new AuthenticatedUser(id, "merchant", UserRole.MERCHANT);
    }

    private AuthenticatedUser adminPrincipal() {
        return new AuthenticatedUser(1001L, "admin", UserRole.ADMIN);
    }
}
