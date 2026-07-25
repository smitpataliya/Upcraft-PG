package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.QueryCategory;
import com.upcraft.stay.domain.enums.QueryStatus;
import com.upcraft.stay.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QueryDto {
    private UUID id;
    private String subject;
    private String description;
    private QueryCategory category;
    private QueryStatus status;
    private String memberName;
    private String memberEmail;
    private List<ReplyDto> replies;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class ReplyDto {
        private UUID id;
        private String message;
        private String senderName;
        private Role senderRole;
        private LocalDateTime at;
    }
}
