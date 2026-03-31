package org.sakaiproject.videotraining.api.repository;

import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideoCategory;

public interface VideoTrainingVideoCategoryRepository extends SpringCrudRepository<VideoTrainingVideoCategory, String> {

    List<VideoTrainingVideoCategory> findByVideoId(String videoId);

    void deleteByVideoId(String videoId);
}
