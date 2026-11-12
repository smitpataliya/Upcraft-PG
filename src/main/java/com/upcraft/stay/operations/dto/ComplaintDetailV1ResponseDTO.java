package com.upcraft.stay.operations.dto;

import java.util.List;

/**
 * Full complaint detail response combining summary, comment thread, and private attachments.
 */
public record ComplaintDetailV1ResponseDTO(
        ComplaintV1ResponseDTO complaint,
        List<ComplaintCommentResponseDTO> comments,
        List<ComplaintAttachmentResponseDTO> attachments
) {
}

