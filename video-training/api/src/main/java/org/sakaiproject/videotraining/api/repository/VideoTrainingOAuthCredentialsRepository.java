package org.sakaiproject.videotraining.api.repository;

import java.util.List;
import java.util.Optional;

import org.sakaiproject.springframework.data.SpringCrudRepository;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;

public interface VideoTrainingOAuthCredentialsRepository extends SpringCrudRepository<VideoTrainingOAuthCredentials, String> {

    Optional<VideoTrainingOAuthCredentials> findByProviderType(VideoProviderType providerType);

    List<VideoTrainingOAuthCredentials> findAllByOrderByProviderTypeAsc();
}