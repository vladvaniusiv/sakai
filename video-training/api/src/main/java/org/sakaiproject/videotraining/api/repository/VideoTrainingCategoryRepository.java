package org.sakaiproject.videotraining.api.repository;

import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingCategory;

public interface VideoTrainingCategoryRepository extends SpringCrudRepository<VideoTrainingCategory, String> {

    List<VideoTrainingCategory> findBySiteIdOrderBySortOrderAscNameAsc(String siteId);
}
