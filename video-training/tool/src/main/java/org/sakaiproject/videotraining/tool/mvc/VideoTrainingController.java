package org.sakaiproject.videotraining.tool.mvc;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.Video;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.UserTimeService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.util.Validator;
import org.sakaiproject.videotraining.api.VideoTrainingConstants;
import org.sakaiproject.videotraining.api.model.PagedResponse;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideoView;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.service.VideoTrainingService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import java.io.IOException;
import java.net.URI;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping
public class VideoTrainingController {

    private static final String SOURCE_MODE_EXTERNAL = "external";
    private static final String SOURCE_MODE_UPLOAD = "upload";
    private static final String SOURCE_MODE_RESOURCES = "resources";
    private static final String MANAGED_UPLOAD_PROPERTY = "video.training.managed";
    private static final String MANAGED_UPLOAD_SITE_PROPERTY = "video.training.siteId";
    private static final String MANAGED_BASE_FOLDER_PROPERTY = "video.training.basefolder";
    private static final String MANAGED_FOLDER_HIDDEN_WITH_ACCESS_PROPERTY = "video.training.folder.hidden.withaccess";
    private static final String DEFAULT_MANAGED_BASE_FOLDER = "Video Training";
    private static final String VIEW_MODE_CARDS = "cards";
    private static final String VIEW_MODE_TABLE = "table";
    private static final String VIEW_MODE_SESSION_PREFIX = "video-training.list.view-mode.";
    private static final String DEFAULT_SORT_FIELD = "modifiedOn";
    private static final String DEFAULT_SORT_DIRECTION = "desc";
    private static final String MODERATION_ENABLED_PROPERTY = "video.training.moderation.enabled";
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_NATIVE_UPLOAD_BYTES = 536_870_912L;
    private static final Set<String> ALLOWED_NATIVE_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "m4v", "avi", "mkv");
    private static final Pattern IFRAME_SRC_PATTERN = Pattern.compile("(?is)<iframe[^>]*\\bsrc=[\"']([^\"']+)[\"']");
    private static final Pattern YOUTUBE_WATCH_PATTERN = Pattern.compile("(?:v=)([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORT_PATTERN = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_EMBED_PATTERN = Pattern.compile("/embed/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORTS_PATTERN = Pattern.compile("/shorts/([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_LIVE_PATTERN = Pattern.compile("/live/([A-Za-z0-9_-]{11})");
    private static final Pattern VIMEO_PLAYER_PATTERN = Pattern.compile("player\\.vimeo\\.com/video/(\\d+)");
    private static final Pattern VIMEO_PAGE_PATTERN = Pattern.compile("vimeo\\.com/(?:video/)?(\\d+)");
    private static final String CONTENT_REFERENCE_ROOT = ContentHostingService.REFERENCE_ROOT;
        private static final Map<String, String> SORT_FIELD_BY_COLUMN = Map.of(
            "title", "title",
            "scope", "visibilityScope",
            "status", "publicationStatus",
            "release", "releaseDate",
            "retract", "retractDate",
            "modified", "modifiedOn");

    private final MessageSource messageSource;
    private final ContentHostingService contentHostingService;
    private final SessionManager sessionManager;
    private final SiteService siteService;
    private final ToolManager toolManager;
    private final UserTimeService userTimeService;
    private final VideoTrainingService videoTrainingService;
    private final SecurityService securityService;

    public VideoTrainingController(MessageSource messageSource,
            ContentHostingService contentHostingService,
            SessionManager sessionManager,
            SiteService siteService,
            ToolManager toolManager,
            @Qualifier("org.sakaiproject.time.api.UserTimeService") UserTimeService userTimeService,
            VideoTrainingService videoTrainingService,
            SecurityService securityService) {
        this.messageSource = messageSource;
        this.contentHostingService = contentHostingService;
        this.sessionManager = sessionManager;
        this.siteService = siteService;
        this.toolManager = toolManager;
        this.userTimeService = userTimeService;
        this.videoTrainingService = videoTrainingService;
        this.securityService = securityService;

    }

    @GetMapping("/videos-manageable")
    public PagedResponse<VideoTrainingVideoView> getManageableVideos(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "asUser", required = false) String asUser,
            HttpServletRequest request,
            Model model,
            Locale locale) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        if (StringUtils.isNotBlank(asUser) && securityService.isSuperUser()) {
            userId = asUser;
        }
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();

        boolean isUserSite = siteService.isUserSite(siteId);

        if (!isUserSite && !videoTrainingService.canManageLibrary(siteId, userId) &&
            !videoTrainingService.hasManagePermission(siteId, userId)) {
            model.addAttribute("error",
                messageSource.getMessage("video.training.accessDenied", null, request.getLocale()));
            return new PagedResponse<>(Collections.emptyList(), 0L, page, size);
        }

        Long totalCount = 0L;

        if (isUserSite) {
            // Admin will see all and the rest will see the visible ones.
            totalCount = videoTrainingService.countGlobalVideosForUser(userId, query);
        } else {
            totalCount = videoTrainingService.countSiteVideosForUser(siteId, userId, query);
        }

        int safeSize = normalizePageSize(size);
        int safePage = normalizePage(page, safeSize, totalCount);

        List<VideoTrainingVideo> paginatedVideoList = new ArrayList<>();

        if (isUserSite) {
            // Admin will see all and the rest will see the visible ones.
            paginatedVideoList = videoTrainingService.getGlobalVideosForUser(userId, query, safePage, safeSize);
        } else {
            // Admin/Profe will see all, manage/TA will see their videos,
            // and the rest will see the visible ones.
            paginatedVideoList =
                videoTrainingService.getSiteVideosForUserPage(siteId, userId, query, safePage, safeSize);
        }

        List<VideoTrainingVideoView> videoViews = buildVideoViews(
            paginatedVideoList, isUserSite, effectiveLocale);

        return new PagedResponse<VideoTrainingVideoView>(videoViews, totalCount, safePage, safeSize);
    }

    @GetMapping("/videos-viewable")
    public PagedResponse<VideoTrainingVideoView> getViewableVideos(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "asUser", required = false) String asUser,
            HttpServletRequest request,
            Model model,
            Locale locale) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        if (StringUtils.isNotBlank(asUser) && securityService.isSuperUser()) {
            userId = asUser;
        }
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();
        String normalizedQuery = StringUtils.trimToEmpty(query);

        boolean isUserSite = siteService.isUserSite(siteId);

        Long totalCount = 0L;

        if (isUserSite) {
            // Admin will see all and the rest will see the visible ones.
            totalCount = videoTrainingService.countGlobalVideosForUser(userId, query);
        } else {
            totalCount = videoTrainingService.countSiteViewableVideosForUser(
                siteId, userId, normalizedQuery
            );
        }

        int safeSize = normalizePageSize(size);
        int safePage = normalizePage(page, safeSize, totalCount);

        List<VideoTrainingVideo> paginatedVideoList = new ArrayList<>();

        if (isUserSite) {
            // Admin will see all and the rest will see the visible ones.
            paginatedVideoList = videoTrainingService.getGlobalVideosForUser(userId, query, safePage, safeSize);
        } else {
            // Admin will see all, manage/TA will see their videos,
            // and the rest will see the visible ones.
            paginatedVideoList = videoTrainingService.getSiteViewableVideosForUserPage(siteId, userId, normalizedQuery, safePage, safeSize);
        }

        List<VideoTrainingVideoView> videoViews = buildVideoViews(
            paginatedVideoList, isUserSite, effectiveLocale);

        return new PagedResponse<VideoTrainingVideoView>(videoViews, totalCount, safePage, safeSize);
    }

    @GetMapping({"/", "/videos"})
    public String list(@RequestParam(name = "viewMode", required = false) String viewMode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "size", required = false, defaultValue = "15") int size,
            @RequestParam(name = "batchSize", required = false) Integer batchSize,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDir", required = false) String sortDir,
            Locale locale,
            Model model) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        boolean canManageAll = videoTrainingService.canManageLibrary(siteId, userId);
        boolean canManageOwn = videoTrainingService.hasManagePermission(siteId, userId);
        boolean canManage = canManageAll;
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();
        String effectiveViewMode = resolveEffectiveViewMode(siteId, canManage, viewMode);
        String normalizedQuery = StringUtils.trimToEmpty(query);
        boolean isUserSite = siteService.isUserSite(siteId);
        int safeSize = normalizePageSize(size);
        int safeBatchSize = normalizePageSize(batchSize != null ? batchSize : safeSize);
        int safeOffset = Math.max(0, offset != null ? offset : 0);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        List<VideoTrainingVideo> videos;
        boolean hasMore = false;
        int nextOffset = safeOffset;

        List<VideoTrainingVideo> fetched;
        int fetchSize = safeOffset > 0 ? safeBatchSize : safeSize;
        String sortField = mapSortField(normalizedSortBy, isUserSite);
        boolean ascending = "asc".equals(normalizedSortDir);
        int requested = fetchSize + 1;
        if (canManageAll) {
            fetched = videoTrainingService.getSiteLibrarySorted(siteId, normalizedQuery, safeOffset, requested, sortField, ascending);
        } else if (canManageOwn) {
            // owner-level managers see only their own videos
            fetched = videoTrainingService.getSiteLibrarySortedForOwner(siteId, userId, normalizedQuery, safeOffset, requested, sortField, ascending);
        } else {
            if (isUserSite) {
                fetched = videoTrainingService.getGlobalVideosSorted(normalizedQuery, safeOffset, requested, sortField, ascending);
            } else {
                fetched = videoTrainingService.getVisibleVideosForUserSorted(siteId, userId, Instant.now(), normalizedQuery,
                        safeOffset, requested, sortField, ascending);
            }
        }

        if (fetched.size() > fetchSize) {
            hasMore = true;
            videos = new ArrayList<>(fetched.subList(0, fetchSize));
        } else {
            videos = fetched;
        }
        nextOffset = safeOffset + videos.size();

        Map<String, String> releaseDisplayById = new HashMap<>();
        Map<String, String> retractDisplayById = new HashMap<>();
        Map<String, String> thumbnailUrlById = new HashMap<>();
        Map<String, Boolean> thumbnailIsVideoById = new HashMap<>();
        Map<String, String> siteNameBySiteId = new HashMap<>();
        for (VideoTrainingVideo video : videos) {
            releaseDisplayById.put(video.getId(), formatInstantForDisplay(video.getReleaseDate(), effectiveLocale));
            retractDisplayById.put(video.getId(), formatInstantForDisplay(video.getRetractDate(), effectiveLocale));
            thumbnailUrlById.put(video.getId(), buildThumbnailUrl(video));
            thumbnailIsVideoById.put(video.getId(), isNativeVideoThumbnail(video));
            if (isUserSite && !siteNameBySiteId.containsKey(video.getSiteId())) {
                siteNameBySiteId.put(video.getSiteId(), getSiteName(video.getSiteId()));
            }
        }

        populateNavigationFlags(model, siteId, userId);

        model.addAttribute("videos", videos);
        model.addAttribute("releaseDisplayById", releaseDisplayById);
        model.addAttribute("retractDisplayById", retractDisplayById);
        model.addAttribute("thumbnailUrlById", thumbnailUrlById);
        model.addAttribute("thumbnailIsVideoById", thumbnailIsVideoById);
        model.addAttribute("siteNameBySiteId", siteNameBySiteId);
        model.addAttribute("isUserSite", isUserSite);
        model.addAttribute("viewMode", effectiveViewMode);
        model.addAttribute("isCardsView", VIEW_MODE_CARDS.equals(effectiveViewMode));
        model.addAttribute("isTableView", VIEW_MODE_TABLE.equals(effectiveViewMode));
        model.addAttribute("q", normalizedQuery);
        model.addAttribute("size", safeSize);
        model.addAttribute("batchSize", safeBatchSize);
        model.addAttribute("offset", safeOffset);
        model.addAttribute("nextOffset", nextOffset);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("hasMore", hasMore);
        model.addAttribute("siteId", siteId);
        model.addAttribute("siteRef", siteService.siteReference(siteId));
        model.addAttribute("moderationEnabled", isModerationEnabled());
        model.addAttribute("title", messageSource.getMessage("video.training.title", null, locale));
        return "video-training/list";
    }

    private String normalizeSortBy(String sortBy) {
        String normalized = StringUtils.trimToEmpty(sortBy);
        if ("context".equals(normalized)) {
            return normalized;
        }
        return SORT_FIELD_BY_COLUMN.containsKey(normalized) ? normalized : "modified";
    }

    private String normalizeSortDir(String sortDir) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(sortDir));
        return "asc".equals(normalized) ? "asc" : DEFAULT_SORT_DIRECTION;
    }

    private String mapSortField(String sortBy, boolean isUserSite) {
        if ("context".equals(sortBy)) {
            return isUserSite ? "siteId" : "providerType";
        }
        return SORT_FIELD_BY_COLUMN.getOrDefault(sortBy, "modifiedOn");
    }

    @GetMapping("/videos/new")
    public String newVideo(RedirectAttributes redirectAttributes, Locale locale, Model model) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        if (!(videoTrainingService.canManageLibrary(siteId, userId) || videoTrainingService.hasManagePermission(siteId, userId))) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }

        populateNavigationFlags(model, siteId, userId);
        model.addAttribute("video", new VideoTrainingVideo());
        model.addAttribute("isEdit", false);
        model.addAttribute("releaseDateInput", "");
        model.addAttribute("retractDateInput", "");
        model.addAttribute("timezoneId", getUserZoneId().getId());
        List<ExistingResourceOption> existingResources = getExistingSiteVideoResources(siteId);
        model.addAttribute("existingVideoResources", existingResources);
        model.addAttribute("sourceMode", SOURCE_MODE_UPLOAD);
        model.addAttribute("providerTypes", VideoProviderType.values());
        model.addAttribute("visibilityScopes", VideoVisibilityScope.values());
        model.addAttribute("publicationStatuses", publicationStatusesForForm());
        return "video-training/edit";
    }

    @GetMapping("/videos/{videoId}/edit")
    public String editVideo(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale,
            Model model) {

        String siteId = currentSiteId();
        String userId = currentUserId();
        VideoTrainingVideo video = videoTrainingService.getVideoById(videoId).orElse(null);
        if (video == null) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        // allow full site managers or owner-level managers for their own videos
        if (!(videoTrainingService.canManageLibrary(siteId, userId)
            || (videoTrainingService.hasManagePermission(siteId, userId) && Objects.equals(video.getOwnerId(), userId)))) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }

        populateNavigationFlags(model, siteId, userId);
        model.addAttribute("video", video);
        model.addAttribute("isEdit", true);
        model.addAttribute("releaseDateInput", formatInstantForInput(video.getReleaseDate()));
        model.addAttribute("retractDateInput", formatInstantForInput(video.getRetractDate()));
        model.addAttribute("timezoneId", getUserZoneId().getId());
        List<ExistingResourceOption> existingResources = getExistingSiteVideoResources(siteId);
        model.addAttribute("existingVideoResources", existingResources);
        model.addAttribute("sourceMode", determineSourceMode(video, existingResources));
        model.addAttribute("providerTypes", VideoProviderType.values());
        model.addAttribute("visibilityScopes", VideoVisibilityScope.values());
        VideoVisibilityScope scope = video.getVisibilityScope() != null ? video.getVisibilityScope() : VideoVisibilityScope.COURSE;
        VideoPublicationStatus[] validTransitions = videoTrainingService.getValidPublicationStatusTransitions(video.getPublicationStatus(), scope);
        model.addAttribute("publicationStatuses", validTransitions);
        model.addAttribute("currentPublicationStatus", video.getPublicationStatus());
        return "video-training/edit";
    }

    @PostMapping("/videos")
        public String createVideo(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("providerType") String providerType,
            @RequestParam(name = "sourceMode", required = false) String sourceMode,
            @RequestParam(name = "sourceReference", required = false) String sourceReference,
            @RequestParam(name = "existingResourceReference", required = false) String existingResourceReference,
            @RequestParam(name = "nativeFile", required = false) MultipartFile nativeFile,
            @RequestParam(name = "inheritTitleMetadata", required = false) String inheritTitleMetadata,
            @RequestParam(name = "inheritDescriptionMetadata", required = false) String inheritDescriptionMetadata,
            @RequestParam(name = "visibilityScope", required = false) String visibilityScope,
            @RequestParam(name = "publicationStatus", required = false) String publicationStatus,
            @RequestParam(name = "releaseDate", required = false) String releaseDate,
            @RequestParam(name = "retractDate", required = false) String retractDate,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        String effectiveSourceMode = resolveSourceMode(sourceMode, providerType);
        VideoProviderType parsedProviderType = providerTypeForSourceMode(effectiveSourceMode);

        String siteId = currentSiteId();
        String uploadedSourceReference = null;
        String resolvedSourceReference;
        Long resolvedFileSizeBytes = null;

        if (SOURCE_MODE_EXTERNAL.equals(effectiveSourceMode)) {
            resolvedSourceReference = normalizeExternalSourceReference(sourceReference);
            if (StringUtils.isBlank(resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidExternalSource", null, locale));
                return "redirect:/videos/new";
            }
            // If user requested inheriting metadata, fetch it (must succeed or return error)
            boolean wantTitle = StringUtils.isNotBlank(inheritTitleMetadata);
            boolean wantDescription = StringUtils.isNotBlank(inheritDescriptionMetadata);
            if (wantTitle || wantDescription) {
                String videoProvider = retrieveVideoProvider(resolvedSourceReference);
                try {
                    MetadataFetchResult meta = fetchExternalMetadata(resolvedSourceReference);

                    if (wantTitle && StringUtils.isBlank(meta.title)) {
                        redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no devolvió título; introduce los datos manualmente.");
                        return "redirect:/videos/new";
                    }
                    if (wantDescription && StringUtils.isBlank(meta.description)) {
                        redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no devolvió descripción; introduce los datos manualmente.");
                        return "redirect:/videos/new";
                    }
                    if (wantTitle && StringUtils.isNotBlank(meta.title)) {
                        title = meta.title;
                    }
                    if (wantDescription && StringUtils.isNotBlank(meta.description)) {
                        description = meta.description;
                    }
                } catch (Exception e) {
                    redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no funciona; introduce los datos manualmente.");
                    return "redirect:/videos/new";
                }
            }
        } else if (SOURCE_MODE_RESOURCES.equals(effectiveSourceMode)) {
            resolvedSourceReference = StringUtils.trimToEmpty(existingResourceReference);
            if (StringUtils.isBlank(resolvedSourceReference)
                    || !isExistingSiteVideoResourceReference(siteId, resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidResourceReference", null, locale));
                return "redirect:/videos/new";
            }
            resolvedFileSizeBytes = resolveNativeResourceSizeBytes(resolvedSourceReference);
        } else {
            if (nativeFile == null || nativeFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadRequired", null, locale));
                return "redirect:/videos/new";
            }
            if (!isValidNativeUpload(nativeFile)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadInvalidType", null, locale));
                return "redirect:/videos/new";
            }

            Long maxNativeUploadBytes = Long.parseLong(ServerConfigurationService.getString("video.training.max.upload.size", "536870912"));
            if (nativeFile.getSize() > maxNativeUploadBytes) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadTooLarge", null, locale));
                return "redirect:/videos/new";
            }
            try {
                uploadedSourceReference = uploadNativeVideo(siteId, nativeFile);
                resolvedSourceReference = uploadedSourceReference;
                resolvedFileSizeBytes = nativeFile.getSize();
                } catch (OverQuotaException ex) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadOverQuota", null, locale));
                    return "redirect:/videos/new";
            } catch (Exception ex) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadFailed", null, locale));
                return "redirect:/videos/new";
            }
        }

        if (StringUtils.isBlank(title)
                || (parsedProviderType == VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))
                || (parsedProviderType == VideoProviderType.NATIVE && StringUtils.isBlank(resolvedSourceReference))) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

        Instant parsedReleaseDate;
        Instant parsedRetractDate;
        try {
            parsedReleaseDate = parseInputDateTime(releaseDate);
            parsedRetractDate = parseInputDateTime(retractDate);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidDateTime", null, locale));
            return "redirect:/videos/new";
        }

        if (!isValidVisibilityWindow(parsedReleaseDate, parsedRetractDate)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidVisibilityWindow", null, locale));
            return "redirect:/videos/new";
        }

        VideoVisibilityScope parsedVisibilityScope;
        VideoPublicationStatus parsedPublicationStatus;
        try {
            parsedVisibilityScope = parseVisibilityScope(visibilityScope);
            parsedPublicationStatus = parsePublicationStatus(publicationStatus);
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

        VideoTrainingVideo video = new VideoTrainingVideo();
        video.setSiteId(siteId);
        video.setTitle(StringUtils.trimToEmpty(title));
        video.setDescription(StringUtils.trimToEmpty(description));
        video.setProviderType(parsedProviderType);
        video.setSourceReference(resolvedSourceReference);
        video.setFileSizeBytes(parsedProviderType == VideoProviderType.NATIVE ? resolvedFileSizeBytes : null);
        video.setVisibilityScope(parsedVisibilityScope);
        video.setPublicationStatus(parsedPublicationStatus);
        video.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        video.setReleaseDate(parsedReleaseDate);
        video.setRetractDate(parsedRetractDate);

        try {
            videoTrainingService.saveVideo(video);
        } catch (SecurityException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("video.training.created", null, locale));
        return "redirect:/videos";
    }

    @PostMapping("/videos/{videoId}")
    public String updateVideo(@PathVariable String videoId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("providerType") String providerType,
            @RequestParam(name = "sourceMode", required = false) String sourceMode,
            @RequestParam(name = "sourceReference", required = false) String sourceReference,
            @RequestParam(name = "existingResourceReference", required = false) String existingResourceReference,
            @RequestParam(name = "nativeFile", required = false) MultipartFile nativeFile,
            @RequestParam(name = "inheritTitleMetadata", required = false) String inheritTitleMetadata,
            @RequestParam(name = "inheritDescriptionMetadata", required = false) String inheritDescriptionMetadata,
            @RequestParam(name = "visibilityScope", required = false) String visibilityScope,
            @RequestParam(name = "publicationStatus", required = false) String publicationStatus,
            @RequestParam(name = "releaseDate", required = false) String releaseDate,
            @RequestParam(name = "retractDate", required = false) String retractDate,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        VideoTrainingVideo existing = videoTrainingService.getVideoById(videoId).orElse(null);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        String effectiveSourceMode = resolveSourceMode(sourceMode, providerType);
        VideoProviderType parsedProviderType = providerTypeForSourceMode(effectiveSourceMode);
        String previousSourceReference = StringUtils.trimToEmpty(existing.getSourceReference());
        VideoProviderType previousProviderType = existing.getProviderType();

        String uploadedSourceReference = null;
        String resolvedSourceReference;
        Long resolvedFileSizeBytes = existing.getFileSizeBytes();
        if (SOURCE_MODE_EXTERNAL.equals(effectiveSourceMode)) {
            resolvedSourceReference = normalizeExternalSourceReference(sourceReference);
            if (StringUtils.isBlank(resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidExternalSource", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
            resolvedFileSizeBytes = null;
            boolean wantTitle = StringUtils.isNotBlank(inheritTitleMetadata);
            boolean wantDescription = StringUtils.isNotBlank(inheritDescriptionMetadata);
            if (wantTitle || wantDescription) {
                String videoProvider = retrieveVideoProvider(resolvedSourceReference);
                try {
                    MetadataFetchResult meta = fetchExternalMetadata(resolvedSourceReference);

                    if (wantTitle && StringUtils.isBlank(meta.title)) {
                        redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no devolvió título; introduce los datos manualmente.");
                        return "redirect:/videos/" + videoId + "/edit";
                    }
                    if (wantDescription && StringUtils.isBlank(meta.description)) {
                        redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no devolvió descripción; introduce los datos manualmente.");
                        return "redirect:/videos/" + videoId + "/edit";
                    }
                    if (wantTitle && StringUtils.isNotBlank(meta.title)) {
                        title = meta.title;
                    }
                    if (wantDescription && StringUtils.isNotBlank(meta.description)) {
                        description = meta.description;
                    }
                } catch (Exception e) {
                    redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no funciona; introduce los datos manualmente.");
                    return "redirect:/videos/" + videoId + "/edit";
                }
            }
        } else if (SOURCE_MODE_RESOURCES.equals(effectiveSourceMode)) {
            resolvedSourceReference = StringUtils.trimToEmpty(existingResourceReference);
            if (StringUtils.isBlank(resolvedSourceReference)
                    || !isExistingSiteVideoResourceReference(existing.getSiteId(), resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidResourceReference", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
            resolvedFileSizeBytes = resolveNativeResourceSizeBytes(resolvedSourceReference);
        } else {
            if (nativeFile != null && !nativeFile.isEmpty()) {
                if (!isValidNativeUpload(nativeFile)) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadInvalidType", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }

                Long maxNativeUploadBytes = Long.parseLong(ServerConfigurationService.getString("video.training.max.upload.size", "536870912"));
                if (nativeFile.getSize() > maxNativeUploadBytes) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadTooLarge", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }
                try {
                    uploadedSourceReference = uploadNativeVideo(existing.getSiteId(), nativeFile);
                    resolvedSourceReference = uploadedSourceReference;
                    resolvedFileSizeBytes = nativeFile.getSize();
                } catch (OverQuotaException ex) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadOverQuota", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                } catch (Exception ex) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadFailed", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }
            } else if (existing.getProviderType() == VideoProviderType.NATIVE) {
                resolvedSourceReference = previousSourceReference;
                if (resolvedFileSizeBytes == null) {
                    resolvedFileSizeBytes = resolveNativeResourceSizeBytes(previousSourceReference);
                }
            } else {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadRequired", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
        }

        if (StringUtils.isBlank(title)
                || (parsedProviderType == VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))
                || (parsedProviderType == VideoProviderType.NATIVE && StringUtils.isBlank(resolvedSourceReference))) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }

        Instant parsedReleaseDate;
        Instant parsedRetractDate;
        try {
            parsedReleaseDate = parseInputDateTime(releaseDate);
            parsedRetractDate = parseInputDateTime(retractDate);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidDateTime", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }

        if (!isValidVisibilityWindow(parsedReleaseDate, parsedRetractDate)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidVisibilityWindow", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }

        VideoVisibilityScope parsedVisibilityScope;
        VideoPublicationStatus parsedPublicationStatus;
        try {
            parsedVisibilityScope = parseVisibilityScope(visibilityScope);
            parsedPublicationStatus = parsePublicationStatus(publicationStatus);
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }

        VideoPublicationStatus currentStatus = existing.getPublicationStatus();
        if (!StringUtils.equals(currentStatus != null ? currentStatus.name() : null,
                parsedPublicationStatus.name())) {
            try {
                VideoPublicationStatus[] validTransitions = videoTrainingService.getValidPublicationStatusTransitions(
                        currentStatus, parsedVisibilityScope);
                boolean transitionValid = false;
                for (VideoPublicationStatus valid : validTransitions) {
                    if (valid == parsedPublicationStatus) {
                        transitionValid = true;
                        break;
                    }
                }
                if (!transitionValid) {
                    cleanupManagedNativeResource(uploadedSourceReference);
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.invalidStatusTransition", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }
            } catch (Exception ex) {
                cleanupManagedNativeResource(uploadedSourceReference);
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidStatusTransition", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
        }

        existing.setTitle(StringUtils.trimToEmpty(title));
        existing.setDescription(StringUtils.trimToEmpty(description));
        existing.setProviderType(parsedProviderType);
        existing.setSourceReference(resolvedSourceReference);
        existing.setFileSizeBytes(parsedProviderType == VideoProviderType.NATIVE ? resolvedFileSizeBytes : null);
        existing.setVisibilityScope(parsedVisibilityScope);
        existing.setPublicationStatus(parsedPublicationStatus);
        existing.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        existing.setReleaseDate(parsedReleaseDate);
        existing.setRetractDate(parsedRetractDate);

        try {
            videoTrainingService.saveVideo(existing);
        } catch (SecurityException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }

        if (previousProviderType == VideoProviderType.NATIVE
                && !StringUtils.equals(previousSourceReference, resolvedSourceReference)) {
            cleanupManagedNativeResource(previousSourceReference);
        }

        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("video.training.updated", null, locale));
        return "redirect:/videos";
    }

    @PostMapping("/videos/{videoId}/delete")
    public String deleteVideo(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        VideoTrainingVideo existing = videoTrainingService.getVideoById(videoId).orElse(null);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        try {
            videoTrainingService.deleteVideo(videoId);
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }

        if (existing.getProviderType() == VideoProviderType.NATIVE) {
            cleanupManagedNativeResource(existing.getSourceReference());
        }

        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("video.training.deleted", null, locale));
        return "redirect:/videos";
    }

        @PostMapping("/videos/{videoId}/publish")
        public String publishVideo(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.PUBLISHED,
            "video.training.published", redirectAttributes, locale);
        }

        @PostMapping("/videos/{videoId}/withdraw")
        public String withdrawVideo(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.WITHDRAWN,
            "video.training.withdrawn", redirectAttributes, locale);
        }

        @PostMapping("/videos/{videoId}/archive")
        public String archiveVideo(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.ARCHIVED,
            "video.training.archived", redirectAttributes, locale);
        }

        @PostMapping("/videos/{videoId}/restore-draft")
        public String restoreVideoToDraft(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.DRAFT,
            "video.training.restoredDraft", redirectAttributes, locale);
        }

        @PostMapping("/videos/{videoId}/submit-approval")
        public String submitVideoForApproval(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.PENDING_APPROVAL,
            "video.training.pendingApprovalSubmitted", redirectAttributes, locale);
        }

        @PostMapping("/videos/{videoId}/reject-approval")
        public String rejectVideoApproval(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updatePublicationStatus(videoId, VideoPublicationStatus.DRAFT,
            "video.training.pendingApprovalRejected", redirectAttributes, locale);
        }

    @GetMapping("/videos/{videoId}")
    public String details(@PathVariable String videoId,
            RedirectAttributes redirectAttributes,
            Locale locale,
            Model model) {

        VideoTrainingVideo video = videoTrainingService.getVideoById(videoId).orElse(null);
        if (video == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        String userId = currentUserId();
        boolean canManageAll = videoTrainingService.canManageLibrary(video.getSiteId(), userId);
        boolean canManageOwn = videoTrainingService.hasManagePermission(video.getSiteId(), userId) && Objects.equals(video.getOwnerId(), userId);
        boolean canManage = canManageAll || canManageOwn;
        if (!canManage && !videoTrainingService.canViewVideo(video, userId, Instant.now())) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }

        videoTrainingService.registerView(video.getSiteId(), videoId, userId, Instant.now());
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();

        populateNavigationFlags(model, video.getSiteId(), userId);
        String externalEmbedUrl = video.getProviderType() == VideoProviderType.EXTERNAL
            ? normalizeExternalSourceReference(video.getSourceReference())
            : "";
        model.addAttribute("video", video);
        model.addAttribute("canManage", canManage);
        model.addAttribute("canManageCaptions", videoTrainingService.canManageCaptions(video.getSiteId(), userId));
        model.addAttribute("isExternalVideo", video.getProviderType() == VideoProviderType.EXTERNAL);
        model.addAttribute("externalEmbedUrl", externalEmbedUrl);
        model.addAttribute("nativePlaybackUrl", resolveNativePlaybackUrl(video));
        model.addAttribute("nativeContentType", resolveNativeContentType(video));
        model.addAttribute("moderationEnabled", isModerationEnabled());
        model.addAttribute("isVisibleNow", videoTrainingService.canViewVideo(video, userId, Instant.now()));
        model.addAttribute("releaseDateDisplay", formatInstantForDisplay(video.getReleaseDate(), effectiveLocale));
        model.addAttribute("retractDateDisplay", formatInstantForDisplay(video.getRetractDate(), effectiveLocale));
        model.addAttribute("newCaption", new VideoTrainingCaption());
        model.addAttribute("captions", videoTrainingService.getCaptionsForVideo(videoId));
        return "video-training/details";
    }

    @GetMapping("/analytics")
    public String analytics(RedirectAttributes redirectAttributes, Locale locale, Model model) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        populateNavigationFlags(model, siteId, userId);
        if (!videoTrainingService.canViewAnalytics(siteId, userId)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }
        model.addAttribute("analytics", videoTrainingService.getSiteAnalyticsSummary(siteId));
        return "video-training/analytics";
    }

    @PostMapping("/videos/{videoId}/captions")
    public String addCaption(@PathVariable String videoId,
            @RequestParam("languageTag") String languageTag,
            @RequestParam(name = "contentReference", required = false) String contentReference,
            @RequestParam(name = "transcriptText", required = false) String transcriptText,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        VideoTrainingVideo video = videoTrainingService.getVideoById(videoId).orElse(null);
        if (video == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        if (StringUtils.isBlank(languageTag)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.caption.invalid", null, locale));
            return "redirect:/videos/" + videoId;
        }

        VideoTrainingCaption caption = new VideoTrainingCaption();
        caption.setVideoId(videoId);
        caption.setLanguageTag(StringUtils.trimToEmpty(languageTag));
        caption.setContentReference(StringUtils.trimToEmpty(contentReference));
        caption.setTranscriptText(StringUtils.trimToEmpty(transcriptText));

        try {
            videoTrainingService.saveCaption(caption);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("video.training.caption.created", null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.caption.invalid", null, locale));
        }

        return "redirect:/videos/" + videoId;
    }

    @PostMapping("/videos/{videoId}/captions/{captionId}/delete")
    public String deleteCaption(@PathVariable String videoId,
            @PathVariable String captionId,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        VideoTrainingVideo video = videoTrainingService.getVideoById(videoId).orElse(null);
        if (video == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        try {
            videoTrainingService.deleteCaption(captionId);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("video.training.caption.deleted", null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
        }

        return "redirect:/videos/" + videoId;
    }

    private void populateNavigationFlags(Model model, String siteId, String userId) {
        model.addAttribute("canManage", videoTrainingService.canManageLibrary(siteId, userId));
        model.addAttribute("canAnalytics", videoTrainingService.canViewAnalytics(siteId, userId));
    }

    private String currentSiteId() {
        return toolManager.getCurrentPlacement().getContext();
    }

    private String currentUserId() {
        return sessionManager.getCurrentSessionUserId();
    }

    private ZoneId getUserZoneId() {
        TimeZone timeZone = userTimeService.getLocalTimeZone();
        return timeZone != null ? timeZone.toZoneId() : ZoneId.systemDefault();
    }

    private Instant parseInputDateTime(String input) {
        String trimmedInput = StringUtils.trimToNull(input);
        if (trimmedInput == null) {
            return null;
        }

        LocalDateTime localDateTime = LocalDateTime.parse(trimmedInput);
        return localDateTime.atZone(getUserZoneId()).toInstant();
    }

    private String formatInstantForInput(Instant instant) {
        if (instant == null) {
            return "";
        }
        return LocalDateTime.ofInstant(instant, getUserZoneId())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private String formatInstantForDisplay(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }
        return userTimeService.shortLocalizedTimestamp(instant, locale != null ? locale : Locale.getDefault());
    }

    private boolean isValidVisibilityWindow(Instant releaseDate, Instant retractDate) {
        return releaseDate == null || retractDate == null || releaseDate.isBefore(retractDate);
    }

    private String resolveSourceMode(String sourceMode, String providerType) {
        String normalizedSourceMode = StringUtils.trimToEmpty(sourceMode);
        if (SOURCE_MODE_EXTERNAL.equals(normalizedSourceMode)
                || SOURCE_MODE_UPLOAD.equals(normalizedSourceMode)
                || SOURCE_MODE_RESOURCES.equals(normalizedSourceMode)) {
            return normalizedSourceMode;
        }

        String normalizedProviderType = StringUtils.trimToEmpty(providerType);
        if ("EXTERNAL".equals(normalizedProviderType)) {
            return SOURCE_MODE_EXTERNAL;
        }

        return SOURCE_MODE_UPLOAD;
    }

    private VideoVisibilityScope parseVisibilityScope(String visibilityScope) {
        String value = StringUtils.trimToEmpty(visibilityScope);
        if (StringUtils.isBlank(value)) {
            return VideoVisibilityScope.COURSE;
        }
        return VideoVisibilityScope.valueOf(value);
    }

    private VideoPublicationStatus parsePublicationStatus(String publicationStatus) {
        String value = StringUtils.trimToEmpty(publicationStatus);
        if (StringUtils.isBlank(value)) {
            return VideoPublicationStatus.DRAFT;
        }
        return VideoPublicationStatus.valueOf(value);
    }

    private String updatePublicationStatus(String videoId,
            VideoPublicationStatus status,
            String successMessageKey,
            RedirectAttributes redirectAttributes,
            Locale locale) {

        VideoTrainingVideo existing = videoTrainingService.getVideoById(videoId).orElse(null);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        existing.setPublicationStatus(status);
        try {
            videoTrainingService.saveVideo(existing);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage(successMessageKey, null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidStatusTransition", null, locale));
        }

        return "redirect:/videos";
    }

    private VideoPublicationStatus[] publicationStatusesForForm() {
        if (isModerationEnabled()) {
            return VideoPublicationStatus.values();
        }
        return new VideoPublicationStatus[] {
            VideoPublicationStatus.DRAFT,
            VideoPublicationStatus.PUBLISHED,
            VideoPublicationStatus.WITHDRAWN,
            VideoPublicationStatus.ARCHIVED
        };
    }

    private boolean isModerationEnabled() {
        return ServerConfigurationService.getBoolean(MODERATION_ENABLED_PROPERTY, false);
    }

    private VideoProviderType providerTypeForSourceMode(String sourceMode) {
        return SOURCE_MODE_EXTERNAL.equals(sourceMode) ? VideoProviderType.EXTERNAL : VideoProviderType.NATIVE;
    }

    private boolean isValidNativeUpload(MultipartFile nativeFile) {
        if (nativeFile == null || nativeFile.isEmpty()) {
            return false;
        }

        String contentType = StringUtils.trimToEmpty(nativeFile.getContentType()).toLowerCase(Locale.ROOT);
        if (contentType.startsWith("video/")) {
            return true;
        }

        String extension = extractLowercaseExtension(nativeFile.getOriginalFilename());
        return ALLOWED_NATIVE_VIDEO_EXTENSIONS.contains(extension);
    }

    private String extractLowercaseExtension(String filename) {
        String normalizedFilename = StringUtils.trimToEmpty(filename);
        int extensionIndex = normalizedFilename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex >= normalizedFilename.length() - 1) {
            return "";
        }
        return normalizedFilename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isExistingSiteVideoResourceReference(String siteId, String sourceId) {
        if (StringUtils.isBlank(siteId) || StringUtils.isBlank(sourceId)) {
            return false;
        }

        for (ExistingResourceOption option : getExistingSiteVideoResources(siteId)) {
            if (StringUtils.equals(option.getReference(), sourceId)) {
                return true;
            }
        }

        return false;
    }

    private String resolveNativeContentType(VideoTrainingVideo video) {
        if (video == null || video.getProviderType() != VideoProviderType.NATIVE) {
            return "video/mp4";
        }

        try {
            ContentResource resource = contentHostingService.getResource(toContentResourceId(video.getSourceReference()));
            return StringUtils.defaultIfBlank(resource.getContentType(), "video/mp4");
        } catch (Exception e) {
            return "video/mp4";
        }
    }

    private String resolveNativePlaybackUrl(VideoTrainingVideo video) {
        if (video == null || video.getProviderType() != VideoProviderType.NATIVE) {
            return "";
        }

        return resolveContentReferenceFromSourceId(video.getSourceReference());
    }

    private Long resolveNativeResourceSizeBytes(String sourceReference) {
        if (StringUtils.isBlank(sourceReference)) {
            return null;
        }

        try {
            ContentResource resource = contentHostingService.getResource(toContentResourceId(sourceReference));
            return resource.getContentLength();
        } catch (Exception e) {
            return null;
        }
    }

    private void cleanupManagedNativeResource(String sourceReference) {
        if (StringUtils.isBlank(sourceReference)) {
            return;
        }

        try {
            ContentResource resource = contentHostingService.getResource(toContentResourceId(sourceReference));
            ResourceProperties properties = resource.getProperties();
            String managedFlag = properties != null ? properties.getProperty(MANAGED_UPLOAD_PROPERTY) : null;
            if (!"true".equalsIgnoreCase(managedFlag)) {
                return;
            }

            String managedSiteId = properties != null ? properties.getProperty(MANAGED_UPLOAD_SITE_PROPERTY) : null;
            if (StringUtils.isNotBlank(managedSiteId) && !StringUtils.equals(managedSiteId, currentSiteId())) {
                return;
            }

            contentHostingService.removeResource(toContentResourceId(sourceReference));
        } catch (Exception e) {
            // best effort cleanup path
        }
    }

    private String toContentResourceId(String sourceReference) {
        String normalized = StringUtils.trimToEmpty(sourceReference);
        if (normalized.startsWith(CONTENT_REFERENCE_ROOT + "/")) {
            return normalized.substring(CONTENT_REFERENCE_ROOT.length());
        }
        return normalized;
    }

    private String resolveEffectiveViewMode(String siteId, boolean canManage, String requestedViewMode) {
        String sessionKey = VIEW_MODE_SESSION_PREFIX + siteId;
        if (isValidViewMode(requestedViewMode)) {
            sessionManager.getCurrentSession().setAttribute(sessionKey, requestedViewMode);
            return requestedViewMode;
        }

        Object sessionValue = sessionManager.getCurrentSession().getAttribute(sessionKey);
        if (sessionValue instanceof String storedViewMode && isValidViewMode(storedViewMode)) {
            return storedViewMode;
        }

        return canManage ? VIEW_MODE_TABLE : VIEW_MODE_CARDS;
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, requestedSize);
    }

    private int normalizePage(int requestedPage, int pageSize, long totalCount) {
        int safePage = Math.max(requestedPage, 1);
        if (totalCount <= 0) {
            return 1;
        }

        int totalPages = (int) Math.max(1, Math.ceil((double) totalCount / pageSize));
        return Math.min(safePage, totalPages);
    }

    private boolean isValidViewMode(String viewMode) {
        return VIEW_MODE_CARDS.equals(viewMode) || VIEW_MODE_TABLE.equals(viewMode);
    }

    private String buildThumbnailUrl(VideoTrainingVideo video) {
        if (video == null) {
            return "";
        }

        if (video.getProviderType() == VideoProviderType.NATIVE) {
            return resolveContentReferenceFromSourceId(video.getSourceReference());
        }

        if (video.getProviderType() != VideoProviderType.EXTERNAL) {
            return "";
        }

        String source = normalizeExternalSourceReference(video.getSourceReference());
        String youtubeVideoId = extractYoutubeVideoId(source);
        if (StringUtils.isNotBlank(youtubeVideoId)) {
            return "https://img.youtube.com/vi/" + youtubeVideoId + "/hqdefault.jpg";
        }

        String vimeoVideoId = extractVimeoVideoId(source);
        if (StringUtils.isNotBlank(vimeoVideoId)) {
            return "https://vumbnail.com/" + vimeoVideoId + ".jpg";
        }

        return "";
    }

    private String resolveContentReferenceFromSourceId(String sourceId) {
        if (StringUtils.isBlank(sourceId)) {
            return "";
        }

        try {
            return StringUtils.defaultIfBlank(contentHostingService.getUrl(toContentResourceId(sourceId)), "");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isNativeVideoThumbnail(VideoTrainingVideo video) {
        return video != null
                && video.getProviderType() == VideoProviderType.NATIVE
                && StringUtils.isNotBlank(video.getSourceReference());
    }

    private String normalizeExternalSourceReference(String sourceReference) {
        String normalized = StringUtils.trimToEmpty(sourceReference);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }

        Matcher iframeMatcher = IFRAME_SRC_PATTERN.matcher(normalized);
        if (iframeMatcher.find()) {
            normalized = StringUtils.trimToEmpty(iframeMatcher.group(1));
        }
        normalized = normalized.replace("&amp;", "&");

        String youtubeVideoId = extractYoutubeVideoId(normalized);
        if (StringUtils.isNotBlank(youtubeVideoId)) {
            return "https://www.youtube.com/embed/" + youtubeVideoId;
        }

        String vimeoVideoId = extractVimeoVideoId(normalized);
        if (StringUtils.isNotBlank(vimeoVideoId)) {
            return "https://player.vimeo.com/video/" + vimeoVideoId;
        }

        return "";
    }

    private String extractYoutubeVideoId(String sourceReference) {
        if (StringUtils.isBlank(sourceReference)) {
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

        return null;
    }

    private String extractVimeoVideoId(String sourceReference) {
        if (StringUtils.isBlank(sourceReference)) {
            return null;
        }

        Matcher playerMatcher = VIMEO_PLAYER_PATTERN.matcher(sourceReference);
        if (playerMatcher.find()) {
            return playerMatcher.group(1);
        }

        Matcher pageMatcher = VIMEO_PAGE_PATTERN.matcher(sourceReference);
        if (pageMatcher.find()) {
            return pageMatcher.group(1);
        }

        return null;
    }

    private String determineSourceMode(VideoTrainingVideo video, List<ExistingResourceOption> existingResources) {
        if (video == null) {
            return SOURCE_MODE_UPLOAD;
        }

        if (video.getProviderType() == VideoProviderType.EXTERNAL) {
            return SOURCE_MODE_EXTERNAL;
        }

        String sourceReference = StringUtils.trimToEmpty(video.getSourceReference());
        for (ExistingResourceOption option : existingResources) {
            if (StringUtils.equals(option.getReference(), sourceReference)) {
                return SOURCE_MODE_RESOURCES;
            }
        }

        return SOURCE_MODE_UPLOAD;
    }

    private List<ExistingResourceOption> getExistingSiteVideoResources(String siteId) {
        List<ExistingResourceOption> options = new ArrayList<>();
        String siteCollection = contentHostingService.getSiteCollection(siteId);
        List<ContentEntity> entities = contentHostingService.getAllEntities(siteCollection);

        for (ContentEntity entity : entities) {
            if (!(entity instanceof ContentResource resource)) {
                continue;
            }

            String contentType = StringUtils.defaultString(resource.getContentType()).toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("video/")) {
                continue;
            }

            ResourceProperties properties = resource.getProperties();
            String displayName = properties != null
                    ? StringUtils.defaultIfBlank(properties.getProperty(ResourceProperties.PROP_DISPLAY_NAME), resource.getId())
                    : resource.getId();
            options.add(new ExistingResourceOption(resource.getId(), displayName, contentType));
        }

        options.sort(Comparator.comparing(ExistingResourceOption::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    public static class ExistingResourceOption {

        private final String reference;
        private final String displayName;
        private final String contentType;

        public ExistingResourceOption(String reference, String displayName, String contentType) {
            this.reference = reference;
            this.displayName = displayName;
            this.contentType = contentType;
        }

        public String getReference() {
            return reference;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getContentType() {
            return contentType;
        }
    }

    private String uploadNativeVideo(String siteId, MultipartFile nativeFile) throws Exception {
        String collectionId = resolveManagedUploadCollectionId(siteId);

        String originalFilename = StringUtils.defaultIfBlank(nativeFile.getOriginalFilename(), "video");
        String extension = "";
        String baseName = originalFilename;
        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex > 0 && extensionIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(extensionIndex);
            baseName = originalFilename.substring(0, extensionIndex);
        }

        String safeBaseName = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(baseName), "video")
                + "-"
                + UUID.randomUUID();

        ContentResourceEdit edit = contentHostingService.addResource(collectionId, safeBaseName, extension, 1);
        boolean committed = false;
        try {
            edit.setContent(nativeFile.getBytes());
            edit.setContentLength(nativeFile.getSize());
            edit.setContentType(StringUtils.defaultIfBlank(nativeFile.getContentType(), "application/octet-stream"));
            edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, originalFilename);
            edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_PROPERTY, "true");
            edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_SITE_PROPERTY, siteId);
            contentHostingService.commitResource(edit, NotificationService.NOTI_NONE);
            committed = true;
            return edit.getId();
        } finally {
            if (!committed) {
                try {
                    contentHostingService.cancelResource(edit);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String resolveManagedUploadCollectionId(String siteId) throws Exception {
        String siteCollectionId = contentHostingService.getSiteCollection(siteId);
        String baseFolder = StringUtils.trimToEmpty(ServerConfigurationService.getString(
                MANAGED_BASE_FOLDER_PROPERTY,
                DEFAULT_MANAGED_BASE_FOLDER));

        String normalizedBaseFolder = Validator.escapeResourceName(baseFolder);
        if (StringUtils.isBlank(normalizedBaseFolder)) {
            normalizedBaseFolder = DEFAULT_MANAGED_BASE_FOLDER;
        }

        String collectionId = siteCollectionId + normalizedBaseFolder + "/";
        boolean hiddenWithAccessibleContent = ServerConfigurationService.getBoolean(
                MANAGED_FOLDER_HIDDEN_WITH_ACCESS_PROPERTY,
                true);

        try {
            contentHostingService.checkCollection(collectionId);
        } catch (IdUnusedException idUnusedException) {
            ContentCollectionEdit edit = contentHostingService.addCollection(collectionId);
            edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, normalizedBaseFolder);
            edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_HIDDEN_WITH_ACCESSIBLE_CONTENT,
                    String.valueOf(hiddenWithAccessibleContent));
            contentHostingService.commitCollection(edit);
        }

        return collectionId;
    }

    private String getSiteName(String siteId) {
        try {
            return siteService.getSite(siteId).getTitle();
        } catch (Exception e) {
            return siteId;
        }
    }

    private List<VideoTrainingVideoView> buildVideoViews(
            List<VideoTrainingVideo> videos,
            boolean isUserSite,
            Locale locale) {
        List<VideoTrainingVideoView> views = new ArrayList<>();

        for (VideoTrainingVideo video : videos) {
            views.add(VideoTrainingVideoView.builder()
                    .id(video.getId())
                    .title(video.getTitle())
                    .description(video.getDescription())
                    .siteName(isUserSite ? getSiteName(video.getSiteId()) : null)
                    .releaseDisplay(formatInstantForDisplay(video.getReleaseDate(), locale))
                    .retractDisplay(formatInstantForDisplay(video.getRetractDate(), locale))
                    .thumbnailUrl(buildThumbnailUrl(video))
                    .thumbnailIsVideo(isNativeVideoThumbnail(video))
                    .visibilityScope(
                        Optional.ofNullable(video.getVisibilityScope())
                                .map(Enum::name)
                                .orElse("COURSE"))
                    .publicationStatus(
                        Optional.ofNullable(video.getPublicationStatus())
                                .map(Enum::name)
                                .orElse("DRAFT"))
                    .build());
        }

        return views;
    }

    private static class MetadataFetchResult {
        public final String title;
        public final String description;

        MetadataFetchResult(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private MetadataFetchResult fetchYoutubeMetadata(String sourceReference) throws Exception {
        String youtubeId = extractYoutubeVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isBlank(youtubeId)) {
            throw new IllegalArgumentException("Not a YouTube URL");
        }

        String apiKey = ServerConfigurationService.getString("video.training.youtube.api.key", "");
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("YouTube API key not configured");
        }

        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), request -> {
        }).setApplicationName("video-training").build();

        YouTube.Videos.List list = youtube.videos().list(Collections.singletonList("snippet")).setId(Collections.singletonList(youtubeId)).setKey(apiKey);
        VideoListResponse resp = list.execute();
        if (resp.getItems() == null || resp.getItems().isEmpty() || resp.getItems().get(0).getSnippet() == null) {
            throw new java.io.IOException("YouTube API returned no snippet metadata");
        }

        Video v = resp.getItems().get(0);
        String title = v.getSnippet().getTitle();
        String description = v.getSnippet().getDescription();
        return new MetadataFetchResult(title != null ? title : "", description != null ? description : "");
    }

    private MetadataFetchResult fetchVimeoMetadata(String sourceReference) throws Exception {
        String vimeoId = extractVimeoVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isBlank(vimeoId)) {
            throw new IllegalArgumentException("Not a Vimeo URL");
        }

        // Use oEmbed endpoint which doesn't require auth
        String videoUrl = "https://vimeo.com/" + vimeoId;
        String apiUrl = "https://vimeo.com/api/oembed.json?url=" + URLEncoder.encode(videoUrl, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Vimeo oEmbed returned status " + response.statusCode());
        }

        JsonNode root = new ObjectMapper().readTree(response.body());

        String title = root.path("title").asText("");
        String description = root.path("description").asText("");

        return new MetadataFetchResult(title, description);
    }

    private MetadataFetchResult fetchExternalMetadata(String sourceReference) throws Exception {
        String youtubeId = extractYoutubeVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isNotBlank(youtubeId)) {
            return fetchYoutubeMetadata(sourceReference);
        }
        String vimeoId = extractVimeoVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isNotBlank(vimeoId)) {
            return fetchVimeoMetadata(sourceReference);
        }
        throw new IllegalArgumentException("Unsupported external video provider");
    }

    private String retrieveVideoProvider(String sourceReference) {
        String youtubeId = extractYoutubeVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isNotBlank(youtubeId)) {
            return "YouTube";
        }
        String vimeoId = extractVimeoVideoId(StringUtils.trimToEmpty(sourceReference));
        if (StringUtils.isNotBlank(vimeoId)) {
            return "Vimeo";
        }
        return ("Unsupported");
    }
}
