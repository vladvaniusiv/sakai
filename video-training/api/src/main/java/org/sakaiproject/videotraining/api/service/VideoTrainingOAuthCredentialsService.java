package org.sakaiproject.videotraining.api.service;

import java.util.List;
import java.util.Optional;

import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;

public interface VideoTrainingOAuthCredentialsService {

    List<VideoTrainingOAuthCredentials> getAllCredentials();

    Optional<VideoTrainingOAuthCredentials> getCredentials(VideoProviderType providerType);

    VideoTrainingOAuthCredentials saveCredentials(VideoProviderType providerType, String clientId, String apiKey, String clientSecret, String refreshToken);

    boolean isConfigured(VideoProviderType providerType);
}