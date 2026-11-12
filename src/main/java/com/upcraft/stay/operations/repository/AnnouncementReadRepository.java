package com.upcraft.stay.operations.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.operations.entity.AnnouncementReadEntity;

/**
 * Persistence access for idempotent announcement read acknowledgments.
 */
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementReadEntity, UUID> {

    Optional<AnnouncementReadEntity> findByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    List<AnnouncementReadEntity> findAllByAnnouncementIdInAndUserId(Collection<UUID> announcementIds, UUID userId);

    @Query("""
            SELECT ar.announcement.id, COUNT(ar) FROM AnnouncementReadEntity ar
            WHERE ar.announcement.id IN :announcementIds
            GROUP BY ar.announcement.id
            """)
    List<Object[]> countReadsByAnnouncementIds(@Param("announcementIds") Collection<UUID> announcementIds);
}

