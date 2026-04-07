package org.sakaiproject.webapi.beans;

import java.util.ArrayList;
import java.util.List;

import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;

import lombok.Data;
@Data
public class VideoTrainingRestBean {

    private String id;
    private String title;
    private String description;
    private String providerType;
    private String sourceReference;
    private Long fileSizeBytes;
    private String visibilityScope;
    private String publicationStatus;
    /*private boolean canView;
    private boolean canManage;
    private boolean canManageCaptions;
    private boolean canViewAnalytics;*/

    public void setProviderType(VideoProviderType providerType) {
        this.providerType = providerType != null ? providerType.name() : "";
    }

    public void setVisibilityScope(VideoVisibilityScope visibilityScope) {
        this.visibilityScope = visibilityScope != null ? visibilityScope.name() : "";
    }

    public void setPublicationStatus(VideoPublicationStatus publicationStatus) {
        this.publicationStatus = publicationStatus != null ? publicationStatus.name() : "";
    }

    public VideoTrainingRestBean(VideoTrainingVideo video) {
        this.id = video.getId();
        this.title = video.getTitle();
        this.description = video.getDescription();
        this.setProviderType(video.getProviderType());
        this.setSourceReference(video.getSourceReference());
        this.fileSizeBytes = video.getFileSizeBytes();
        this.setVisibilityScope(video.getVisibilityScope());
        this.setPublicationStatus(video.getPublicationStatus());
    }

}
