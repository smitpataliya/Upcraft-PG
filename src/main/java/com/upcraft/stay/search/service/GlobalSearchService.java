package com.upcraft.stay.search.service;

import java.util.UUID;

import com.upcraft.stay.search.dto.GlobalSearchResponseDTO;

/**
 * Service contract for bounded role-scoped global search across core PG entities.
 */
public interface GlobalSearchService {

    GlobalSearchResponseDTO search(String principalEmail, String queryText, UUID propertyId);
}

