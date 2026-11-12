package com.upcraft.stay.operations.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.ManagerTaskResponseDTO;
import com.upcraft.stay.operations.dto.UpdateManagerTaskStatusRequestDTO;
import com.upcraft.stay.operations.dto.UpsertManagerTaskRequestDTO;
import com.upcraft.stay.operations.enums.ManagerTaskStatus;

/**
 * Property-scoped manager operational tasks service.
 */
public interface ManagerTaskV1Service {

    PageResponseDTO<ManagerTaskResponseDTO> listTasks(
            String principalEmail,
            UUID propertyId,
            ManagerTaskStatus taskStatus,
            UUID assignedToUserId,
            int page,
            Integer size
    );

    ManagerTaskResponseDTO createTask(String principalEmail, UpsertManagerTaskRequestDTO request);

    ManagerTaskResponseDTO updateTaskStatus(
            String principalEmail,
            UUID taskId,
            UpdateManagerTaskStatusRequestDTO request
    );

    void deleteTask(String principalEmail, UUID taskId);
}

