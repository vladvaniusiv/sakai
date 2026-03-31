package org.sakaiproject.videotraining.api.repository;

import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsEvent;

public interface VideoTrainingAnalyticsEventRepository extends SpringCrudRepository<VideoTrainingAnalyticsEvent, String> {

    List<VideoTrainingAnalyticsEvent> findByVideoIdOrderByEventTimeDesc(String videoId);

    List<VideoTrainingAnalyticsEvent> findBySiteIdAndEventType(String siteId, String eventType);
}
