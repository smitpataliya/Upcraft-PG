package com.upcraft.stay.operations.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Announcement;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.operations.dto.AnnouncementReadResponseDTO;
import com.upcraft.stay.operations.dto.AnnouncementV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertAnnouncementV1RequestDTO;
import com.upcraft.stay.operations.entity.AnnouncementReadEntity;
import com.upcraft.stay.operations.enums.AnnouncementAudienceType;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.repository.AnnouncementReadRepository;
import com.upcraft.stay.operations.service.AnnouncementV1Service;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.AnnouncementRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.storage.repository.StoredFileRepository;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Implements org/property-scoped announcements, read tracking, and notification fan-out.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementV1ServiceImpl implements AnnouncementV1Service {

    private final AccessGuardService accessGuardService;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final StoredFileRepository storedFileRepository;
    private final TenancyRepository tenancyRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AnnouncementV1ResponseDTO> listAnnouncements(
            String principalEmail,
            UUID propertyId,
            boolean activeOnly,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        Pageable basePageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", Set.of("createdAt"), "createdAt", appProperties
        );
        Pageable pageable = PageRequest.of(
                basePageable.getPageNumber(),
                basePageable.getPageSize(),
                Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"))
        );

        List<UUID> scopedPropertyIds = resolveVisiblePropertyIds(currentUser, organization, propertyId);
        Specification<Announcement> specification = buildSpecification(
                organization.getId(), scopedPropertyIds, currentUser.getRole() == Role.PG_MEMBER, activeOnly
        );
        Page<Announcement> pageResult = announcementRepository.findAll(specification, pageable);
        List<UUID> announcementIds = pageResult.getContent().stream().map(Announcement::getId).toList();
        if (announcementIds.isEmpty()) {
            return PageResponseDTO.from(pageResult.map(item -> operationsMapper.toAnnouncementResponse(item, false, 0L)));
        }

        Set<UUID> readIds = announcementReadRepository.findAllByAnnouncementIdInAndUserId(announcementIds, currentUser.getId())
                .stream()
                .map(read -> read.getAnnouncement().getId())
                .collect(Collectors.toSet());
        Map<UUID, Long> readCounts = announcementReadRepository.countReadsByAnnouncementIds(announcementIds)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return PageResponseDTO.from(pageResult.map(item -> operationsMapper.toAnnouncementResponse(
                item,
                readIds.contains(item.getId()),
                readCounts.getOrDefault(item.getId(), 0L)
        )));
    }

    @Override
    @Transactional
    public AnnouncementV1ResponseDTO createAnnouncement(
            String principalEmail,
            UpsertAnnouncementV1RequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        PropertyEntity property = resolveWriteProperty(currentUser, request.propertyId(), request.audienceType());
        validatePublishWindow(request.publishAt(), request.expiresAt());
        StoredFileEntity attachmentFile = resolveAttachment(organization, request.attachmentFileId());

        Announcement announcement = Announcement.builder()
                .organization(organization)
                .property(property)
                .title(request.title().trim())
                .body(request.body().trim())
                .category(Optional.ofNullable(request.category()).filter(StringUtils::isNotBlank).map(String::trim).orElse("GENERAL"))
                .audienceType(request.audienceType())
                .pinned(request.pinned())
                .publishAt(Optional.ofNullable(request.publishAt()).orElseGet(Instant::now))
                .expiresAt(request.expiresAt())
                .attachmentFile(attachmentFile)
                .author(currentUser.getName())
                .build();
        Announcement saved = announcementRepository.save(announcement);

        notifyAudience(organization, property, saved);
        return operationsMapper.toAnnouncementResponse(saved, false, 0L);
    }

    @Override
    @Transactional
    public AnnouncementV1ResponseDTO updateAnnouncement(
            String principalEmail,
            UUID announcementId,
            UpsertAnnouncementV1RequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Announcement announcement = loadAuthorizedForWrite(currentUser, announcementId);
        PropertyEntity property = resolveWriteProperty(currentUser, request.propertyId(), request.audienceType());
        validatePublishWindow(request.publishAt(), request.expiresAt());
        StoredFileEntity attachmentFile = resolveAttachment(announcement.getOrganization(), request.attachmentFileId());

        announcement.setProperty(property);
        announcement.setTitle(request.title().trim());
        announcement.setBody(request.body().trim());
        announcement.setCategory(Optional.ofNullable(request.category()).filter(StringUtils::isNotBlank).map(String::trim).orElse("GENERAL"));
        announcement.setAudienceType(request.audienceType());
        announcement.setPinned(request.pinned());
        announcement.setPublishAt(Optional.ofNullable(request.publishAt()).orElse(announcement.getPublishAt()));
        announcement.setExpiresAt(request.expiresAt());
        announcement.setAttachmentFile(attachmentFile);
        announcement.setUpdatedAt(LocalDateTime.now());
        announcement.setUpdatedBy(currentUser.getEmail());

        Announcement saved = announcementRepository.save(announcement);
        long readCount = announcementReadRepository.countReadsByAnnouncementIds(List.of(saved.getId()))
                .stream().findFirst().map(row -> (Long) row[1]).orElse(0L);
        boolean readByMe = announcementReadRepository.findByAnnouncementIdAndUserId(saved.getId(), currentUser.getId()).isPresent();
        return operationsMapper.toAnnouncementResponse(saved, readByMe, readCount);
    }

    @Override
    @Transactional
    public AnnouncementReadResponseDTO markRead(String principalEmail, UUID announcementId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        Announcement announcement = announcementRepository.findWithDetailsById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
        if (Optional.ofNullable(announcement.getOrganization()).isEmpty()
                || !announcement.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Announcement is outside your organization scope");
        }

        Optional<AnnouncementReadEntity> existing = announcementReadRepository
                .findByAnnouncementIdAndUserId(announcementId, currentUser.getId());
        if (existing.isPresent()) {
            return new AnnouncementReadResponseDTO(announcementId, existing.get().getReadAt(), true);
        }

        Instant now = Instant.now();
        AnnouncementReadEntity readEntity = new AnnouncementReadEntity();
        readEntity.setOrganization(organization);
        readEntity.setAnnouncement(announcement);
        readEntity.setUser(currentUser);
        readEntity.setReadAt(now);
        try {
            announcementReadRepository.save(readEntity);
            return new AnnouncementReadResponseDTO(announcementId, now, false);
        } catch (DataIntegrityViolationException conflict) {
            return new AnnouncementReadResponseDTO(announcementId, now, true);
        }
    }

    @Override
    @Transactional
    public void deleteAnnouncement(String principalEmail, UUID announcementId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Announcement announcement = loadAuthorizedForWrite(currentUser, announcementId);
        announcement.setRecordStatus(AppConstants.STATUS_DELETED);
        announcement.setDeletedAt(LocalDateTime.now());
        announcement.setDeletedBy(currentUser.getEmail());
        announcementRepository.save(announcement);
    }

    private Announcement loadAuthorizedForWrite(User currentUser, UUID announcementId) {
        Announcement announcement = announcementRepository.findWithDetailsById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        if (Optional.ofNullable(announcement.getOrganization()).isEmpty()
                || !announcement.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Announcement is outside your organization scope");
        }
        if (Optional.ofNullable(announcement.getProperty()).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(currentUser, announcement.getProperty().getId(), "NOTICES");
        } else if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can modify organization-wide notices");
        }
        return announcement;
    }

    private PropertyEntity resolveWriteProperty(
            User currentUser,
            UUID propertyId,
            AnnouncementAudienceType audienceType
    ) {
        if (audienceType == AnnouncementAudienceType.SELECTED_PROPERTY && Optional.ofNullable(propertyId).isEmpty()) {
            throw new ValidationException("Property ID is required for SELECTED_PROPERTY announcements");
        }
        if (Optional.ofNullable(propertyId).isEmpty()) {
            if (currentUser.getRole() != Role.PG_OWNER) {
                throw new ForbiddenException("Managers must select an assigned property");
            }
            return null;
        }
        return accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "NOTICES");
    }

    private StoredFileEntity resolveAttachment(OrganizationEntity organization, UUID fileId) {
        if (Optional.ofNullable(fileId).isEmpty()) {
            return null;
        }
        StoredFileEntity file = storedFileRepository.findWithDetailsById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment file not found"));
        if (!file.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Attachment belongs to another organization");
        }
        return file;
    }

    private void validatePublishWindow(Instant publishAt, Instant expiresAt) {
        if (Optional.ofNullable(publishAt).isPresent()
                && Optional.ofNullable(expiresAt).isPresent()
                && expiresAt.isBefore(publishAt)) {
            throw new ValidationException("Expiry timestamp cannot be before publish timestamp");
        }
    }

    private List<UUID> resolveVisiblePropertyIds(User currentUser, OrganizationEntity organization, UUID propertyId) {
        if (currentUser.getRole() == Role.PG_MEMBER) {
            return tenancyRepository.findSelfResidencyTenancies(
                            currentUser.getId(), organization.getId(), currentUser.getEmail().trim().toLowerCase(Locale.ROOT)
                    ).stream()
                    .map(tenancy -> tenancy.getProperty().getId())
                    .distinct()
                    .toList();
        }
        return accessGuardService.resolveAccessiblePropertyIds(currentUser, Optional.ofNullable(propertyId));
    }

    private Specification<Announcement> buildSpecification(
            UUID organizationId,
            List<UUID> scopedPropertyIds,
            boolean memberView,
            boolean activeOnly
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("organization").get("id"), organizationId));
            if (scopedPropertyIds.isEmpty()) {
                predicates.add(criteriaBuilder.isNull(root.get("property")));
            } else {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("property")),
                        root.get("property").get("id").in(scopedPropertyIds)
                ));
            }
            if (memberView) {
                predicates.add(criteriaBuilder.notEqual(root.get("audienceType"), AnnouncementAudienceType.MANAGERS_ONLY));
            }
            if (activeOnly || memberView) {
                Instant now = Instant.now();
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("publishAt")),
                        criteriaBuilder.lessThanOrEqualTo(root.get("publishAt"), now)
                ));
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("expiresAt")),
                        criteriaBuilder.greaterThan(root.get("expiresAt"), now)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void notifyAudience(OrganizationEntity organization, PropertyEntity property, Announcement announcement) {
        List<User> recipients;
        if (announcement.getAudienceType() == AnnouncementAudienceType.MANAGERS_ONLY) {
            recipients = managerPropertyAssignmentRepository.findAllByOrganizationId(organization.getId(), Pageable.unpaged())
                    .stream()
                    .filter(assignment -> assignment.getStatus() == AppConstants.STATUS_ACTIVE)
                    .filter(assignment -> Optional.ofNullable(property).isEmpty()
                            || assignment.getProperty().getId().equals(property.getId()))
                    .map(assignment -> assignment.getManagerUser())
                    .toList();
        } else if (Optional.ofNullable(property).isPresent()) {
            recipients = tenancyRepository.findAccessibleTenancies(
                            List.of(property.getId()), false, null, false, null, Pageable.unpaged()
                    ).stream()
                    .map(TenancyEntity::getTenant)
                    .map(tenant -> tenant.getUser())
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            recipients = organizationMemberRepository.findAllByOrganizationId(organization.getId()).stream()
                    .map(OrganizationMemberEntity::getUser)
                    .toList();
        }
        notificationDispatchService.dispatchToUsers(
                organization, property, recipients,
                NotificationEventType.ANNOUNCEMENT_PUBLISHED,
                "Notice: " + announcement.getTitle(),
                StringUtils.left(announcement.getBody(), 240),
                "ANNOUNCEMENT", announcement.getId()
        );
    }
}
