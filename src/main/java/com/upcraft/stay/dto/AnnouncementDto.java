package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AnnouncementDto {
    private UUID id;
    private String title;
    private String body;
    private String category;
    private boolean pinned;
    private String author;
    private LocalDateTime createdAt;
}
