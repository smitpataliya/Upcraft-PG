package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.RecordDepositTransactionRequestDTO;
import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.billing.mapper.BillingMapper;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Security deposit ledger implementation maintaining complete separation from rent revenue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private static final ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);

    private final AccessGuardService accessGuardService;
    private final BillingAccessHelper billingAccessHelper;
    private final TenancyRepository tenancyRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final BillingMapper billingMapper;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public DepositAccountSummaryResponseDTO getTenancyDepositSummary(
            String principalEmail,
            UUID tenancyId,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenancyEntity tenancyEntity = loadTenancy(tenancyId);
        billingAccessHelper.requireTenantReadAccess(currentUser, tenancyEntity.getTenant());

        Pageable pageable = PaginationUtils.createPageable(
                page, size, "transactionDate", "DESC", Set.of("transactionDate"), "transactionDate", appProperties
        );
        List<DepositTransactionEntity> allTransactions =
                depositTransactionRepository.findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(tenancyId);
        Page<DepositTransactionResponseDTO> pagedTransactions = depositTransactionRepository
                .findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(tenancyId, pageable)
                .map(billingMapper::toDepositTransactionResponseDTO);

        return buildSummary(tenancyEntity, allTransactions, pagedTransactions);
    }

    @Override
    @Transactional
    public DepositTransactionResponseDTO recordDepositTransaction(
            String principalEmail,
            UUID tenancyId,
            RecordDepositTransactionRequestDTO requestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenancyEntity tenancyEntity = loadTenancy(tenancyId);
        accessGuardService.requirePropertyManagementAccess(
                currentUser, tenancyEntity.getProperty().getId(), "PAYMENTS"
        );

        List<DepositTransactionEntity> existingTransactions =
                depositTransactionRepository.findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(tenancyId);
        BigDecimal currentHeld = computeHeldBalance(existingTransactions);
        BigDecimal txAmount = requestDTO.amount().setScale(2, RoundingMode.HALF_UP);

        if (isOutflowType(requestDTO.transactionType()) && txAmount.compareTo(currentHeld) > 0) {
            throw new ValidationException("Deduction or refund amount cannot exceed held security deposit balance");
        }

        DepositTransactionEntity savedEntity = persistDepositRow(
                tenancyEntity,
                currentUser,
                requestDTO.transactionType(),
                txAmount,
                Optional.ofNullable(requestDTO.transactionDate()).orElseGet(() -> LocalDate.now(INDIA_ZONE)),
                Optional.ofNullable(requestDTO.paymentMethod()).orElse(PaymentMethod.OTHER),
                StringUtils.trimToNull(requestDTO.referenceNumber()),
                requestDTO.reason().trim()
        );
        syncTenancyDepositTotals(tenancyEntity);
        auditLogService.recordEvent(new AuditLogCommand(
                tenancyEntity.getOrganization(), tenancyEntity.getProperty(), currentUser, principalEmail,
                AuditAction.DEPOSIT_TRANSACTION_RECORDED, AuditEntityType.DEPOSIT, savedEntity.getId(),
                String.format("Recorded %s deposit transaction of %s", requestDTO.transactionType(), txAmount),
                Map.of("type", requestDTO.transactionType().name(), "amount", txAmount)
        ));

        log.info("Security deposit transaction recorded. tenancyId={}, type={}, amount={}",
                tenancyId, requestDTO.transactionType(), txAmount);
        return billingMapper.toDepositTransactionResponseDTO(savedEntity);
    }

    @Override
    @Transactional
    public void recordInitialDepositOnMoveIn(TenancyEntity tenancyEntity, User actorUser) {
        BigDecimal initialDeposit = Optional.ofNullable(tenancyEntity.getDepositReceivedAmount())
                .orElse(BigDecimal.ZERO);
        if (initialDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        persistDepositRow(
                tenancyEntity,
                actorUser,
                DepositTransactionType.COLLECTED,
                initialDeposit.setScale(2, RoundingMode.HALF_UP),
                tenancyEntity.getMoveInDate(),
                PaymentMethod.OTHER,
                null,
                "Initial security deposit collected on move-in"
        );
    }

    @Override
    @Transactional
    public void recordSettlementTransactionsOnMoveOut(
            TenancyEntity tenancyEntity,
            User actorUser,
            BigDecimal deductionsAmount,
            String deductionReason,
            BigDecimal refundableAmount,
            LocalDate moveOutDate
    ) {
        if (deductionsAmount.compareTo(BigDecimal.ZERO) > 0) {
            persistDepositRow(
                    tenancyEntity,
                    actorUser,
                    DepositTransactionType.DEDUCTION,
                    deductionsAmount.setScale(2, RoundingMode.HALF_UP),
                    moveOutDate,
                    PaymentMethod.OTHER,
                    null,
                    StringUtils.defaultIfBlank(deductionReason, "Move-out settlement deductions")
            );
        }
        if (tenancyEntity.getRefundStatus() == RefundStatus.REFUNDED
                && refundableAmount.compareTo(BigDecimal.ZERO) > 0) {
            persistDepositRow(
                    tenancyEntity,
                    actorUser,
                    DepositTransactionType.REFUND,
                    refundableAmount.setScale(2, RoundingMode.HALF_UP),
                    moveOutDate,
                    PaymentMethod.OTHER,
                    null,
                    "Move-out refundable security deposit payout"
            );
        }
    }

    private DepositAccountSummaryResponseDTO buildSummary(
            TenancyEntity tenancyEntity,
            List<DepositTransactionEntity> allTransactions,
            Page<DepositTransactionResponseDTO> pagedTransactions
    ) {
        BigDecimal collected = sumByType(allTransactions, DepositTransactionType.COLLECTED)
                .add(sumByType(allTransactions, DepositTransactionType.ADJUSTMENT));
        BigDecimal deductions = sumByType(allTransactions, DepositTransactionType.DEDUCTION);
        BigDecimal refunded = sumByType(allTransactions, DepositTransactionType.REFUND);
        BigDecimal forfeited = sumByType(allTransactions, DepositTransactionType.FORFEIT);
        BigDecimal heldBalance = collected.subtract(deductions).subtract(refunded).subtract(forfeited)
                .max(BigDecimal.ZERO);
        BigDecimal expected = tenancyEntity.getSecurityDepositAmount();
        BigDecimal outstanding = expected.subtract(collected).max(BigDecimal.ZERO);

        return new DepositAccountSummaryResponseDTO(
                tenancyEntity.getId(),
                tenancyEntity.getProperty().getId(),
                tenancyEntity.getProperty().getName(),
                tenancyEntity.getTenant().getId(),
                tenancyEntity.getTenant().getFullName(),
                tenancyEntity.getRoom().getNumber(),
                tenancyEntity.getBed().getBedCode(),
                expected,
                collected,
                deductions,
                refunded,
                forfeited,
                heldBalance,
                outstanding,
                tenancyEntity.getRefundStatus(),
                PageResponseDTO.from(pagedTransactions)
        );
    }

    private void syncTenancyDepositTotals(TenancyEntity tenancyEntity) {
        List<DepositTransactionEntity> allTransactions =
                depositTransactionRepository.findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(tenancyEntity.getId());
        BigDecimal collected = sumByType(allTransactions, DepositTransactionType.COLLECTED)
                .add(sumByType(allTransactions, DepositTransactionType.ADJUSTMENT));
        BigDecimal deductions = sumByType(allTransactions, DepositTransactionType.DEDUCTION);
        BigDecimal refunded = sumByType(allTransactions, DepositTransactionType.REFUND);
        BigDecimal forfeited = sumByType(allTransactions, DepositTransactionType.FORFEIT);

        tenancyEntity.setDepositReceivedAmount(collected);
        tenancyEntity.setDeductionsAmount(deductions);
        if (refunded.compareTo(BigDecimal.ZERO) > 0) {
            tenancyEntity.setRefundStatus(deductions.compareTo(BigDecimal.ZERO) > 0
                    ? RefundStatus.PARTIAL
                    : RefundStatus.REFUNDED);
        } else if (forfeited.compareTo(BigDecimal.ZERO) > 0) {
            tenancyEntity.setRefundStatus(RefundStatus.FORFEITED);
        }
        tenancyRepository.save(tenancyEntity);
    }

    private DepositTransactionEntity persistDepositRow(
            TenancyEntity tenancyEntity,
            User actorUser,
            DepositTransactionType type,
            BigDecimal amount,
            LocalDate transactionDate,
            PaymentMethod paymentMethod,
            String referenceNumber,
            String reason
    ) {
        DepositTransactionEntity tx = new DepositTransactionEntity();
        tx.setOrganization(tenancyEntity.getOrganization());
        tx.setProperty(tenancyEntity.getProperty());
        tx.setTenant(tenancyEntity.getTenant());
        tx.setTenancy(tenancyEntity);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setTransactionDate(transactionDate);
        tx.setPaymentMethod(paymentMethod);
        tx.setReferenceNumber(referenceNumber);
        tx.setReason(reason);
        tx.setRecordedByUser(actorUser);
        return depositTransactionRepository.save(tx);
    }

    private BigDecimal computeHeldBalance(List<DepositTransactionEntity> transactions) {
        BigDecimal collected = sumByType(transactions, DepositTransactionType.COLLECTED)
                .add(sumByType(transactions, DepositTransactionType.ADJUSTMENT));
        BigDecimal outflows = sumByType(transactions, DepositTransactionType.DEDUCTION)
                .add(sumByType(transactions, DepositTransactionType.REFUND))
                .add(sumByType(transactions, DepositTransactionType.FORFEIT));
        return collected.subtract(outflows).max(BigDecimal.ZERO);
    }

    private BigDecimal sumByType(List<DepositTransactionEntity> transactions, DepositTransactionType type) {
        return transactions.stream()
                .filter(tx -> tx.getTransactionType() == type)
                .map(DepositTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isOutflowType(DepositTransactionType type) {
        return type == DepositTransactionType.DEDUCTION
                || type == DepositTransactionType.REFUND
                || type == DepositTransactionType.FORFEIT;
    }

    private TenancyEntity loadTenancy(UUID tenancyId) {
        return tenancyRepository.findWithDetailsById(tenancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenancy record not found"));
    }
}
