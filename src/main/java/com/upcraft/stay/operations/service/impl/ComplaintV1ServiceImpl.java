package com.upcraft.stay.operations.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.ComplaintTimeline;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.ComplaintCategory;
import com.upcraft.stay.domain.enums.ComplaintPriority;
import com.upcraft.stay.domain.enums.ComplaintStatus;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.operations.dto.AssignComplaintRequestDTO;
import com.upcraft.stay.operations.dto.ComplaintAttachmentResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintCommentResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintDetailV1ResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintV1ResponseDTO;
import com.upcraft.stay.operations.dto.CreateComplaintCommentRequestDTO;
import com.upcraft.stay.operations.dto.CreateComplaintV1RequestDTO;
import com.upcraft.stay.operations.dto.UpdateComplaintStatusV1RequestDTO;
import com.upcraft.stay.operations.entity.ComplaintAttachmentEntity;
import com.upcraft.stay.operations.entity.ComplaintCommentEntity;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.repository.ComplaintAttachmentRepository;
import com.upcraft.stay.operations.repository.ComplaintCommentRepository;
import com.upcraft.stay.operations.service.ComplaintV1Service;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.repository.ManagerPropertyAssignmentRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.storage.repository.StoredFileRepository;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Implements multi-property complaints, status transitions, comments, attachments, and notifications.
 */
