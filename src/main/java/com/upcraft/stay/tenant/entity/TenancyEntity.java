package com.upcraft.stay.tenant.entity;

import java.math.BigDecimal;
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

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.entity.AuditableEntity;
import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.BedEntity;

/**
 * Historical and active bed allocation record tracking move-in, rent terms,
 * move-out settlement, and security deposit reconciliation.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenancies")
@SQLRestriction("status <> 9")
public class TenancyEntity extends AuditableEntity {

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
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id", nullable = false)
    private BedEntity bed;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenancy_status", nullable = false, length = 30)
    private TenancyStatus tenancyStatus = TenancyStatus.ACTIVE;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "expected_move_out_date")
    private LocalDate expectedMoveOutDate;

    @Column(name = "actual_move_out_date")
    private LocalDate actualMoveOutDate;

    @Column(name = "agreed_monthly_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedMonthlyRent = BigDecimal.ZERO;

    @Column(name = "security_deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDepositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_received_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositReceivedAmount = BigDecimal.ZERO;

    @Column(name = "rent_due_day", nullable = false)
    private Integer rentDueDay = AppConstants.DEFAULT_RENT_DUE_DAY;

    @Column(name = "initial_meter_reading", precision = 12, scale = 2)
    private BigDecimal initialMeterReading;

    @Column(name = "final_meter_reading", precision = 12, scale = 2)
    private BigDecimal finalMeterReading;

    @Column(name = "unpaid_dues_settled", nullable = false, precision = 12, scale = 2)
    private BigDecimal unpaidDuesSettled = BigDecimal.ZERO;

    @Column(name = "deductions_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductionsAmount = BigDecimal.ZERO;

    @Column(name = "deduction_reason", columnDefinition = "TEXT")
    private String deductionReason;

    @Column(name = "refundable_deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundableDepositAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "agreement_start_date")
    private LocalDate agreementStartDate;

    @Column(name = "agreement_end_date")
    private LocalDate agreementEndDate;

    @Column(name = "move_in_notes", columnDefinition = "TEXT")
    private String moveInNotes;

    @Column(name = "move_out_notes", columnDefinition = "TEXT")
    private String moveOutNotes;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof TenancyEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

