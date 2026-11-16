package com.upcraft.stay.auth.service.impl;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.auth.dto.ForgotPasswordRequestDTO;
import com.upcraft.stay.auth.dto.LoginRequestDTO;
import com.upcraft.stay.auth.dto.RegisterOwnerRequestDTO;
import com.upcraft.stay.auth.dto.ResetPasswordRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpResponseDTO;
import com.upcraft.stay.auth.service.AuthV1Service;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.UnauthorizedException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.entity.OrganizationMemberEntity;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.organization.repository.OrganizationRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.security.JwtTokenFactory;
import com.upcraft.stay.service.OtpService;

/**
 * Versioned authentication and owner organization onboarding implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthV1ServiceImpl implements AuthV1Service {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final PropertyRepository propertyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenFactory jwtTokenFactory;
    private final AccessGuardService accessGuardService;
    private final OtpService otpService;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public AuthTokenResponseDTO registerOwner(RegisterOwnerRequestDTO registerOwnerRequestDTO) {
        String normalizedEmail = registerOwnerRequestDTO.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        User ownerUser = createOwnerUser(registerOwnerRequestDTO, normalizedEmail);
        OrganizationEntity organizationEntity = createOrganization(registerOwnerRequestDTO, ownerUser);
        createOwnerMembership(organizationEntity, ownerUser);
        PropertyEntity initialProperty = createInitialProperty(registerOwnerRequestDTO, organizationEntity);
        auditLogService.recordEvent(new AuditLogCommand(
                organizationEntity,
                initialProperty,
                ownerUser,
                normalizedEmail,
                AuditAction.USER_REGISTERED,
                AuditEntityType.USER,
                ownerUser.getId(),
                "Registered owner organization " + organizationEntity.getName(),
                Map.of("role", ownerUser.getRole().name())
        ));

        log.info("Registered new PG owner organization. userId={}, organizationId={}",
                ownerUser.getId(), organizationEntity.getId());
        return issueTokenResponse(ownerUser);
    }

    @Override
    @Transactional
    public AuthTokenResponseDTO login(LoginRequestDTO loginRequestDTO) {
        String identifier = loginRequestDTO.email().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, loginRequestDTO.password()));
        } catch (AuthenticationException exception) {
            recordFailedLoginAudit(identifier);
            throw exception;
        }

        User userEntity = userRepository.findByEmailOrUsername(identifier, identifier)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials or inactive account"));

        auditLogService.recordEvent(new AuditLogCommand(
                safeResolveOrganization(userEntity),
                null,
                userEntity,
                userEntity.getEmail(),
                AuditAction.LOGIN_SUCCESS,
                AuditEntityType.USER,
                userEntity.getId(),
                "Successful password login",
                Map.of("role", userEntity.getRole().name())
        ));
        return issueTokenResponse(userEntity);
    }

    private void recordFailedLoginAudit(String identifier) {
        Optional<User> existingUser = userRepository.findByEmailOrUsername(identifier, identifier);
        OrganizationEntity organizationEntity = existingUser.map(this::safeResolveOrganization).orElse(null);
        auditLogService.recordIndependentEvent(new AuditLogCommand(
                organizationEntity,
                null,
                existingUser.orElse(null),
                identifier,
                AuditAction.LOGIN_FAILED,
                AuditEntityType.USER,
                existingUser.map(User::getId).orElse(null),
                "Failed login attempt",
                Map.of("reason", "INVALID_CREDENTIALS")
        ));
    }

    private OrganizationEntity safeResolveOrganization(User userEntity) {
        try {
            return accessGuardService.resolveUserOrganization(userEntity);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserDTO getCurrentUser(String principalEmail) {
        User userEntity = accessGuardService.loadActiveUser(principalEmail);
        return accessGuardService.buildAuthenticatedUser(userEntity);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO forgotPasswordRequestDTO) {
        otpService.sendOtp(forgotPasswordRequestDTO.email());
    }

    @Override
    public VerifyOtpResponseDTO verifyOtp(VerifyOtpRequestDTO verifyOtpRequestDTO) {
        try {
            String resetToken = otpService.verifyOtp(verifyOtpRequestDTO.email(), verifyOtpRequestDTO.otp());
            return new VerifyOtpResponseDTO(resetToken, "OTP verified successfully");
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO resetPasswordRequestDTO) {
        try {
            otpService.resetPasswordWithToken(
                    resetPasswordRequestDTO.resetToken(),
                    resetPasswordRequestDTO.newPassword());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }

    private User createOwnerUser(RegisterOwnerRequestDTO registerOwnerRequestDTO, String normalizedEmail) {
        User userEntity = User.builder()
                .name(registerOwnerRequestDTO.name().trim())
                .email(normalizedEmail)
                .phone(registerOwnerRequestDTO.phone().trim())
                .password(passwordEncoder.encode(registerOwnerRequestDTO.password()))
                .role(Role.PG_OWNER)
                .active(true)
                .build();
        return userRepository.save(userEntity);
    }

    private OrganizationEntity createOrganization(RegisterOwnerRequestDTO requestDTO, User ownerUser) {
        OrganizationEntity organizationEntity = new OrganizationEntity();
        organizationEntity.setName(requestDTO.organizationName().trim());
        organizationEntity.setSlug(generateUniqueSlug(requestDTO.organizationName()));
        organizationEntity.setOwnerUser(ownerUser);
        organizationEntity.setContactEmail(ownerUser.getEmail());
        organizationEntity.setContactPhone(ownerUser.getPhone());
        organizationEntity.setTimezone(appProperties.getTimezone());
        organizationEntity.setCurrencyCode(appProperties.getCurrency());
        return organizationRepository.save(organizationEntity);
    }

    private void createOwnerMembership(OrganizationEntity organizationEntity, User ownerUser) {
        OrganizationMemberEntity memberEntity = new OrganizationMemberEntity();
        memberEntity.setOrganization(organizationEntity);
        memberEntity.setUser(ownerUser);
        memberEntity.setMemberRole(Role.PG_OWNER);
        memberEntity.setPrimary(true);
        organizationMemberRepository.save(memberEntity);
    }

    private PropertyEntity createInitialProperty(RegisterOwnerRequestDTO requestDTO, OrganizationEntity organizationEntity) {
        PropertyEntity propertyEntity = new PropertyEntity();
        propertyEntity.setOrganization(organizationEntity);
        propertyEntity.setName(requestDTO.propertyName().trim());
        propertyEntity.setPropertyType(Optional.ofNullable(requestDTO.propertyType()).orElse(PropertyType.PG));
        propertyEntity.setAddressLine(requestDTO.addressLine().trim());
        propertyEntity.setLandmark(StringUtils.trimToNull(requestDTO.landmark()));
        propertyEntity.setCity(requestDTO.city().trim());
        propertyEntity.setState(requestDTO.state().trim());
        propertyEntity.setPinCode(requestDTO.pinCode().trim());
        propertyEntity.setCountry("India");
        propertyEntity.setContactEmail(organizationEntity.getContactEmail());
        propertyEntity.setContactPhone(organizationEntity.getContactPhone());
        propertyEntity.setPropertyStatus(PropertyStatus.ACTIVE);
        return propertyRepository.save(propertyEntity);
    }

    private String generateUniqueSlug(String rawName) {
        String baseSlug = rawName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String safeBase = StringUtils.defaultIfBlank(baseSlug, "upcraft-pg");
        String candidate = safeBase;
        if (organizationRepository.existsBySlug(candidate)) {
            candidate = safeBase + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private AuthTokenResponseDTO issueTokenResponse(User userEntity) {
        AuthenticatedUserDTO authenticatedUserDTO = accessGuardService.buildAuthenticatedUser(userEntity);
        return jwtTokenFactory.createTokenResponse(authenticatedUserDTO, userEntity.getPassword(),
                userEntity.isActive());
    }
}
