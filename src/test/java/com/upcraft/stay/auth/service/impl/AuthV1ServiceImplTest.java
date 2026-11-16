package com.upcraft.stay.auth.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.auth.dto.LoginRequestDTO;
import com.upcraft.stay.auth.dto.RegisterOwnerRequestDTO;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.PropertyType;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.repository.OrganizationMemberRepository;
import com.upcraft.stay.organization.repository.OrganizationRepository;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.security.JwtTokenFactory;
import com.upcraft.stay.service.OtpService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthV1ServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenFactory jwtTokenFactory;

    @Mock
    private AccessGuardService accessGuardService;

    @Mock
    private OtpService otpService;

    @Mock
    private AuditLogService auditLogService;

    private AppProperties appProperties;
    private AuthV1ServiceImpl authV1Service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        authV1Service = new AuthV1ServiceImpl(
                authenticationManager,
                userRepository,
                organizationRepository,
                organizationMemberRepository,
                propertyRepository,
                passwordEncoder,
                jwtTokenFactory,
                accessGuardService,
                otpService,
                appProperties,
                auditLogService
        );
    }

    @Test
    void registerOwner_whenNewEmail_createsOwnerOrgAndInitialProperty() {
        RegisterOwnerRequestDTO requestDTO = new RegisterOwnerRequestDTO(
                "Smit Patel",
                "Owner@Example.com",
                "+91 9876543210",
                "StrongPass@123",
                "Upcraft Spaces",
                "Upcraft Navrangpura",
                PropertyType.PG,
                "12 CG Road",
                "Near Metro",
                "Ahmedabad",
                "Gujarat",
                "380009"
        );
        UUID ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass@123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(ownerId);
            return savedUser;
        });
        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(organizationRepository.save(any(OrganizationEntity.class))).thenAnswer(invocation -> {
            OrganizationEntity savedOrganization = invocation.getArgument(0);
            savedOrganization.setId(organizationId);
            return savedOrganization;
        });
        when(propertyRepository.save(any(PropertyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedUserDTO expectedUser = new AuthenticatedUserDTO(
                ownerId, "Smit Patel", "owner@example.com", null, Role.PG_OWNER,
                "+91 9876543210", null, organizationId, "Upcraft Spaces", List.of()
        );
        when(accessGuardService.buildAuthenticatedUser(any(User.class))).thenReturn(expectedUser);
        when(jwtTokenFactory.createTokenResponse(any(), anyString(), anyBoolean()))
                .thenReturn(new AuthTokenResponseDTO("jwt-token", "Bearer", 3600000L, expectedUser));

        AuthTokenResponseDTO responseDTO = authV1Service.registerOwner(requestDTO);

        assertThat(responseDTO.token()).isEqualTo("jwt-token");
        assertThat(responseDTO.user().role()).isEqualTo(Role.PG_OWNER);
        assertThat(responseDTO.user().organizationId()).isEqualTo(organizationId);
        verify(organizationMemberRepository).save(any());
        verify(propertyRepository).save(any(PropertyEntity.class));
    }

    @Test
    void registerOwner_whenDuplicateEmail_throwsConflictException() {
        RegisterOwnerRequestDTO requestDTO = new RegisterOwnerRequestDTO(
                "Smit Patel", "dup@example.com", "9876543210", "StrongPass@123",
                "Upcraft Spaces", "Main PG", PropertyType.PG,
                "12 CG Road", null, "Ahmedabad", "Gujarat", "380009"
        );
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authV1Service.registerOwner(requestDTO))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void login_whenValidCredentials_returnsTokenAndOrgContext() {
        UUID userId = UUID.randomUUID();
        User userEntity = User.builder()
                .id(userId)
                .name("Manager One")
                .email("mgr@example.com")
                .password("encoded")
                .role(Role.MANAGER)
                .active(true)
                .build();
        AuthenticatedUserDTO userDTO = new AuthenticatedUserDTO(
                userId, "Manager One", "mgr@example.com", null, Role.MANAGER,
                null, null, UUID.randomUUID(), "Upcraft Spaces", List.of(UUID.randomUUID())
        );

        when(userRepository.findByEmailOrUsername("mgr@example.com", "mgr@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(accessGuardService.buildAuthenticatedUser(userEntity)).thenReturn(userDTO);
        when(jwtTokenFactory.createTokenResponse(userDTO, "encoded", true))
                .thenReturn(new AuthTokenResponseDTO("jwt-mgr", "Bearer", 3600000L, userDTO));

        AuthTokenResponseDTO response = authV1Service.login(new LoginRequestDTO("Mgr@Example.com", "Secret@123"));

        assertThat(response.token()).isEqualTo("jwt-mgr");
        assertThat(response.user().assignedPropertyIds()).hasSize(1);
    }
}

