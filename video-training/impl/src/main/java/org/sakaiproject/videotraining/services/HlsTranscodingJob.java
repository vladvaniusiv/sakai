package org.sakaiproject.videotraining.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.api.app.scheduler.ScheduledInvocationCommand;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.util.Validator;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJobStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.repository.VideoTrainingProcessJobRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;
import org.sakaiproject.videotraining.api.service.VideoTrainingService;

@Slf4j
public class HlsTranscodingJob implements ScheduledInvocationCommand {

    private static final ResourceLoader RB = new ResourceLoader("video-training");
    private static final String MASTER_PLAYLIST = "master.m3u8";
    private static final String DEFAULT_BASE_FOLDER = "Video Training";
    private static final String DEFAULT_GLOBAL_ROOT = "/public/video-training/";
    private static final String DEFAULT_GLOBAL_ROOT_FOLDER = "videoTraining";
    private static final String BASE_FOLDER_PROPERTY = "video.training.basefolder";
    private static final String GLOBAL_ROOT_PROPERTY = "video.training.global.root";
    private static final String GLOBAL_ROOT_BASE_FOLDER_PROPERTY = "video.training.global.root.basefolder";
    private static final String LESSON_BASE_FOLDER_PROPERTY = "lessonbuilder.basefolder";
    private static final String HIDDEN_WITH_ACCESS_PROPERTY = "video.training.folder.hidden.withaccess";
    private static final String HLS_FFMPEG_PROPERTY = "video.training.hls.ffmpeg";
    private static final String DEFAULT_HLS_FFMPEG = "ffmpeg";

    @Setter
    private VideoTrainingVideoRepository videoRepository;

    @Setter
    private VideoTrainingProcessJobRepository processJobRepository;

    @Setter
    private VideoTrainingService videoTrainingService;

    @Setter
    private ContentHostingService contentHostingService;

    @Setter
    private SessionManager sessionManager;

    @Setter
    private EmailService emailService;

    @Setter
    private UserDirectoryService userDirectoryService;

    @Setter
    private ServerConfigurationService serverConfigurationService;

    @Override
    public void execute(String opaqueContext) {
        logIn();
        String videoId = StringUtils.trimToNull(opaqueContext);
        log.info("HlsTranscodingJob - executing for videoId {}", videoId);
        try {
            if (StringUtils.isBlank(videoId)) {
                log.warn("HlsTranscodingJob - received blank videoId context");
                return;
            }

            List<VideoTrainingProcessJob> jobs = processJobRepository.findByVideoIdOrderByModifiedOnDesc(videoId);
            if (jobs == null || jobs.isEmpty()) {
                log.warn("HlsTranscodingJob - no process job found for video {}", videoId);
                return;
            }

            VideoTrainingProcessJob job = jobs.get(0);
            if (!(job.getStatus() == VideoTrainingProcessJobStatus.PENDING
                    || job.getStatus() == VideoTrainingProcessJobStatus.RETRY)) {
                log.info("HlsTranscodingJob - job {} for video {} is already in status {}, skipping", job.getId(), videoId, job.getStatus());
                return;
            }

            processSingleJob(job);
        } catch (RuntimeException ex) {
            log.error("HlsTranscodingJob - failed", ex);
        } finally {
            try {
                logOut();
            } catch (Exception ex) {
                log.warn("HlsTranscodingJob - failed to cleanup Sakai session.", ex);
            }
        }
    }

