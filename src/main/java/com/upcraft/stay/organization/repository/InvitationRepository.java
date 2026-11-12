package com.upcraft.stay.organization.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.organization.entity.InvitationEntity;

/**
 * Persistence access for secure manager and tenant onboarding invitations.
 */
public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "invitedByUser"})
    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"organization", "property", "invitedByUser"})
    Optional<InvitationEntity> findByInviteCodeHash(String inviteCodeHash);

    @EntityGraph(attributePaths = {"property", "invitedByUser"})
    Page<InvitationEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "invitedByUser"})
    Page<InvitationEntity> findAllByOrganizationIdAndPropertyId(UUID organizationId, UUID propertyId, Pageable pageable);

    Optional<InvitationEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}

