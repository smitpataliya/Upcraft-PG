package com.upcraft.stay.search.dto;

import java.util.List;

/**
 * Role- and property-scoped global search payload bounded to a maximum of 5 hits per category.
 */
public record GlobalSearchResponseDTO(
        String query,
        List<SearchResultItemDTO> tenants,
        List<SearchResultItemDTO> roomsAndBeds,
        List<SearchResultItemDTO> invoices,
        List<SearchResultItemDTO> payments,
        List<SearchResultItemDTO> complaints,
        int totalHits
) {
}

