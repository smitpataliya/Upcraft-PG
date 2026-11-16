package com.upcraft.stay.search.dto;

import java.util.UUID;

import com.upcraft.stay.search.enums.SearchCategory;

/**
 * Normalized search hit returned inside grouped {@link GlobalSearchResponseDTO} buckets.
 */
public record SearchResultItemDTO(
        UUID id,
        SearchCategory category,
        String title,
        String subtitle,
        UUID propertyId,
        String propertyName,
        String routeHint,
        String badge
) {
}

