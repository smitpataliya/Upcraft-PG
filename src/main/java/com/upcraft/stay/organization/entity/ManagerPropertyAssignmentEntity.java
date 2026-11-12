package com.upcraft.stay.organization.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import com.upcraft.stay.common.entity.AuditableEntity;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Scopes a manager or warden to specific assigned properties and operational permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "manager_property_assignments")
@SQLRestriction("status <> 9")
public class ManagerPropertyAssignmentEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyEntity property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id", nullable = false)
    private User managerUser;

    @Column(name = "can_manage_tenants", nullable = false)
    private boolean canManageTenants = true;

    @Column(name = "can_record_payments", nullable = false)
    private boolean canRecordPayments = true;

    @Column(name = "can_enter_meters", nullable = false)
    private boolean canEnterMeters = true;

    @Column(name = "can_handle_complaints", nullable = false)
    private boolean canHandleComplaints = true;

    @Column(name = "can_publish_notices", nullable = false)
    private boolean canPublishNotices = true;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof ManagerPropertyAssignmentEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

