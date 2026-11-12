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

import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.ComplaintStatus;

/**
 * Persistence access for legacy and v1 multi-property complaints.
 */
public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

    List<Complaint> findByMemberOrderByCreatedAtDesc(User member);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "tenancy", "room", "bed", "member", "assignedToUser"})
    Page<Complaint> findAll(Specification<Complaint> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "tenancy", "room", "bed", "member", "assignedToUser"})
    Page<Complaint> findAllByMemberId(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "tenancy", "room", "bed", "member", "assignedToUser"})
    Optional<Complaint> findWithDetailsById(UUID id);
}
