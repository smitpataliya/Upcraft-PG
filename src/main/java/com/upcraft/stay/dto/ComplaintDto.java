package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.ComplaintCategory;
import com.upcraft.stay.domain.enums.ComplaintPriority;
import com.upcraft.stay.domain.enums.ComplaintStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ComplaintDto {
    private UUID id;
    private String title;
    private String description;
    private ComplaintCategory category;
    private ComplaintPriority priority;
    private ComplaintStatus status;
    private String memberName;
    private String memberEmail;
    private List<TimelineDto> timeline;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class TimelineDto {
        private String label;
        private String updatedBy;
        private LocalDateTime at;
    }
}
