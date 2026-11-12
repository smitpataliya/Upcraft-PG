package com.upcraft.stay.billing.service.impl;

import java.util.Locale;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Shared billing authorization helper enforcing tenant self-isolation and property payment permissions.
 */
@Component
@RequiredArgsConstructor
public class BillingAccessHelper {

    private final AccessGuardService accessGuardService;

    /**
     * Verifies either property management permission for owners/managers or strict self-ownership for PG members.
     *
     * @param currentUser  authenticated active user
     * @param tenantEntity target tenant record
     */
    public void requireTenantReadAccess(User currentUser, TenantEntity tenantEntity) {
        if (currentUser.getRole() == Role.PG_MEMBER) {
            if (!isLinkedToMember(currentUser, tenantEntity)) {
                throw new ForbiddenException("Residents may only access their own billing records");
            }
            return;
        }
        accessGuardService.requirePropertyManagementAccess(
                currentUser, tenantEntity.getProperty().getId(), "READ"
        );
    }

    /**
     * Resolves whether a tenant profile belongs to the given resident user via user ID or unlinked org+email match.
     *
     * @param memberUser   resident user account
     * @param tenantEntity candidate tenant profile
     * @return true when owned by the resident user
     */
    public boolean isLinkedToMember(User memberUser, TenantEntity tenantEntity) {
        boolean matchesByUserId = Optional.ofNullable(tenantEntity.getUser())
                .map(User::getId)
                .map(id -> id.equals(memberUser.getId()))
                .orElse(false);
        if (matchesByUserId) {
            return true;
        }

        boolean unlinkedTenant = Optional.ofNullable(tenantEntity.getUser()).isEmpty();
        if (!unlinkedTenant || StringUtils.isBlank(tenantEntity.getEmail())) {
            return false;
        }
        OrganizationEntity memberOrg = accessGuardService.resolveUserOrganization(memberUser);
        return tenantEntity.getOrganization().getId().equals(memberOrg.getId())
                && tenantEntity.getEmail().trim().toLowerCase(Locale.ROOT)
                .equals(memberUser.getEmail().trim().toLowerCase(Locale.ROOT));
    }
}

