package com.tortoise.common;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }
}
