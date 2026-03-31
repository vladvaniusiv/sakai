package org.sakaiproject.videotraining.api.repository;

import java.time.Instant;
import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;

public interface VideoTrainingVideoRepository extends SpringCrudRepository<VideoTrainingVideo, String> {

    List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId);

    List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId, String searchText, int offset, int limit);

        List<VideoTrainingVideo> findBySiteIdSorted(String siteId, String searchText, int offset, int limit,
            String sortField, boolean ascending);

        List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDescCursor(String siteId, String searchText,
            Instant cursorModifiedOn, String cursorVideoId, int limit);

    long countBySiteId(String siteId, String searchText);

    List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now);

    List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now, String searchText, int offset, int limit);

        List<VideoTrainingVideo> findVisibleBySiteIdAtSorted(String siteId, Instant now, String searchText, int offset, int limit,
            String sortField, boolean ascending);

        List<VideoTrainingVideo> findVisibleBySiteIdAtCursor(String siteId, Instant now, String searchText,
            Instant cursorModifiedOn, String cursorVideoId, int limit);

    long countVisibleBySiteIdAt(String siteId, Instant now, String searchText);

    List<VideoTrainingVideo> findGlobalPublishedCursor(String searchText, Instant cursorModifiedOn, String cursorVideoId, int limit);

        List<VideoTrainingVideo> findGlobalPublishedSorted(String searchText, int offset, int limit,
            String sortField, boolean ascending);

    long sumNativeStorageBytesBySiteId(String siteId);
}
