package com.upcraft.stay.property.service.impl;

import java.math.BigDecimal;
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
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.dto.CreatePropertyRequestDTO;
import com.upcraft.stay.property.dto.PropertyOccupancySummaryResponseDTO;
import com.upcraft.stay.property.dto.PropertySummaryResponseDTO;
import com.upcraft.stay.property.dto.UpdatePropertyRequestDTO;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.mapper.PropertyMapper;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.property.service.PropertyService;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.room.repository.FloorRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Organization- and assignment-scoped PG property service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "name", "city");
    private static final List<TenancyStatus> BLOCKING_ARCHIVE_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD,
            TenancyStatus.RESERVED
    );

    private final AccessGuardService accessGuardService;
    private final PropertyRepository propertyRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyMapper propertyMapper;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PropertySummaryResponseDTO> listAccessibleProperties(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, ALLOWED_SORT_FIELDS, "createdAt", appProperties
        );

        if (currentUser.getRole() == Role.PG_OWNER) {
            Page<PropertySummaryResponseDTO> propertyPage = propertyRepository
                    .findAllByOrganizationId(organizationEntity.getId(), pageable)
                    .map(propertyMapper::toPropertySummaryResponseDTO);
            return PageResponseDTO.from(propertyPage);
        }

        if (currentUser.getRole() == Role.MANAGER) {
            List<UUID> assignedPropertyIds = accessGuardService.resolveAssignedPropertyIds(currentUser);
            if (CollectionUtils.isEmpty(assignedPropertyIds)) {
                return PageResponseDTO.from(Page.empty(pageable));
            }
            Page<PropertySummaryResponseDTO> assignedPage = propertyRepository
                    .findAllByIdIn(assignedPropertyIds, pageable)
                    .map(propertyMapper::toPropertySummaryResponseDTO);
            return PageResponseDTO.from(assignedPage);
        }

        throw new ForbiddenException("Property listing is available to owners and managers only");
    }

    @Override
    @Transactional(readOnly = true)
    public PropertySummaryResponseDTO getPropertyById(String principalEmail, UUID propertyId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        return propertyMapper.toPropertySummaryResponseDTO(propertyEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyOccupancySummaryResponseDTO getPropertyOccupancySummary(String principalEmail, UUID propertyId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        return buildOccupancySummary(propertyEntity);
    }

    @Override
    @Transactional
    public PropertySummaryResponseDTO createProperty(
            String principalEmail,
            CreatePropertyRequestDTO createPropertyRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can create properties");
        }
        OrganizationEntity organizationEntity = accessGuardService.resolveUserOrganization(currentUser);
        String cleanName = createPropertyRequestDTO.name().trim();
        if (propertyRepository.existsByOrganizationIdAndNameIgnoreCase(organizationEntity.getId(), cleanName)) {
            throw new ConflictException("A property with this name already exists in your organization");
        }

        PropertyEntity propertyEntity = buildPropertyEntity(createPropertyRequestDTO, organizationEntity, cleanName);
        PropertyEntity savedProperty = propertyRepository.save(propertyEntity);
        auditLogService.recordEvent(new AuditLogCommand(
                organizationEntity, savedProperty, currentUser, principalEmail,
                AuditAction.PROPERTY_CREATED, AuditEntityType.PROPERTY, savedProperty.getId(),
                "Created property " + savedProperty.getName(),
                Map.of("city", savedProperty.getCity(), "propertyType", savedProperty.getPropertyType().name())
        ));
        log.info("Property created. propertyId={}, organizationId={}", savedProperty.getId(), organizationEntity.getId());
        return propertyMapper.toPropertySummaryResponseDTO(savedProperty);
    }

    @Override
    @Transactional
    public PropertySummaryResponseDTO updateProperty(
            String principalEmail,
            UUID propertyId,
            UpdatePropertyRequestDTO updatePropertyRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can update properties");
        }
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        String cleanName = updatePropertyRequestDTO.name().trim();
        if (propertyRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
                propertyEntity.getOrganization().getId(), cleanName, propertyId)) {
            throw new ConflictException("Another property with this name already exists in your organization");
        }
        applyPropertyUpdates(propertyEntity, updatePropertyRequestDTO, cleanName);
        PropertyEntity savedProperty = propertyRepository.save(propertyEntity);
        log.info("Property updated. propertyId={}, userId={}", savedProperty.getId(), currentUser.getId());
        return propertyMapper.toPropertySummaryResponseDTO(savedProperty);
    }

    @Override
    @Transactional
    public PropertySummaryResponseDTO archiveProperty(String principalEmail, UUID propertyId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can archive properties");
        }
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        if (tenancyRepository.existsByPropertyIdAndTenancyStatusIn(propertyId, BLOCKING_ARCHIVE_STATUSES)) {
            throw new ConflictException("Cannot archive property while active, notice-period, or reserved tenancies exist");
        }
        propertyEntity.setPropertyStatus(PropertyStatus.ARCHIVED);
        PropertyEntity archivedProperty = propertyRepository.save(propertyEntity);
        auditLogService.recordEvent(new AuditLogCommand(
                archivedProperty.getOrganization(), archivedProperty, currentUser, principalEmail,
                AuditAction.PROPERTY_ARCHIVED, AuditEntityType.PROPERTY, archivedProperty.getId(),
                "Archived property " + archivedProperty.getName(),
                Map.of("status", PropertyStatus.ARCHIVED.name())
        ));
        log.info("Property archived. propertyId={}, userId={}", propertyId, currentUser.getId());
        return propertyMapper.toPropertySummaryResponseDTO(archivedProperty);
    }

    private PropertyEntity buildPropertyEntity(
            CreatePropertyRequestDTO requestDTO,
            OrganizationEntity organizationEntity,
            String cleanName
    ) {
        PropertyEntity propertyEntity = propertyMapper.toPropertyEntity(requestDTO);
        propertyEntity.setOrganization(organizationEntity);
        propertyEntity.setName(cleanName);
        propertyEntity.setPropertyType(Optional.ofNullable(requestDTO.propertyType()).orElse(PropertyType.PG));
        propertyEntity.setCountry(StringUtils.defaultIfBlank(requestDTO.country(), "India"));
        propertyEntity.setRentDueDay(Optional.ofNullable(requestDTO.rentDueDay()).orElse(AppConstants.DEFAULT_RENT_DUE_DAY));
        propertyEntity.setGracePeriodDays(
                Optional.ofNullable(requestDTO.gracePeriodDays()).orElse(AppConstants.DEFAULT_GRACE_PERIOD_DAYS)
        );
        propertyEntity.setLateFeeType(Optional.ofNullable(requestDTO.lateFeeType()).orElse(LateFeeType.NONE));
        propertyEntity.setLateFeeAmount(Optional.ofNullable(requestDTO.lateFeeAmount()).orElse(BigDecimal.ZERO));
        propertyEntity.setDefaultElectricityRate(
                Optional.ofNullable(requestDTO.defaultElectricityRate()).orElse(BigDecimal.ZERO)
        );
        propertyEntity.setPropertyStatus(PropertyStatus.ACTIVE);
        return propertyEntity;
    }

    private void applyPropertyUpdates(
            PropertyEntity propertyEntity,
            UpdatePropertyRequestDTO requestDTO,
            String cleanName
    ) {
        propertyEntity.setName(cleanName);
        propertyEntity.setPropertyType(requestDTO.propertyType());
        propertyEntity.setAddressLine(requestDTO.addressLine().trim());
        propertyEntity.setLandmark(StringUtils.trimToNull(requestDTO.landmark()));
        propertyEntity.setCity(requestDTO.city().trim());
        propertyEntity.setState(requestDTO.state().trim());
        propertyEntity.setPinCode(requestDTO.pinCode().trim());
        propertyEntity.setCountry(StringUtils.defaultIfBlank(requestDTO.country(), "India"));
        propertyEntity.setContactPhone(StringUtils.trimToNull(requestDTO.contactPhone()));
        propertyEntity.setContactEmail(StringUtils.trimToNull(requestDTO.contactEmail()));
        propertyEntity.setRentDueDay(Optional.ofNullable(requestDTO.rentDueDay()).orElse(propertyEntity.getRentDueDay()));
        propertyEntity.setGracePeriodDays(
                Optional.ofNullable(requestDTO.gracePeriodDays()).orElse(propertyEntity.getGracePeriodDays())
        );
        propertyEntity.setLateFeeType(Optional.ofNullable(requestDTO.lateFeeType()).orElse(propertyEntity.getLateFeeType()));
        propertyEntity.setLateFeeAmount(
                Optional.ofNullable(requestDTO.lateFeeAmount()).orElse(propertyEntity.getLateFeeAmount())
        );
        propertyEntity.setDefaultElectricityRate(
                Optional.ofNullable(requestDTO.defaultElectricityRate()).orElse(propertyEntity.getDefaultElectricityRate())
        );
        propertyEntity.setCheckInRules(StringUtils.trimToNull(requestDTO.checkInRules()));
        propertyEntity.setCheckOutRules(StringUtils.trimToNull(requestDTO.checkOutRules()));
        Optional.ofNullable(requestDTO.propertyStatus()).ifPresent(propertyEntity::setPropertyStatus);
    }

    private PropertyOccupancySummaryResponseDTO buildOccupancySummary(PropertyEntity propertyEntity) {
        UUID propertyId = propertyEntity.getId();
        long totalBeds = bedRepository.countByPropertyId(propertyId);
        long vacantBeds = bedRepository.countByPropertyIdAndBedStatus(propertyId, BedStatus.VACANT);
        long occupiedBeds = bedRepository.countByPropertyIdAndBedStatus(propertyId, BedStatus.OCCUPIED);
        long reservedBeds = bedRepository.countByPropertyIdAndBedStatus(propertyId, BedStatus.RESERVED);
        long maintenanceBeds = bedRepository.countByPropertyIdAndBedStatus(propertyId, BedStatus.MAINTENANCE);
        long inactiveBeds = bedRepository.countByPropertyIdAndBedStatus(propertyId, BedStatus.INACTIVE);
        double occupancyPercent = totalBeds == 0 ? 0.0 : Math.round((occupiedBeds * 1000.0) / totalBeds) / 10.0;

        return new PropertyOccupancySummaryResponseDTO(
                propertyId,
                propertyEntity.getName(),
                propertyEntity.getPropertyType(),
                propertyEntity.getPropertyStatus(),
                propertyEntity.getCity(),
                propertyEntity.getState(),
                propertyEntity.getCheckInRules(),
                propertyEntity.getCheckOutRules(),
                propertyEntity.getRentDueDay(),
                propertyEntity.getGracePeriodDays(),
                propertyEntity.getLateFeeType(),
                propertyEntity.getLateFeeAmount(),
                propertyEntity.getDefaultElectricityRate(),
                floorRepository.countByPropertyId(propertyId),
                roomRepository.countByPropertyId(propertyId),
                totalBeds,
                vacantBeds,
                occupiedBeds,
                reservedBeds,
                maintenanceBeds,
                inactiveBeds,
                occupancyPercent
        );
    }
}