    private void processSingleJob(VideoTrainingProcessJob job) {
        if (job == null || StringUtils.isBlank(job.getVideoId()) || StringUtils.isBlank(job.getTempFilePath())) {
            return;
        }

        log.info("HlsTranscodingJob - starting job {} for video {}", job.getId(), job.getVideoId());

        job.setStatus(VideoTrainingProcessJobStatus.PROCESSING);
        job.setErrorMessage(null);
        job.setModifiedOn(Instant.now());
        processJobRepository.save(job);

        VideoTrainingVideo video = videoRepository.findById(job.getVideoId()).orElse(null);
        if (video == null) {
            failJob(job, null, "Video not found for HLS processing: " + job.getVideoId());
            return;
        }

        Path inputFile = Paths.get(job.getTempFilePath());
        Path workDir = inputFile.getParent();
        if (workDir == null || !Files.exists(inputFile)) {
            failJob(job, video, "Temporary upload not found for HLS processing.");
            return;
        }

        Path outputDir = workDir.resolve("hls");
        try {
            Files.createDirectories(outputDir);
            renderVariants(inputFile, outputDir);
            try {
                extractThumbnail(inputFile, outputDir);
            } catch (Exception e) {
                log.warn("HlsTranscodingJob - The thumbnail for the video {}, could not be extracted, continuing...", video.getId(), e);
            }
            long outputBytes = calculateDirectorySize(outputDir);
            Long quotaBytes = videoTrainingService.getSiteStorageQuotaBytes(video.getSiteId());
            long usageBytes = videoTrainingService.getSiteStorageUsageBytes(video.getSiteId());

            if (quotaBytes != null && quotaBytes > 0 && usageBytes + outputBytes > quotaBytes) {
                failJob(job, video, "Uploading the generated HLS package would exceed the site quota.");
                return;
            }

            String masterResourceId = uploadGeneratedContent(video, outputDir);
            video.setSourceReference(masterResourceId);
            video.setFileSizeBytes(outputBytes);
            video.setProviderType(VideoProviderType.HLS_UPLOAD);
            video.setPublicationStatus(VideoPublicationStatus.PUBLISHED);
            video.setModifiedOn(Instant.now());
            videoRepository.save(video);

            job.setStatus(VideoTrainingProcessJobStatus.COMPLETED);
            job.setErrorMessage(null);
            job.setModifiedOn(Instant.now());
            processJobRepository.save(job);
            sendCompletionEmail(video, job, null);
            cleanupTempDirectory(workDir);
            log.info("HlsTranscodingJob - completed video {}", video.getId());
        } catch (IOException ex) {
            failJob(job, video, "HLS transcoder is not available on this server.");
        } catch (Exception ex) {
            failJob(job, video, "HLS processing failed.");
        }
    }

    private void extractThumbnail(Path inputFile, Path outputDir) throws IOException, InterruptedException {
        Path thumbnail = outputDir.resolve("thumbnail.jpg");
        List<String> command = new ArrayList<>();

        command.add(resolveFfmpegCommand());
        command.add("-y");
        command.add("-i");
        command.add(inputFile.toString());
        command.add("-ss");
        command.add("00:00:01.000");
        command.add("-vframes");
        command.add("1");
        command.add("-f");
        command.add("image2");
        command.add("-update");
        command.add("1");
        command.add("-vf");
        command.add("scale=640:-1");
        command.add("-q:v");
        command.add("4");
        command.add(thumbnail.toString());

        log.info("HlsTranscodingJob - Running FFmpeg for thumbnail: {}", String.join(" ", command));
        runProcess(command, outputDir);
    }

    private void renderVariants(Path inputFile, Path outputDir) throws IOException, InterruptedException {
        List<HlsVariant> variants = List.of(
                new HlsVariant("720p", 720, "3000k", "3500k", "6000k", 1280, 720),
                new HlsVariant("480p", 480, "1400k", "1600k", "3000k", 854, 480));

        for (HlsVariant variant : variants) {
            Path playlist = outputDir.resolve(variant.name + ".m3u8");
            Path segments = outputDir.resolve(variant.name + "_%03d.ts");
            List<String> command = new ArrayList<>();
            command.add(resolveFfmpegCommand());
            command.add("-y");
            command.add("-i");
            command.add(inputFile.toString());
            String videoFilter = String.format(
                "scale=w=%d:h=%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2",
                variant.width, variant.height, variant.width, variant.height
            );
            command.add("-vf");
            command.add(videoFilter);
            command.add("-c:v");
            command.add("libx264");
            command.add("-profile:v");
            command.add("main");
            command.add("-preset");
            command.add("veryfast");
            command.add("-b:v");
            command.add(variant.videoBitrate);
            command.add("-maxrate");
            command.add(variant.maxRate);
            command.add("-bufsize");
            command.add(variant.bufferSize);
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
            command.add("-f");
            command.add("hls");
            command.add("-hls_time");
            command.add("6");
            command.add("-hls_playlist_type");
            command.add("vod");
            command.add("-hls_flags");
            command.add("independent_segments");
            command.add("-hls_segment_filename");
            command.add(segments.toString());
            command.add(playlist.toString());

            runProcess(command, outputDir);
        }

        writeMasterPlaylist(outputDir, variants);
    }

    private void runProcess(List<String> command, Path workDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new IOException("HLS transcoder is not available on this server.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("HLS transcoding failed with exit code " + exitCode);
        }
    }

    private String resolveFfmpegCommand() {
        return StringUtils.trimToEmpty(serverConfigurationService.getString(HLS_FFMPEG_PROPERTY, DEFAULT_HLS_FFMPEG));
    }

