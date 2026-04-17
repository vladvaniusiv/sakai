package org.sakaiproject.videotraining.api.repository;

import java.util.List;
import java.util.Optional;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingUserVideoPreference;

public interface VideoTrainingUserVideoPreferenceRepository extends SpringCrudRepository<VideoTrainingUserVideoPreference, String> {

    Optional<VideoTrainingUserVideoPreference> findBySiteIdAndUserIdAndVideoId(String siteId, String userId, String videoId);

    List<VideoTrainingUserVideoPreference> findBySiteIdAndUserIdAndVideoIds(String siteId, String userId, List<String> videoIds);

    List<VideoTrainingUserVideoPreference> findBySiteIdAndUserIdAndFavoriteTrueOrderByModifiedOnDesc(String siteId, String userId);

    List<VideoTrainingUserVideoPreference> findBySiteIdAndUserIdAndWatchLaterTrueOrderByModifiedOnDesc(String siteId, String userId);

    void deleteByVideoId(String videoId);
}
