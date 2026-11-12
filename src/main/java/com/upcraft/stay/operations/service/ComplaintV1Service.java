package com.upcraft.stay.operations.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.domain.enums.ComplaintCategory;
import com.upcraft.stay.domain.enums.ComplaintPriority;
import com.upcraft.stay.domain.enums.ComplaintStatus;
import com.upcraft.stay.operations.dto.AssignComplaintRequestDTO;
import com.upcraft.stay.operations.dto.ComplaintCommentResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintDetailV1ResponseDTO;
import com.upcraft.stay.operations.dto.ComplaintV1ResponseDTO;
import com.upcraft.stay.operations.dto.CreateComplaintCommentRequestDTO;
import com.upcraft.stay.operations.dto.CreateComplaintV1RequestDTO;
import com.upcraft.stay.operations.dto.UpdateComplaintStatusV1RequestDTO;

/**
 * Multi-property complaint lifecycle, assignment, comment, and private photo service.
 */
public interface ComplaintV1Service {

    PageResponseDTO<ComplaintV1ResponseDTO> listComplaints(
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
    );

    PageResponseDTO<ComplaintV1ResponseDTO> listMyComplaints(
            String principalEmail,
            int page,
            Integer size
    );

    ComplaintV1ResponseDTO createComplaint(String principalEmail, CreateComplaintV1RequestDTO request);

    ComplaintDetailV1ResponseDTO getComplaintDetail(String principalEmail, UUID complaintId);

    ComplaintV1ResponseDTO updateStatus(
            String principalEmail,
            UUID complaintId,
            UpdateComplaintStatusV1RequestDTO request
    );

    ComplaintV1ResponseDTO assignComplaint(
            String principalEmail,
            UUID complaintId,
            AssignComplaintRequestDTO request
    );

    ComplaintCommentResponseDTO addComment(
            String principalEmail,
            UUID complaintId,
            CreateComplaintCommentRequestDTO request
    );
}