    private void writeMasterPlaylist(Path outputDir, List<HlsVariant> variants) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");
        lines.add("#EXT-X-VERSION:3");
        lines.add("#EXT-X-INDEPENDENT-SEGMENTS");
        for (HlsVariant variant : variants) {
            lines.add("#EXT-X-STREAM-INF:BANDWIDTH=" + variant.bandwidth + ",RESOLUTION=" + variant.width + "x" + variant.height);
            lines.add(variant.name + ".m3u8");
        }
        Files.write(outputDir.resolve(MASTER_PLAYLIST), lines, StandardCharsets.UTF_8);
    }

    private long calculateDirectorySize(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    })
                    .sum();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private String uploadGeneratedContent(VideoTrainingVideo video, Path outputDir) throws IOException {
        String collectionId;
        try {
            collectionId = resolveTargetCollectionId(video.getSiteId(), video.getOwnerId(), video.getVisibilityScope());
            collectionId = ensureVideoHlsCollection(collectionId, video);
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(outputDir)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }

        String masterResourceId = null;
        List<String> uploadedResources = new ArrayList<>();
        try {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                String extension = extractExtension(filename);
                String baseName = extension.isEmpty() ? filename : filename.substring(0, filename.length() - extension.length());
                ContentResourceEdit edit = contentHostingService.addResource(collectionId, baseName, extension, 1);
                boolean committed = false;
                try {
                    byte[] content = Files.readAllBytes(file);
                    edit.setContent(content);
                    edit.setContentLength(content.length);
                    edit.setContentType(contentTypeFor(filename));
                    edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, filename);
                    contentHostingService.commitResource(edit, NotificationService.NOTI_NONE);
                    committed = true;
                    uploadedResources.add(edit.getId());
                    if (MASTER_PLAYLIST.equals(filename)) {
                        masterResourceId = edit.getId();
                    }
                } finally {
                    if (!committed) {
                        try {
                            contentHostingService.cancelResource(edit);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            for (String resourceId : uploadedResources) {
                try {
                    contentHostingService.removeResource(resourceId);
                } catch (Exception ignored) {
                }
            }
            throw ex instanceof IOException ? (IOException) ex : new IOException(ex);
        }

        if (StringUtils.isBlank(masterResourceId)) {
            throw new IOException("Master playlist was not generated");
        }
        return masterResourceId;
    }

    private String ensureVideoHlsCollection(String parentCollectionId, VideoTrainingVideo video) throws Exception {
        String videoFolderName = Validator.escapeResourceName(StringUtils.defaultIfBlank(video.getId(), "video"));
        String collectionId = parentCollectionId;
        if (!collectionId.endsWith("/")) {
            collectionId = collectionId + "/";
        }
        collectionId = collectionId + videoFolderName + "/";
        String displayName = StringUtils.defaultIfBlank(video.getTitle(), videoFolderName);
        ensureCollection(collectionId, displayName, false);
        return collectionId;
    }

    private String resolveTargetCollectionId(String siteId, String ownerId, VideoVisibilityScope visibilityScope) throws Exception {
        VideoVisibilityScope scope = visibilityScope != null ? visibilityScope : VideoVisibilityScope.COURSE;
        if (scope == VideoVisibilityScope.GLOBAL) {
            String globalRoot = StringUtils.trimToEmpty(serverConfigurationService.getString(GLOBAL_ROOT_PROPERTY, DEFAULT_GLOBAL_ROOT));
            if (!globalRoot.startsWith("/")) {
                globalRoot = "/" + globalRoot;
            }
            if (!globalRoot.endsWith("/")) {
                globalRoot = globalRoot + "/";
            }
            String normalizedOwner = Validator.escapeResourceName(StringUtils.defaultIfBlank(ownerId, "owner"));
            String collectionId = globalRoot + normalizedOwner + "/";
            ensureCollection(globalRoot, StringUtils.trimToEmpty(serverConfigurationService.getString(GLOBAL_ROOT_BASE_FOLDER_PROPERTY, DEFAULT_GLOBAL_ROOT_FOLDER)), true);
            ensureCollection(collectionId, normalizedOwner, true);
            return collectionId;
        }

        String siteCollectionId = contentHostingService.getSiteCollection(siteId);
        String folderProperty = scope == VideoVisibilityScope.LESSON ? LESSON_BASE_FOLDER_PROPERTY : BASE_FOLDER_PROPERTY;
        String defaultFolder = scope == VideoVisibilityScope.LESSON ? "Lessons" : DEFAULT_BASE_FOLDER;
        String folder = StringUtils.trimToEmpty(serverConfigurationService.getString(folderProperty, defaultFolder));
        String normalizedFolder = Validator.escapeResourceName(folder);
        if (StringUtils.isBlank(normalizedFolder)) {
            normalizedFolder = defaultFolder;
        }

        String collectionId = siteCollectionId + normalizedFolder + "/";
        ensureCollection(collectionId, normalizedFolder, false);
        return collectionId;
    }

    private void ensureCollection(String collectionId, String displayName, boolean forceVisible) throws Exception {
        boolean hiddenWithAccessibleContent = !forceVisible && serverConfigurationService.getBoolean(HIDDEN_WITH_ACCESS_PROPERTY, true);
        try {
            contentHostingService.checkCollection(collectionId);
        } catch (IdUnusedException ex) {
            ContentCollectionEdit edit = contentHostingService.addCollection(collectionId);
            if (StringUtils.isNotBlank(displayName)) {
                edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, displayName);
            }
            edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_HIDDEN_WITH_ACCESSIBLE_CONTENT, String.valueOf(hiddenWithAccessibleContent));
            contentHostingService.commitCollection(edit);
        }
    }

    private void failJob(VideoTrainingProcessJob job, VideoTrainingVideo video, String message) {
        log.warn("HlsTranscodingJob - failed for job {}: {}", job.getId(), message);
        job.setStatus(VideoTrainingProcessJobStatus.FAILED);
        job.setErrorMessage(StringUtils.abbreviate(StringUtils.defaultString(message), 3900));
        job.setModifiedOn(Instant.now());
        processJobRepository.save(job);
        if (video != null) {
            video.setModifiedOn(Instant.now());
            videoRepository.save(video);
            sendCompletionEmail(video, job, message);
        }
        cleanupTempDirectory(Paths.get(job.getTempFilePath()).getParent());
    }

    private void sendCompletionEmail(VideoTrainingVideo video, VideoTrainingProcessJob job, String errorMessage) {
        String to = resolveRecipientEmail(job.getSubmitterUserId());
        if (StringUtils.isBlank(to)) {
            return;
        }

        if (StringUtils.isBlank(serverConfigurationService.getString("smtp@org.sakaiproject.email.api.EmailService", null))) {
            log.debug("HlsTranscodingJob - skipping email because SMTP is not configured");
            return;
        }

        String from = serverConfigurationService.getSmtpFrom();
        String serviceName = serverConfigurationService.getString("ui.service", "Video Training");
        String subject;
        String body;
        if (StringUtils.isBlank(errorMessage)) {
            subject = RB.getFormattedMessage("video.training.hls.email.completed.subject", new Object[] { video.getTitle() });
            body = RB.getFormattedMessage("video.training.hls.email.completed.body",
                    new Object[] { video.getTitle(), video.getSiteId(), serviceName });
        } else {
            subject = RB.getFormattedMessage("video.training.hls.email.failed.subject", new Object[] { video.getTitle() });
            body = RB.getFormattedMessage("video.training.hls.email.failed.body",
                    new Object[] { video.getTitle(), StringUtils.defaultString(errorMessage), video.getSiteId(), serviceName });
        }

        try {
            emailService.send(from, to, subject, body, null, null, null);
        } catch (Exception ex) {
            log.warn("HlsTranscodingJob - failed to send email to {}", to, ex);
        }
    }

    private String resolveRecipientEmail(String userId) {
        try {
            User user = userDirectoryService.getUser(userId);
            return user != null ? user.getEmail() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void cleanupTempDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.debug("HlsTranscodingJob - failed to delete a temporary HLS file", ex);
                }
            });
        } catch (IOException ex) {
            log.debug("HlsTranscodingJob - cleanup failed for temporary HLS files", ex);
        }
    }

    private String contentTypeFor(String filename) {
        String extension = extractExtension(filename).toLowerCase(Locale.ROOT);
        if (".m3u8".equals(extension)) {
            return "application/vnd.apple.mpegurl";
        }
        if (".ts".equals(extension)) {
            return "video/mp2t";
        }
        if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String extractExtension(String filename) {
        String normalized = StringUtils.defaultString(filename);
        int index = normalized.lastIndexOf('.');
        return index >= 0 ? normalized.substring(index) : "";
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

    private static final class HlsVariant {
        private final String name;
        private final int height;
        private final String videoBitrate;
        private final String maxRate;
        private final String bufferSize;
        private final int width;
        private final int bandwidth;

        private HlsVariant(String name, int height, String videoBitrate, String maxRate, String bufferSize, int width, int bandwidth) {
            this.name = name;
            this.height = height;
            this.videoBitrate = videoBitrate;
            this.maxRate = maxRate;
            this.bufferSize = bufferSize;
            this.width = width;
            this.bandwidth = bandwidth;
        }
    }
}