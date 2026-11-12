package com.upcraft.stay.room.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.room.dto.FloorResponseDTO;
import com.upcraft.stay.room.entity.FloorEntity;

/**
 * MapStruct mapper for floor entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FloorMapper {

    @Mapping(target = "propertyId", source = "floorEntity.property.id")
    @Mapping(target = "roomCount", source = "roomCount")
    FloorResponseDTO toFloorResponseDTO(FloorEntity floorEntity, long roomCount);
}

