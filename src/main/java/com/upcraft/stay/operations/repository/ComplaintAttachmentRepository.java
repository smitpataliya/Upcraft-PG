package com.upcraft.stay.operations.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.operations.entity.ComplaintAttachmentEntity;

/**
 * Persistence access for private file attachments linked to complaints.
 */
public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachmentEntity, UUID> {

    @EntityGraph(attributePaths = {"storedFile", "storedFile.uploadedByUser", "comment"})
    List<ComplaintAttachmentEntity> findAllByComplaintIdOrderByCreatedAtAsc(UUID complaintId);

    @Query("""
            SELECT COUNT(ca) > 0 FROM ComplaintAttachmentEntity ca
            JOIN ca.complaint c
            WHERE ca.storedFile.id = :storedFileId
              AND c.member.id = :memberUserId
            """)
    boolean isComplaintAttachmentAccessibleToMember(
            @Param("storedFileId") UUID storedFileId,
            @Param("memberUserId") UUID memberUserId
    );
}

