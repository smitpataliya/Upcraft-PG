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
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Security deposit movement ledger tracked strictly separately from rent revenue.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deposit_transactions")
@SQLRestriction("status <> 9")
public class DepositTransactionEntity extends AuditableEntity {

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
    @JoinColumn(name = "tenancy_id", nullable = false)
    private TenancyEntity tenancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private DepositTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.OTHER;

    @Column(name = "reference_number", length = 120)
    private String referenceNumber;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedByUser;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof DepositTransactionEntity otherEntity)) {
            return false;
        }
        return Optional.ofNullable(id).isPresent() && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
