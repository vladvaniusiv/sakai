package org.sakaiproject.videotraining.api.model;

import java.util.ArrayList;
import java.util.List;

public class VideoTrainingCourseGroup {

    private String siteId;
    private String siteTitle;
    private long totalVideos;
    private List<VideoTrainingVideo> videos = new ArrayList<>();

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getSiteTitle() {
        return siteTitle;
    }

    public void setSiteTitle(String siteTitle) {
        this.siteTitle = siteTitle;
    }

    public long getTotalVideos() {
        return totalVideos;
    }

    public void setTotalVideos(long totalVideos) {
        this.totalVideos = totalVideos;
    }

    public List<VideoTrainingVideo> getVideos() {
        return videos;
    }

    public void setVideos(List<VideoTrainingVideo> videos) {
        this.videos = videos;
    }
}
