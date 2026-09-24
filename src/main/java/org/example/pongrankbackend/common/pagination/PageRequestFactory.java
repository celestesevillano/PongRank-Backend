package org.example.pongrankbackend.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Builds the Pageable used by list endpoints: defaults and a maximum size so a client cannot request everything at once
public final class PageRequestFactory {

    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "10";
    public static final int MAX_SIZE = 50;

    private PageRequestFactory() {
    }

    // Out-of-range values are adjusted: negative page -> 0, size below 1 -> 1, size above MAX_SIZE -> MAX_SIZE
    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
