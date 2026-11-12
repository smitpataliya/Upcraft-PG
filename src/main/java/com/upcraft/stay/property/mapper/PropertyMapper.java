package com.upcraft.stay.property.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.property.dto.CreatePropertyRequestDTO;
import com.upcraft.stay.property.dto.PropertySummaryResponseDTO;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * MapStruct mapper for PG property entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyMapper {

    @Mapping(target = "organizationId", source = "organization.id")
    PropertySummaryResponseDTO toPropertySummaryResponseDTO(PropertyEntity propertyEntity);

    PropertyEntity toPropertyEntity(CreatePropertyRequestDTO createPropertyRequestDTO);
}

