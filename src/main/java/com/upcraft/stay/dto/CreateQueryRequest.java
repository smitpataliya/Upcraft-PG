package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.QueryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateQueryRequest {
    @NotBlank
    private String subject;

    @NotBlank
    private String description;

    @NotNull
    private QueryCategory category;
}
