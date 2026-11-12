package com.upcraft.stay.electricity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Mandatory reason captured when rejecting a submitted electricity meter reading.
 */
public record RejectReadingRequestDTO(
        @NotBlank(message = "Rejection reason is required")
        String reason
) {}

