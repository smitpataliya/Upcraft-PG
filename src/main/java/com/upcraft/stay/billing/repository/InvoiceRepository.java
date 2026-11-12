package com.upcraft.stay.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;

/**
 * Persistence access for invoices, idempotent billing generation, and financial summaries.
 */
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "tenancy", "room", "bed"})
    Optional<InvoiceEntity> findWithDetailsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"organization", "property", "tenant", "tenancy", "room", "bed"})
    @Query("SELECT inv FROM InvoiceEntity inv WHERE inv.id = :id")
    Optional<InvoiceEntity> findLockedById(@Param("id") UUID id);

    @Query("""
            SELECT inv.tenancy.id FROM InvoiceEntity inv
            WHERE inv.tenancy.id IN :tenancyIds
              AND inv.billingYear = :billingYear
              AND inv.billingMonth = :billingMonth
              AND inv.invoiceType = :invoiceType
              AND inv.invoiceStatus <> :excludedStatus
            """)
    List<UUID> findAlreadyBilledTenancyIds(
            @Param("tenancyIds") Collection<UUID> tenancyIds,
            @Param("billingYear") int billingYear,
            @Param("billingMonth") int billingMonth,
            @Param("invoiceType") InvoiceType invoiceType,
            @Param("excludedStatus") InvoiceStatus excludedStatus
    );

    @EntityGraph(attributePaths = {"property", "tenant", "room", "bed"})
    @Query("""
            SELECT inv FROM InvoiceEntity inv
            JOIN inv.property p
            JOIN inv.tenant t
            WHERE p.id IN :propertyIds
              AND (:hasStatusFilter = false OR inv.invoiceStatus = :invoiceStatus)
              AND (:hasTenantFilter = false OR t.id = :tenantId)
              AND (:hasYearFilter = false OR inv.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR inv.billingMonth = :billingMonth)
              AND (:hasSearch = false
                   OR LOWER(inv.invoiceNumber) LIKE :searchPattern
                   OR LOWER(t.fullName) LIKE :searchPattern
                   OR LOWER(t.phone) LIKE :searchPattern)
            """)
    Page<InvoiceEntity> findAccessibleInvoices(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("invoiceStatus") InvoiceStatus invoiceStatus,
            @Param("hasTenantFilter") boolean hasTenantFilter,
            @Param("tenantId") UUID tenantId,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"property", "tenant", "room", "bed"})
    @Query("""
            SELECT inv FROM InvoiceEntity inv
            JOIN inv.tenant t
            WHERE t.user.id = :userId
               OR (t.user IS NULL AND t.organization.id = :organizationId AND LOWER(COALESCE(t.email, '')) = :email)
            """)
    Page<InvoiceEntity> findSelfInvoices(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"property", "tenant", "room", "bed"})
    List<InvoiceEntity> findAllByTenantIdOrderByBillingYearDescBillingMonthDescIssueDateDesc(UUID tenantId);

    @Query("""
            SELECT COALESCE(SUM(inv.totalAmount), 0)
            FROM InvoiceEntity inv
            WHERE inv.property.id IN :propertyIds
              AND inv.invoiceStatus NOT IN :excludedStatuses
              AND (:hasYearFilter = false OR inv.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR inv.billingMonth = :billingMonth)
            """)
    BigDecimal sumActiveBilledAmount(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("excludedStatuses") Collection<InvoiceStatus> excludedStatuses,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth
    );

    @Query("""
            SELECT COALESCE(SUM(inv.paidAmount), 0)
            FROM InvoiceEntity inv
            WHERE inv.property.id IN :propertyIds
              AND inv.invoiceStatus NOT IN :excludedStatuses
              AND (:hasYearFilter = false OR inv.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR inv.billingMonth = :billingMonth)
            """)
    BigDecimal sumActiveCollectedAmount(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("excludedStatuses") Collection<InvoiceStatus> excludedStatuses,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth
    );

    @Query("""
            SELECT COALESCE(SUM(inv.balanceDue), 0)
            FROM InvoiceEntity inv
            WHERE inv.property.id IN :propertyIds
              AND inv.invoiceStatus IN :openStatuses
              AND (:hasYearFilter = false OR inv.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR inv.billingMonth = :billingMonth)
            """)
    BigDecimal sumOutstandingBalance(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("openStatuses") Collection<InvoiceStatus> openStatuses,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth
    );

    @Query("""
            SELECT COALESCE(SUM(inv.balanceDue), 0)
            FROM InvoiceEntity inv
            WHERE inv.property.id IN :propertyIds
              AND inv.invoiceStatus IN :openStatuses
              AND inv.dueDate < :today
              AND (:hasYearFilter = false OR inv.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR inv.billingMonth = :billingMonth)
            """)
    BigDecimal sumOverdueBalance(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("openStatuses") Collection<InvoiceStatus> openStatuses,
            @Param("today") LocalDate today,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth
    );

    @Query("""
            SELECT COUNT(inv)
            FROM InvoiceEntity inv
            WHERE inv.property.id IN :propertyIds
              AND inv.invoiceStatus IN :openStatuses
              AND inv.dueDate < :today
            """)
    long countOverdueInvoices(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("openStatuses") Collection<InvoiceStatus> openStatuses,
            @Param("today") LocalDate today
    );

    @Query("""
            SELECT COALESCE(SUM(inv.balanceDue), 0)
            FROM InvoiceEntity inv
            WHERE inv.tenancy.id = :tenancyId
              AND inv.invoiceStatus IN :openStatuses
            """)
    BigDecimal sumOutstandingByTenancyId(
            @Param("tenancyId") UUID tenancyId,
            @Param("openStatuses") Collection<InvoiceStatus> openStatuses
    );

    long countByOrganizationId(UUID organizationId);

    boolean existsByPropertyId(UUID propertyId);
}

