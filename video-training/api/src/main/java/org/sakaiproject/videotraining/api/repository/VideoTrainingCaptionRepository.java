package org.sakaiproject.videotraining.api.repository;

import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;

public interface VideoTrainingCaptionRepository extends SpringCrudRepository<VideoTrainingCaption, String> {

    List<VideoTrainingCaption> findByVideoIdOrderByLanguageTagAsc(String videoId);
}
