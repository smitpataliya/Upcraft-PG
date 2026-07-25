package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.*;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomDto createRoom(CreateRoomRequest req) {
        if (roomRepository.existsByNumber(req.getNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists");
        }
        Room room = Room.builder()
                .number(req.getNumber())
                .floor(req.getFloor())
                .sharing(req.getSharing())
                .capacity(req.getCapacity())
                .occupied(0)
                .price(req.getPrice())
                .amenities(req.getAmenities() != null ? req.getAmenities() : new ArrayList<>())
                .build();
        return toDto(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomDto> getAllRooms() {
        return roomRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public RoomDto updateRoom(UUID id, CreateRoomRequest req) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        room.setNumber(req.getNumber());
        room.setFloor(req.getFloor());
        room.setSharing(req.getSharing());
        room.setCapacity(req.getCapacity());
        room.setPrice(req.getPrice());
        if (req.getAmenities() != null) room.setAmenities(req.getAmenities());
        return toDto(roomRepository.save(room));
    }

    @Transactional
    public void deleteRoom(UUID id) {
        roomRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        roomRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public MyRoomDto getMyRoom(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRoom() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No room assigned yet");
        }
        Room room = user.getRoom();
        List<RoommateDto> roommates = userRepository.findAllByRole(Role.PG_MEMBER).stream()
                .filter(m -> m.getRoom() != null && m.getRoom().getId().equals(room.getId()) && !m.getId().equals(user.getId()))
                .map(m -> RoommateDto.builder().id(m.getId()).name(m.getName()).bedNumber(m.getBedNumber()).build())
                .collect(Collectors.toList());

        return MyRoomDto.builder()
                .room(toDto(room))
                .bedNumber(user.getBedNumber())
                .checkInDate(user.getCheckInDate())
                .roommates(roommates)
                .build();
    }

    public RoomDto toDto(Room r) {
        return RoomDto.builder()
                .id(r.getId())
                .number(r.getNumber())
                .floor(r.getFloor())
                .sharing(r.getSharing())
                .capacity(r.getCapacity())
                .occupied(r.getOccupied())
                .price(r.getPrice())
                .amenities(r.getAmenities())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
