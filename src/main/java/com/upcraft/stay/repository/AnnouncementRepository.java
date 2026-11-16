package com.upcraft.stay.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.upcraft.stay.domain.entity.Announcement;

/**
 * Persistence access for legacy and v1 multi-property announcements.
 */
public interface AnnouncementRepository
        extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    List<Announcement> findAllByOrderByPinnedDescCreatedAtDesc();

    @EntityGraph(attributePaths = { "organization", "property", "attachmentFile" })
    Page<Announcement> findAll(Specification<Announcement> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "organization", "property", "attachmentFile" })
    Optional<Announcement> findWithDetailsById(UUID id);

    boolean existsByAttachmentFileIdAndOrganizationId(UUID attachmentFileId, UUID organizationId);
}
