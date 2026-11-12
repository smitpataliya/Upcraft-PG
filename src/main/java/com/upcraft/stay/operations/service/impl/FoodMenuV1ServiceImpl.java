package com.upcraft.stay.operations.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.domain.entity.Meal;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.operations.dto.MealV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertMealV1RequestDTO;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.service.FoodMenuV1Service;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.MealRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Implements property-scoped weekly food menu management and read-only resident menu view.
 */
@Service
@RequiredArgsConstructor
public class FoodMenuV1ServiceImpl implements FoodMenuV1Service {

    private final AccessGuardService accessGuardService;
    private final MealRepository mealRepository;
    private final TenancyRepository tenancyRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public List<MealV1ResponseDTO> getWeeklyMenu(String principalEmail, UUID propertyId, LocalDate startDate) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        LocalDate effectiveStart = Optional.ofNullable(startDate)
                .orElseGet(() -> LocalDate.now(ZoneId.of(appProperties.getTimezone())));
        LocalDate effectiveEnd = effectiveStart.plusDays(6);

        if (currentUser.getRole() == Role.PG_MEMBER) {
            List<UUID> residentPropertyIds = tenancyRepository.findSelfResidencyTenancies(
                            currentUser.getId(), organization.getId(), currentUser.getEmail().trim().toLowerCase(Locale.ROOT)
                    ).stream()
                    .map(tenancy -> tenancy.getProperty().getId())
                    .distinct()
                    .toList();
            List<Meal> meals = residentPropertyIds.isEmpty()
                    ? mealRepository.findAllByOrganizationIdAndDateScheduledBetweenOrderByDateScheduledAscTypeAsc(
                            organization.getId(), effectiveStart, effectiveEnd)
                    : mealRepository.findAllByOrganizationIdAndPropertyIdInAndDateScheduledBetweenOrderByDateScheduledAscTypeAsc(
                            organization.getId(), residentPropertyIds, effectiveStart, effectiveEnd);
            return meals.stream()
                    .filter(Meal::isAvailable)
                    .filter(Meal::isPublished)
                    .map(operationsMapper::toMealResponse)
                    .toList();
        }

        List<UUID> accessiblePropertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        if (accessiblePropertyIds.isEmpty()) {
            return List.of();
        }
        return mealRepository.findAllByOrganizationIdAndPropertyIdInAndDateScheduledBetweenOrderByDateScheduledAscTypeAsc(
                        organization.getId(), accessiblePropertyIds, effectiveStart, effectiveEnd)
                .stream()
                .map(operationsMapper::toMealResponse)
                .toList();
    }

    @Override
    @Transactional
    public MealV1ResponseDTO createMeal(String principalEmail, UpsertMealV1RequestDTO request) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity property = accessGuardService.requirePropertyManagementAccess(
                currentUser, request.propertyId(), "NOTICES"
        );
        Meal meal = Meal.builder()
                .organization(property.getOrganization())
                .property(property)
                .name(request.name().trim())
                .description(StringUtils.trimToNull(request.description()))
                .imageUrl(StringUtils.trimToNull(request.imageUrl()))
                .type(request.type())
                .servingTime(StringUtils.trimToNull(request.servingTime()))
                .veg(request.veg())
                .available(request.available())
                .published(request.published())
                .dateScheduled(request.dateScheduled())
                .dayOfWeek(request.dateScheduled().getDayOfWeek().name())
                .build();
        Meal saved = mealRepository.save(meal);
        if (saved.isPublished() && saved.isAvailable()) {
            notifyPropertyResidents(property, saved);
        }
        return operationsMapper.toMealResponse(saved);
    }

    @Override
    @Transactional
    public MealV1ResponseDTO updateMeal(String principalEmail, UUID mealId, UpsertMealV1RequestDTO request) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Meal existing = mealRepository.findWithDetailsById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal entry not found"));
        PropertyEntity property = accessGuardService.requirePropertyManagementAccess(
                currentUser, request.propertyId(), "NOTICES"
        );
        if (Optional.ofNullable(existing.getOrganization()).isPresent()
                && !existing.getOrganization().getId().equals(property.getOrganization().getId())) {
            throw new ForbiddenException("Meal entry is outside your organization scope");
        }
        existing.setOrganization(property.getOrganization());
        existing.setProperty(property);
        existing.setName(request.name().trim());
        existing.setDescription(StringUtils.trimToNull(request.description()));
        existing.setImageUrl(StringUtils.trimToNull(request.imageUrl()));
        existing.setType(request.type());
        existing.setServingTime(StringUtils.trimToNull(request.servingTime()));
        existing.setVeg(request.veg());
        existing.setAvailable(request.available());
        existing.setPublished(request.published());
        existing.setDateScheduled(request.dateScheduled());
        existing.setDayOfWeek(request.dateScheduled().getDayOfWeek().name());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(currentUser.getEmail());
        return operationsMapper.toMealResponse(mealRepository.save(existing));
    }

    @Override
    @Transactional
    public void deleteMeal(String principalEmail, UUID mealId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Meal existing = mealRepository.findWithDetailsById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal entry not found"));
        if (Optional.ofNullable(existing.getProperty()).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(currentUser, existing.getProperty().getId(), "NOTICES");
        } else if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can delete organization-wide menu entries");
        }
        existing.setRecordStatus(AppConstants.STATUS_DELETED);
        existing.setDeletedAt(LocalDateTime.now());
        existing.setDeletedBy(currentUser.getEmail());
        mealRepository.save(existing);
    }

    private void notifyPropertyResidents(PropertyEntity property, Meal meal) {
        List<User> recipients = tenancyRepository.findAccessibleTenancies(
                        List.of(property.getId()), false, null, false, null, Pageable.unpaged()
                ).stream()
                .map(TenancyEntity::getTenant)
                .map(TenantEntity::getUser)
                .filter(Objects::nonNull)
                .toList();
        notificationDispatchService.dispatchToUsers(
                property.getOrganization(), property, recipients,
                NotificationEventType.MENU_UPDATED,
                "Food Menu Updated (" + meal.getType().name() + ")",
                meal.getName() + " scheduled for " + meal.getDateScheduled(),
                "MEAL", meal.getId()
        );
    }
}

