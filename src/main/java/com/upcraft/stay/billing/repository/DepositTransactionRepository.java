package com.upcraft.stay.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.common.enums.DepositTransactionType;

/**
 * Persistence access for segregated security deposit ledger transactions.
 */
public interface DepositTransactionRepository extends JpaRepository<DepositTransactionEntity, UUID> {

    @EntityGraph(attributePaths = {"recordedByUser", "tenancy"})
    Page<DepositTransactionEntity> findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(
            UUID tenancyId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"recordedByUser"})
    List<DepositTransactionEntity> findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(UUID tenancyId);

    @EntityGraph(attributePaths = {"recordedByUser", "tenancy"})
    List<DepositTransactionEntity> findAllByTenantIdOrderByTransactionDateDescCreatedAtDesc(UUID tenantId);

    @Query("""
            SELECT COALESCE(SUM(dt.amount), 0)
            FROM DepositTransactionEntity dt
            WHERE dt.property.id IN :propertyIds
              AND dt.transactionType = :transactionType
            """)
    BigDecimal sumByPropertyIdsAndType(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("transactionType") DepositTransactionType transactionType
    );

    @EntityGraph(attributePaths = {"property", "tenant", "tenancy", "recordedByUser"})
    @Query("""
            SELECT dt FROM DepositTransactionEntity dt
            WHERE dt.property.id IN :propertyIds
              AND (:hasFromDate = false OR dt.transactionDate >= :fromDate)
              AND (:hasToDate = false OR dt.transactionDate <= :toDate)
            """)
    Page<DepositTransactionEntity> findAccessibleDepositReport(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasFromDate") boolean hasFromDate,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasToDate") boolean hasToDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
