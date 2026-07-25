package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateComplaintStatusRequest {
    @NotNull
    private ComplaintStatus status;

    private String note;
}
