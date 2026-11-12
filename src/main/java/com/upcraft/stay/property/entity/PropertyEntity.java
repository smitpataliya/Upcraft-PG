package com.upcraft.stay.property.entity;

import java.math.BigDecimal;
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
import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;
import com.upcraft.stay.organization.entity.OrganizationEntity;

/**
 * Represents a PG, hostel, or co-living property managed within an organization.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "properties")
@SQLRestriction("status <> 9")
public class PropertyEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 30)
    private PropertyType propertyType = PropertyType.PG;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "landmark", length = 150)
    private String landmark;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pin_code", nullable = false, length = 12)
    private String pinCode;

    @Column(name = "country", nullable = false, length = 80)
    private String country = "India";

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "rent_due_day", nullable = false)
    private Integer rentDueDay = AppConstants.DEFAULT_RENT_DUE_DAY;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays = AppConstants.DEFAULT_GRACE_PERIOD_DAYS;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_fee_type", nullable = false, length = 30)
    private LateFeeType lateFeeType = LateFeeType.NONE;

    @Column(name = "late_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "default_electricity_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal defaultElectricityRate = BigDecimal.ZERO;

    @Column(name = "check_in_rules", columnDefinition = "TEXT")
    private String checkInRules;

    @Column(name = "check_out_rules", columnDefinition = "TEXT")
    private String checkOutRules;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_status", nullable = false, length = 30)
    private PropertyStatus propertyStatus = PropertyStatus.ACTIVE;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof PropertyEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

