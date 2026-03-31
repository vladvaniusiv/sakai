package org.sakaiproject.videotraining.api.model;

public class VideoTrainingAnalyticsSummary {

    private String videoId;
    private long viewCount;
    private long uniqueViewerCount;

    public VideoTrainingAnalyticsSummary() {
    }

    public VideoTrainingAnalyticsSummary(String videoId, long viewCount, long uniqueViewerCount) {
        this.videoId = videoId;
        this.viewCount = viewCount;
        this.uniqueViewerCount = uniqueViewerCount;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getUniqueViewerCount() {
        return uniqueViewerCount;
    }

    public void setUniqueViewerCount(long uniqueViewerCount) {
        this.uniqueViewerCount = uniqueViewerCount;
    }
}
