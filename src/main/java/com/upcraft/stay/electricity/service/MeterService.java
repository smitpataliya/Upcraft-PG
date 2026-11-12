package com.upcraft.stay.electricity.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.electricity.dto.CreateMeterRequestDTO;
import com.upcraft.stay.electricity.dto.MeterResponseDTO;
import com.upcraft.stay.electricity.dto.UpdateMeterRequestDTO;

/**
 * Service managing installation, tariff configuration, and property-scoped retrieval of electricity meters.
 */
public interface MeterService {

    MeterResponseDTO createMeter(String principalEmail, CreateMeterRequestDTO createRequestDTO);

    MeterResponseDTO updateMeter(String principalEmail, UUID meterId, UpdateMeterRequestDTO updateRequestDTO);

    PageResponseDTO<MeterResponseDTO> listMeters(
            String principalEmail,
            UUID propertyId,
            MeterScope meterScope,
            MeterStatus meterStatus,
            String search,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );
}

