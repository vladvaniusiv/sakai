package org.sakaiproject.videotraining.api.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class VideoTrainingCourseGroup {

    private String siteId;
    private String siteTitle;
    private long totalVideos;
    private List<VideoTrainingVideo> videos = new ArrayList<>();

}
