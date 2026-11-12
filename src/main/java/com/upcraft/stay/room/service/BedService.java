package com.upcraft.stay.room.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.room.dto.BedAvailabilityResponseDTO;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.dto.CreateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedStatusRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;

/**
 * Bed inventory, vacancy search, status transitions, and allocation history service.
 */
public interface BedService {

    PageResponseDTO<BedResponseDTO> listRoomBeds(
            String principalEmail,
            UUID roomId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    BedResponseDTO createBed(String principalEmail, UUID roomId, CreateBedRequestDTO createBedRequestDTO);

    BedResponseDTO updateBed(String principalEmail, UUID bedId, UpdateBedRequestDTO updateBedRequestDTO);

    BedResponseDTO updateBedStatus(String principalEmail, UUID bedId, UpdateBedStatusRequestDTO statusRequestDTO);

    PageResponseDTO<BedAvailabilityResponseDTO> findAvailableBeds(
            String principalEmail,
            UUID propertyId,
            BedStatus bedStatus,
            String roomType,
            BigDecimal maxRent,
            LocalDate availableBefore,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PageResponseDTO<TenancyResponseDTO> getBedHistory(
            String principalEmail,
            UUID bedId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );
}

