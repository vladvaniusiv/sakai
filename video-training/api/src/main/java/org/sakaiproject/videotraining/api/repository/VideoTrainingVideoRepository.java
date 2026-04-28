package org.sakaiproject.videotraining.api.repository;

import java.time.Instant;
import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;

public interface VideoTrainingVideoRepository extends SpringCrudRepository<VideoTrainingVideo, String> {

    List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId);

    List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId, String searchText, int offset, int limit);

    List<VideoTrainingVideo> findBySiteIdAndOwnerIdOrderByModifiedOnDesc(String siteId, String ownerId);

    List<VideoTrainingVideo> findBySiteIdAndOwnerIdOrderByModifiedOnDesc(String siteId, String ownerId, String searchText, int offset, int limit);

    List<VideoTrainingVideo> findBySiteIdAndOwnerIdSorted(String siteId, String ownerId, String searchText, int offset, int limit,
        String sortField, boolean ascending);

    List<VideoTrainingVideo> findBySiteIdSorted(String siteId, String searchText, int offset, int limit,
        String sortField, boolean ascending);

    List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDescCursor(String siteId, String searchText,
        Instant cursorModifiedOn, String cursorVideoId, int limit);

    long countBySiteId(String siteId, String searchText);

    long countBySiteIdAndOwnerId(String siteId, String ownerId, String searchText);

    long countByGlobal(String searchText);

    List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now);

    List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now, String searchText, int offset, int limit);

    List<VideoTrainingVideo> findVisibleBySiteIdAtSorted(String siteId, Instant now, String searchText, int offset, int limit,
        String sortField, boolean ascending);

    List<VideoTrainingVideo> findVisibleBySiteIdAtCursor(String siteId, Instant now, String searchText,
        Instant cursorModifiedOn, String cursorVideoId, int limit);

    long countVisibleBySiteIdAt(String siteId, Instant now, String searchText);

    List<VideoTrainingVideo> findVisibleByGlobal(String searchText, int offset, int size);

    long adminCountAllGlobal(String searchText);

    List<VideoTrainingVideo> adminFindAllGlobal(String searchText, int offset, int size);

    /**
     * Count all videos (no visibility/scope restriction). If searchText is empty or null,
     * returns total count of videos.
     */
    long countAll(String searchText);

    /**
     * Return all videos (no visibility/scope restriction) in a paginated fashion.
     */
    List<VideoTrainingVideo> findAll(String searchText, int offset, int size);

    List<VideoTrainingVideo> findGlobalPublishedCursor(String searchText, Instant cursorModifiedOn, String cursorVideoId, int limit);

    List<VideoTrainingVideo> findGlobalPublishedSorted(String searchText, int offset, int limit,
        String sortField, boolean ascending);

    long sumNativeStorageBytesBySiteId(String siteId);
}
