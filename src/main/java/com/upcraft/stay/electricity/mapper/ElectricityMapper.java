package com.upcraft.stay.electricity.mapper;

import java.util.Optional;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.upcraft.stay.electricity.dto.ElectricityBillResponseDTO;
import com.upcraft.stay.electricity.dto.MeterReadingResponseDTO;
import com.upcraft.stay.electricity.dto.MeterResponseDTO;
import com.upcraft.stay.electricity.entity.ElectricityBillEntity;
import com.upcraft.stay.electricity.entity.MeterEntity;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;
import com.upcraft.stay.storage.entity.StoredFileEntity;

/**
 * MapStruct mapper projecting meter, reading, and electricity bill entities to read-only API DTOs.
 */
@Mapper(componentModel = "spring")
public interface ElectricityMapper {

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "roomNumber", source = "room.number")
    @Mapping(target = "bedId", source = "bed.id")
    @Mapping(target = "bedCode", source = "bed.bedCode")
    MeterResponseDTO toMeterResponseDTO(MeterEntity meterEntity);

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "meterId", source = "meter.id")
    @Mapping(target = "meterNumber", source = "meter.meterNumber")
    @Mapping(target = "meterScope", source = "meter.meterScope")
    @Mapping(target = "splitMethod", source = "meter.splitMethod")
    @Mapping(target = "roomNumber", source = "meter.room.number")
    @Mapping(target = "bedCode", source = "meter.bed.bedCode")
    @Mapping(target = "photoFileId", source = "photoFile.id")
    @Mapping(target = "photoDownloadPath", expression = "java(resolvePhotoPath(readingEntity.getPhotoFile()))")
    @Mapping(target = "recordedByName", source = "recordedByUser.name")
    @Mapping(target = "approvedByName", source = "approvedByUser.name")
    MeterReadingResponseDTO toMeterReadingResponseDTO(MeterReadingEntity readingEntity);

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "propertyName", source = "property.name")
    @Mapping(target = "meterId", source = "meter.id")
    @Mapping(target = "meterNumber", source = "meter.meterNumber")
    @Mapping(target = "readingId", source = "reading.id")
    @Mapping(target = "tenancyId", source = "tenancy.id")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.fullName")
    @Mapping(target = "tenantPhone", source = "tenant.phone")
    @Mapping(target = "roomNumber", source = "room.number")
    @Mapping(target = "bedCode", source = "bed.bedCode")
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "invoiceNumber", source = "invoice.invoiceNumber")
    @Mapping(target = "readingDate", source = "reading.readingDate")
    @Mapping(target = "previousReading", source = "reading.previousValue")
    @Mapping(target = "currentReading", source = "reading.currentValue")
    @Mapping(target = "totalMeterUnits", source = "reading.unitsConsumed")
    @Mapping(target = "photoFileId", source = "reading.photoFile.id")
    @Mapping(target = "photoDownloadPath", expression = "java(resolvePhotoPath(billEntity.getReading().getPhotoFile()))")
    ElectricityBillResponseDTO toElectricityBillResponseDTO(ElectricityBillEntity billEntity);

    default String resolvePhotoPath(StoredFileEntity storedFileEntity) {
        return Optional.ofNullable(storedFileEntity)
                .map(StoredFileEntity::getId)
                .map(UUID::toString)
                .map(fileId -> "/api/v1/files/" + fileId + "/content")
                .orElse(null);
    }
}

