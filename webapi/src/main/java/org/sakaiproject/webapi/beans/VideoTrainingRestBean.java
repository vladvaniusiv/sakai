package org.sakaiproject.webapi.beans;

import java.util.ArrayList;
import java.util.List;

public class VideoTrainingRestBean {

    private String id;
    private String title;
    private String description;
    private String providerType;
    private String sourceReference;
    private Long fileSizeBytes;
    private String visibilityScope;
    private String publicationStatus;
    private int lessonLinkCount;
    private List<String> categoryIds = new ArrayList<>();
    private String requiredViewPermission;
    private boolean canView;
    private boolean canManage;
    private boolean canManageCaptions;
    private boolean canViewAnalytics;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getVisibilityScope() {
        return visibilityScope;
    }

    public void setVisibilityScope(String visibilityScope) {
        this.visibilityScope = visibilityScope;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(String publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public int getLessonLinkCount() {
        return lessonLinkCount;
    }

    public void setLessonLinkCount(int lessonLinkCount) {
        this.lessonLinkCount = lessonLinkCount;
    }

    public List<String> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<String> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public String getRequiredViewPermission() {
        return requiredViewPermission;
    }

    public void setRequiredViewPermission(String requiredViewPermission) {
        this.requiredViewPermission = requiredViewPermission;
    }

    public boolean isCanView() {
        return canView;
    }

    public void setCanView(boolean canView) {
        this.canView = canView;
    }

    public boolean isCanManage() {
        return canManage;
    }

    public void setCanManage(boolean canManage) {
        this.canManage = canManage;
    }

    public boolean isCanManageCaptions() {
        return canManageCaptions;
    }

    public void setCanManageCaptions(boolean canManageCaptions) {
        this.canManageCaptions = canManageCaptions;
    }

    public boolean isCanViewAnalytics() {
        return canViewAnalytics;
    }

    public void setCanViewAnalytics(boolean canViewAnalytics) {
        this.canViewAnalytics = canViewAnalytics;
    }
}
