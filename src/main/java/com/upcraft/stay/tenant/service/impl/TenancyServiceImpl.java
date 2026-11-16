package com.upcraft.stay.tenant.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.mapper.BedMapper;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.dto.MoveInRequestDTO;
import com.upcraft.stay.tenant.dto.MoveOutRequestDTO;
import com.upcraft.stay.tenant.dto.ReserveBedRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.dto.TransferBedRequestDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.mapper.TenancyMapper;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.service.TenancyService;

/**
 * Transactional tenancy orchestration for move-in, bed reservation, transfer, and move-out settlement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenancyServiceImpl implements TenancyService {

    private static final Set<String> SORT_FIELDS = Set.of("moveInDate", "createdAt", "tenancyStatus");

    private final AccessGuardService accessGuardService;
    private final BedRepository bedRepository;
    private final TenancyRepository tenancyRepository;
    private final TenancyLifecycleHelper lifecycleHelper;
    private final BedMapper bedMapper;
    private final TenancyMapper tenancyMapper;
    private final DepositService depositService;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<TenancyResponseDTO> listTenancies(
            String principalEmail,
            UUID propertyId,
            TenancyStatus tenancyStatus,
            UUID tenantId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "moveInDate", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<TenancyStatus> statusOptional = Optional.ofNullable(tenancyStatus);
        Optional<UUID> tenantFilterOptional = Optional.ofNullable(tenantId);
        Page<TenancyResponseDTO> pageResult = tenancyRepository.findAccessibleTenancies(
                        propertyIds,
                        statusOptional.isPresent(),
                        statusOptional.orElse(TenancyStatus.ACTIVE),
                        tenantFilterOptional.isPresent(),
                        tenantFilterOptional.orElseGet(UUID::randomUUID),
                        pageable
                )
                .map(tenancyMapper::toTenancyResponseDTO);
        return PageResponseDTO.from(pageResult);
    }

    @Override
    @Transactional
    public BedResponseDTO reserveBed(String principalEmail, ReserveBedRequestDTO reserveBedRequestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        BedEntity bedEntity = lifecycleHelper.loadLockedBed(reserveBedRequestDTO.bedId());
        accessGuardService.requirePropertyManagementAccess(currentUser, bedEntity.getProperty().getId(), "TENANTS");

        if (bedEntity.getBedStatus() != BedStatus.VACANT) {
            throw new ConflictException("Only vacant beds can be reserved");
        }
        bedEntity.setBedStatus(BedStatus.RESERVED);
        bedEntity.setAvailableFrom(reserveBedRequestDTO.availableFrom());
        bedEntity.setReservedUntil(reserveBedRequestDTO.reservedUntil());
        if (StringUtils.isNotBlank(reserveBedRequestDTO.notes())) {
            bedEntity.setNotes(reserveBedRequestDTO.notes().trim());
        }
        return bedMapper.toBedResponseDTO(bedRepository.save(bedEntity), null, null);
    }

    @Override
    @Transactional
    public TenancyResponseDTO moveIn(String principalEmail, MoveInRequestDTO moveInRequestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        BedEntity bedEntity = lifecycleHelper.loadLockedBed(moveInRequestDTO.bedId());
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, bedEntity.getProperty().getId(), "TENANTS"
        );
        lifecycleHelper.validateAllocatableBed(bedEntity, propertyEntity);

        TenantEntity tenantEntity = lifecycleHelper.resolveOrCreateTenant(propertyEntity, moveInRequestDTO);
        if (tenancyRepository.existsByTenantIdAndTenancyStatusIn(
                tenantEntity.getId(), TenancyLifecycleHelper.LIVE_OCCUPANCY_STATUSES)) {
            throw new ConflictException("Tenant already holds an active residency; use bed transfer or move out first");
        }

        TenancyEntity savedTenancy = lifecycleHelper.persistActiveTenancy(
                propertyEntity, bedEntity, tenantEntity, moveInRequestDTO
        );
        depositService.recordInitialDepositOnMoveIn(savedTenancy, currentUser);
        lifecycleHelper.markBedOccupiedAndSyncRoom(bedEntity, tenantEntity, moveInRequestDTO.moveInDate());
        lifecycleHelper.sendOptionalPortalInvite(principalEmail, propertyEntity, tenantEntity, moveInRequestDTO);
        auditLogService.recordEvent(new AuditLogCommand(
                propertyEntity.getOrganization(), propertyEntity, currentUser, principalEmail,
                AuditAction.TENANCY_MOVE_IN, AuditEntityType.TENANCY, savedTenancy.getId(),
                String.format("Tenant moved into bed %s", bedEntity.getBedCode()),
                Map.of("bedCode", bedEntity.getBedCode(), "monthlyRent", savedTenancy.getAgreedMonthlyRent())
        ));

        log.info("Tenant moved in. tenancyId={}, tenantId={}, bedId={}",
                savedTenancy.getId(), tenantEntity.getId(), bedEntity.getId());
        return tenancyMapper.toTenancyResponseDTO(savedTenancy);
    }

    @Override
    @Transactional
    public TenancyResponseDTO transferBed(
            String principalEmail,
            UUID tenancyId,
            TransferBedRequestDTO transferBedRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenancyEntity currentTenancy = loadTenancyWithDetails(tenancyId);
        accessGuardService.requirePropertyManagementAccess(
                currentUser, currentTenancy.getProperty().getId(), "TENANTS"
        );
        validateActiveTenancyForTransition(currentTenancy);

        if (currentTenancy.getBed().getId().equals(transferBedRequestDTO.targetBedId())) {
            throw new ValidationException("Target bed must differ from the current bed");
        }

        BedEntity[] lockedBeds = lifecycleHelper.lockBedsInDeterministicOrder(
                currentTenancy.getBed().getId(), transferBedRequestDTO.targetBedId()
        );
        BedEntity sourceBed = lockedBeds[0];
        BedEntity targetBed = lockedBeds[1];
        PropertyEntity targetProperty = accessGuardService.requirePropertyManagementAccess(
                currentUser, targetBed.getProperty().getId(), "TENANTS"
        );
        lifecycleHelper.validateAllocatableBed(targetBed, targetProperty);

        lifecycleHelper.closeSourceTenancySegment(currentTenancy, sourceBed, transferBedRequestDTO);
        TenancyEntity nextTenancy = lifecycleHelper.createTransferredTenancy(
                currentTenancy, targetBed, transferBedRequestDTO
        );
        lifecycleHelper.markBedOccupiedAndSyncRoom(
                targetBed, currentTenancy.getTenant(), transferBedRequestDTO.transferDate()
        );
        auditLogService.recordEvent(new AuditLogCommand(
                targetProperty.getOrganization(), targetProperty, currentUser, principalEmail,
                AuditAction.TENANCY_TRANSFER, AuditEntityType.TENANCY, nextTenancy.getId(),
                String.format("Bed transferred from %s to %s", sourceBed.getBedCode(), targetBed.getBedCode()),
                Map.of("fromBed", sourceBed.getBedCode(), "toBed", targetBed.getBedCode())
        ));

        log.info("Tenancy bed transferred. fromTenancyId={}, toTenancyId={}, targetBedId={}",
                currentTenancy.getId(), nextTenancy.getId(), targetBed.getId());
        return tenancyMapper.toTenancyResponseDTO(nextTenancy);
    }

    @Override
    @Transactional
    public TenancyResponseDTO moveOut(
            String principalEmail,
            UUID tenancyId,
            MoveOutRequestDTO moveOutRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenancyEntity tenancyEntity = loadTenancyWithDetails(tenancyId);
        accessGuardService.requirePropertyManagementAccess(
                currentUser, tenancyEntity.getProperty().getId(), "TENANTS"
        );
        validateActiveTenancyForTransition(tenancyEntity);
        if (moveOutRequestDTO.actualMoveOutDate().isBefore(tenancyEntity.getMoveInDate())) {
            throw new ValidationException("Move-out date cannot precede move-in date");
        }

        BedStatus targetBedStatus = Optional.ofNullable(moveOutRequestDTO.releaseBedStatus()).orElse(BedStatus.VACANT);
        if (targetBedStatus == BedStatus.OCCUPIED || targetBedStatus == BedStatus.RESERVED) {
            throw new ValidationException("Released bed status after move-out must be VACANT, MAINTENANCE, or INACTIVE");
        }

        lifecycleHelper.applyMoveOutSettlement(tenancyEntity, moveOutRequestDTO);
        TenancyEntity savedTenancy = tenancyRepository.save(tenancyEntity);
        depositService.recordSettlementTransactionsOnMoveOut(
                savedTenancy,
                currentUser,
                savedTenancy.getDeductionsAmount(),
                savedTenancy.getDeductionReason(),
                savedTenancy.getRefundableDepositAmount(),
                moveOutRequestDTO.actualMoveOutDate()
        );
        lifecycleHelper.releaseBedAndSyncTenant(tenancyEntity, targetBedStatus, moveOutRequestDTO.actualMoveOutDate());
        auditLogService.recordEvent(new AuditLogCommand(
                savedTenancy.getOrganization(), savedTenancy.getProperty(), currentUser, principalEmail,
                AuditAction.TENANCY_MOVE_OUT, AuditEntityType.TENANCY, savedTenancy.getId(),
                String.format("Tenant moved out from bed %s", savedTenancy.getBed().getBedCode()),
                Map.of("releasedStatus", targetBedStatus.name(), "refundableDeposit", savedTenancy.getRefundableDepositAmount())
        ));

        log.info("Tenant moved out. tenancyId={}, bedId={}, refundableDeposit={}",
                savedTenancy.getId(), tenancyEntity.getBed().getId(), savedTenancy.getRefundableDepositAmount());
        return tenancyMapper.toTenancyResponseDTO(savedTenancy);
    }

    private void validateActiveTenancyForTransition(TenancyEntity tenancyEntity) {
        if (!TenancyLifecycleHelper.LIVE_OCCUPANCY_STATUSES.contains(tenancyEntity.getTenancyStatus())) {
            throw new ConflictException("Only active or notice-period tenancies can perform this action");
        }
    }

    private TenancyEntity loadTenancyWithDetails(UUID tenancyId) {
        return tenancyRepository.findWithDetailsById(tenancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenancy record not found"));
    }
}
