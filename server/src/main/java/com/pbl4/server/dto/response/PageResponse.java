package com.pbl4.server.dto.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/**
 * Kết quả phân trang gọn cho frontend:
 * { content, page (từ 0), size, totalElements, totalPages }.
 */
public class PageResponse<T> {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponse(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <E, T> PageResponse<T> of(
            Page<E> source, Function<E, T> mapper) {

        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }

    /** Chuẩn hóa tham số: page >= 0, 1 <= size <= MAX_SIZE. */
    public static Pageable pageable(int page, int size, Sort sort) {

        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        return PageRequest.of(safePage, safeSize, sort);
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}