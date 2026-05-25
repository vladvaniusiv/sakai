package org.sakaiproject.videotraining.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.api.app.scheduler.ScheduledInvocationCommand;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJobStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.repository.VideoTrainingProcessJobRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;
import org.sakaiproject.videotraining.api.service.VideoTrainingOAuthCredentialsService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;

@Slf4j
public class ExternalUploadJob implements ScheduledInvocationCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_APPLICATION_NAME = "Sakai Video Training";

    @Setter
    private VideoTrainingVideoRepository videoRepository;

    @Setter
    private VideoTrainingProcessJobRepository processJobRepository;

    @Setter
    private VideoTrainingOAuthCredentialsService credentialsService;

    @Setter
    private SessionManager sessionManager;

    @Override
    public void execute(String opaqueContext) {
        logIn();
        String videoId = StringUtils.trimToNull(opaqueContext);
        log.info("ExternalUploadJob - executing for videoId {}", videoId);

        try {
            if (StringUtils.isBlank(videoId)) {
                log.warn("ExternalUploadJob - received blank videoId context");
                return;
            }

            List<VideoTrainingProcessJob> jobs = processJobRepository.findByVideoIdOrderByModifiedOnDesc(videoId);
            if (jobs == null || jobs.isEmpty()) {
                log.warn("ExternalUploadJob - no process job found for video {}", videoId);
                return;
            }

            VideoTrainingProcessJob job = jobs.get(0);
            if (!(job.getStatus() == VideoTrainingProcessJobStatus.PENDING || job.getStatus() == VideoTrainingProcessJobStatus.RETRY)) {
                log.info("ExternalUploadJob - job {} for video {} is already in status {}, skipping", job.getId(), videoId, job.getStatus());
                return;
            }

            processSingleJob(job);
        } catch (RuntimeException ex) {
            log.error("ExternalUploadJob - failed", ex);
        } finally {
            try {
                logOut();
            } catch (Exception ex) {
                log.warn("ExternalUploadJob - failed to cleanup Sakai session.", ex);
            }
        }
    }

    private void processSingleJob(VideoTrainingProcessJob job) {
        if (job == null || StringUtils.isBlank(job.getVideoId()) || StringUtils.isBlank(job.getTempFilePath())) {
            return;
        }

        log.info("ExternalUploadJob - starting job {} for video {}", job.getId(), job.getVideoId());

        job.setStatus(VideoTrainingProcessJobStatus.PROCESSING);
        job.setErrorMessage(null);
        job.setModifiedOn(Instant.now());
        processJobRepository.save(job);

        VideoTrainingVideo video = videoRepository.findById(job.getVideoId()).orElse(null);
        if (video == null) {
            failJob(job, null, "Video not found for external upload: " + job.getVideoId());
            return;
        }

        if (!isExternalUploadProvider(video.getProviderType())) {
            failJob(job, video, "Video is not configured for an external upload destination.");
            return;
        }

        Path inputFile = Paths.get(job.getTempFilePath());
        Path workDir = inputFile.getParent();
        if (workDir == null || !Files.exists(inputFile)) {
            failJob(job, video, "Temporary upload not found for external upload processing.");
            return;
        }

        try {
            VideoTrainingOAuthCredentials credentials = credentialsService.getCredentials(video.getProviderType()).orElse(null);
            if (credentials == null || StringUtils.isBlank(credentials.getClientSecret())) {
                failJob(job, video, "OAuth credentials are not configured for " + video.getProviderType());
                return;
            }

            if (video.getProviderType() != VideoProviderType.YOUTUBE_UPLOAD) {
                failJob(job, video, "Unsupported external upload provider: " + video.getProviderType());
                return;
            }

            String sourceReference = uploadToYouTube(video, inputFile, credentials);

            video.setSourceReference(sourceReference);
            video.setProviderType(VideoProviderType.EXTERNAL);
            video.setPublicationStatus(VideoPublicationStatus.PUBLISHED);
            video.setModifiedOn(Instant.now());
            videoRepository.save(video);

            job.setStatus(VideoTrainingProcessJobStatus.COMPLETED);
            job.setErrorMessage(null);
            job.setModifiedOn(Instant.now());
            processJobRepository.save(job);

            deleteRecursively(workDir);
            log.info("ExternalUploadJob - completed video {}", video.getId());
        } catch (Exception ex) {
            failJob(job, video, StringUtils.defaultIfBlank(ex.getMessage(), "External upload failed."));
        }
    }

    private String uploadToYouTube(VideoTrainingVideo video, Path inputFile, VideoTrainingOAuthCredentials credentials) throws IOException {
        String contentType = detectContentType(inputFile);
        GoogleCredential googleCredential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(JacksonFactory.getDefaultInstance())
                .setClientSecrets(credentials.getClientId(), credentials.getClientSecret())
                .build();
        googleCredential.setRefreshToken(credentials.getRefreshToken());
        try {
            if (!googleCredential.refreshToken()) {
                throw new IOException("Unable to refresh Google access token");
            }
        } catch (IOException ex) {
            throw ex;
        }

        HttpRequestInitializer initializer = request -> {
            googleCredential.initialize(request);
            request.setConnectTimeout(120000);
            request.setReadTimeout(120000);
        };

        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), initializer)
                .setApplicationName(DEFAULT_APPLICATION_NAME)
                .build();

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(StringUtils.defaultIfBlank(video.getTitle(), DEFAULT_APPLICATION_NAME));
        snippet.setDescription(StringUtils.defaultString(video.getDescription()));

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus("private");

        Video body = new Video();
        body.setSnippet(snippet);
        body.setStatus(status);

        FileContent mediaContent = new FileContent(contentType, inputFile.toFile());
        com.google.api.services.youtube.YouTube.Videos.Insert insert = youtube.videos().insert(Arrays.asList("snippet", "status"), body, mediaContent);
        insert.getMediaHttpUploader().setDirectUploadEnabled(false);
        insert.getMediaHttpUploader().setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
        Video uploaded = insert.execute();
        if (uploaded == null || StringUtils.isBlank(uploaded.getId())) {
            throw new IOException("YouTube did not return a video id");
        }
        return "https://www.youtube.com/watch?v=" + uploaded.getId();
    }

    private boolean isExternalUploadProvider(VideoProviderType providerType) {
        return providerType == VideoProviderType.YOUTUBE_UPLOAD;
    }

    private String detectContentType(Path inputFile) {
        try {
            String contentType = Files.probeContentType(inputFile);
            return StringUtils.defaultIfBlank(contentType, "video/mp4");
        } catch (IOException e) {
            return "video/mp4";
        }
    }

    private void failJob(VideoTrainingProcessJob job, VideoTrainingVideo video, String message) {
        log.warn("ExternalUploadJob - job {} failed: {}", job != null ? job.getId() : null, message);
        if (job != null) {
            job.setStatus(VideoTrainingProcessJobStatus.FAILED);
            job.setErrorMessage(StringUtils.abbreviate(message, 4000));
            job.setModifiedOn(Instant.now());
            processJobRepository.save(job);
        }
        if (video != null) {
            video.setModifiedOn(Instant.now());
            videoRepository.save(video);
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try {
            Files.walk(path)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException ex) {
                            log.warn("ExternalUploadJob - could not delete {}", candidate, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("ExternalUploadJob - could not clean up {}", path, ex);
        }
    }

    private void logIn() {
        Session sakaiSession = sessionManager.getCurrentSession();
        sakaiSession.setUserId("admin");
        sakaiSession.setUserEid("admin");
    }

    private void logOut() {
        final Session currentSession = sessionManager.getCurrentSession();
        currentSession.invalidate();
    }
}