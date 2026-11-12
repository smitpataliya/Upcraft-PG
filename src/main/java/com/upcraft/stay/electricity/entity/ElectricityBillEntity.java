package com.upcraft.stay.electricity.entity;

import java.math.BigDecimal;
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

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.common.entity.AuditableEntity;
import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.SplitMethod;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Apportioned tenant electricity charge holding immutable split and tariff snapshots, linked to an invoice.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "electricity_bills")
@SQLRestriction("status <> 9")
public class ElectricityBillEntity extends AuditableEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_id", nullable = false)
    private MeterReadingEntity reading;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenancy_id", nullable = false)
    private TenancyEntity tenancy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    private BedEntity bed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private InvoiceEntity invoice;

    @Column(name = "bill_number", nullable = false, length = 50)
    private String billNumber;

    @Column(name = "billing_year", nullable = false)
    private Integer billingYear;

    @Column(name = "billing_month", nullable = false)
    private Integer billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_method_snapshot", nullable = false, length = 30)
    private SplitMethod splitMethodSnapshot;

    @Column(name = "share_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal sharePercentage = BigDecimal.valueOf(100);

    @Column(name = "units_allocated", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitsAllocated = BigDecimal.ZERO;

    @Column(name = "rate_per_unit_snapshot", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratePerUnitSnapshot = BigDecimal.ZERO;

    @Column(name = "energy_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal energyCharge = BigDecimal.ZERO;

    @Column(name = "fixed_charge_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedChargeShare = BigDecimal.ZERO;

    @Column(name = "additional_charge_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalChargeShare = BigDecimal.ZERO;

    @Column(name = "tax_amount_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmountShare = BigDecimal.ZERO;

    @Column(name = "credit_share", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditShare = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_status", nullable = false, length = 20)
    private ElectricityBillStatus billStatus = ElectricityBillStatus.GENERATED;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof ElectricityBillEntity otherEntity)) {
            return false;
        }
        return Optional.ofNullable(id).isPresent() && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

