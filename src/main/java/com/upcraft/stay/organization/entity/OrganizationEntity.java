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

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.entity.AuditableEntity;
import com.upcraft.stay.domain.entity.User;

/**
 * Multi-tenant PG operator organization owning one or more properties.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organizations")
@SQLRestriction("status <> 9")
public class OrganizationEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = AppConstants.DEFAULT_TIMEZONE;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = AppConstants.DEFAULT_CURRENCY;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof OrganizationEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

