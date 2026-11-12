package com.upcraft.stay.organization.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.organization.dto.InvitationResponseDTO;
import com.upcraft.stay.organization.dto.ManagerAssignmentResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationMemberResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationResponseDTO;
import com.upcraft.stay.organization.entity.InvitationEntity;
import com.upcraft.stay.organization.entity.ManagerPropertyAssignmentEntity;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;

/**
 * MapStruct mapper for organization, membership, manager assignment, and invitation projections.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrganizationMapper {

    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    OrganizationResponseDTO toOrganizationResponseDTO(OrganizationEntity organizationEntity);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "active", source = "user.active")
    OrganizationMemberResponseDTO toOrganizationMemberResponseDTO(OrganizationMemberEntity organizationMemberEntity);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "managerUserId", source = "managerUser.id")
    @Mapping(target = "managerName", source = "managerUser.name")
    @Mapping(target = "managerEmail", source = "managerUser.email")
    @Mapping(target = "active", expression = "java(assignmentEntity.getStatus() != null && assignmentEntity.getStatus() == 1)")
    ManagerAssignmentResponseDTO toManagerAssignmentResponseDTO(ManagerPropertyAssignmentEntity assignmentEntity);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "inviteUrl", ignore = true)
    @Mapping(target = "inviteCode", ignore = true)
    InvitationResponseDTO toInvitationResponseDTO(InvitationEntity invitationEntity);
}

