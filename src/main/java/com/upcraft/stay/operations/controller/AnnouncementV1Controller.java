package com.upcraft.stay.operations.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.AnnouncementReadResponseDTO;
import com.upcraft.stay.operations.dto.AnnouncementV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertAnnouncementV1RequestDTO;
import com.upcraft.stay.operations.service.AnnouncementV1Service;

/**
 * Multi-property announcements, attachment, and read-tracking controller.
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementV1Controller {

    private final AnnouncementV1Service announcementV1Service;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<AnnouncementV1ResponseDTO>>> listAnnouncements(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Announcements loaded",
                announcementV1Service.listAnnouncements(authentication.getName(), propertyId, activeOnly, page, size),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<AnnouncementV1ResponseDTO>> createAnnouncement(
            Authentication authentication,
            @Valid @RequestBody UpsertAnnouncementV1RequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Announcement published",
                announcementV1Service.createAnnouncement(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PutMapping("/{announcementId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<AnnouncementV1ResponseDTO>> updateAnnouncement(
            Authentication authentication,
            @PathVariable UUID announcementId,
            @Valid @RequestBody UpsertAnnouncementV1RequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Announcement updated",
                announcementV1Service.updateAnnouncement(authentication.getName(), announcementId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping("/{announcementId}/read")
    public ResponseEntity<ApiResponseDTO<AnnouncementReadResponseDTO>> markRead(
            Authentication authentication,
            @PathVariable UUID announcementId
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Announcement marked read",
                announcementV1Service.markRead(authentication.getName(), announcementId),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @DeleteMapping("/{announcementId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<Void> deleteAnnouncement(
            Authentication authentication,
            @PathVariable UUID announcementId
    ) {
        announcementV1Service.deleteAnnouncement(authentication.getName(), announcementId);
        return ResponseEntity.noContent().build();
    }
}

