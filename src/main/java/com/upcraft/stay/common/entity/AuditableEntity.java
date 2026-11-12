package com.upcraft.stay.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.upcraft.stay.common.constants.AppConstants;

/**
 * Base JPA mapped superclass defining the 8 mandatory audit and soft-delete columns
 * plus optimistic locking version control.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "status", nullable = false)
    private Short status = AppConstants.STATUS_ACTIVE;

    @Column(name = "audit_tracker_id", length = 64)
    private String auditTrackerId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Marks this entity as soft-deleted in one atomic state update.
     *
     * @param actor     identifier of user or system performing deletion
     * @param trackerId correlation/audit tracker identifier
     */
    public void markDeleted(String actor, String trackerId) {
        this.status = AppConstants.STATUS_DELETED;
        this.deletedAt = Instant.now();
        this.deletedBy = actor;
        this.auditTrackerId = trackerId;
    }
}

