package com.upcraft.stay.electricity.entity;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.storage.entity.StoredFileEntity;

/**
 * Immutable snapshot meter reading preserving tariff rates, consumption, photo proof, and approval trail.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meter_readings")
@SQLRestriction("status <> 9")
public class MeterReadingEntity extends AuditableEntity {

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
    @JoinColumn(name = "meter_id", nullable = false)
    private MeterEntity meter;

    @Column(name = "billing_year", nullable = false)
    private Integer billingYear;

    @Column(name = "billing_month", nullable = false)
    private Integer billingMonth;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "previous_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal previousValue = BigDecimal.ZERO;

    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "units_consumed", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitsConsumed = BigDecimal.ZERO;

    @Column(name = "rate_per_unit_snapshot", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratePerUnitSnapshot = BigDecimal.ZERO;

    @Column(name = "fixed_charge_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedChargeSnapshot = BigDecimal.ZERO;

    @Column(name = "energy_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal energyCharge = BigDecimal.ZERO;

    @Column(name = "additional_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalCharge = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_status", nullable = false, length = 20)
    private MeterReadingStatus readingStatus = MeterReadingStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_file_id")
    private StoredFileEntity photoFile;

    @Column(name = "unusual_usage", nullable = false)
    private boolean unusualUsage = false;

    @Column(name = "unusual_usage_reason", length = 255)
    private String unusualUsageReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_of_reading_id")
    private MeterReadingEntity correctionOfReading;

    @Column(name = "meter_reset_applied", nullable = false)
    private boolean meterResetApplied = false;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof MeterReadingEntity otherEntity)) {
            return false;
        }
        return Optional.ofNullable(id).isPresent() && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

