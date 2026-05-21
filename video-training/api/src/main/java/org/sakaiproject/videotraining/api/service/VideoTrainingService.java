package org.sakaiproject.videotraining.api.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsEvent;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsSummary;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.model.VideoTrainingCategory;
import org.sakaiproject.videotraining.api.model.VideoTrainingCourseGroup;
import org.sakaiproject.videotraining.api.model.VideoTrainingLessonLink;
import org.sakaiproject.videotraining.api.model.VideoTrainingUserVideoPreference;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;

public interface VideoTrainingService {

    VideoTrainingVideo updateVideoVisibility(String videoId, VideoVisibilityScope newScope);

    VideoTrainingVideo updateVideoStatus(String videoId, VideoPublicationStatus newStatus);

    VideoTrainingVideo updateVideoSchedule(String videoId, Instant releaseDate, Instant retractDate);

    VideoTrainingVideo saveVideo(VideoTrainingVideo video);

    Optional<VideoTrainingVideo> getVideoById(String videoId);

    void deleteVideo(String videoId);

    List<VideoTrainingVideo> getSiteLibrary(String siteId);

    List<VideoTrainingVideo> getSiteLibraryPage(String siteId, String searchText, int page, int size);

    List<VideoTrainingVideo> getSiteLibraryPageForOwner(String siteId, String ownerId, String searchText, int page, int size);

    List<VideoTrainingVideo> getSiteLibraryCursor(String siteId, String searchText, Instant cursorModifiedOn, String cursorVideoId, int size);

    List<VideoTrainingVideo> getSiteLibrarySorted(String siteId, String searchText, int offset, int size,
        String sortField, boolean ascending);

    List<VideoTrainingVideo> getSiteLibrarySortedForOwner(String siteId, String ownerId, String searchText, int offset, int size,
        String sortField, boolean ascending);

    long countSiteLibrary(String siteId, String searchText);

    long countSiteLibraryForOwner(String siteId, String ownerId, String searchText);

    long countSiteViewableVideosForUser(String siteId, String userId, String searchText);

    long countGlobalVideos(String searchText);

    List<VideoTrainingVideo> getVisibleVideosForUser(String siteId, String userId, Instant now);

    List<VideoTrainingVideo> getVisibleVideosForUserPage(String siteId, String userId, Instant now, String searchText, int page, int size);

    List<VideoTrainingVideo> getVisibleVideosForUserSorted(String siteId, String userId, Instant now, String searchText,
        int offset, int size, String sortField, boolean ascending);

    List<VideoTrainingVideo> getVisibleVideosForUserCursor(String siteId, String userId, Instant now, String searchText,
        Instant cursorModifiedOn, String cursorVideoId, int size);

    long countVisibleVideosForUser(String siteId, String userId, Instant now, String searchText);

    long countGlobalVideosForUser(String userId, String searchText);

    /**
     * Return the total count of videos for a site as seen by a given user, delegating permission checks.
     */
    long countSiteVideosForUser(String siteId, String userId, String searchText);

    long countCategoriesForSite(String siteId, String userId);

    List<VideoTrainingVideo> getVisibleGlobalVideosPage(String searchText, int page, int size);

    List<VideoTrainingVideo> getGlobalVideosForUser(String userId, String searchText, int page, int size);

    /**
     * Return a page of videos for a site as seen by a given user, delegating permission checks.
     */
    List<VideoTrainingVideo> getSiteVideosForUserPage(String siteId, String userId, String searchText, int page, int size);

    /**
     * Return a page of videos for a site that the user can view (not edit).
     * Similar to `getSiteVideosForUserPage` but treats owner-level `manage` permission
     * as a viewer (i.e., returns visible videos rather than owner-only lists).
     */
    List<VideoTrainingVideo> getSiteViewableVideosForUserPage(String siteId, String userId, String searchText, int page, int size);

    List<VideoTrainingVideo> getAdminAllGlobalVideosPage(String searchText, int page, int size);

    long adminCountAllGlobal(String searchText);

    List<VideoTrainingVideo> getGlobalVideosCursor(String searchText, Instant cursorModifiedOn, String cursorVideoId, int size);

    List<VideoTrainingVideo> getGlobalVideosSorted(String searchText, int offset, int size,
        String sortField, boolean ascending);

    List<VideoTrainingCaption> getCaptionsForVideo(String videoId);

    VideoTrainingCaption saveCaption(VideoTrainingCaption caption);

    void deleteCaption(String captionId);

    void registerView(String siteId, String videoId, String userId, Instant when);

    List<VideoTrainingAnalyticsEvent> getEventsForVideo(String videoId);

    List<VideoTrainingAnalyticsSummary> getSiteAnalyticsSummary(String siteId);

    boolean canManageLibrary(String siteId, String userId);

    boolean hasManagePermission(String siteId, String userId);

    boolean hasViewPermission(String siteId, String userId);

    boolean canViewVideo(VideoTrainingVideo video, String userId, Instant now);

    boolean canViewAnalytics(String siteId, String userId);

    boolean canManageCategories(String siteId, String userId);

    boolean canManageCaptions(String siteId, String userId);

    Long getSiteStorageQuotaBytes(String siteId);

    long getSiteStorageUsageBytes(String siteId);

    void registerAudit(String siteId, String userId, String action, String videoId, String details);

    List<VideoTrainingCategory> getCategories(String siteId, int offset, int limit);

    Optional<VideoTrainingCategory> getCategoryById(String categoryId);

    VideoTrainingCategory saveCategory(VideoTrainingCategory category);

    void deleteCategory(String categoryId);

    List<String> getVideoCategoryIds(String videoId);

    void setVideoCategoryIds(String videoId, List<String> categoryIds);

    List<VideoTrainingCourseGroup> getCourseGroupsForSites(List<String> siteIds, String userId, Instant now, int limitPerSite);

    VideoTrainingLessonLink saveLessonLink(VideoTrainingLessonLink lessonLink);

    void deleteLessonLink(String lessonLinkId);

    List<VideoTrainingLessonLink> getLessonLinksForVideo(String videoId);

    VideoTrainingVideo promoteLessonResource(String siteId, String lessonPageId, String lessonItemId,
            String resourceReference, String title, String description, Long fileSizeBytes);

        Optional<VideoTrainingUserVideoPreference> getUserVideoPreference(String siteId, String videoId, String userId);

        Map<String, VideoTrainingUserVideoPreference> getUserVideoPreferences(String siteId, String userId, List<String> videoIds);

        void setUserFavorite(String siteId, String videoId, String userId, boolean favorite);

        void setUserWatchLater(String siteId, String videoId, String userId, boolean watchLater);

        List<VideoTrainingVideo> getUserFavoriteVideos(String siteId, String userId, Instant now);

        List<VideoTrainingVideo> getUserWatchLaterVideos(String siteId, String userId, Instant now);

    /**
     * Get the publication statuses that are valid transition targets from the given current status.
     * Takes into account the visibility scope and moderation settings.
     * @param currentStatus the current publication status (null treated as DRAFT)
     * @param visibilityScope the visibility scope of the video
     * @return array of valid target statuses, never null but may be empty if no transitions allowed
     */
    VideoPublicationStatus[] getValidPublicationStatusTransitions(VideoPublicationStatus currentStatus,
            org.sakaiproject.videotraining.api.model.VideoVisibilityScope visibilityScope);
}
