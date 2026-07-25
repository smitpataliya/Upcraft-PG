package com.upcraft.stay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAnnouncementRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String body;

    private String category;
    private boolean pinned = false;
}
