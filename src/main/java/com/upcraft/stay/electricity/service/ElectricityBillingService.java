package com.upcraft.stay.electricity.service;

import java.util.List;
import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.electricity.dto.ConsumptionReportResponseDTO;
import com.upcraft.stay.electricity.dto.ElectricityBillResponseDTO;
import com.upcraft.stay.electricity.dto.GenerateElectricityBillsRequestDTO;

/**
 * Service apportioning approved meter readings across active tenancies and building consumption analytics.
 */
public interface ElectricityBillingService {

    List<ElectricityBillResponseDTO> generateBillsForReading(
            String principalEmail,
            UUID readingId,
            GenerateElectricityBillsRequestDTO generateRequestDTO
    );

    PageResponseDTO<ElectricityBillResponseDTO> listBills(
            String principalEmail,
            UUID propertyId,
            UUID tenantId,
            UUID meterId,
            Integer billingYear,
            Integer billingMonth,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PageResponseDTO<ElectricityBillResponseDTO> listMyBills(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    ConsumptionReportResponseDTO getConsumptionReport(
            String principalEmail,
            UUID propertyId,
            Integer billingYear,
            Integer billingMonth
    );
}

