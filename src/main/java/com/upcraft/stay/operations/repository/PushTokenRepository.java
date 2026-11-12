package com.upcraft.stay.operations.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.operations.entity.PushTokenEntity;

/**
 * Persistence access for mobile push tokens.
 */
public interface PushTokenRepository extends JpaRepository<PushTokenEntity, UUID> {

    Optional<PushTokenEntity> findByToken(String token);

    List<PushTokenEntity> findAllByUserIdAndActiveTrue(UUID userId);

    List<PushTokenEntity> findAllByUserIdInAndActiveTrue(Collection<UUID> userIds);
}

