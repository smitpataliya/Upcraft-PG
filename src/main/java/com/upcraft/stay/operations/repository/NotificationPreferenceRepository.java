package com.upcraft.stay.operations.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.operations.entity.NotificationPreferenceEntity;

/**
 * Persistence access for user notification channel preferences.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {

    Optional<NotificationPreferenceEntity> findByUserId(UUID userId);

    List<NotificationPreferenceEntity> findAllByUserIdIn(Collection<UUID> userIds);
}

