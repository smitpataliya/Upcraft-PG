package com.upcraft.stay.operations.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.operations.dto.ManagerTaskResponseDTO;
import com.upcraft.stay.operations.dto.UpdateManagerTaskStatusRequestDTO;
import com.upcraft.stay.operations.dto.UpsertManagerTaskRequestDTO;
import com.upcraft.stay.operations.entity.ManagerTaskEntity;
import com.upcraft.stay.operations.enums.ManagerTaskStatus;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.repository.ManagerTaskRepository;
import com.upcraft.stay.operations.service.ManagerTaskV1Service;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Implements property-scoped manager tasks and assignee notifications.
 */
@Service
@RequiredArgsConstructor
public class ManagerTaskV1ServiceImpl implements ManagerTaskV1Service {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "dueAt", "priority", "taskStatus");

    private final AccessGuardService accessGuardService;
    private final ManagerTaskRepository managerTaskRepository;
    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ManagerTaskResponseDTO> listTasks(
            String principalEmail,
            UUID propertyId,
            ManagerTaskStatus taskStatus,
            UUID assignedToUserId,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> accessiblePropertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        if (accessiblePropertyIds.isEmpty()) {
            return PageResponseDTO.from(Page.empty());
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", SORT_FIELDS, "createdAt", appProperties
        );
        Specification<ManagerTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("property").get("id").in(accessiblePropertyIds));
            Optional.ofNullable(taskStatus).ifPresent(val -> predicates.add(cb.equal(root.get("taskStatus"), val)));
            Optional.ofNullable(assignedToUserId).ifPresent(val -> predicates.add(cb.equal(root.get("assignedToUser").get("id"), val)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponseDTO.from(managerTaskRepository.findAll(spec, pageable).map(operationsMapper::toManagerTaskResponse));
    }

    @Override
    @Transactional
    public ManagerTaskResponseDTO createTask(String principalEmail, UpsertManagerTaskRequestDTO request) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity property = accessGuardService.requirePropertyManagementAccess(
                currentUser, request.propertyId(), "READ"
        );
        User assignee = resolveAssignee(property.getOrganization(), request.assignedToUserId());
        Complaint complaint = resolveComplaint(property.getOrganization(), request.complaintId());

        ManagerTaskEntity task = new ManagerTaskEntity();
        task.setOrganization(property.getOrganization());
        task.setProperty(property);
        task.setAssignedToUser(assignee);
        task.setCreatedByUser(currentUser);
        task.setComplaint(complaint);
        task.setTitle(request.title().trim());
        task.setDescription(StringUtils.trimToNull(request.description()));
        task.setPriority(request.priority());
        task.setTaskStatus(ManagerTaskStatus.OPEN);
        task.setDueAt(request.dueAt());

        ManagerTaskEntity saved = managerTaskRepository.save(task);
        Optional.ofNullable(assignee).ifPresent(targetUser ->
                notificationDispatchService.dispatchToUsers(
                        property.getOrganization(), property, List.of(targetUser),
                        NotificationEventType.TASK_ASSIGNED,
                        "New Task Assigned: " + saved.getTitle(),
                        Optional.ofNullable(saved.getDescription()).orElse("Assigned at " + property.getName()),
                        "MANAGER_TASK", saved.getId()
                ));
        return operationsMapper.toManagerTaskResponse(saved);
    }

    @Override
    @Transactional
    public ManagerTaskResponseDTO updateTaskStatus(
            String principalEmail,
            UUID taskId,
            UpdateManagerTaskStatusRequestDTO request
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        ManagerTaskEntity task = managerTaskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager task not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, task.getProperty().getId(), "READ");

        task.setTaskStatus(request.taskStatus());
        task.setCompletionNotes(StringUtils.trimToNull(request.completionNotes()));
        task.setCompletedAt(request.taskStatus() == ManagerTaskStatus.COMPLETED ? Instant.now() : null);
        return operationsMapper.toManagerTaskResponse(managerTaskRepository.save(task));
    }

    @Override
    @Transactional
    public void deleteTask(String principalEmail, UUID taskId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        ManagerTaskEntity task = managerTaskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager task not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, task.getProperty().getId(), "READ");
        task.markDeleted(currentUser.getEmail(), null);
        managerTaskRepository.save(task);
    }

    private User resolveAssignee(OrganizationEntity organization, UUID assignedToUserId) {
        if (Optional.ofNullable(assignedToUserId).isEmpty()) {
            return null;
        }
        User assignee = userRepository.findById(assignedToUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee user not found"));
        OrganizationEntity assigneeOrg = accessGuardService.resolveUserOrganization(assignee);
        if (!assigneeOrg.getId().equals(organization.getId())) {
            throw new ForbiddenException("Assignee belongs to another organization");
        }
        return assignee;
    }

    private Complaint resolveComplaint(OrganizationEntity organization, UUID complaintId) {
        if (Optional.ofNullable(complaintId).isEmpty()) {
            return null;
        }
        Complaint complaint = complaintRepository.findWithDetailsById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked complaint not found"));
        if (Optional.ofNullable(complaint.getOrganization()).isEmpty()
                || !complaint.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Complaint belongs to another organization");
        }
        return complaint;
    }
}