@Service
@RequiredArgsConstructor
public class ComplaintV1ServiceImpl implements ComplaintV1Service {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "priority", "status");
    private static final Set<ComplaintStatus> REOPENABLE_STATUSES = Set.of(
            ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED
    );

    private final AccessGuardService accessGuardService;
    private final ComplaintRepository complaintRepository;
    private final ComplaintCommentRepository complaintCommentRepository;
    private final ComplaintAttachmentRepository complaintAttachmentRepository;
    private final TenancyRepository tenancyRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final ManagerPropertyAssignmentRepository managerPropertyAssignmentRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ComplaintV1ResponseDTO> listComplaints(
            String principalEmail,
            UUID propertyId,
            ComplaintStatus status,
            ComplaintCategory category,
            ComplaintPriority priority,
            UUID assignedToUserId,
            String search,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        if (propertyIds.isEmpty()) {
            return PageResponseDTO.from(Page.empty());
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "createdAt", appProperties
        );
        Specification<Complaint> specification = buildSpecification(
                propertyIds, status, category, priority, assignedToUserId, search
        );
        return PageResponseDTO.from(
                complaintRepository.findAll(specification, pageable).map(operationsMapper::toComplaintResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ComplaintV1ResponseDTO> listMyComplaints(
            String principalEmail,
            int page,
            Integer size
    ) {
        User member = accessGuardService.loadActiveUser(principalEmail);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", SORT_FIELDS, "createdAt", appProperties
        );
        return PageResponseDTO.from(
                complaintRepository.findAllByMemberId(member.getId(), pageable).map(operationsMapper::toComplaintResponse)
        );
    }

    @Override
    @Transactional
    public ComplaintV1ResponseDTO createComplaint(String principalEmail, CreateComplaintV1RequestDTO request) {
        User member = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(member);
        TenancyEntity residency = resolveResidentTenancy(member, organization, request.propertyId());

        Complaint complaint = Complaint.builder()
                .organization(organization)
                .property(Optional.ofNullable(residency).map(TenancyEntity::getProperty).orElse(null))
                .tenant(Optional.ofNullable(residency).map(TenancyEntity::getTenant).orElse(null))
                .tenancy(residency)
                .room(Optional.ofNullable(residency).map(TenancyEntity::getRoom).orElse(member.getRoom()))
                .bed(Optional.ofNullable(residency).map(TenancyEntity::getBed).orElse(null))
                .title(request.title().trim())
                .description(request.description().trim())
                .category(request.category())
                .priority(request.priority())
                .preferredContactTime(StringUtils.trimToNull(request.preferredContactTime()))
                .status(ComplaintStatus.SUBMITTED)
                .member(member)
                .build();
        complaint.getTimeline().add(ComplaintTimeline.builder()
                .label("Complaint submitted")
                .updatedBy(member.getName())
                .complaint(complaint)
                .build());

        Complaint saved = complaintRepository.save(complaint);
        saveAttachments(organization, saved, null, request.attachmentFileIds(), member);
        notifyPropertyStaff(organization, saved.getProperty(), saved, "New Complaint: " + saved.getTitle());
        return operationsMapper.toComplaintResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ComplaintDetailV1ResponseDTO getComplaintDetail(String principalEmail, UUID complaintId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Complaint complaint = loadAuthorizedComplaint(currentUser, complaintId, "READ");
        boolean isMember = currentUser.getRole() == Role.PG_MEMBER;

        List<ComplaintCommentEntity> comments = isMember
                ? complaintCommentRepository.findAllByComplaintIdAndInternalFalseOrderByCreatedAtAsc(complaintId)
                : complaintCommentRepository.findAllByComplaintIdOrderByCreatedAtAsc(complaintId);

        List<ComplaintAttachmentEntity> allAttachments = complaintAttachmentRepository
                .findAllByComplaintIdOrderByCreatedAtAsc(complaintId);

        Map<UUID, List<ComplaintAttachmentResponseDTO>> attachmentsByComment = allAttachments.stream()
                .filter(attachment -> Optional.ofNullable(attachment.getComment()).isPresent())
                .collect(Collectors.groupingBy(
                        attachment -> attachment.getComment().getId(),
                        Collectors.mapping(operationsMapper::toAttachmentResponse, Collectors.toList())
                ));

        List<ComplaintCommentResponseDTO> commentDtos = comments.stream()
                .map(comment -> operationsMapper.toCommentResponse(
                        comment, attachmentsByComment.getOrDefault(comment.getId(), List.of())))
                .toList();

        List<ComplaintAttachmentResponseDTO> rootAttachments = allAttachments.stream()
                .filter(attachment -> Optional.ofNullable(attachment.getComment()).isEmpty())
                .map(operationsMapper::toAttachmentResponse)
                .toList();

        return new ComplaintDetailV1ResponseDTO(
                operationsMapper.toComplaintResponse(complaint),
                commentDtos,
                rootAttachments
        );
    }

    @Override
    @Transactional
    public ComplaintV1ResponseDTO updateStatus(
            String principalEmail,
            UUID complaintId,
            UpdateComplaintStatusV1RequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Complaint complaint = loadAuthorizedComplaint(currentUser, complaintId, "COMPLAINTS");
        if (currentUser.getRole() == Role.PG_MEMBER) {
            if (request.status() != ComplaintStatus.REOPENED || !REOPENABLE_STATUSES.contains(complaint.getStatus())) {
                throw new ForbiddenException("Residents can only reopen resolved, closed, or rejected complaints");
            }
        }

        Instant now = Instant.now();
        complaint.setStatus(request.status());
        applyStatusTimestamps(complaint, request, now);
        String label = StringUtils.isNotBlank(request.note())
                ? request.note().trim()
                : "Status updated to " + request.status().name();
        complaint.getTimeline().add(ComplaintTimeline.builder()
                .label(label)
                .updatedBy(currentUser.getName())
                .complaint(complaint)
                .build());

        Complaint saved = complaintRepository.save(complaint);
        if (currentUser.getRole() != Role.PG_MEMBER) {
            notificationDispatchService.dispatchToUsers(
                    saved.getOrganization(), saved.getProperty(), List.of(saved.getMember()),
                    NotificationEventType.COMPLAINT_UPDATED,
                    "Complaint " + saved.getStatus().name(),
                    saved.getTitle() + " — " + label,
                    "COMPLAINT", saved.getId()
            );
        }
        return operationsMapper.toComplaintResponse(saved);
    }

    @Override
    @Transactional
    public ComplaintV1ResponseDTO assignComplaint(
            String principalEmail,
            UUID complaintId,
            AssignComplaintRequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Complaint complaint = loadAuthorizedComplaint(currentUser, complaintId, "COMPLAINTS");
        if (currentUser.getRole() == Role.PG_MEMBER) {
            throw new ForbiddenException("Residents cannot assign complaints");
        }
        User assignee = userRepository.findById(request.assignedToUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee user not found"));
        OrganizationEntity assigneeOrg = accessGuardService.resolveUserOrganization(assignee);
        if (!assigneeOrg.getId().equals(complaint.getOrganization().getId())) {
            throw new ForbiddenException("Assignee does not belong to this organization");
        }

        complaint.setAssignedToUser(assignee);
        if (complaint.getStatus() == ComplaintStatus.SUBMITTED
                || complaint.getStatus() == ComplaintStatus.ACKNOWLEDGED
                || complaint.getStatus() == ComplaintStatus.PENDING) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
        }
        if (Optional.ofNullable(complaint.getAcknowledgedAt()).isEmpty()) {
            complaint.setAcknowledgedAt(Instant.now());
        }
        String label = StringUtils.isNotBlank(request.note())
                ? request.note().trim()
                : "Assigned to " + assignee.getName();
        complaint.getTimeline().add(ComplaintTimeline.builder()
                .label(label)
                .updatedBy(currentUser.getName())
                .complaint(complaint)
                .build());

        Complaint saved = complaintRepository.save(complaint);
        notificationDispatchService.dispatchToUsers(
                saved.getOrganization(), saved.getProperty(), List.of(assignee, saved.getMember()),
                NotificationEventType.COMPLAINT_UPDATED,
                "Complaint Assigned",
                saved.getTitle() + " assigned to " + assignee.getName(),
                "COMPLAINT", saved.getId()
        );
        return operationsMapper.toComplaintResponse(saved);
    }

    @Override
    @Transactional
    public ComplaintCommentResponseDTO addComment(
            String principalEmail,
            UUID complaintId,
            CreateComplaintCommentRequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Complaint complaint = loadAuthorizedComplaint(currentUser, complaintId, "COMPLAINTS");
        if (currentUser.getRole() == Role.PG_MEMBER && request.internal()) {
            throw new ForbiddenException("Residents cannot post internal staff notes");
        }

        ComplaintCommentEntity comment = new ComplaintCommentEntity();
        comment.setOrganization(complaint.getOrganization());
        comment.setComplaint(complaint);
        comment.setAuthorUser(currentUser);
        comment.setMessage(request.message().trim());
        comment.setInternal(request.internal());
        ComplaintCommentEntity savedComment = complaintCommentRepository.save(comment);

        List<ComplaintAttachmentResponseDTO> attachments = saveAttachments(
                complaint.getOrganization(), complaint, savedComment, request.attachmentFileIds(), currentUser
        );
        notifyOnComment(complaint, currentUser, savedComment);
        return operationsMapper.toCommentResponse(savedComment, attachments);
    }

    private Complaint loadAuthorizedComplaint(User currentUser, UUID complaintId, String managerAbility) {
        Complaint complaint = complaintRepository.findWithDetailsById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        if (currentUser.getRole() == Role.PG_MEMBER) {
            if (!complaint.getMember().getId().equals(currentUser.getId())) {
                throw new ForbiddenException("You can only access your own complaints");
            }
            return complaint;
        }
        if (Optional.ofNullable(complaint.getProperty()).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(
                    currentUser, complaint.getProperty().getId(), managerAbility
            );
            return complaint;
        }
        OrganizationEntity userOrg = accessGuardService.resolveUserOrganization(currentUser);
        if (Optional.ofNullable(complaint.getOrganization()).isEmpty()
                || !userOrg.getId().equals(complaint.getOrganization().getId())) {
            throw new ForbiddenException("Complaint is outside your organization scope");
        }
        return complaint;
    }

    private TenancyEntity resolveResidentTenancy(User member, OrganizationEntity organization, UUID requestedPropertyId) {
        List<TenancyEntity> tenancies = tenancyRepository.findSelfResidencyTenancies(
                member.getId(), organization.getId(), member.getEmail().trim().toLowerCase(Locale.ROOT)
        );
        if (Optional.ofNullable(requestedPropertyId).isPresent()) {
            return tenancies.stream()
                    .filter(tenancy -> tenancy.getProperty().getId().equals(requestedPropertyId))
                    .findFirst()
                    .orElseThrow(() -> new ForbiddenException("You are not linked to the selected property"));
        }
        return tenancies.stream().findFirst().orElse(null);
    }

    private List<ComplaintAttachmentResponseDTO> saveAttachments(
            OrganizationEntity organization,
            Complaint complaint,
            ComplaintCommentEntity comment,
            List<UUID> attachmentFileIds,
            User actor
    ) {
        if (CollectionUtils.isEmpty(attachmentFileIds)) {
            return List.of();
        }
        List<ComplaintAttachmentEntity> toSave = attachmentFileIds.stream()
                .distinct()
                .map(fileId -> {
                    StoredFileEntity file = storedFileRepository.findWithDetailsById(fileId)
                            .orElseThrow(() -> new ResourceNotFoundException("Uploaded attachment file not found"));
                    if (!file.getOrganization().getId().equals(organization.getId())) {
                        throw new ForbiddenException("Attachment belongs to another organization");
                    }
                    if (actor.getRole() == Role.PG_MEMBER && !file.getUploadedByUser().getId().equals(actor.getId())) {
                        throw new ForbiddenException("Residents can only attach files they uploaded");
                    }
                    ComplaintAttachmentEntity attachment = new ComplaintAttachmentEntity();
                    attachment.setOrganization(organization);
                    attachment.setComplaint(complaint);
                    attachment.setComment(comment);
                    attachment.setStoredFile(file);
                    return attachment;
                })
                .toList();
        return complaintAttachmentRepository.saveAll(toSave).stream()
                .map(operationsMapper::toAttachmentResponse)
                .toList();
    }

    private void applyStatusTimestamps(
            Complaint complaint,
            UpdateComplaintStatusV1RequestDTO request,
            Instant now
    ) {
        if (request.status() == ComplaintStatus.ACKNOWLEDGED && Optional.ofNullable(complaint.getAcknowledgedAt()).isEmpty()) {
            complaint.setAcknowledgedAt(now);
        }
        if (request.status() == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(now);
            if (StringUtils.isNotBlank(request.resolutionSummary())) {
                complaint.setResolutionSummary(request.resolutionSummary().trim());
            }
        }
        if (request.status() == ComplaintStatus.CLOSED) {
            complaint.setClosedAt(now);
        }
        if (request.status() == ComplaintStatus.REOPENED) {
            complaint.setResolvedAt(null);
            complaint.setClosedAt(null);
        }
    }

    private void notifyPropertyStaff(OrganizationEntity organization, PropertyEntity property, Complaint complaint, String title) {
        List<User> recipients = new ArrayList<>();
        recipients.add(organization.getOwnerUser());
        if (Optional.ofNullable(property).isPresent()) {
            managerPropertyAssignmentRepository.findAllByOrganizationId(organization.getId(), Pageable.unpaged())
                    .stream()
                    .filter(assignment -> assignment.getStatus() == AppConstants.STATUS_ACTIVE)
                    .filter(assignment -> assignment.getProperty().getId().equals(property.getId()))
                    .map(assignment -> assignment.getManagerUser())
                    .forEach(recipients::add);
        }
        notificationDispatchService.dispatchToUsers(
                organization, property, recipients,
                NotificationEventType.COMPLAINT_CREATED,
                title, complaint.getDescription(),
                "COMPLAINT", complaint.getId()
        );
    }

    private void notifyOnComment(Complaint complaint, User author, ComplaintCommentEntity comment) {
        if (comment.isInternal()) {
            return;
        }
        List<User> recipients = author.getRole() == Role.PG_MEMBER
                ? Optional.ofNullable(complaint.getAssignedToUser())
                        .map(assignee -> List.of(complaint.getOrganization().getOwnerUser(), assignee))
                        .orElseGet(() -> List.of(complaint.getOrganization().getOwnerUser()))
                : List.of(complaint.getMember());
        notificationDispatchService.dispatchToUsers(
                complaint.getOrganization(), complaint.getProperty(), recipients,
                NotificationEventType.COMPLAINT_COMMENT,
                "New Reply on Complaint: " + complaint.getTitle(),
                comment.getMessage(),
                "COMPLAINT", complaint.getId()
        );
    }

    private Specification<Complaint> buildSpecification(
            List<UUID> propertyIds,
            ComplaintStatus status,
            ComplaintCategory category,
            ComplaintPriority priority,
            UUID assignedToUserId,
            String search
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("property").get("id").in(propertyIds));
            Optional.ofNullable(status).ifPresent(val -> predicates.add(criteriaBuilder.equal(root.get("status"), val)));
            Optional.ofNullable(category).ifPresent(val -> predicates.add(criteriaBuilder.equal(root.get("category"), val)));
            Optional.ofNullable(priority).ifPresent(val -> predicates.add(criteriaBuilder.equal(root.get("priority"), val)));
            Optional.ofNullable(assignedToUserId).ifPresent(val -> predicates.add(criteriaBuilder.equal(root.get("assignedToUser").get("id"), val)));
            if (StringUtils.isNotBlank(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
