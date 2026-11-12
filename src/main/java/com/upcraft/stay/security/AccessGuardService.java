package com.upcraft.stay.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.UnauthorizedException;
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

/**
 * Backend authorization guard resolving organization and property scopes strictly from persistence.
 */
@Service("accessGuard")
@RequiredArgsConstructor
public class AccessGuardService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;
    private final PropertyRepository propertyRepository;

    /**
     * Loads an active user entity by email/principal name.
     *
     * @param principalEmail authenticated email address
     * @return user entity
     */
    @Transactional(readOnly = true)
    public User loadActiveUser(String principalEmail) {
        return userRepository.findByEmail(principalEmail.trim().toLowerCase())
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Authenticated account is inactive or missing"));
    }

    /**
     * Resolves the primary organization linked to a user.
     *
     * @param userEntity authenticated user
     * @return organization entity
     */
    @Transactional(readOnly = true)
    public OrganizationEntity resolveUserOrganization(User userEntity) {
        Optional<OrganizationEntity> memberOrganization = organizationMemberRepository
                .findFirstByUserIdAndPrimaryTrue(userEntity.getId())
                .or(() -> organizationMemberRepository.findFirstByUserId(userEntity.getId()))
                .map(OrganizationMemberEntity::getOrganization);

        return memberOrganization
                .or(() -> organizationRepository.findFirstByOwnerUserIdOrderByCreatedAtAsc(userEntity.getId()))
                .orElseThrow(() -> new ForbiddenException("No organization is linked to this account"));
    }

    /**
     * Resolves optional organization ID for users like SUPER_ADMIN who may not belong to an organization.
     *
     * @param userEntity authenticated user
     * @return optional organization entity
     */
    @Transactional(readOnly = true)
    public Optional<OrganizationEntity> findUserOrganization(User userEntity) {
        return organizationMemberRepository.findFirstByUserIdAndPrimaryTrue(userEntity.getId())
                .or(() -> organizationMemberRepository.findFirstByUserId(userEntity.getId()))
                .map(OrganizationMemberEntity::getOrganization)
                .or(() -> organizationRepository.findFirstByOwnerUserIdOrderByCreatedAtAsc(userEntity.getId()));
    }

    /**
     * Resolves property IDs accessible to the user.
     *
     * @param userEntity authenticated user
     * @return list of assigned property UUIDs for managers
     */
    @Transactional(readOnly = true)
    public List<UUID> resolveAssignedPropertyIds(User userEntity) {
        if (userEntity.getRole() != Role.MANAGER) {
            return List.of();
        }
        return managerPropertyAssignmentRepository
                .findAllByManagerUserIdAndStatus(userEntity.getId(), AppConstants.STATUS_ACTIVE)
                .stream()
                .map(assignment -> assignment.getProperty().getId())
                .toList();
    }

    /**
     * Builds full authenticated user context including organization and property scope.
     *
     * @param userEntity authenticated user
     * @return user DTO
     */
    @Transactional(readOnly = true)
    public AuthenticatedUserDTO buildAuthenticatedUser(User userEntity) {
        Optional<OrganizationEntity> organizationOptional = findUserOrganization(userEntity);
        UUID organizationId = organizationOptional.map(OrganizationEntity::getId).orElse(null);
        String organizationName = organizationOptional.map(OrganizationEntity::getName).orElse(null);
        List<UUID> assignedPropertyIds = resolveAssignedPropertyIds(userEntity);
        return new AuthenticatedUserDTO(
                userEntity.getId(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getUsername(),
                userEntity.getRole(),
                userEntity.getPhone(),
                userEntity.getAvatarUrl(),
                organizationId,
                organizationName,
                assignedPropertyIds
        );
    }

    /**
     * Checks whether the current security principal can read/access the given property.
     *
     * @param propertyId target property UUID
     * @return true when permitted
     */
    @Transactional(readOnly = true)
    public boolean canAccessProperty(UUID propertyId) {
        Optional<User> currentUserOptional = resolveSecurityContextUser();
        if (currentUserOptional.isEmpty()) {
            return false;
        }
        User currentUser = currentUserOptional.get();
        return switch (currentUser.getRole()) {
            case PG_OWNER -> findUserOrganization(currentUser)
                    .flatMap(org -> propertyRepository.findByIdAndOrganizationId(propertyId, org.getId()))
                    .isPresent();
            case MANAGER -> managerPropertyAssignmentRepository
                    .existsByPropertyIdAndManagerUserIdAndStatus(propertyId, currentUser.getId(), AppConstants.STATUS_ACTIVE);
            case PG_MEMBER, SUPER_ADMIN -> false;
        };
    }

    /**
     * Verifies property write/operation permission for owner or assigned manager.
     *
     * @param userEntity       authenticated user
     * @param propertyId       target property UUID
     * @param requiredAbility  permission flag name ({@code TENANTS}, {@code PAYMENTS}, {@code METERS}, etc.)
     */
    @Transactional(readOnly = true)
    public PropertyEntity requirePropertyManagementAccess(User userEntity, UUID propertyId, String requiredAbility) {
        OrganizationEntity organizationEntity = resolveUserOrganization(userEntity);
        PropertyEntity propertyEntity = propertyRepository.findByIdAndOrganizationId(propertyId, organizationEntity.getId())
                .orElseThrow(() -> new ForbiddenException("Property is outside your organization scope"));

        if (userEntity.getRole() == Role.PG_OWNER) {
            return propertyEntity;
        }
        if (userEntity.getRole() != Role.MANAGER) {
            throw new ForbiddenException("Insufficient permissions for property operations");
        }

        ManagerPropertyAssignmentEntity assignment = managerPropertyAssignmentRepository
                .findByPropertyIdAndManagerUserId(propertyId, userEntity.getId())
                .filter(item -> item.getStatus() == AppConstants.STATUS_ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Manager is not assigned to this property"));

        boolean permitted = switch (requiredAbility) {
            case "TENANTS" -> assignment.isCanManageTenants();
            case "PAYMENTS" -> assignment.isCanRecordPayments();
            case "METERS" -> assignment.isCanEnterMeters();
            case "COMPLAINTS" -> assignment.isCanHandleComplaints();
            case "NOTICES" -> assignment.isCanPublishNotices();
            default -> true;
        };
        if (!permitted) {
            throw new ForbiddenException("Manager lacks required permission for this property");
        }
        return propertyEntity;
    }

    /**
     * Resolves all property IDs readable/accessible to the authenticated owner or manager,
     * optionally narrowing and verifying access when a specific {@code propertyId} filter is provided.
     *
     * @param userEntity         authenticated user
     * @param requestedPropertyId optional property UUID filter
     * @return non-empty list of permitted property UUIDs (may be empty if manager has no assignments)
     */
    @Transactional(readOnly = true)
    public List<UUID> resolveAccessiblePropertyIds(User userEntity, Optional<UUID> requestedPropertyId) {
        if (requestedPropertyId.isPresent()) {
            PropertyEntity allowedProperty = requirePropertyManagementAccess(
                    userEntity, requestedPropertyId.get(), "READ"
            );
            return List.of(allowedProperty.getId());
        }
        OrganizationEntity organizationEntity = resolveUserOrganization(userEntity);
        if (userEntity.getRole() == Role.PG_OWNER) {
            return propertyRepository.findAllByOrganizationIdOrderByNameAsc(organizationEntity.getId())
                    .stream()
                    .map(PropertyEntity::getId)
                    .toList();
        }
        if (userEntity.getRole() == Role.MANAGER) {
            return resolveAssignedPropertyIds(userEntity);
        }
        throw new ForbiddenException("Insufficient permissions for property operations");
    }

    private Optional<User> resolveSecurityContextUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .flatMap(email -> userRepository.findByEmail(email.trim().toLowerCase()))
                .filter(User::isActive);
    }
}

