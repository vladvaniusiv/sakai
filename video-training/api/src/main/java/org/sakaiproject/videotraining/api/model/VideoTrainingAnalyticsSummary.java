package org.sakaiproject.videotraining.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoTrainingAnalyticsSummary {

    private String videoId;
    private long viewCount;
    private long uniqueViewerCount;

}
