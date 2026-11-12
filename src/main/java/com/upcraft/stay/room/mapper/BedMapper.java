package com.upcraft.stay.room.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.upcraft.stay.room.dto.BedAvailabilityResponseDTO;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.entity.BedEntity;

/**
 * MapStruct mapper for bed inventory and vacancy projections.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BedMapper {

    @Mapping(target = "propertyId", source = "bedEntity.property.id")
    @Mapping(target = "roomId", source = "bedEntity.room.id")
    @Mapping(target = "roomNumber", source = "bedEntity.room.number")
    @Mapping(target = "activeTenancyId", source = "activeTenancyId")
    @Mapping(target = "occupantName", source = "occupantName")
    BedResponseDTO toBedResponseDTO(BedEntity bedEntity, UUID activeTenancyId, String occupantName);

    @Mapping(target = "bedId", source = "id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "city", source = "property.city")
    @Mapping(target = "floorId", source = "room.floorEntity.id")
    @Mapping(target = "floorName", source = "room.floorEntity.name")
    @Mapping(target = "floorNumber", source = "room.floor")
    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "roomNumber", source = "room.number")
    @Mapping(target = "roomType", source = "room.roomType")
    @Mapping(target = "sharing", source = "room.sharing")
    @Mapping(target = "amenities", expression = "java(copyAmenities(bedEntity))")
    BedAvailabilityResponseDTO toBedAvailabilityResponseDTO(BedEntity bedEntity);

    default List<String> copyAmenities(BedEntity bedEntity) {
        return List.copyOf(bedEntity.getRoom().getAmenities());
    }
}
