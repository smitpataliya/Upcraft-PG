package com.upcraft.stay.organization.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.InvitationStatus;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.HashUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.dto.AcceptInvitationRequestDTO;
import com.upcraft.stay.organization.dto.CreateInvitationRequestDTO;
import com.upcraft.stay.organization.dto.InvitationResponseDTO;
import com.upcraft.stay.organization.entity.InvitationEntity;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.mapper.OrganizationMapper;
import com.upcraft.stay.organization.repository.InvitationRepository;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.security.JwtTokenFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private AccessGuardService accessGuardService;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenFactory jwtTokenFactory;

    private InvitationServiceImpl invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationServiceImpl(
                accessGuardService,
                invitationRepository,
                organizationMemberRepository,
                managerPropertyAssignmentRepository,
                userRepository,
                organizationMapper,
                passwordEncoder,
                jwtTokenFactory,
                new ObjectMapper(),
                new AppProperties()
        );
    }

    @Test
    void createInvitation_whenOwnerInvitesManager_hashesTokenAndCode() {
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User ownerUser = User.builder().id(UUID.randomUUID()).email("owner@pg.com").role(Role.PG_OWNER).active(true).build();
        OrganizationEntity organizationEntity = new OrganizationEntity();
        organizationEntity.setId(orgId);
        PropertyEntity propertyEntity = new PropertyEntity();
        propertyEntity.setId(propertyId);
        propertyEntity.setName("Navrangpura PG");

        when(accessGuardService.loadActiveUser("owner@pg.com")).thenReturn(ownerUser);
        when(accessGuardService.resolveUserOrganization(ownerUser)).thenReturn(organizationEntity);
        when(accessGuardService.requirePropertyManagementAccess(ownerUser, propertyId, "TENANTS"))
                .thenReturn(propertyEntity);
        when(invitationRepository.save(any(InvitationEntity.class))).thenAnswer(invocation -> {
            InvitationEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(organizationMapper.toInvitationResponseDTO(any(InvitationEntity.class))).thenAnswer(invocation -> {
            InvitationEntity entity = invocation.getArgument(0);
            return new InvitationResponseDTO(
                    entity.getId(), orgId, propertyId, "Navrangpura PG", entity.getEmail(), entity.getPhone(),
                    entity.getInviteeName(), entity.getTargetRole(), entity.getInvitationStatus(),
                    entity.getExpiresAt(), null, null, null, Instant.now()
            );
        });

        CreateInvitationRequestDTO requestDTO = new CreateInvitationRequestDTO(
                "warden@pg.com", "9876543211", "Ramesh Warden", Role.MANAGER,
                propertyId, true, true, true, true, false
        );

        InvitationResponseDTO responseDTO = invitationService.createInvitation("owner@pg.com", requestDTO);

        ArgumentCaptor<InvitationEntity> captor = ArgumentCaptor.forClass(InvitationEntity.class);
        verify(invitationRepository).save(captor.capture());
        InvitationEntity persisted = captor.getValue();

        assertThat(responseDTO.inviteCode()).hasSize(8);
        assertThat(responseDTO.inviteUrl()).contains("token=");
        assertThat(persisted.getInviteCodeHash()).isEqualTo(HashUtils.sha256Hex(responseDTO.inviteCode()));
        assertThat(persisted.getTokenHash()).isNotEqualTo(responseDTO.inviteUrl());
    }

    @Test
    void createInvitation_whenManagerInvitesManager_throwsForbiddenException() {
        User managerUser = User.builder().id(UUID.randomUUID()).email("mgr@pg.com").role(Role.MANAGER).active(true).build();
        when(accessGuardService.loadActiveUser("mgr@pg.com")).thenReturn(managerUser);

        CreateInvitationRequestDTO requestDTO = new CreateInvitationRequestDTO(
                "other@pg.com", null, "Another Mgr", Role.MANAGER, UUID.randomUUID(),
                true, true, true, true, true
        );

        assertThatThrownBy(() -> invitationService.createInvitation("mgr@pg.com", requestDTO))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptInvitation_whenExpired_throwsValidationException() {
        InvitationEntity expiredEntity = new InvitationEntity();
        expiredEntity.setId(UUID.randomUUID());
        expiredEntity.setInvitationStatus(InvitationStatus.PENDING);
        expiredEntity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(invitationRepository.findByInviteCodeHash(HashUtils.sha256Hex("ABCD1234")))
                .thenReturn(Optional.of(expiredEntity));

        AcceptInvitationRequestDTO requestDTO = new AcceptInvitationRequestDTO(
                null, "abcd1234", "Tenant User", "9876543210", "StrongPass@123"
        );

        assertThatThrownBy(() -> invitationService.acceptInvitation(requestDTO))
                .isInstanceOf(ValidationException.class);
    }
}
