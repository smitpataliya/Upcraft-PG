package com.upcraft.stay.electricity.dto;

/**
 * Optional owner approval note recorded alongside meter reading verification.
 */
public record ApproveReadingRequestDTO(
        String notes
) {}

