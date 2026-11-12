package com.upcraft.stay.tenant.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.tenant.dto.MoveInRequestDTO;
import com.upcraft.stay.tenant.dto.MoveOutRequestDTO;
import com.upcraft.stay.tenant.dto.ReserveBedRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.dto.TransferBedRequestDTO;

/**
 * Transactional tenancy lifecycle service for move-in, bed reservation, transfer, and move-out settlement.
 */
public interface TenancyService {

    PageResponseDTO<TenancyResponseDTO> listTenancies(
            String principalEmail,
            UUID propertyId,
            TenancyStatus tenancyStatus,
            UUID tenantId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    BedResponseDTO reserveBed(String principalEmail, ReserveBedRequestDTO reserveBedRequestDTO);

    TenancyResponseDTO moveIn(String principalEmail, MoveInRequestDTO moveInRequestDTO);

    TenancyResponseDTO transferBed(
            String principalEmail,
            UUID tenancyId,
            TransferBedRequestDTO transferBedRequestDTO
    );

    TenancyResponseDTO moveOut(
            String principalEmail,
            UUID tenancyId,
            MoveOutRequestDTO moveOutRequestDTO
    );
}

