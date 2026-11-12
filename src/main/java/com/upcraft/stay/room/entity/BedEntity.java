package com.upcraft.stay.room.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Individual allocatable bed belonging to a room and property.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "beds")
@SQLRestriction("status <> 9")
public class BedEntity extends AuditableEntity {

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
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "bed_code", nullable = false, length = 60)
    private String bedCode;

    @Column(name = "monthly_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRent = BigDecimal.ZERO;

    @Column(name = "default_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultDeposit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_status", nullable = false, length = 30)
    private BedStatus bedStatus = BedStatus.VACANT;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof BedEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

