package com.upcraft.stay.operations.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.operations.entity.NotificationDeliveryEntity;

/**
 * Persistence access for outbound notification provider delivery records.
 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    Page<NotificationDeliveryEntity> findAllByOrganizationIdOrderByAttemptedAtDesc(UUID organizationId, Pageable pageable);
}

