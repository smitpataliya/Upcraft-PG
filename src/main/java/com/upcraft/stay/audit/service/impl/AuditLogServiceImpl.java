package com.upcraft.stay.audit.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.dto.AuditLogResponseDTO;
import com.upcraft.stay.audit.entity.AuditLogEntity;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.repository.AuditLogRepository;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.filter.TrustedProxyIpResolver;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Persists transactional, sanitized, append-only audit logs and serves role-scoped audit queries.
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "action", "entityType");

    private final AuditLogRepository auditLogRepository;
    private final AccessGuardService accessGuardService;
    private final AuditSanitizer auditSanitizer;
    private final AppProperties appProperties;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(AuditLogCommand command) {
        persistEntry(command);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependentEvent(AuditLogCommand command) {
        persistEntry(command);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AuditLogResponseDTO> listAuditLogs(
            String principalEmail,
            UUID propertyId,
            AuditAction action,
            AuditEntityType entityType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            Integer size
    ) {
        if (Optional.ofNullable(fromDate).isPresent()
                && Optional.ofNullable(toDate).isPresent()
                && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate");
        }
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        List<UUID> accessiblePropertyIds = accessGuardService.resolveAccessiblePropertyIds(
                actorUser, Optional.ofNullable(propertyId)
        );
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", SORT_FIELDS, "createdAt", appProperties
        );
        if (actorUser.getRole() == Role.MANAGER && CollectionUtils.isEmpty(accessiblePropertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Specification<AuditLogEntity> spec = buildSpecification(
                organization.getId(), actorUser.getRole(), accessiblePropertyIds,
                propertyId, action, entityType, fromDate, toDate
        );
        Page<AuditLogResponseDTO> mappedPage = auditLogRepository.findAll(spec, pageable)
                .map(this::mapToResponse);
        return PageResponseDTO.from(mappedPage);
    }

    private void persistEntry(AuditLogCommand command) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setOrganization(command.organization());
        entity.setProperty(command.property());
        entity.setActorUser(command.actorUser());
        entity.setActorEmail(resolveActorEmail(command));
        entity.setActorRole(Optional.ofNullable(command.actorUser()).map(user -> user.getRole().name()).orElse("ANONYMOUS"));
        entity.setAction(command.action());
        entity.setEntityType(command.entityType());
        entity.setEntityId(command.entityId());
        entity.setSummary(auditSanitizer.truncateSummary(command.summary()));
        entity.setMetadataJson(auditSanitizer.toSafeJson(command.metadata()));
        entity.setClientIp(resolveCurrentClientIp());
        entity.setTraceId(MDC.get(AppConstants.TRACE_ID_MDC_KEY));
        auditLogRepository.save(entity);
    }

    private Specification<AuditLogEntity> buildSpecification(
            UUID organizationId,
            Role actorRole,
            List<UUID> accessiblePropertyIds,
            UUID requestedPropertyId,
            AuditAction action,
            AuditEntityType entityType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organization").get("id"), organizationId));
            if (Optional.ofNullable(requestedPropertyId).isPresent()) {
                predicates.add(builder.equal(root.get("property").get("id"), requestedPropertyId));
            } else if (actorRole == Role.MANAGER) {
                predicates.add(root.get("property").get("id").in(accessiblePropertyIds));
            }
            Optional.ofNullable(action).ifPresent(act -> predicates.add(builder.equal(root.get("action"), act)));
            Optional.ofNullable(entityType).ifPresent(type -> predicates.add(builder.equal(root.get("entityType"), type)));
            Optional.ofNullable(fromDate)
                    .map(date -> date.atStartOfDay(zoneId).toInstant())
                    .ifPresent(start -> predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), start)));
            Optional.ofNullable(toDate)
                    .map(date -> date.atTime(LocalTime.MAX).atZone(zoneId).toInstant())
                    .ifPresent(end -> predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), end)));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditLogResponseDTO mapToResponse(AuditLogEntity entity) {
        UUID orgId = Optional.ofNullable(entity.getOrganization()).map(OrganizationEntity::getId).orElse(null);
        UUID propId = Optional.ofNullable(entity.getProperty()).map(PropertyEntity::getId).orElse(null);
        String propName = Optional.ofNullable(entity.getProperty()).map(PropertyEntity::getName).orElse(null);
        UUID actorId = Optional.ofNullable(entity.getActorUser()).map(User::getId).orElse(null);
        return new AuditLogResponseDTO(
                entity.getId(),
                orgId,
                propId,
                propName,
                actorId,
                entity.getActorEmail(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getSummary(),
                auditSanitizer.parseSafeJson(entity.getMetadataJson()),
                entity.getClientIp(),
                entity.getTraceId(),
                Optional.ofNullable(entity.getCreatedAt()).orElseGet(Instant::now)
        );
    }

    private String resolveActorEmail(AuditLogCommand command) {
        return Optional.ofNullable(command.actorUser())
                .map(User::getEmail)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> StringUtils.left(StringUtils.trimToNull(command.actorEmailFallback()), 255));
    }

    private String resolveCurrentClientIp() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(this::extractTrustedIp)
                .orElse(null);
    }

    private String extractTrustedIp(HttpServletRequest request) {
        return TrustedProxyIpResolver.resolveClientIp(request, appProperties.getRateLimit().getTrustedProxies());
    }
}

