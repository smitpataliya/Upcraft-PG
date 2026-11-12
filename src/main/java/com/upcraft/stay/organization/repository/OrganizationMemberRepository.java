package com.upcraft.stay.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;

/**
 * Persistence access for organization memberships.
 */
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "user"})
    Optional<OrganizationMemberEntity> findFirstByUserIdAndPrimaryTrue(UUID userId);

    @EntityGraph(attributePaths = {"organization", "user"})
    Optional<OrganizationMemberEntity> findFirstByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Page<OrganizationMemberEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<OrganizationMemberEntity> findAllByOrganizationIdAndMemberRole(UUID organizationId, Role memberRole);

    @EntityGraph(attributePaths = {"user"})
    List<OrganizationMemberEntity> findAllByOrganizationId(UUID organizationId);

    Optional<OrganizationMemberEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}

