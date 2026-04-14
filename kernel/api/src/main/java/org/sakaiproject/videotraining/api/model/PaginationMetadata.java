package org.sakaiproject.videotraining.api.model;

import lombok.Data;

@Data
public class PaginationMetadata {

    private final int page;
    private final int size;
    private final long totalCount;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrev;

    public PaginationMetadata(long totalCount, int page, int size) {
        this.totalCount = totalCount;
        this.size = size > 0 ? size : 10;

        this.totalPages = (totalCount == 0)
                ? 1
                : (int) Math.ceil((double) totalCount / this.size);

        this.page = Math.max(1, Math.min(page, this.totalPages));

        this.hasPrev = this.page > 1;
        this.hasNext = this.page < this.totalPages;
    }
}
