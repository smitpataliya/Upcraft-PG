package com.upcraft.stay.organization.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.domain.enums.Role;

/**
 * Request to invite a manager or tenant into an organization property.
 */
public record CreateInvitationRequestDTO(
        @NotBlank(message = "Invitee email is required")
        @Email(message = "Valid email address is required")
        String email,

        @Pattern(regexp = "^$|^(\\+91[-\\s]?)?[6-9]\\d{9}$", message = "Enter a valid Indian mobile number")
        String phone,

        @NotBlank(message = "Invitee name is required")
        @Size(max = 150, message = "Invitee name must not exceed 150 characters")
        String inviteeName,

        @NotNull(message = "Target role is required")
        Role targetRole,

        UUID propertyId,
        Boolean canManageTenants,
        Boolean canRecordPayments,
        Boolean canEnterMeters,
        Boolean canHandleComplaints,
        Boolean canPublishNotices
) {}

