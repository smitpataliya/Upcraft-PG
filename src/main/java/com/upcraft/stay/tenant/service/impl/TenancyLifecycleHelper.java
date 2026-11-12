package com.upcraft.stay.tenant.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.enums.RoomStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.dto.CreateInvitationRequestDTO;
import com.upcraft.stay.organization.service.InvitationService;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.tenant.dto.MoveInRequestDTO;
import com.upcraft.stay.tenant.dto.MoveOutRequestDTO;
import com.upcraft.stay.tenant.dto.TransferBedRequestDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

/**
 * Helper handling tenant linking, bed locking/sync, deposit settlement, and portal invite side effects.
 */
@Component
@RequiredArgsConstructor
public class TenancyLifecycleHelper {

    public static final List<TenancyStatus> LIVE_OCCUPANCY_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD
    );

    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final TenancyRepository tenancyRepository;
    private final UserRepository userRepository;
    private final InvitationService invitationService;

    public BedEntity loadLockedBed(UUID bedId) {
        return bedRepository.findLockedById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
    }

    public BedEntity[] lockBedsInDeterministicOrder(UUID sourceBedId, UUID targetBedId) {
        if (sourceBedId.compareTo(targetBedId) <= 0) {
            return new BedEntity[]{loadLockedBed(sourceBedId), loadLockedBed(targetBedId)};
        }
        BedEntity target = loadLockedBed(targetBedId);
        BedEntity source = loadLockedBed(sourceBedId);
        return new BedEntity[]{source, target};
    }

    public void validateAllocatableBed(BedEntity bedEntity, PropertyEntity propertyEntity) {
        if (propertyEntity.getPropertyStatus() != PropertyStatus.ACTIVE) {
            throw new ConflictException("Cannot allocate a bed in a non-active property");
        }
        if (bedEntity.getRoom().getRoomStatus() != RoomStatus.ACTIVE) {
            throw new ConflictException("Cannot allocate a bed while its room is not ACTIVE");
        }
        if (bedEntity.getBedStatus() != BedStatus.VACANT && bedEntity.getBedStatus() != BedStatus.RESERVED) {
            throw new ConflictException("Bed is not available for allocation (status: " + bedEntity.getBedStatus() + ")");
        }
        if (tenancyRepository.existsByBedIdAndTenancyStatusIn(bedEntity.getId(), LIVE_OCCUPANCY_STATUSES)) {
            throw new ConflictException("Bed already has an active tenancy");
        }
    }

    public TenantEntity resolveOrCreateTenant(PropertyEntity propertyEntity, MoveInRequestDTO requestDTO) {
        if (Optional.ofNullable(requestDTO.existingTenantId()).isPresent()) {
            TenantEntity existingTenant = tenantRepository.findWithDetailsById(requestDTO.existingTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Selected tenant profile not found"));
            if (!existingTenant.getProperty().getId().equals(propertyEntity.getId())) {
                throw new ValidationException("Selected tenant belongs to a different property");
            }
            existingTenant.setTenantStatus(TenantStatus.ACTIVE);
            return tenantRepository.save(existingTenant);
        }
        if (StringUtils.isBlank(requestDTO.fullName()) || StringUtils.isBlank(requestDTO.phone())) {
            throw new ValidationException("Tenant full name and phone number are required for new resident move-in");
        }
        TenantEntity newTenant = buildTenantEntity(propertyEntity, requestDTO);
        linkExistingUserIfMember(newTenant);
        return tenantRepository.save(newTenant);
    }

    public TenancyEntity persistActiveTenancy(
            PropertyEntity propertyEntity,
            BedEntity bedEntity,
            TenantEntity tenantEntity,
            MoveInRequestDTO requestDTO
    ) {
        TenancyEntity tenancy = new TenancyEntity();
        tenancy.setOrganization(propertyEntity.getOrganization());
        tenancy.setProperty(propertyEntity);
        tenancy.setTenant(tenantEntity);
        tenancy.setRoom(bedEntity.getRoom());
        tenancy.setBed(bedEntity);
        tenancy.setTenancyStatus(TenancyStatus.ACTIVE);
        tenancy.setMoveInDate(requestDTO.moveInDate());
        tenancy.setExpectedMoveOutDate(requestDTO.expectedMoveOutDate());
        tenancy.setAgreedMonthlyRent(Optional.ofNullable(requestDTO.agreedMonthlyRent()).orElse(bedEntity.getMonthlyRent()));
        tenancy.setSecurityDepositAmount(
                Optional.ofNullable(requestDTO.securityDepositAmount()).orElse(bedEntity.getDefaultDeposit())
        );
        tenancy.setDepositReceivedAmount(Optional.ofNullable(requestDTO.depositReceivedAmount()).orElse(BigDecimal.ZERO));
        tenancy.setRentDueDay(Optional.ofNullable(requestDTO.rentDueDay()).orElse(propertyEntity.getRentDueDay()));
        tenancy.setInitialMeterReading(requestDTO.initialMeterReading());
        tenancy.setAgreementStartDate(Optional.ofNullable(requestDTO.agreementStartDate()).orElse(requestDTO.moveInDate()));
        tenancy.setAgreementEndDate(requestDTO.agreementEndDate());
        tenancy.setMoveInNotes(StringUtils.trimToNull(requestDTO.moveInNotes()));
        return tenancyRepository.save(tenancy);
    }

    public void markBedOccupiedAndSyncRoom(BedEntity bedEntity, TenantEntity tenantEntity, LocalDate moveInDate) {
        bedEntity.setBedStatus(BedStatus.OCCUPIED);
        bedEntity.setAvailableFrom(null);
        bedEntity.setReservedUntil(null);
        bedRepository.save(bedEntity);
        syncRoomOccupiedCount(bedEntity.getRoom());

        Optional.ofNullable(tenantEntity.getUser()).ifPresent(user -> {
            user.setRoom(bedEntity.getRoom());
            user.setBedNumber(bedEntity.getBedCode());
            user.setCheckInDate(moveInDate);
            userRepository.save(user);
        });
    }

    public void sendOptionalPortalInvite(
            String principalEmail,
            PropertyEntity propertyEntity,
            TenantEntity tenantEntity,
            MoveInRequestDTO requestDTO
    ) {
        if (!Boolean.TRUE.equals(requestDTO.sendPortalInvite())
                || StringUtils.isBlank(tenantEntity.getEmail())
                || Optional.ofNullable(tenantEntity.getUser()).isPresent()) {
            return;
        }
        CreateInvitationRequestDTO inviteRequest = new CreateInvitationRequestDTO(
                tenantEntity.getEmail(),
                tenantEntity.getPhone(),
                tenantEntity.getFullName(),
                Role.PG_MEMBER,
                propertyEntity.getId(),
                false, false, false, false, false
        );
        invitationService.createInvitation(principalEmail, inviteRequest);
    }

    public void closeSourceTenancySegment(
            TenancyEntity currentTenancy,
            BedEntity sourceBed,
            TransferBedRequestDTO transferDTO
    ) {
        currentTenancy.setTenancyStatus(TenancyStatus.COMPLETED);
        currentTenancy.setActualMoveOutDate(transferDTO.transferDate());
        currentTenancy.setRefundStatus(RefundStatus.NONE);
        currentTenancy.setMoveOutNotes(StringUtils.defaultIfBlank(transferDTO.notes(), "Transferred to another bed"));
        tenancyRepository.save(currentTenancy);

        sourceBed.setBedStatus(BedStatus.VACANT);
        sourceBed.setAvailableFrom(transferDTO.transferDate());
        bedRepository.save(sourceBed);
        syncRoomOccupiedCount(sourceBed.getRoom());
    }

    public TenancyEntity createTransferredTenancy(
            TenancyEntity sourceTenancy,
            BedEntity targetBed,
            TransferBedRequestDTO transferDTO
    ) {
        TenancyEntity nextTenancy = new TenancyEntity();
        nextTenancy.setOrganization(targetBed.getOrganization());
        nextTenancy.setProperty(targetBed.getProperty());
        nextTenancy.setTenant(sourceTenancy.getTenant());
        nextTenancy.setRoom(targetBed.getRoom());
        nextTenancy.setBed(targetBed);
        nextTenancy.setTenancyStatus(TenancyStatus.ACTIVE);
        nextTenancy.setMoveInDate(transferDTO.transferDate());
        nextTenancy.setExpectedMoveOutDate(sourceTenancy.getExpectedMoveOutDate());
        nextTenancy.setAgreedMonthlyRent(
                Optional.ofNullable(transferDTO.newMonthlyRent()).orElse(targetBed.getMonthlyRent())
        );
        nextTenancy.setSecurityDepositAmount(sourceTenancy.getSecurityDepositAmount());
        nextTenancy.setDepositReceivedAmount(sourceTenancy.getDepositReceivedAmount());
        nextTenancy.setRentDueDay(sourceTenancy.getRentDueDay());
        nextTenancy.setAgreementStartDate(transferDTO.transferDate());
        nextTenancy.setAgreementEndDate(sourceTenancy.getAgreementEndDate());
        nextTenancy.setMoveInNotes(StringUtils.trimToNull(transferDTO.notes()));
        return tenancyRepository.save(nextTenancy);
    }

    public void applyMoveOutSettlement(TenancyEntity tenancyEntity, MoveOutRequestDTO requestDTO) {
        BigDecimal unpaidDues = Optional.ofNullable(requestDTO.unpaidDuesSettled()).orElse(BigDecimal.ZERO);
        BigDecimal deductions = Optional.ofNullable(requestDTO.deductionsAmount()).orElse(BigDecimal.ZERO);
        BigDecimal depositReceived = Optional.ofNullable(tenancyEntity.getDepositReceivedAmount())
                .orElse(BigDecimal.ZERO);
        BigDecimal netBalance = depositReceived.subtract(unpaidDues).subtract(deductions);
        BigDecimal refundable = netBalance.compareTo(BigDecimal.ZERO) > 0 ? netBalance : BigDecimal.ZERO;

        tenancyEntity.setTenancyStatus(TenancyStatus.COMPLETED);
        tenancyEntity.setActualMoveOutDate(requestDTO.actualMoveOutDate());
        tenancyEntity.setFinalMeterReading(requestDTO.finalMeterReading());
        tenancyEntity.setUnpaidDuesSettled(unpaidDues);
        tenancyEntity.setDeductionsAmount(deductions);
        tenancyEntity.setDeductionReason(StringUtils.trimToNull(requestDTO.deductionReason()));
        tenancyEntity.setRefundableDepositAmount(refundable);
        tenancyEntity.setRefundStatus(Optional.ofNullable(requestDTO.refundStatus())
                .orElse(refundable.compareTo(BigDecimal.ZERO) > 0 ? RefundStatus.PENDING : RefundStatus.NONE));
        tenancyEntity.setMoveOutNotes(StringUtils.trimToNull(requestDTO.moveOutNotes()));
    }

    public void releaseBedAndSyncTenant(
            TenancyEntity tenancyEntity,
            BedStatus targetBedStatus,
            LocalDate availableDate
    ) {
        BedEntity bedEntity = loadLockedBed(tenancyEntity.getBed().getId());
        bedEntity.setBedStatus(targetBedStatus);
        bedEntity.setAvailableFrom(availableDate);
        bedEntity.setReservedUntil(null);
        bedRepository.save(bedEntity);
        syncRoomOccupiedCount(bedEntity.getRoom());

        TenantEntity tenantEntity = tenancyEntity.getTenant();
        if (!tenancyRepository.existsByTenantIdAndTenancyStatusIn(tenantEntity.getId(), LIVE_OCCUPANCY_STATUSES)) {
            tenantEntity.setTenantStatus(TenantStatus.MOVED_OUT);
            tenantRepository.save(tenantEntity);
            Optional.ofNullable(tenantEntity.getUser()).ifPresent(user -> {
                user.setRoom(null);
                user.setBedNumber(null);
                userRepository.save(user);
            });
        }
    }

    private TenantEntity buildTenantEntity(PropertyEntity propertyEntity, MoveInRequestDTO requestDTO) {
        TenantEntity newTenant = new TenantEntity();
        newTenant.setOrganization(propertyEntity.getOrganization());
        newTenant.setProperty(propertyEntity);
        newTenant.setFullName(requestDTO.fullName().trim());
        newTenant.setPhone(requestDTO.phone().trim());
        newTenant.setEmail(Optional.ofNullable(StringUtils.trimToNull(requestDTO.email()))
                .map(email -> email.toLowerCase(Locale.ROOT))
                .orElse(null));
        newTenant.setEmergencyContact(StringUtils.trimToNull(requestDTO.emergencyContact()));
        newTenant.setEmergencyPhone(StringUtils.trimToNull(requestDTO.emergencyPhone()));
        newTenant.setAddress(StringUtils.trimToNull(requestDTO.address()));
        newTenant.setTenantStatus(TenantStatus.ACTIVE);
        return newTenant;
    }

    private void linkExistingUserIfMember(TenantEntity tenantEntity) {
        if (StringUtils.isBlank(tenantEntity.getEmail())) {
            return;
        }
        userRepository.findByEmail(tenantEntity.getEmail())
                .filter(user -> user.getRole() == Role.PG_MEMBER && user.isActive())
                .ifPresent(tenantEntity::setUser);
    }

    private void syncRoomOccupiedCount(Room room) {
        long occupiedCount = bedRepository.countByRoomIdAndBedStatus(room.getId(), BedStatus.OCCUPIED);
        room.setOccupied((int) occupiedCount);
        roomRepository.save(room);
    }
}

