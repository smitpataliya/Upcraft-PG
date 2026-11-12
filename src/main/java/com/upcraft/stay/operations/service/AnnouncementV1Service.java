package com.upcraft.stay.operations.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.AnnouncementReadResponseDTO;
import com.upcraft.stay.operations.dto.AnnouncementV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertAnnouncementV1RequestDTO;

/**
 * Multi-property announcements, scheduling, attachment, and idempotent read-tracking service.
 */
public interface AnnouncementV1Service {

    PageResponseDTO<AnnouncementV1ResponseDTO> listAnnouncements(
            String principalEmail,
            UUID propertyId,
            boolean activeOnly,
            int page,
            Integer size
    );

    AnnouncementV1ResponseDTO createAnnouncement(String principalEmail, UpsertAnnouncementV1RequestDTO request);

    AnnouncementV1ResponseDTO updateAnnouncement(
            String principalEmail,
            UUID announcementId,
            UpsertAnnouncementV1RequestDTO request
    );

    AnnouncementReadResponseDTO markRead(String principalEmail, UUID announcementId);

    void deleteAnnouncement(String principalEmail, UUID announcementId);
}

