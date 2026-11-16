package com.upcraft.stay.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.domain.entity.Meal;

/**
 * Persistence access for daily and weekly PG food menus.
 */
public interface MealRepository extends JpaRepository<Meal, UUID> {

        List<Meal> findByDateScheduledAndAvailableTrue(LocalDate date);

        List<Meal> findByDateScheduledBetweenAndAvailableTrue(LocalDate start, LocalDate end);

        @EntityGraph(attributePaths = { "organization", "property" })
        List<Meal> findAllByOrganizationIdAndPropertyIdInAndDateScheduledBetweenOrderByDateScheduledAscTypeAsc(
                        UUID organizationId,
                        Collection<UUID> propertyIds,
                        LocalDate startDate,
                        LocalDate endDate);

        @EntityGraph(attributePaths = { "organization", "property" })
        List<Meal> findAllByOrganizationIdAndDateScheduledBetweenOrderByDateScheduledAscTypeAsc(
                        UUID organizationId,
                        LocalDate startDate,
                        LocalDate endDate);

        @EntityGraph(attributePaths = { "organization", "property" })
        Optional<Meal> findWithDetailsById(UUID id);
}
