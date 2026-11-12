package com.upcraft.stay.operations.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import com.upcraft.stay.operations.service.ComplaintV1Service;

/**
 * Multi-property complaint lifecycle, assignment, comment, and photo attachment controller.
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintV1Controller {

    private final ComplaintV1Service complaintV1Service;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ComplaintV1ResponseDTO>>> listComplaints(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintCategory category,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Complaints loaded",
                complaintV1Service.listComplaints(
                        authentication.getName(), propertyId, status, category, priority,
                        assignedToUserId, search, page, size, sortBy, sortDirection
                ),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ComplaintV1ResponseDTO>>> listMyComplaints(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Resident complaints loaded",
                complaintV1Service.listMyComplaints(authentication.getName(), page, size),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ComplaintV1ResponseDTO>> createComplaint(
            Authentication authentication,
            @Valid @RequestBody CreateComplaintV1RequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Complaint submitted",
                complaintV1Service.createComplaint(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/{complaintId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ComplaintDetailV1ResponseDTO>> getDetail(
            Authentication authentication,
            @PathVariable UUID complaintId
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Complaint detail loaded",
                complaintV1Service.getComplaintDetail(authentication.getName(), complaintId),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PatchMapping("/{complaintId}/status")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ComplaintV1ResponseDTO>> updateStatus(
            Authentication authentication,
            @PathVariable UUID complaintId,
            @Valid @RequestBody UpdateComplaintStatusV1RequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Complaint status updated",
                complaintV1Service.updateStatus(authentication.getName(), complaintId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PatchMapping("/{complaintId}/assign")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<ComplaintV1ResponseDTO>> assignComplaint(
            Authentication authentication,
            @PathVariable UUID complaintId,
            @Valid @RequestBody AssignComplaintRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Complaint assigned",
                complaintV1Service.assignComplaint(authentication.getName(), complaintId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping("/{complaintId}/comments")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ComplaintCommentResponseDTO>> addComment(
            Authentication authentication,
            @PathVariable UUID complaintId,
            @Valid @RequestBody CreateComplaintCommentRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Comment added",
                complaintV1Service.addComment(authentication.getName(), complaintId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }
}

