package com.upcraft.stay.organization.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import com.upcraft.stay.common.entity.AuditableEntity;
import com.upcraft.stay.common.enums.InvitationStatus;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Secure one-time invitation for onboarding managers and tenants without open registration.
 * Stores SHA-256 digests of the link token and one-time code rather than plaintext secrets.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invitations")
@SQLRestriction("status <> 9")
public class InvitationEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private PropertyEntity property;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "invitee_name", nullable = false, length = 150)
    private String inviteeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false, length = 30)
    private Role targetRole;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "invite_code_hash", nullable = false, length = 128)
    private String inviteCodeHash;

    @Column(name = "permissions_json", columnDefinition = "TEXT")
    private String permissionsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false, length = 30)
    private InvitationStatus invitationStatus = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id")
    private User acceptedByUser;

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (!(otherObject instanceof InvitationEntity otherEntity)) {
            return false;
        }
        return id != null && Objects.equals(id, otherEntity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

