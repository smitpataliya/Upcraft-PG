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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.ManagerTaskResponseDTO;
import com.upcraft.stay.operations.dto.UpdateManagerTaskStatusRequestDTO;
import com.upcraft.stay.operations.dto.UpsertManagerTaskRequestDTO;
import com.upcraft.stay.operations.enums.ManagerTaskStatus;
import com.upcraft.stay.operations.service.ManagerTaskV1Service;

/**
 * Property-scoped manager and warden operational task board controller.
 */
@RestController
@RequestMapping("/api/v1/manager-tasks")
@RequiredArgsConstructor
public class ManagerTaskV1Controller {

    private final ManagerTaskV1Service managerTaskV1Service;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ManagerTaskResponseDTO>>> listTasks(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) ManagerTaskStatus taskStatus,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Manager tasks loaded",
                managerTaskV1Service.listTasks(
                        authentication.getName(), propertyId, taskStatus, assignedToUserId, page, size
                ),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<ManagerTaskResponseDTO>> createTask(
            Authentication authentication,
            @Valid @RequestBody UpsertManagerTaskRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Task created",
                managerTaskV1Service.createTask(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PatchMapping("/{taskId}/status")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<ManagerTaskResponseDTO>> updateTaskStatus(
            Authentication authentication,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateManagerTaskStatusRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Task status updated",
                managerTaskV1Service.updateTaskStatus(authentication.getName(), taskId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<Void> deleteTask(
            Authentication authentication,
            @PathVariable UUID taskId
    ) {
        managerTaskV1Service.deleteTask(authentication.getName(), taskId);
        return ResponseEntity.noContent().build();
    }
}

