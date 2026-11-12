package com.upcraft.stay.electricity.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
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
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.enums.SplitMethod;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.BedEntity;

/**
 * Electricity meter installed at property, room, shared area, or individual bed level.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meters")
@SQLRestriction("status <> 9")
public class MeterEntity extends AuditableEntity {

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
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    private BedEntity bed;

    @Column(name = "meter_number", nullable = false, length = 60)
    private String meterNumber;

    @Column(name = "meter_label", length = 120)
    private String meterLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_scope", nullable = false, length = 20)
    private MeterScope meterScope = MeterScope.ROOM;

    @Column(name = "unit_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal unitRate = BigDecimal.ZERO;

    @Column(name = "fixed_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedCharge = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_method", nullable = false, length = 30)
    private SplitMethod splitMethod = SplitMethod.EQUAL;

    @Column(name = "initial_reading", nullable = false, precision = 12, scale = 2)
    private BigDecimal initialReading = BigDecimal.ZERO;

    @Column(name = "last_reading_value", precision = 12, scale = 2)
    private BigDecimal lastReadingValue;

    @Column(name = "last_reading_date")
    private LocalDate lastReadingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_status", nullable = false, length = 20)
    private MeterStatus meterStatus = MeterStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof MeterEntity otherEntity)) {
            return false;
        }
        return Optional.ofNullable(id).isPresent() && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

