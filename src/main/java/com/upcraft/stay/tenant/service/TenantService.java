package com.upcraft.stay.tenant.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.tenant.dto.CreateTenantRequestDTO;
import com.upcraft.stay.tenant.dto.TenantDetailResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSelfPortalResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSummaryResponseDTO;
import com.upcraft.stay.tenant.dto.UpdateTenantRequestDTO;

/**
 * Property-scoped tenant profile management and resident self-portal service.
 */
public interface TenantService {

    PageResponseDTO<TenantSummaryResponseDTO> listTenants(
            String principalEmail,
            UUID propertyId,
            TenantStatus tenantStatus,
            String search,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    TenantDetailResponseDTO getTenantDetail(String principalEmail, UUID tenantId);

    TenantDetailResponseDTO createTenant(String principalEmail, CreateTenantRequestDTO createTenantRequestDTO);

    TenantDetailResponseDTO updateTenant(
            String principalEmail,
            UUID tenantId,
            UpdateTenantRequestDTO updateTenantRequestDTO
    );

    TenantSelfPortalResponseDTO getSelfPortal(String principalEmail);
}

