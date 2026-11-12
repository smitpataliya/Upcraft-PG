package com.upcraft.stay.tenant.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.dto.CreateTenantRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.dto.TenantDetailResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSelfPortalResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSummaryResponseDTO;
import com.upcraft.stay.tenant.dto.UpdateTenantRequestDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.mapper.TenancyMapper;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;
import com.upcraft.stay.tenant.service.TenantService;

/**
 * Property-scoped tenant directory service and strict resident self-service portal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final Set<String> SORT_FIELDS = Set.of("fullName", "createdAt", "tenantStatus");
    private static final List<TenancyStatus> ACTIVE_RESIDENCY_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD
    );

    private final AccessGuardService accessGuardService;
    private final TenantRepository tenantRepository;
    private final TenancyRepository tenancyRepository;
    private final UserRepository userRepository;
    private final TenancyMapper tenancyMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<TenantSummaryResponseDTO> listTenants(
            String principalEmail,
            UUID propertyId,
            TenantStatus tenantStatus,
            String search,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "createdAt", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<TenantStatus> statusOptional = Optional.ofNullable(tenantStatus);
        boolean hasSearch = StringUtils.isNotBlank(search);
        String searchPattern = hasSearch ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%" : "";

        Page<TenantEntity> tenantPage = tenantRepository.searchAccessibleTenants(
                propertyIds,
                statusOptional.isPresent(),
                statusOptional.orElse(TenantStatus.ACTIVE),
                hasSearch,
                searchPattern,
                pageable
        );
        return PageResponseDTO.from(mapSummaryPage(tenantPage));
    }

    @Override
    @Transactional(readOnly = true)
    public TenantDetailResponseDTO getTenantDetail(String principalEmail, UUID tenantId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenantEntity tenantEntity = findTenantWithDetails(tenantId);
        accessGuardService.requirePropertyManagementAccess(currentUser, tenantEntity.getProperty().getId(), "READ");
        return buildTenantDetail(tenantEntity);
    }

    @Override
    @Transactional
    public TenantDetailResponseDTO createTenant(
            String principalEmail,
            CreateTenantRequestDTO createTenantRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, createTenantRequestDTO.propertyId(), "TENANTS"
        );
        TenantEntity tenantEntity = buildNewTenant(propertyEntity, createTenantRequestDTO);
        TenantEntity savedTenant = tenantRepository.save(tenantEntity);
        log.info("Tenant profile created. tenantId={}, propertyId={}", savedTenant.getId(), propertyEntity.getId());
        return buildTenantDetail(savedTenant);
    }

    @Override
    @Transactional
    public TenantDetailResponseDTO updateTenant(
            String principalEmail,
            UUID tenantId,
            UpdateTenantRequestDTO updateTenantRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenantEntity tenantEntity = findTenantWithDetails(tenantId);
        accessGuardService.requirePropertyManagementAccess(currentUser, tenantEntity.getProperty().getId(), "TENANTS");

        String cleanEmail = StringUtils.trimToNull(updateTenantRequestDTO.email());
        tenantEntity.setFullName(updateTenantRequestDTO.fullName().trim());
        tenantEntity.setPhone(updateTenantRequestDTO.phone().trim());
        tenantEntity.setEmail(Optional.ofNullable(cleanEmail).map(val -> val.toLowerCase(Locale.ROOT)).orElse(null));
        tenantEntity.setEmergencyContact(StringUtils.trimToNull(updateTenantRequestDTO.emergencyContact()));
        tenantEntity.setEmergencyPhone(StringUtils.trimToNull(updateTenantRequestDTO.emergencyPhone()));
        tenantEntity.setAddress(StringUtils.trimToNull(updateTenantRequestDTO.address()));
        tenantEntity.setNotes(StringUtils.trimToNull(updateTenantRequestDTO.notes()));
        Optional.ofNullable(updateTenantRequestDTO.tenantStatus()).ifPresent(tenantEntity::setTenantStatus);
        linkMatchingPortalUser(tenantEntity);

        TenantEntity savedTenant = tenantRepository.save(tenantEntity);
        return buildTenantDetail(savedTenant);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantSelfPortalResponseDTO getSelfPortal(String principalEmail) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_MEMBER) {
            throw new ForbiddenException("Resident self-portal is limited to tenant accounts");
        }
        Optional<UUID> orgIdOptional = accessGuardService.findUserOrganization(currentUser).map(org -> org.getId());
        List<TenancyEntity> rawTenancies = orgIdOptional
                .map(orgId -> tenancyRepository.findSelfResidencyTenancies(
                        currentUser.getId(), orgId, currentUser.getEmail().trim().toLowerCase(Locale.ROOT)
                ))
                .orElseGet(() -> tenancyRepository.findAllByTenantUserIdOrderByMoveInDateDescCreatedAtDesc(
                        currentUser.getId()
                ));

        List<TenancyResponseDTO> history = rawTenancies.stream()
                .map(tenancyMapper::toTenancyResponseDTO)
                .toList();

        Optional<TenancyResponseDTO> activeTenancy = history.stream()
                .filter(item -> item.tenancyStatus() == TenancyStatus.ACTIVE
                        || item.tenancyStatus() == TenancyStatus.NOTICE_PERIOD)
                .findFirst();

        Optional<PropertyEntity> activeProperty = rawTenancies.stream()
                .filter(item -> ACTIVE_RESIDENCY_STATUSES.contains(item.getTenancyStatus()))
                .map(TenancyEntity::getProperty)
                .findFirst();

        return new TenantSelfPortalResponseDTO(
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getEmail(),
                currentUser.getPhone(),
                activeProperty.map(PropertyEntity::getCheckInRules).orElse(null),
                activeProperty.map(PropertyEntity::getCheckOutRules).orElse(null),
                activeTenancy.orElse(null),
                history
        );
    }

    private Page<TenantSummaryResponseDTO> mapSummaryPage(Page<TenantEntity> tenantPage) {
        List<UUID> tenantIds = tenantPage.getContent().stream().map(TenantEntity::getId).toList();
        Map<UUID, TenancyEntity> activeByTenant = CollectionUtils.isEmpty(tenantIds)
                ? Map.of()
                : tenancyRepository.findAllByTenantIdInAndTenancyStatusIn(tenantIds, ACTIVE_RESIDENCY_STATUSES)
                        .stream()
                        .collect(Collectors.toMap(
                                tenancy -> tenancy.getTenant().getId(),
                                Function.identity(),
                                (first, second) -> first
                        ));
        return tenantPage.map(tenantEntity -> toSummaryDTO(tenantEntity, activeByTenant.get(tenantEntity.getId())));
    }

    private TenantSummaryResponseDTO toSummaryDTO(TenantEntity tenantEntity, TenancyEntity activeTenancy) {
        Optional<TenancyEntity> tenancyOptional = Optional.ofNullable(activeTenancy);
        return new TenantSummaryResponseDTO(
                tenantEntity.getId(),
                tenantEntity.getProperty().getId(),
                tenantEntity.getProperty().getName(),
                Optional.ofNullable(tenantEntity.getUser()).map(User::getId).orElse(null),
                tenantEntity.getFullName(),
                tenantEntity.getPhone(),
                tenantEntity.getEmail(),
                tenantEntity.getTenantStatus(),
                tenancyOptional.map(TenancyEntity::getId).orElse(null),
                tenancyOptional.map(tenancy -> tenancy.getRoom().getNumber()).orElse(null),
                tenancyOptional.map(tenancy -> tenancy.getBed().getBedCode()).orElse(null),
                tenancyOptional.map(TenancyEntity::getMoveInDate).orElse(null),
                tenantEntity.getCreatedAt()
        );
    }

    private TenantDetailResponseDTO buildTenantDetail(TenantEntity tenantEntity) {
        List<TenancyResponseDTO> tenancies = tenancyRepository
                .findAllByTenantIdOrderByMoveInDateDescCreatedAtDesc(tenantEntity.getId())
                .stream()
                .map(tenancyMapper::toTenancyResponseDTO)
                .toList();

        return new TenantDetailResponseDTO(
                tenantEntity.getId(),
                tenantEntity.getOrganization().getId(),
                tenantEntity.getProperty().getId(),
                tenantEntity.getProperty().getName(),
                Optional.ofNullable(tenantEntity.getUser()).map(User::getId).orElse(null),
                tenantEntity.getFullName(),
                tenantEntity.getPhone(),
                tenantEntity.getEmail(),
                tenantEntity.getEmergencyContact(),
                tenantEntity.getEmergencyPhone(),
                tenantEntity.getAddress(),
                tenantEntity.getNotes(),
                tenantEntity.getTenantStatus(),
                tenantEntity.getCreatedAt(),
                tenancies
        );
    }

    private TenantEntity buildNewTenant(PropertyEntity propertyEntity, CreateTenantRequestDTO requestDTO) {
        String normalizedEmail = Optional.ofNullable(StringUtils.trimToNull(requestDTO.email()))
                .map(email -> email.toLowerCase(Locale.ROOT))
                .orElse(null);

        TenantEntity tenantEntity = new TenantEntity();
        tenantEntity.setOrganization(propertyEntity.getOrganization());
        tenantEntity.setProperty(propertyEntity);
        tenantEntity.setFullName(requestDTO.fullName().trim());
        tenantEntity.setPhone(requestDTO.phone().trim());
        tenantEntity.setEmail(normalizedEmail);
        tenantEntity.setEmergencyContact(StringUtils.trimToNull(requestDTO.emergencyContact()));
        tenantEntity.setEmergencyPhone(StringUtils.trimToNull(requestDTO.emergencyPhone()));
        tenantEntity.setAddress(StringUtils.trimToNull(requestDTO.address()));
        tenantEntity.setNotes(StringUtils.trimToNull(requestDTO.notes()));
        tenantEntity.setTenantStatus(TenantStatus.PROSPECT);
        linkMatchingPortalUser(tenantEntity);
        return tenantEntity;
    }

    private void linkMatchingPortalUser(TenantEntity tenantEntity) {
        if (Optional.ofNullable(tenantEntity.getUser()).isPresent() || StringUtils.isBlank(tenantEntity.getEmail())) {
            return;
        }
        userRepository.findByEmail(tenantEntity.getEmail())
                .filter(user -> user.getRole() == Role.PG_MEMBER && user.isActive())
                .ifPresent(tenantEntity::setUser);
    }

    private TenantEntity findTenantWithDetails(UUID tenantId) {
        return tenantRepository.findWithDetailsById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant profile not found"));
    }
}
