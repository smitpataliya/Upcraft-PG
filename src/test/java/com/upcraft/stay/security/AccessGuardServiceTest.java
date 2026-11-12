package com.upcraft.stay.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.ManagerPropertyAssignmentEntity;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.organization.repository.OrganizationRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGuardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private AccessGuardService accessGuardService;

    @BeforeEach
    void setUp() {
        accessGuardService = new AccessGuardService(
                userRepository,
                organizationRepository,
                organizationMemberRepository,
                managerPropertyAssignmentRepository,
                propertyRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canAccessProperty_whenOwnerInSameOrg_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User ownerUser = User.builder().id(userId).email("owner@pg.com").role(Role.PG_OWNER).active(true).build();
        OrganizationEntity organizationEntity = new OrganizationEntity();
        organizationEntity.setId(orgId);
        OrganizationMemberEntity memberEntity = new OrganizationMemberEntity();
        memberEntity.setOrganization(organizationEntity);
        PropertyEntity propertyEntity = new PropertyEntity();
        propertyEntity.setId(propertyId);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@pg.com", "pw", List.of())
        );
        when(userRepository.findByEmail("owner@pg.com")).thenReturn(Optional.of(ownerUser));
        when(organizationMemberRepository.findFirstByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.of(memberEntity));
        when(propertyRepository.findByIdAndOrganizationId(propertyId, orgId)).thenReturn(Optional.of(propertyEntity));

        assertThat(accessGuardService.canAccessProperty(propertyId)).isTrue();
    }

    @Test
    void canAccessProperty_whenManagerNotAssigned_returnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        User managerUser = User.builder().id(userId).email("mgr@pg.com").role(Role.MANAGER).active(true).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mgr@pg.com", "pw", List.of())
        );
        when(userRepository.findByEmail("mgr@pg.com")).thenReturn(Optional.of(managerUser));
        when(managerPropertyAssignmentRepository.existsByPropertyIdAndManagerUserIdAndStatus(
                propertyId, userId, AppConstants.STATUS_ACTIVE
        )).thenReturn(false);

        assertThat(accessGuardService.canAccessProperty(propertyId)).isFalse();
    }

    @Test
    void requirePropertyManagementAccess_whenManagerLacksPaymentsFlag_throwsForbiddenException() {
        UUID managerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User managerUser = User.builder().id(managerId).email("mgr@pg.com").role(Role.MANAGER).active(true).build();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        OrganizationMemberEntity memberEntity = new OrganizationMemberEntity();
        memberEntity.setOrganization(org);
        PropertyEntity property = new PropertyEntity();
        property.setId(propertyId);

        ManagerPropertyAssignmentEntity assignment = new ManagerPropertyAssignmentEntity();
        assignment.setStatus(AppConstants.STATUS_ACTIVE);
        assignment.setCanRecordPayments(false);

        when(organizationMemberRepository.findFirstByUserIdAndPrimaryTrue(managerId))
                .thenReturn(Optional.of(memberEntity));
        when(propertyRepository.findByIdAndOrganizationId(propertyId, orgId)).thenReturn(Optional.of(property));
        when(managerPropertyAssignmentRepository.findByPropertyIdAndManagerUserId(propertyId, managerId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> accessGuardService.requirePropertyManagementAccess(managerUser, propertyId, "PAYMENTS"))
                .isInstanceOf(ForbiddenException.class);
    }
}
