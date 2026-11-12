package com.upcraft.stay.operations.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.domain.entity.Announcement;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.Meal;
import com.upcraft.stay.domain.entity.Notification;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.operations.dto.AnnouncementV1ResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintAttachmentResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintCommentResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintV1ResponseDTO;
import com.upcraft.stay.operations.dto.DocumentResponseDTO;
import com.upcraft.stay.operations.dto.ManagerTaskResponseDTO;
import com.upcraft.stay.operations.dto.MealV1ResponseDTO;
import com.upcraft.stay.operations.dto.NotificationDeliveryResponseDTO;
import com.upcraft.stay.operations.dto.NotificationPreferenceResponseDTO;
import com.upcraft.stay.operations.dto.NotificationV1ResponseDTO;
import com.upcraft.stay.operations.dto.PushTokenResponseDTO;
import com.upcraft.stay.operations.entity.ComplaintAttachmentEntity;
import com.upcraft.stay.operations.entity.ComplaintCommentEntity;
import com.upcraft.stay.operations.entity.DocumentEntity;
import com.upcraft.stay.operations.entity.ManagerTaskEntity;
import com.upcraft.stay.operations.entity.NotificationDeliveryEntity;
import com.upcraft.stay.operations.entity.NotificationPreferenceEntity;
import com.upcraft.stay.operations.entity.PushTokenEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Maps Milestone 5 operational entities to immutable API response records.
 */
@Component
public class OperationsMapper {

