package org.sakaiproject.videotraining.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoTrainingVideoView {
	private String id;
    private String title;
    private String description;
    private String siteName;
    private String releaseDisplay;
    private String retractDisplay;
    private String thumbnailUrl;
    private boolean thumbnailIsVideo;
    private String visibilityScope;
    private String publicationStatus;
}
