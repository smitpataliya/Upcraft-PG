package com.upcraft.stay.operations.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.operations.entity.ComplaintCommentEntity;

/**
 * Persistence access for threaded complaint comments and internal notes.
 */
public interface ComplaintCommentRepository extends JpaRepository<ComplaintCommentEntity, UUID> {

    @EntityGraph(attributePaths = {"authorUser"})
    List<ComplaintCommentEntity> findAllByComplaintIdOrderByCreatedAtAsc(UUID complaintId);

    @EntityGraph(attributePaths = {"authorUser"})
    List<ComplaintCommentEntity> findAllByComplaintIdAndInternalFalseOrderByCreatedAtAsc(UUID complaintId);
}

