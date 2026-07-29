package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.*;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberDto createMember(CreateMemberRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        String username = req.getUsername() != null ? req.getUsername().trim().toLowerCase() : null;
        if (username != null && !username.isEmpty()) {
            if (userRepository.existsByUsername(username)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
            }
        } else {
            String prefix = email.split("@")[0];
            username = prefix;
            if (userRepository.existsByUsername(username)) {
                username = prefix + UUID.randomUUID().toString().substring(0, 4);
            }
        }

        Room room = null;
        if (req.getRoomId() != null) {
            room = roomRepository.findById(req.getRoomId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
            if (room.getOccupied() >= room.getCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is already full");
            }
            room.setOccupied(room.getOccupied() + 1);
            roomRepository.save(room);
        }

        User member = User.builder()
                .name(req.getName())
                .email(email)
                .username(username)
                .phone(req.getPhone())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.PG_MEMBER)
                .active(true)
                .emergencyContact(req.getEmergencyContact())
                .address(req.getAddress())
                .checkInDate(req.getCheckInDate())
                .bedNumber(req.getBedNumber())
                .room(room)
                .build();

        return toDto(userRepository.save(member));
    }

    @Transactional(readOnly = true)
    public List<MemberDto> getAllMembers() {
        return userRepository.findAllByRole(Role.PG_MEMBER)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MemberDto getMemberById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return toDto(user);
    }

    @Transactional
    public MemberDto updateMember(UUID id, UpdateMemberRequest req) {
        User member = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (req.getName() != null) member.setName(req.getName());

        if (req.getEmail() != null && !req.getEmail().equalsIgnoreCase(member.getEmail())) {
            String cleanEmail = req.getEmail().toLowerCase().trim();
            if (userRepository.existsByEmail(cleanEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            member.setEmail(cleanEmail);
        }

        if (req.getUsername() != null && !req.getUsername().equalsIgnoreCase(member.getUsername())) {
            String cleanUsername = req.getUsername().toLowerCase().trim();
            if (userRepository.existsByUsername(cleanUsername)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
            }
            member.setUsername(cleanUsername);
        }

        if (req.getPhone() != null) member.setPhone(req.getPhone());
        if (req.getEmergencyContact() != null) member.setEmergencyContact(req.getEmergencyContact());
        if (req.getAddress() != null) member.setAddress(req.getAddress());
        if (req.getBedNumber() != null) member.setBedNumber(req.getBedNumber());
        if (req.getCheckInDate() != null) member.setCheckInDate(req.getCheckInDate());
        if (req.getActive() != null) member.setActive(req.getActive());

        if (req.getRoomId() != null) {
            Room newRoom = roomRepository.findById(req.getRoomId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
            if (member.getRoom() == null || !member.getRoom().getId().equals(newRoom.getId())) {
                if (member.getRoom() != null) {
                    Room oldRoom = member.getRoom();
                    oldRoom.setOccupied(Math.max(0, oldRoom.getOccupied() - 1));
                    roomRepository.save(oldRoom);
                }
                newRoom.setOccupied(newRoom.getOccupied() + 1);
                roomRepository.save(newRoom);
                member.setRoom(newRoom);
            }
        }

        return toDto(userRepository.save(member));
    }

    @Transactional
    public void assignRoom(UUID memberId, AssignRoomRequest req) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        user.setRoom(room);
        user.setBedNumber(req.getBedNumber());
        userRepository.save(user);
    }

    @Transactional
    public void deactivateMember(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (user.getRole() != Role.PG_MEMBER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target must be a member");
        }
        if (user.getRoom() != null) {
            Room room = user.getRoom();
            room.setOccupied(Math.max(0, room.getOccupied() - 1));
            roomRepository.save(room);
        }
        userRepository.delete(user);
    }

    @Transactional
    public MemberDto updateProfile(String email, UpdateProfileRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (req.getName() != null) user.setName(req.getName());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getEmergencyContact() != null) user.setEmergencyContact(req.getEmergencyContact());
        if (req.getAddress() != null) user.setAddress(req.getAddress());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void resetMemberPassword(UUID memberId, String newPassword) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public MemberDto toDto(User u) {
        return MemberDto.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .username(u.getUsername())
                .phone(u.getPhone())
                .active(u.isActive())
                .emergencyContact(u.getEmergencyContact())
                .address(u.getAddress())
                .bedNumber(u.getBedNumber())
                .checkInDate(u.getCheckInDate())
                .avatarUrl(u.getAvatarUrl())
                .roomNumber(u.getRoom() != null ? u.getRoom().getNumber() : null)
                .room(u.getRoom() != null ? RoomSummaryDto.builder()
                        .id(u.getRoom().getId())
                        .number(u.getRoom().getNumber())
                        .floor(u.getRoom().getFloor())
                        .sharing(u.getRoom().getSharing())
                        .build() : null)
                .createdAt(u.getCreatedAt())
                .build();
    }
}
