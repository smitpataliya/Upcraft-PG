package com.upcraft.stay.room.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.room.dto.CreatePropertyRoomRequestDTO;
import com.upcraft.stay.room.dto.PropertyRoomResponseDTO;
import com.upcraft.stay.room.dto.UpdatePropertyRoomRequestDTO;

/**
 * Property-scoped room management service supporting floors, decimal rent defaults, and auto-bed generation.
 */
public interface PropertyRoomService {

    PageResponseDTO<PropertyRoomResponseDTO> listPropertyRooms(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PropertyRoomResponseDTO getPropertyRoom(String principalEmail, UUID propertyId, UUID roomId);

    PropertyRoomResponseDTO createPropertyRoom(
            String principalEmail,
            UUID propertyId,
            CreatePropertyRoomRequestDTO createRoomRequestDTO
    );

    PropertyRoomResponseDTO updatePropertyRoom(
            String principalEmail,
            UUID propertyId,
            UUID roomId,
            UpdatePropertyRoomRequestDTO updateRoomRequestDTO
    );

    void deletePropertyRoom(String principalEmail, UUID propertyId, UUID roomId);
}

