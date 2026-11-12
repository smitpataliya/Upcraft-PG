package com.upcraft.stay.organization.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.InvitationStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.HashUtils;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.dto.AcceptInvitationRequestDTO;
import com.upcraft.stay.organization.dto.CreateInvitationRequestDTO;
import com.upcraft.stay.organization.dto.InvitationPreviewResponseDTO;
import com.upcraft.stay.organization.dto.InvitationResponseDTO;
import com.upcraft.stay.organization.entity.InvitationEntity;
import com.upcraft.stay.organization.entity.ManagerPropertyAssignmentEntity;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;
import com.upcraft.stay.organization.mapper.OrganizationMapper;
import com.upcraft.stay.organization.repository.InvitationRepository;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.organization.service.InvitationService;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.security.JwtTokenFactory;

/**
 * Secure invitation creation, verification, acceptance, and revocation
 * implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "expiresAt", "invitationStatus");

    private final AccessGuardService accessGuardService;
    private final InvitationRepository invitationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;
    private final UserRepository userRepository;
    private final OrganizationMapper organizationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenFactory jwtTokenFactory;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public InvitationResponseDTO createInvitation(
            String principalEmail,
            CreateInvitationRequestDTO requestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        validateInvitationCreatorRole(currentUser, requestDTO);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        PropertyEntity propertyEntity = resolveTargetProperty(currentUser, requestDTO);

        String rawToken = HashUtils.generateUrlSafeToken();
        String rawCode = HashUtils.generateInviteCode();

        InvitationEntity invitationEntity = buildInvitationEntity(
                currentUser, organizationEntity, propertyEntity, requestDTO, rawToken, rawCode);
        InvitationEntity savedInvitation = invitationRepository.save(invitationEntity);
        log.info("Invitation created. invitationId={}, targetRole={}, organizationId={}",
                savedInvitation.getId(), savedInvitation.getTargetRole(), organizationEntity.getId());

        InvitationResponseDTO mapped = organizationMapper.toInvitationResponseDTO(savedInvitation);
        String inviteUrl = String.format("%s/auth/invite?token=%s", appProperties.getInvitation().getBaseUrl(),
                rawToken);
        return new InvitationResponseDTO(
                mapped.id(), mapped.organizationId(), mapped.propertyId(), mapped.propertyName(),
                mapped.email(), mapped.phone(), mapped.inviteeName(), mapped.targetRole(),
                mapped.invitationStatus(), mapped.expiresAt(), mapped.acceptedAt(),
                inviteUrl, rawCode, mapped.createdAt());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InvitationResponseDTO> listInvitations(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "createdAt", appProperties);

        if (Optional.ofNullable(propertyId).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "TENANTS");
            Page<InvitationResponseDTO> filtered = invitationRepository
                    .findAllByOrganizationIdAndPropertyId(organizationEntity.getId(), propertyId, pageable)
                    .map(organizationMapper::toInvitationResponseDTO);
            return PageResponseDTO.from(filtered);
        }

        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Managers must filter invitations by an assigned property");
        }
        Page<InvitationResponseDTO> allPage = invitationRepository
                .findAllByOrganizationId(organizationEntity.getId(), pageable)
                .map(organizationMapper::toInvitationResponseDTO);
        return PageResponseDTO.from(allPage);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationPreviewResponseDTO verifyInvitation(String token, String code) {
        InvitationEntity invitationEntity = findInvitationByTokenOrCode(token, code);
        boolean activeAndUnexpired = invitationEntity.getInvitationStatus() == InvitationStatus.PENDING
                && invitationEntity.getExpiresAt().isAfter(Instant.now());
        InvitationStatus effectiveStatus = invitationEntity.getExpiresAt().isBefore(Instant.now())
                && invitationEntity.getInvitationStatus() == InvitationStatus.PENDING
                        ? InvitationStatus.EXPIRED
                        : invitationEntity.getInvitationStatus();
        String propertyName = Optional.ofNullable(invitationEntity.getProperty())
                .map(PropertyEntity::getName)
                .orElse(null);

        return new InvitationPreviewResponseDTO(
                invitationEntity.getOrganization().getName(),
                propertyName,
                invitationEntity.getInviteeName(),
                invitationEntity.getEmail(),
                invitationEntity.getTargetRole(),
                effectiveStatus,
                invitationEntity.getExpiresAt(),
                activeAndUnexpired);
    }

    @Override
    @Transactional
    public AuthTokenResponseDTO acceptInvitation(AcceptInvitationRequestDTO requestDTO) {
        InvitationEntity invitationEntity = findInvitationByTokenOrCode(requestDTO.token(), requestDTO.code());
        validateInvitationForAcceptance(invitationEntity);

        String normalizedEmail = invitationEntity.getEmail().trim().toLowerCase(Locale.ROOT);
        User acceptedUser = upsertInvitedUser(invitationEntity, requestDTO, normalizedEmail);
        upsertOrganizationMember(invitationEntity.getOrganization(), acceptedUser, invitationEntity.getTargetRole());
        assignManagerIfApplicable(invitationEntity, acceptedUser);

        invitationEntity.setInvitationStatus(InvitationStatus.ACCEPTED);
        invitationEntity.setAcceptedAt(Instant.now());
        invitationEntity.setAcceptedByUser(acceptedUser);
        invitationRepository.save(invitationEntity);

        log.info("Invitation accepted. invitationId={}, userId={}, role={}",
                invitationEntity.getId(), acceptedUser.getId(), acceptedUser.getRole());
        return issueTokenResponse(acceptedUser);
    }

    @Override
    @Transactional
    public void revokeInvitation(String principalEmail, UUID invitationId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        InvitationEntity invitationEntity = invitationRepository
                .findByIdAndOrganizationId(invitationId, organizationEntity.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (currentUser.getRole() == Role.MANAGER && Optional.ofNullable(invitationEntity.getProperty()).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(
                    currentUser, invitationEntity.getProperty().getId(), "TENANTS");
        } else if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("You do not have permission to revoke this invitation");
        }

        if (invitationEntity.getInvitationStatus() == InvitationStatus.ACCEPTED) {
            throw new ConflictException("Accepted invitations cannot be revoked");
        }
        invitationEntity.setInvitationStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitationEntity);
    }

    private void validateInvitationCreatorRole(User creator, CreateInvitationRequestDTO requestDTO) {
        if (requestDTO.targetRole() != Role.MANAGER && requestDTO.targetRole() != Role.PG_MEMBER) {
            throw new ValidationException("Invitations support only MANAGER or PG_MEMBER roles");
        }
        if (creator.getRole() == Role.PG_OWNER) {
            return;
        }
        if (creator.getRole() == Role.MANAGER && requestDTO.targetRole() == Role.PG_MEMBER) {
            return;
        }
        throw new ForbiddenException("You do not have permission to invite this role");
    }

    private PropertyEntity resolveTargetProperty(User creator, CreateInvitationRequestDTO requestDTO) {
        if (Optional.ofNullable(requestDTO.propertyId()).isEmpty()) {
            throw new ValidationException("Property ID is required for manager and tenant invitations");
        }
        return accessGuardService.requirePropertyManagementAccess(creator, requestDTO.propertyId(), "TENANTS");
    }

    private InvitationEntity buildInvitationEntity(
            User creator,
            OrganizationEntity organizationEntity,
            PropertyEntity propertyEntity,
            CreateInvitationRequestDTO requestDTO,
            String rawToken,
            String rawCode) {
        InvitationEntity entity = new InvitationEntity();
        entity.setOrganization(organizationEntity);
        entity.setProperty(propertyEntity);
        entity.setEmail(requestDTO.email().trim().toLowerCase(Locale.ROOT));
        entity.setPhone(StringUtils.trimToNull(requestDTO.phone()));
        entity.setInviteeName(requestDTO.inviteeName().trim());
        entity.setTargetRole(requestDTO.targetRole());
        entity.setTokenHash(HashUtils.sha256Hex(rawToken));
        entity.setInviteCodeHash(HashUtils.sha256Hex(rawCode));
        entity.setPermissionsJson(serializePermissions(requestDTO));
        entity.setInvitationStatus(InvitationStatus.PENDING);
        entity.setExpiresAt(Instant.now().plus(appProperties.getInvitation().getExpiryHours(), ChronoUnit.HOURS));
        entity.setInvitedByUser(creator);
        return entity;
    }

    private String serializePermissions(CreateInvitationRequestDTO requestDTO) {
        if (requestDTO.targetRole() != Role.MANAGER) {
            return null;
        }
        ManagerPermissionsPayload payload = new ManagerPermissionsPayload(
                Optional.ofNullable(requestDTO.canManageTenants()).orElse(true),
                Optional.ofNullable(requestDTO.canRecordPayments()).orElse(true),
                Optional.ofNullable(requestDTO.canEnterMeters()).orElse(true),
                Optional.ofNullable(requestDTO.canHandleComplaints()).orElse(true),
                Optional.ofNullable(requestDTO.canPublishNotices()).orElse(true));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new ValidationException("Failed to serialize manager permissions");
        }
    }

    private InvitationEntity findInvitationByTokenOrCode(String token, String code) {
        if (StringUtils.isNotBlank(token)) {
            return invitationRepository.findByTokenHash(HashUtils.sha256Hex(token.trim()))
                    .orElseThrow(() -> new ResourceNotFoundException("Invitation link is invalid"));
        }
        if (StringUtils.isNotBlank(code)) {
            return invitationRepository.findByInviteCodeHash(HashUtils.sha256Hex(code.trim().toUpperCase(Locale.ROOT)))
                    .orElseThrow(() -> new ResourceNotFoundException("Invitation code is invalid"));
        }
        throw new ValidationException("Provide an invitation token or code");
    }

    private void validateInvitationForAcceptance(InvitationEntity invitationEntity) {
        if (invitationEntity.getInvitationStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("Invitation is no longer active");
        }
        if (invitationEntity.getExpiresAt().isBefore(Instant.now())) {
            invitationEntity.setInvitationStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitationEntity);
            throw new ValidationException("Invitation has expired");
        }
    }

    private User upsertInvitedUser(
            InvitationEntity invitationEntity,
            AcceptInvitationRequestDTO requestDTO,
            String normalizedEmail) {
        Optional<User> existingOptional = userRepository.findByEmail(normalizedEmail);
        if (existingOptional.isPresent()) {
            User existingUser = existingOptional.get();
            if (existingUser.getRole() != invitationEntity.getTargetRole()) {
                throw new ConflictException("Existing account role does not match invitation role");
            }
            existingUser.setActive(true);
            existingUser.setName(requestDTO.name().trim());
            if (StringUtils.isNotBlank(requestDTO.phone())) {
                existingUser.setPhone(requestDTO.phone().trim());
            }
            existingUser.setPassword(passwordEncoder.encode(requestDTO.password()));
            return userRepository.save(existingUser);
        }

        User newUser = User.builder()
                .name(requestDTO.name().trim())
                .email(normalizedEmail)
                .phone(StringUtils.defaultIfBlank(StringUtils.trimToNull(requestDTO.phone()),
                        invitationEntity.getPhone()))
                .password(passwordEncoder.encode(requestDTO.password()))
                .role(invitationEntity.getTargetRole())
                .active(true)
                .build();
        return userRepository.save(newUser);
    }

    private void upsertOrganizationMember(OrganizationEntity organizationEntity, User userEntity, Role role) {
        OrganizationMemberEntity memberEntity = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationEntity.getId(), userEntity.getId())
                .orElseGet(OrganizationMemberEntity::new);
        memberEntity.setOrganization(organizationEntity);
        memberEntity.setUser(userEntity);
        memberEntity.setMemberRole(role);
        memberEntity.setPrimary(true);
        memberEntity.setStatus(AppConstants.STATUS_ACTIVE);
        organizationMemberRepository.save(memberEntity);
    }

    private void assignManagerIfApplicable(InvitationEntity invitationEntity, User acceptedUser) {
        if (invitationEntity.getTargetRole() != Role.MANAGER
                || Optional.ofNullable(invitationEntity.getProperty()).isEmpty()) {
            return;
        }
        ManagerPermissionsPayload permissions = parsePermissions(invitationEntity.getPermissionsJson());
        ManagerPropertyAssignmentEntity assignment = managerPropertyAssignmentRepository
                .findByPropertyIdAndManagerUserId(invitationEntity.getProperty().getId(), acceptedUser.getId())
                .orElseGet(ManagerPropertyAssignmentEntity::new);
        assignment.setOrganization(invitationEntity.getOrganization());
        assignment.setProperty(invitationEntity.getProperty());
        assignment.setManagerUser(acceptedUser);
        assignment.setCanManageTenants(permissions.canManageTenants());
        assignment.setCanRecordPayments(permissions.canRecordPayments());
        assignment.setCanEnterMeters(permissions.canEnterMeters());
        assignment.setCanHandleComplaints(permissions.canHandleComplaints());
        assignment.setCanPublishNotices(permissions.canPublishNotices());
        assignment.setStatus(AppConstants.STATUS_ACTIVE);
        managerPropertyAssignmentRepository.save(assignment);
    }

    private ManagerPermissionsPayload parsePermissions(String permissionsJson) {
        if (StringUtils.isBlank(permissionsJson)) {
            return new ManagerPermissionsPayload(true, true, true, true, true);
        }
        try {
            return objectMapper.readValue(permissionsJson, ManagerPermissionsPayload.class);
        } catch (JacksonException exception) {
            return new ManagerPermissionsPayload(true, true, true, true, true);
        }
    }

    private AuthTokenResponseDTO issueTokenResponse(User userEntity) {
        AuthenticatedUserDTO authenticatedUserDTO = accessGuardService.buildAuthenticatedUser(userEntity);
        return jwtTokenFactory.createTokenResponse(authenticatedUserDTO, userEntity.getPassword(),
                userEntity.isActive());
    }

    private record ManagerPermissionsPayload(
            boolean canManageTenants,
            boolean canRecordPayments,
            boolean canEnterMeters,
            boolean canHandleComplaints,
            boolean canPublishNotices) {
    }
}
