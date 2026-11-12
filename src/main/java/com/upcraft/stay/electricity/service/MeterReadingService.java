package com.upcraft.stay.electricity.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.electricity.dto.ApproveReadingRequestDTO;
import com.upcraft.stay.electricity.dto.MeterReadingResponseDTO;
import com.upcraft.stay.electricity.dto.RecordMeterReadingRequestDTO;
import com.upcraft.stay.electricity.dto.RejectReadingRequestDTO;

/**
 * Service handling electricity meter reading capture, anomaly detection, reset controls, and approvals.
 */
public interface MeterReadingService {

    MeterReadingResponseDTO recordReading(
            String principalEmail,
            UUID meterId,
            RecordMeterReadingRequestDTO recordRequestDTO
    );

    PageResponseDTO<MeterReadingResponseDTO> listReadingsForMeter(
            String principalEmail,
            UUID meterId,
            int page,
            Integer size
    );

    PageResponseDTO<MeterReadingResponseDTO> listReadings(
            String principalEmail,
            UUID propertyId,
            UUID meterId,
            MeterReadingStatus readingStatus,
            Integer billingYear,
            Integer billingMonth,
            Boolean unusualOnly,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    MeterReadingResponseDTO approveReading(
            String principalEmail,
            UUID readingId,
            ApproveReadingRequestDTO approveRequestDTO
    );

    MeterReadingResponseDTO rejectReading(
            String principalEmail,
            UUID readingId,
            RejectReadingRequestDTO rejectRequestDTO
    );
}

