package com.upcraft.stay.organization.service.impl;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.dto.AssignManagerRequestDTO;
import com.upcraft.stay.organization.dto.ManagerAssignmentResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationMemberResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationResponseDTO;
import com.upcraft.stay.organization.entity.ManagerPropertyAssignmentEntity;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;
import com.upcraft.stay.organization.mapper.OrganizationMapper;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Organization profile, member listing, and manager-property assignment service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements com.upcraft.stay.organization.service.OrganizationService {

    private static final Set<String> MEMBER_SORT_FIELDS = Set.of("createdAt", "memberRole");
    private static final Set<String> ASSIGNMENT_SORT_FIELDS = Set.of("createdAt");

    private final AccessGuardService accessGuardService;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final OrganizationMapper organizationMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getCurrentOrganization(String principalEmail) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        return organizationMapper.toOrganizationResponseDTO(organizationEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<OrganizationMemberResponseDTO> getOrganizationMembers(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);

        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, MEMBER_SORT_FIELDS, "createdAt", appProperties
        );
        Page<OrganizationMemberResponseDTO> memberPage = organizationMemberRepository
                .findAllByOrganizationId(organizationEntity.getId(), pageable)
                .map(organizationMapper::toOrganizationMemberResponseDTO);
        return PageResponseDTO.from(memberPage);
    }

    @Override
    @Transactional
    public ManagerAssignmentResponseDTO assignManagerToProperty(
            String principalEmail,
            AssignManagerRequestDTO assignManagerRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);

        PropertyEntity propertyEntity = propertyRepository
                .findByIdAndOrganizationId(assignManagerRequestDTO.propertyId(), organizationEntity.getId())
                .orElseThrow(() -> new ForbiddenException("Target property does not belong to your organization"));

        User managerUser = userRepository.findById(assignManagerRequestDTO.managerUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Manager user not found"));

        validateManagerOrganizationMembership(organizationEntity.getId(), managerUser);

        ManagerPropertyAssignmentEntity assignmentEntity = managerPropertyAssignmentRepository
                .findByPropertyIdAndManagerUserId(propertyEntity.getId(), managerUser.getId())
                .orElseGet(ManagerPropertyAssignmentEntity::new);

        assignmentEntity.setOrganization(organizationEntity);
        assignmentEntity.setProperty(propertyEntity);
        assignmentEntity.setManagerUser(managerUser);
        assignmentEntity.setStatus(AppConstants.STATUS_ACTIVE);
        applyPermissions(assignmentEntity, assignManagerRequestDTO);

        ManagerPropertyAssignmentEntity savedAssignment = managerPropertyAssignmentRepository.save(assignmentEntity);
        log.info("Manager assigned to property. assignmentId={}, propertyId={}, managerId={}",
                savedAssignment.getId(), propertyEntity.getId(), managerUser.getId());
        return organizationMapper.toManagerAssignmentResponseDTO(savedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ManagerAssignmentResponseDTO> getManagerAssignments(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);

        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, ASSIGNMENT_SORT_FIELDS, "createdAt", appProperties
        );
        Page<ManagerAssignmentResponseDTO> assignmentPage = managerPropertyAssignmentRepository
                .findAllByOrganizationId(organizationEntity.getId(), pageable)
                .map(organizationMapper::toManagerAssignmentResponseDTO);
        return PageResponseDTO.from(assignmentPage);
    }

    @Override
    @Transactional
    public void revokeManagerAssignment(String principalEmail, UUID assignmentId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);

        ManagerPropertyAssignmentEntity assignmentEntity = managerPropertyAssignmentRepository
                .findById(assignmentId)
                .filter(item -> item.getOrganization().getId().equals(organizationEntity.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Manager assignment not found"));

        assignmentEntity.markDeleted(currentUser.getEmail(), UUID.randomUUID().toString());
        managerPropertyAssignmentRepository.save(assignmentEntity);
        log.info("Manager property assignment revoked. assignmentId={}", assignmentId);
    }

    private void requireOwnerRole(User userEntity) {
        if (userEntity.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can manage organization staff and assignments");
        }
    }

    private void validateManagerOrganizationMembership(UUID organizationId, User managerUser) {
        if (managerUser.getRole() != Role.MANAGER) {
            throw new ValidationException("Assigned user must hold the MANAGER role");
        }
        OrganizationMemberEntity membership = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, managerUser.getId())
                .orElseThrow(() -> new ForbiddenException("Manager does not belong to this organization"));
        if (membership.getStatus() != AppConstants.STATUS_ACTIVE) {
            throw new ForbiddenException("Manager membership is inactive");
        }
    }

    private void applyPermissions(
            ManagerPropertyAssignmentEntity assignmentEntity,
            AssignManagerRequestDTO requestDTO
    ) {
        assignmentEntity.setCanManageTenants(Optional.ofNullable(requestDTO.canManageTenants()).orElse(true));
        assignmentEntity.setCanRecordPayments(Optional.ofNullable(requestDTO.canRecordPayments()).orElse(true));
        assignmentEntity.setCanEnterMeters(Optional.ofNullable(requestDTO.canEnterMeters()).orElse(true));
        assignmentEntity.setCanHandleComplaints(Optional.ofNullable(requestDTO.canHandleComplaints()).orElse(true));
        assignmentEntity.setCanPublishNotices(Optional.ofNullable(requestDTO.canPublishNotices()).orElse(true));
    }
}

