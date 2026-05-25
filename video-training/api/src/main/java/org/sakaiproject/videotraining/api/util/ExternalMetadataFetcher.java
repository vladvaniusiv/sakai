package org.sakaiproject.videotraining.api.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;
import org.sakaiproject.videotraining.api.service.VideoTrainingOAuthCredentialsService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

public final class ExternalMetadataFetcher {

    private ExternalMetadataFetcher() {}

	@Data
    public static class MetadataFetchResult {
        public final String title;
        public final String description;

        public MetadataFetchResult(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static final Pattern IFRAME_SRC_PATTERN = Pattern.compile("(?is)<iframe[^>]*\\bsrc=[\"']([^\"']+)[\"']");
    private static final Pattern YOUTUBE_WATCH_PATTERN = Pattern.compile("(?:v=)([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORT_PATTERN = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_EMBED_PATTERN = Pattern.compile("/embed/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORTS_PATTERN = Pattern.compile("/shorts/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_LIVE_PATTERN = Pattern.compile("/live/([A-Za-z0-9_-]{11})");

    public static MetadataFetchResult fetchExternalMetadata(String sourceReference) throws Exception {
        String youtubeId = extractYoutubeVideoId(sourceReference == null ? "" : sourceReference.trim());
        if (youtubeId != null && !youtubeId.isEmpty()) {
            return fetchYoutubeMetadata(youtubeId);
        }
        throw new IllegalArgumentException("Unsupported external video provider");
    }

    public static String retrieveVideoProvider(String sourceReference) {
        String youtubeId = extractYoutubeVideoId(sourceReference == null ? "" : sourceReference.trim());
        if (youtubeId != null && !youtubeId.isEmpty()) {
            return "YouTube";
        }
        return "Unsupported";
    }

    public static String extractYoutubeVideoId(String sourceReference) {
        if (sourceReference == null || sourceReference.isBlank()) {
            return null;
        }

        Matcher watchMatcher = YOUTUBE_WATCH_PATTERN.matcher(sourceReference);
        if (watchMatcher.find()) {
            return watchMatcher.group(1);
        }

        Matcher shortMatcher = YOUTUBE_SHORT_PATTERN.matcher(sourceReference);
        if (shortMatcher.find()) {
            return shortMatcher.group(1);
        }

        Matcher embedMatcher = YOUTUBE_EMBED_PATTERN.matcher(sourceReference);
        if (embedMatcher.find()) {
            return embedMatcher.group(1);
        }

        Matcher shortsMatcher = YOUTUBE_SHORTS_PATTERN.matcher(sourceReference);
        if (shortsMatcher.find()) {
            return shortsMatcher.group(1);
        }

        Matcher liveMatcher = YOUTUBE_LIVE_PATTERN.matcher(sourceReference);
        if (liveMatcher.find()) {
            return liveMatcher.group(1);
        }

        Matcher iframeMatcher = IFRAME_SRC_PATTERN.matcher(sourceReference);
        if (iframeMatcher.find()) {
            String src = iframeMatcher.group(1);
            return extractYoutubeVideoId(src);
        }

        return null;
    }

    private static MetadataFetchResult fetchYoutubeMetadata(String youtubeId) throws Exception {
        if (isBlank(youtubeId)) {
            throw new IllegalArgumentException("youtubeId required");
        }
        String apiKey = resolveYoutubeApiKey();
        if (isBlank(apiKey)) {
            throw new IllegalStateException("YouTube API key not configured");
        }

        String apiUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=" + URLEncoder.encode(youtubeId, StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("YouTube API returned status " + response.statusCode());
        }

        JsonNode root = new ObjectMapper().readTree(response.body());
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IOException("YouTube API returned no items");
        }
        JsonNode snippet = items.get(0).path("snippet");
        String title = snippet.path("title").asText("");
        String description = snippet.path("description").asText("");
        return new MetadataFetchResult(title, description);
    }

    private static String resolveYoutubeApiKey() {
        String configuredKey = trimToEmpty(ServerConfigurationService.getString("video.training.youtube.api.key", ""));
        if (isNotBlank(configuredKey)) {
            return configuredKey;
        }

        try {
            VideoTrainingOAuthCredentialsService credentialsService = ComponentManager.get(VideoTrainingOAuthCredentialsService.class);
            if (credentialsService == null) {
                return "";
            }

            return credentialsService.getCredentials(VideoProviderType.YOUTUBE_UPLOAD)
                    .map(VideoTrainingOAuthCredentials::getApiKey)
                    .map(ExternalMetadataFetcher::trimToEmpty)
                    .orElse("");
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
