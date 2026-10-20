package com.docdrift.Docdrift.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paged results: {content, page, size, totalElements, totalPages}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
