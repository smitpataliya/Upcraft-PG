package com.upcraft.stay.operations.service;

import java.util.Collection;
import java.util.UUID;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Dispatches in-app notifications and triggers asynchronous external channel delivery.
 */
public interface NotificationDispatchService {

    /**
     * Persists in-app notifications for active recipients and schedules async external channel delivery.
     *
     * @param organization  owning organization
     * @param property      optional property context
     * @param recipients    recipient users
     * @param eventType     domain event classification
     * @param title         notification title
     * @param message       notification body
     * @param referenceType optional linked entity type
     * @param referenceId   optional linked entity UUID
     */
    void dispatchToUsers(
            OrganizationEntity organization,
            PropertyEntity property,
            Collection<User> recipients,
            NotificationEventType eventType,
            String title,
            String message,
            String referenceType,
            UUID referenceId
    );
}

