package org.sakaiproject.videotraining.api.repository;

import java.util.Collection;
import java.util.List;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJobStatus;

public interface VideoTrainingProcessJobRepository extends SpringCrudRepository<VideoTrainingProcessJob, String> {

    List<VideoTrainingProcessJob> findByVideoIdOrderByModifiedOnDesc(String videoId);

    List<VideoTrainingProcessJob> findBySubmitterUserIdOrderByModifiedOnDesc(String submitterUserId);

    List<VideoTrainingProcessJob> findByStatusInOrderByModifiedOnAsc(Collection<VideoTrainingProcessJobStatus> statuses);
}