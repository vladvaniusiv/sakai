package org.sakaiproject.videotraining.api.model;

import java.util.List;

import lombok.Data;

@Data
public class PagedResponse<T> {

    private List<T> data;
    private PaginationMetadata pagination;

    public PagedResponse(List<T> data, long totalCount, int page, int size) {
        this.data = data;
        this.pagination = new PaginationMetadata(totalCount, page, size);
    }

}
