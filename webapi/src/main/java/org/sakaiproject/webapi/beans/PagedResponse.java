package org.sakaiproject.webapi.beans;

import java.util.List;

import lombok.Data;

@Data
public class PagedResponse<T> {

	private List<T> data;
    private PaginationMetadataRestBean pagination;

	public PagedResponse(List<T> data, long totalCount, int page, int size) {
        this.data = data;
        this.pagination = new PaginationMetadataRestBean(totalCount, page, size);
    }

}
