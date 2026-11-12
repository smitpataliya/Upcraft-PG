package com.upcraft.stay.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.RecordDepositTransactionRequestDTO;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * Service contract for security deposit accounting segregated from rent revenue.
 */
public interface DepositService {

    DepositAccountSummaryResponseDTO getTenancyDepositSummary(
            String principalEmail,
            UUID tenancyId,
            int page,
            Integer size
    );

    DepositTransactionResponseDTO recordDepositTransaction(
            String principalEmail,
            UUID tenancyId,
            RecordDepositTransactionRequestDTO requestDTO
    );

    void recordInitialDepositOnMoveIn(TenancyEntity tenancyEntity, User actorUser);

    void recordSettlementTransactionsOnMoveOut(
            TenancyEntity tenancyEntity,
            User actorUser,
            BigDecimal deductionsAmount,
            String deductionReason,
            BigDecimal refundableAmount,
            LocalDate moveOutDate
    );
}

