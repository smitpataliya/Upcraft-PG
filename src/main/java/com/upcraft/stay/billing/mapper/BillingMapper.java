package com.upcraft.stay.billing.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceItemResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceSummaryResponseDTO;
import com.upcraft.stay.billing.dto.PaymentAdjustmentResponseDTO;
import com.upcraft.stay.billing.dto.PaymentResponseDTO;
import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.InvoiceItemEntity;
import com.upcraft.stay.billing.entity.PaymentAdjustmentEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.enums.InvoiceStatus;

/**
 * MapStruct mapper converting billing entities into immutable API response DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BillingMapper {

    ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);

    InvoiceItemResponseDTO toInvoiceItemResponseDTO(InvoiceItemEntity itemEntity);

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.fullName")
    @Mapping(target = "tenantPhone", source = "tenant.phone")
    @Mapping(target = "tenancyId", source = "tenancy.id")
    @Mapping(target = "roomNumber", source = "room.number")
    @Mapping(target = "bedCode", source = "bed.bedCode")
    @Mapping(target = "invoiceStatus", expression = "java(resolveEffectiveStatus(invoiceEntity))")
    @Mapping(target = "overdue", expression = "java(isInvoiceOverdue(invoiceEntity))")
    InvoiceSummaryResponseDTO toInvoiceSummaryResponseDTO(InvoiceEntity invoiceEntity);

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.fullName")
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "invoiceNumber", source = "invoice.invoiceNumber")
    @Mapping(target = "recordedByName", source = "recordedByUser.name")
    PaymentResponseDTO toPaymentResponseDTO(PaymentEntity paymentEntity);

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "receiptNumber", source = "payment.receiptNumber")
    @Mapping(target = "adjustedByName", source = "adjustedByUser.name")
    PaymentAdjustmentResponseDTO toPaymentAdjustmentResponseDTO(PaymentAdjustmentEntity adjustmentEntity);

    @Mapping(target = "tenancyId", source = "tenancy.id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "recordedByName", expression = "java(resolveRecorderName(txEntity))")
    DepositTransactionResponseDTO toDepositTransactionResponseDTO(DepositTransactionEntity txEntity);

    default InvoiceStatus resolveEffectiveStatus(InvoiceEntity invoiceEntity) {
        if (isInvoiceOverdue(invoiceEntity)) {
            return InvoiceStatus.OVERDUE;
        }
        return invoiceEntity.getInvoiceStatus();
    }

    default boolean isInvoiceOverdue(InvoiceEntity invoiceEntity) {
        InvoiceStatus status = invoiceEntity.getInvoiceStatus();
        if (status == InvoiceStatus.CANCELLED || status == InvoiceStatus.WAIVED || status == InvoiceStatus.PAID) {
            return false;
        }
        return invoiceEntity.getBalanceDue().compareTo(BigDecimal.ZERO) > 0
                && invoiceEntity.getDueDate().isBefore(LocalDate.now(INDIA_ZONE));
    }

    default String resolveRecorderName(DepositTransactionEntity txEntity) {
        return Optional.ofNullable(txEntity.getRecordedByUser())
                .map(user -> user.getName())
                .orElse(AppConstants.SYSTEM_ACTOR);
    }
}

