package com.upcraft.stay.storage.service.impl;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.FileCategory;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.electricity.repository.ElectricityBillRepository;
import com.upcraft.stay.operations.repository.ComplaintAttachmentRepository;
import com.upcraft.stay.operations.repository.DocumentRepository;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.AnnouncementRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.storage.dto.StoredFileResponseDTO;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.storage.repository.StoredFileRepository;
import com.upcraft.stay.storage.service.FileStorageBoundary;
import com.upcraft.stay.storage.service.StoredFileService;

/**
 * Validated private file upload and authorization-guarded download service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoredFileServiceImpl implements StoredFileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf"
    );
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    private final AccessGuardService accessGuardService;
    private final StoredFileRepository storedFileRepository;
    private final ElectricityBillRepository electricityBillRepository;
    private final ComplaintAttachmentRepository complaintAttachmentRepository;
    private final DocumentRepository documentRepository;
    private final AnnouncementRepository announcementRepository;
    private final FileStorageBoundary fileStorageBoundary;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public StoredFileResponseDTO uploadPrivateFile(
            String principalEmail,
            UUID propertyId,
            FileCategory category,
            MultipartFile multipartFile
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        PropertyEntity property = resolveUploadProperty(currentUser, propertyId, category);

        validateUpload(multipartFile);
        String contentType = multipartFile.getContentType().trim().toLowerCase(Locale.ROOT);
        String cleanFilename = Optional.ofNullable(multipartFile.getOriginalFilename())
                .filter(StringUtils::isNotBlank)
                .map(name -> name.replaceAll("[^a-zA-Z0-9._-]", "_"))
                .orElse("attachment." + EXTENSION_BY_TYPE.getOrDefault(contentType, "bin"));

        UUID fileId = UUID.randomUUID();
        String storageKey = String.format("%s/%s/%s.%s",
                organization.getId(), category.name().toLowerCase(Locale.ROOT),
                fileId, EXTENSION_BY_TYPE.getOrDefault(contentType, "bin"));
        String checksum = fileStorageBoundary.save(storageKey, multipartFile);

        StoredFileEntity entity = new StoredFileEntity();
        entity.setOrganization(organization);
        entity.setProperty(property);
        entity.setUploadedByUser(currentUser);
        entity.setFileCategory(category);
        entity.setOriginalFilename(StringUtils.left(cleanFilename, 255));
        entity.setContentType(contentType);
        entity.setFileSizeBytes(multipartFile.getSize());
        entity.setStorageKey(storageKey);
        entity.setSha256Checksum(checksum);

        StoredFileEntity saved = storedFileRepository.save(entity);
        log.info("Private file stored. fileId={}, category={}, sizeBytes={}",
                saved.getId(), category, saved.getFileSizeBytes());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileDownload loadAuthorizedFile(String principalEmail, UUID fileId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        StoredFileEntity storedFile = storedFileRepository.findWithDetailsById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Stored file not found"));
        enforceReadAuthorization(currentUser, storedFile);
        return new StoredFileDownload(
                storedFile.getOriginalFilename(),
                storedFile.getContentType(),
                fileStorageBoundary.loadAsResource(storedFile.getStorageKey())
        );
    }

    private PropertyEntity resolveUploadProperty(User currentUser, UUID propertyId, FileCategory category) {
        if (currentUser.getRole() == Role.PG_MEMBER) {
            if (category == FileCategory.METER_PHOTO || category == FileCategory.PROPERTY_DOCUMENT) {
                throw new ForbiddenException("Residents cannot upload meter photos or property documents");
            }
            return Optional.ofNullable(propertyId)
                    .map(targetPropId -> {
                        PropertyEntity placeholder = new PropertyEntity();
                        placeholder.setId(targetPropId);
                        return placeholder;
                    })
                    .orElse(null);
        }
        String ability = category == FileCategory.METER_PHOTO ? "METERS" : "READ";
        return Optional.ofNullable(propertyId)
                .map(targetPropId -> accessGuardService.requirePropertyManagementAccess(currentUser, targetPropId, ability))
                .orElse(null);
    }

    private void validateUpload(MultipartFile multipartFile) {
        if (Optional.ofNullable(multipartFile).isEmpty() || multipartFile.isEmpty()) {
            throw new ValidationException("Uploaded file must not be empty");
        }
        long maxBytes = appProperties.getStorage().getMaxFileBytes();
        if (multipartFile.getSize() > maxBytes) {
            throw new ValidationException(String.format("File exceeds maximum allowed size of %d bytes", maxBytes));
        }
        String rawContentType = Optional.ofNullable(multipartFile.getContentType())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .orElse("");
        if (!ALLOWED_CONTENT_TYPES.contains(rawContentType)) {
            throw new ValidationException("Only JPEG, PNG, WebP images and PDF files are permitted");
        }
    }

    private void enforceReadAuthorization(User currentUser, StoredFileEntity storedFile) {
        OrganizationEntity userOrg = accessGuardService.resolveUserOrganization(currentUser);
        if (!userOrg.getId().equals(storedFile.getOrganization().getId())) {
            throw new ForbiddenException("File is outside your organization scope");
        }
        if (currentUser.getRole() == Role.PG_OWNER) {
            return;
        }
        if (currentUser.getRole() == Role.MANAGER) {
            Optional.ofNullable(storedFile.getProperty())
                    .ifPresent(prop -> accessGuardService.requirePropertyManagementAccess(currentUser, prop.getId(), "READ"));
            return;
        }
        boolean ownUpload = storedFile.getUploadedByUser().getId().equals(currentUser.getId());
        String normalizedEmail = currentUser.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean residentBilledMeterPhoto = electricityBillRepository.isReadingPhotoAccessibleToResident(
                storedFile.getId(), ElectricityBillStatus.CANCELLED, currentUser.getId(),
                userOrg.getId(), normalizedEmail);
        boolean residentComplaintPhoto = complaintAttachmentRepository.isComplaintAttachmentAccessibleToMember(
                storedFile.getId(), currentUser.getId());
        boolean residentSharedDocument = documentRepository.isDocumentFileAccessibleToResident(
                storedFile.getId(), currentUser.getId(), userOrg.getId(), normalizedEmail);
        boolean orgAnnouncementAttachment = announcementRepository.existsByAttachmentFileIdAndOrganizationId(
                storedFile.getId(), userOrg.getId());
        if (!ownUpload && !residentBilledMeterPhoto && !residentComplaintPhoto
                && !residentSharedDocument && !orgAnnouncementAttachment) {
            throw new ForbiddenException("You are not authorized to access this private file");
        }
    }

    private StoredFileResponseDTO mapToResponse(StoredFileEntity entity) {
        return new StoredFileResponseDTO(
                entity.getId(),
                Optional.ofNullable(entity.getProperty()).map(PropertyEntity::getId).orElse(null),
                entity.getFileCategory(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getFileSizeBytes(),
                "/api/v1/files/" + entity.getId() + "/content",
                entity.getUploadedByUser().getName(),
                entity.getCreatedAt()
        );
    }
}

