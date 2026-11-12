package com.upcraft.stay.billing.entity;

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
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Persistent rent or charge invoice with snapshot totals, due date, and payment balance tracking.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoices")
@SQLRestriction("status <> 9")
public class InvoiceEntity extends AuditableEntity {

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
    @JoinColumn(name = "tenancy_id")
    private TenancyEntity tenancy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    private BedEntity bed;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 30)
    private InvoiceType invoiceType = InvoiceType.MONTHLY_RENT;

    @Column(name = "billing_year", nullable = false)
    private Integer billingYear;

    @Column(name = "billing_month", nullable = false)
    private Integer billingMonth;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "late_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_status", nullable = false, length = 30)
    private InvoiceStatus invoiceStatus = InvoiceStatus.UNPAID;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof InvoiceEntity otherEntity)) {
            return false;
        }
        return Optional.ofNullable(id).isPresent() && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
