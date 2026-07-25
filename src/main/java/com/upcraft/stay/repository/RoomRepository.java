package com.upcraft.stay.repository;

import com.upcraft.stay.domain.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByNumber(String number);
    Optional<Room> findByNumber(String number);
}
