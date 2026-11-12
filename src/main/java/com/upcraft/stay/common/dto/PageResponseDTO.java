package com.upcraft.stay.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Standard paginated list response DTO preventing raw Spring {@code Page} leakage.
 *
 * @param <T> item DTO type
 */
public record PageResponseDTO<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean last
) {

    /**
     * Converts a Spring Data {@link Page} into an immutable {@link PageResponseDTO}.
     *
     * @param page Spring Data page
     * @param <T>  element type
     * @return paginated DTO
     */
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(
                List.copyOf(page.getContent()),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isLast()
        );
    }
}

