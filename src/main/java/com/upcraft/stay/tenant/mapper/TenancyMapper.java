package com.upcraft.stay.tenant.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * MapStruct mapper for tenancy allocations and settlements.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenancyMapper {

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.fullName")
    @Mapping(target = "tenantPhone", source = "tenant.phone")
    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "roomNumber", source = "room.number")
    @Mapping(target = "bedId", source = "bed.id")
    @Mapping(target = "bedCode", source = "bed.bedCode")
    TenancyResponseDTO toTenancyResponseDTO(TenancyEntity tenancyEntity);
}

