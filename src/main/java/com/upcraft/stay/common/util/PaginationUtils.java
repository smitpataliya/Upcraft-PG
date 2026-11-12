package com.upcraft.stay.common.util;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.exception.ValidationException;

/**
 * Validates pagination sizes and sort fields against configured limits and indexed columns.
 */
public final class PaginationUtils {

    private PaginationUtils() {
    }

    /**
     * Builds a bounded {@link Pageable} using configured default and maximum page sizes.
     *
     * @param page             zero-based page index
     * @param size             requested page size (falls back to configured default when non-positive)
     * @param sortBy           requested sort column/property
     * @param sortDirection    {@code ASC} or {@code DESC}
     * @param allowedSortField whitelist of indexed entity properties allowed for sorting
     * @param defaultSortField fallback sort property when {@code sortBy} is blank
     * @param appProperties    typed application settings
     * @return validated {@link Pageable}
     */
    public static Pageable createPageable(
            int page,
            Integer size,
            String sortBy,
            String sortDirection,
            Set<String> allowedSortField,
            String defaultSortField,
            AppProperties appProperties
    ) {
        if (page < 0) {
            throw new ValidationException("Page index must be zero or positive");
        }
        int resolvedSize = (size == null || size <= 0) ? appProperties.getPagination().getDefaultSize() : size;
        int maxSize = appProperties.getPagination().getMaxSize();
        if (resolvedSize > maxSize) {
            throw new ValidationException(String.format("Page size cannot exceed %d", maxSize));
        }

        String resolvedSort = StringUtils.isBlank(sortBy) ? defaultSortField : sortBy.trim();
        if (!allowedSortField.contains(resolvedSort)) {
            throw new ValidationException(String.format("Sorting by '%s' is not supported", resolvedSort));
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(StringUtils.trimToEmpty(sortDirection))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, resolvedSize, Sort.by(direction, resolvedSort));
    }
}

