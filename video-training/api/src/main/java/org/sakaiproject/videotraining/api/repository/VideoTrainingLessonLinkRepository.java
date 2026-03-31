package org.sakaiproject.videotraining.api.repository;

import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingLessonLink;

public interface VideoTrainingLessonLinkRepository extends SpringCrudRepository<VideoTrainingLessonLink, String> {

    List<VideoTrainingLessonLink> findByVideoIdOrderByCreatedOnDesc(String videoId);

    List<VideoTrainingLessonLink> findBySiteIdAndLessonPageId(String siteId, String lessonPageId);

    void deleteByVideoId(String videoId);
}
