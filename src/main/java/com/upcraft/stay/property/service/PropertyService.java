package com.upcraft.stay.property.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.property.dto.CreatePropertyRequestDTO;
import com.upcraft.stay.property.dto.PropertyOccupancySummaryResponseDTO;
import com.upcraft.stay.property.dto.PropertySummaryResponseDTO;
import com.upcraft.stay.property.dto.UpdatePropertyRequestDTO;

/**
 * Property management service scoped by organization and manager property assignments.
 */
public interface PropertyService {

    PageResponseDTO<PropertySummaryResponseDTO> listAccessibleProperties(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PropertySummaryResponseDTO getPropertyById(String principalEmail, UUID propertyId);

    PropertyOccupancySummaryResponseDTO getPropertyOccupancySummary(String principalEmail, UUID propertyId);

    PropertySummaryResponseDTO createProperty(String principalEmail, CreatePropertyRequestDTO createPropertyRequestDTO);

    PropertySummaryResponseDTO updateProperty(
            String principalEmail,
            UUID propertyId,
            UpdatePropertyRequestDTO updatePropertyRequestDTO
    );

    PropertySummaryResponseDTO archiveProperty(String principalEmail, UUID propertyId);
}