    public ComplaintV1ResponseDTO toComplaintResponse(Complaint complaint) {
        return new ComplaintV1ResponseDTO(
                complaint.getId(),
                Optional.ofNullable(complaint.getProperty()).map(PropertyEntity::getId).orElse(null),
                Optional.ofNullable(complaint.getProperty()).map(PropertyEntity::getName).orElse(null),
                Optional.ofNullable(complaint.getTenant()).map(TenantEntity::getId).orElse(null),
                Optional.ofNullable(complaint.getTenant()).map(TenantEntity::getFullName).orElse(complaint.getMember().getName()),
                Optional.ofNullable(complaint.getRoom()).map(Room::getNumber).orElse(null),
                Optional.ofNullable(complaint.getBed()).map(BedEntity::getBedCode).orElse(null),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCategory(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getPreferredContactTime(),
                complaint.getResolutionSummary(),
                complaint.getMember().getId(),
                complaint.getMember().getName(),
                Optional.ofNullable(complaint.getAssignedToUser()).map(User::getId).orElse(null),
                Optional.ofNullable(complaint.getAssignedToUser()).map(User::getName).orElse(null),
                complaint.getAcknowledgedAt(),
                complaint.getResolvedAt(),
                complaint.getClosedAt(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt()
        );
    }

    public ComplaintAttachmentResponseDTO toAttachmentResponse(ComplaintAttachmentEntity attachment) {
        StoredFileEntity file = attachment.getStoredFile();
        return new ComplaintAttachmentResponseDTO(
                attachment.getId(),
                file.getId(),
                Optional.ofNullable(attachment.getComment()).map(ComplaintCommentEntity::getId).orElse(null),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getFileSizeBytes(),
                "/api/v1/files/" + file.getId() + "/content",
                attachment.getCreatedAt()
        );
    }

    public ComplaintCommentResponseDTO toCommentResponse(
            ComplaintCommentEntity comment,
            List<ComplaintAttachmentResponseDTO> attachments
    ) {
        return new ComplaintCommentResponseDTO(
                comment.getId(),
                comment.getAuthorUser().getId(),
                comment.getAuthorUser().getName(),
                comment.getAuthorUser().getRole().name(),
                comment.getMessage(),
                comment.isInternal(),
                attachments,
                comment.getCreatedAt()
        );
    }

    public AnnouncementV1ResponseDTO toAnnouncementResponse(
            Announcement announcement,
            boolean readByCurrentUser,
            long readCount
    ) {
        UUID fileId = Optional.ofNullable(announcement.getAttachmentFile()).map(StoredFileEntity::getId).orElse(null);
        String attachmentUrl = Optional.ofNullable(fileId).map(id -> "/api/v1/files/" + id + "/content").orElse(null);
        return new AnnouncementV1ResponseDTO(
                announcement.getId(),
                Optional.ofNullable(announcement.getProperty()).map(PropertyEntity::getId).orElse(null),
                Optional.ofNullable(announcement.getProperty()).map(PropertyEntity::getName).orElse(null),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCategory(),
                announcement.getAudienceType(),
                announcement.isPinned(),
                announcement.getPublishAt(),
                announcement.getExpiresAt(),
                fileId,
                attachmentUrl,
                announcement.getAuthor(),
                readByCurrentUser,
                readCount,
                announcement.getCreatedAt()
        );
    }

    public MealV1ResponseDTO toMealResponse(Meal meal) {
        return new MealV1ResponseDTO(
                meal.getId(),
                Optional.ofNullable(meal.getProperty()).map(PropertyEntity::getId).orElse(null),
                Optional.ofNullable(meal.getProperty()).map(PropertyEntity::getName).orElse(null),
                meal.getName(),
                meal.getDescription(),
                meal.getImageUrl(),
                meal.getType(),
                meal.getServingTime(),
                meal.isVeg(),
                meal.isAvailable(),
                meal.isPublished(),
                meal.getDateScheduled(),
                meal.getDayOfWeek(),
                meal.getCreatedAt()
        );
    }

    public DocumentResponseDTO toDocumentResponse(DocumentEntity document) {
        StoredFileEntity file = document.getStoredFile();
        return new DocumentResponseDTO(
                document.getId(),
                Optional.ofNullable(document.getProperty()).map(PropertyEntity::getId).orElse(null),
                Optional.ofNullable(document.getProperty()).map(PropertyEntity::getName).orElse(null),
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getFileSizeBytes(),
                "/api/v1/files/" + file.getId() + "/content",
                document.getScopeType(),
                document.getDocumentCategory(),
                document.getTitle(),
                document.getDescription(),
                Optional.ofNullable(document.getTenant()).map(TenantEntity::getId).orElse(null),
                Optional.ofNullable(document.getTenant()).map(TenantEntity::getFullName).orElse(null),
                Optional.ofNullable(document.getTenancy()).map(TenancyEntity::getId).orElse(null),
                Optional.ofNullable(document.getInvoice()).map(InvoiceEntity::getId).orElse(null),
                Optional.ofNullable(document.getPayment()).map(PaymentEntity::getId).orElse(null),
                document.isSharedWithTenant(),
                document.getUploadedByUser().getName(),
                document.getCreatedAt()
        );
    }

    public ManagerTaskResponseDTO toManagerTaskResponse(ManagerTaskEntity task) {
        return new ManagerTaskResponseDTO(
                task.getId(),
                task.getProperty().getId(),
                task.getProperty().getName(),
                Optional.ofNullable(task.getAssignedToUser()).map(User::getId).orElse(null),
                Optional.ofNullable(task.getAssignedToUser()).map(User::getName).orElse(null),
                task.getCreatedByUser().getId(),
                task.getCreatedByUser().getName(),
                Optional.ofNullable(task.getComplaint()).map(Complaint::getId).orElse(null),
                Optional.ofNullable(task.getComplaint()).map(Complaint::getTitle).orElse(null),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getTaskStatus(),
                task.getDueAt(),
                task.getCompletedAt(),
                task.getCompletionNotes(),
                task.getCreatedAt()
        );
    }

    public NotificationV1ResponseDTO toNotificationResponse(Notification notification) {
        return new NotificationV1ResponseDTO(
                notification.getId(),
                Optional.ofNullable(notification.getProperty()).map(PropertyEntity::getId).orElse(null),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getEventType(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    public PushTokenResponseDTO toPushTokenResponse(PushTokenEntity token) {
        return new PushTokenResponseDTO(
                token.getId(),
                token.getToken(),
                token.getPlatform(),
                token.getDeviceName(),
                token.isActive(),
                token.getLastSeenAt()
        );
    }

    public NotificationPreferenceResponseDTO toPreferenceResponse(NotificationPreferenceEntity preference) {
        return new NotificationPreferenceResponseDTO(
                preference.isInAppEnabled(),
                preference.isPushEnabled(),
                preference.isEmailEnabled(),
                preference.isSmsEnabled(),
                preference.isWhatsappEnabled(),
                preference.getDigestQuietHours()
        );
    }

    public NotificationDeliveryResponseDTO toDeliveryResponse(NotificationDeliveryEntity delivery) {
        return new NotificationDeliveryResponseDTO(
                delivery.getId(),
                delivery.getChannel(),
                delivery.getDeliveryStatus(),
                delivery.getProviderName(),
                delivery.getProviderMessageId(),
                delivery.getErrorMessage(),
                delivery.getAttemptedAt()
        );
    }
}
