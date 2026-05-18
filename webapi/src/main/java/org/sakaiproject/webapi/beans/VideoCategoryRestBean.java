package org.sakaiproject.webapi.beans;

import java.time.Instant;

import org.sakaiproject.videotraining.api.model.VideoTrainingCategory;

import lombok.Data;

@Data
public class VideoCategoryRestBean {
	private String id;
	private String name;
	private String parentCategoryId;
	private Instant createdOn;
	private Instant modifiedOn;

	public VideoCategoryRestBean(VideoTrainingCategory category) {
		this.id = category.getId();
		this.name = category.getName();
		this.parentCategoryId = category.getParentCategoryId();
		this.createdOn = category.getCreatedOn();
		this.modifiedOn = category.getModifiedOn();
	}
}
