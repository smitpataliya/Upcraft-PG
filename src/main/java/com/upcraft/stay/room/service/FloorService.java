package com.upcraft.stay.room.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.room.dto.CreateFloorRequestDTO;
import com.upcraft.stay.room.dto.FloorResponseDTO;
import com.upcraft.stay.room.dto.UpdateFloorRequestDTO;

/**
 * Property floor lifecycle management service.
 */
public interface FloorService {

    PageResponseDTO<FloorResponseDTO> listFloors(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    FloorResponseDTO createFloor(String principalEmail, UUID propertyId, CreateFloorRequestDTO createFloorRequestDTO);

    FloorResponseDTO updateFloor(
            String principalEmail,
            UUID propertyId,
            UUID floorId,
            UpdateFloorRequestDTO updateFloorRequestDTO
    );

    void deleteFloor(String principalEmail, UUID propertyId, UUID floorId);
}

