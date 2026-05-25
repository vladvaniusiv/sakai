package org.sakaiproject.videotraining.tool.mvc;

import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.Video;
import org.sakaiproject.videotraining.api.util.ExternalMetadataFetcher;
import org.sakaiproject.videotraining.api.util.ContentResourceHelper;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.api.app.scheduler.ScheduledInvocationManager;
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
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJobStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideoView;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.repository.VideoTrainingProcessJobRepository;
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
@Slf4j
public class VideoTrainingController {

    private static final String SOURCE_MODE_EXTERNAL = "external";
    private static final String SOURCE_MODE_UPLOAD = "upload";
    private static final String SOURCE_MODE_RESOURCES = "resources";
    private static final String MANAGEABLE_LIST_PATH = "/videos-manageable";
    private static final String VIEWABLE_LIST_PATH = "/videos-viewable";
    private static final String ACCESS_MODE_MANAGEABLE = "manageable";
    private static final String ACCESS_MODE_VIEWABLE = "viewable";
    private static final String FAVORITES_PATH = "/favorites";
    private static final String WATCH_LATER_PATH = "/watch-later";
    private static final String MANAGED_UPLOAD_PROPERTY = "video.training.managed";
    private static final String MANAGED_UPLOAD_SITE_PROPERTY = "video.training.siteId";
    private static final String MANAGED_UPLOAD_OWNER_PROPERTY = "video.training.ownerId";
    private static final String MANAGED_UPLOAD_SCOPE_PROPERTY = "video.training.visibilityScope";
    private static final String MANAGED_BASE_FOLDER_PROPERTY = "video.training.basefolder";
    private static final String MANAGED_GLOBAL_ROOT_BASE_FOLDER_PROPERTY = "video.training.global.root.basefolder";
    private static final String MANAGED_LESSON_BASE_FOLDER_PROPERTY = "lessonbuilder.basefolder";
    private static final String MANAGED_GLOBAL_ROOT_PROPERTY = "video.training.global.root";
    private static final String MANAGED_FOLDER_HIDDEN_WITH_ACCESS_PROPERTY = "video.training.folder.hidden.withaccess";
    private static final String DEFAULT_MANAGED_BASE_FOLDER = "Video Training";    
    private static final String DEFAULT_MANAGED_GLOBAL_ROOT_BASE_FOLDER_PROPERTY = "videoTraining";
    private static final String DEFAULT_MANAGED_GLOBAL_ROOT = "/public/video-training/";
    private static final String DEFAULT_LESSON_FOLDER_FALLBACK = "Lessons";
    private static final String VIEW_MODE_CARDS = "cards";
    private static final String VIEW_MODE_TABLE = "table";
    private static final String VIEW_MODE_SESSION_PREFIX = "video-training.list.view-mode.";
    private static final String DEFAULT_SORT_FIELD = "modifiedOn";
    private static final String DEFAULT_SORT_DIRECTION = "desc";
    private static final String MODERATION_ENABLED_PROPERTY = "video.training.moderation.enabled";
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String MAX_NATIVE_UPLOAD_SIZE_PROPERTY = "video.training.max.upload.size";
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final long DEFAULT_MAX_NATIVE_UPLOAD_MB = 512L;
    private static final Set<String> ALLOWED_NATIVE_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "m4v", "avi", "mkv");

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
    private final VideoTrainingProcessJobRepository processJobRepository;
    private final ScheduledInvocationManager scheduledInvocationManager;
    private final org.sakaiproject.component.api.ServerConfigurationService serverConfigurationService;
    private final SecurityService securityService;

    public VideoTrainingController(MessageSource messageSource,
            ContentHostingService contentHostingService,
            SessionManager sessionManager,
            SiteService siteService,
            ToolManager toolManager,
            @Qualifier("org.sakaiproject.time.api.UserTimeService") UserTimeService userTimeService,
            VideoTrainingService videoTrainingService,
            VideoTrainingProcessJobRepository processJobRepository,
            ScheduledInvocationManager scheduledInvocationManager,
            org.sakaiproject.component.api.ServerConfigurationService serverConfigurationService,
            SecurityService securityService) {
        this.messageSource = messageSource;
        this.contentHostingService = contentHostingService;
        ContentResourceHelper.setContentHostingService(contentHostingService);
        this.sessionManager = sessionManager;
        this.siteService = siteService;
        this.toolManager = toolManager;
        this.userTimeService = userTimeService;
        this.videoTrainingService = videoTrainingService;
        this.processJobRepository = processJobRepository;
        this.scheduledInvocationManager = scheduledInvocationManager;
        this.serverConfigurationService = serverConfigurationService;
        this.securityService = securityService;

    }

    @GetMapping(value = MANAGEABLE_LIST_PATH, params = { "page" })
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

    @GetMapping(value = VIEWABLE_LIST_PATH, params = { "page" })
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
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDir", required = false) String sortDir,
            @RequestParam(name = "accessMode", required = false) String accessMode,
            Locale locale,
            Model model) {

        String siteId = currentSiteId();
        String userId = currentUserId();
        boolean canManageAll = videoTrainingService.canManageLibrary(siteId, userId);
        boolean canManageOwn = videoTrainingService.hasManagePermission(siteId, userId);
        boolean manageableList = resolveManageableListMode(accessMode, canManageAll, canManageOwn);
        String effectiveAccessMode = manageableList ? ACCESS_MODE_MANAGEABLE : ACCESS_MODE_VIEWABLE;
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();
        String effectiveViewMode = resolveEffectiveViewMode(siteId, manageableList, viewMode);
        String normalizedQuery = StringUtils.trimToEmpty(query);
        boolean isUserSite = siteService.isUserSite(siteId);
        int safeSize = normalizePageSize(size);
        int safeOffset = Math.max(0, offset != null ? offset : 0);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        long totalCount;
        if (isUserSite) {
            totalCount = videoTrainingService.countGlobalVideosForUser(userId, normalizedQuery);
        } else if (manageableList && canManageAll) {
            totalCount = videoTrainingService.countSiteLibrary(siteId, normalizedQuery);
        } else if (manageableList && canManageOwn) {
            totalCount = videoTrainingService.countSiteLibraryForOwner(siteId, userId, normalizedQuery);
        } else {
            totalCount = videoTrainingService.countVisibleVideosForUser(siteId, userId, Instant.now(), normalizedQuery);
        }

        int requestedPage = page != null ? page : ((safeOffset / safeSize) + 1);
        int safePage = normalizePage(requestedPage, safeSize, totalCount);
        int pageOffset = (safePage - 1) * safeSize;
        int totalPages = (int) Math.max(1, Math.ceil((double) totalCount / safeSize));

        List<VideoTrainingVideo> videos;
        String sortField = mapSortField(normalizedSortBy, isUserSite);
        boolean ascending = "asc".equals(normalizedSortDir);
        if (isUserSite) {
            videos = videoTrainingService.getGlobalVideosSorted(normalizedQuery, pageOffset, safeSize, sortField, ascending);
        } else if (manageableList && canManageAll) {
            videos = videoTrainingService.getSiteLibrarySorted(siteId, normalizedQuery, pageOffset, safeSize, sortField, ascending);
        } else if (manageableList && canManageOwn) {
            videos = videoTrainingService.getSiteLibrarySortedForOwner(siteId, userId, normalizedQuery, pageOffset, safeSize, sortField, ascending);
        } else {
            videos = videoTrainingService.getVisibleVideosForUserSorted(siteId, userId, Instant.now(), normalizedQuery, pageOffset, safeSize, sortField, ascending);
        }

        populateNavigationFlags(model, siteId, userId);

        populateVideoPresentationModel(model, videos, siteId, userId, effectiveLocale, isUserSite);
        model.addAttribute("isUserSite", isUserSite);
        model.addAttribute("viewMode", effectiveViewMode);
        model.addAttribute("isCardsView", VIEW_MODE_CARDS.equals(effectiveViewMode));
        model.addAttribute("isTableView", VIEW_MODE_TABLE.equals(effectiveViewMode));
        model.addAttribute("q", normalizedQuery);
        model.addAttribute("size", safeSize);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("offset", pageOffset);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("siteId", siteId);
        model.addAttribute("siteRef", siteService.siteReference(siteId));
        model.addAttribute("currentPath", "/videos");
        model.addAttribute("isManageableList", manageableList);
        model.addAttribute("isViewableList", !manageableList);
        model.addAttribute("accessMode", effectiveAccessMode);
        model.addAttribute("showAccessModeSwitch", canManageOwn && !canManageAll);
        model.addAttribute("moderationEnabled", isModerationEnabled());
        populatePagerModel(model, safePage, safeSize, totalCount);
        model.addAttribute("title", messageSource.getMessage("video.training.title", null, locale));
        return "video-training/list";
    }

    private boolean resolveManageableListMode(String accessMode, boolean canManageAll, boolean canManageOwn) {
        if (canManageAll) {
            return true;
        }
        if (!canManageOwn) {
            return false;
        }

        String normalizedAccessMode = StringUtils.lowerCase(StringUtils.trimToEmpty(accessMode));
        return !ACCESS_MODE_VIEWABLE.equals(normalizedAccessMode);
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
        model.addAttribute("hlsUploadEnabled", isHlsUploadEnabled());
        List<ExistingResourceOption> existingResources = getExistingSiteVideoResources(siteId);
        model.addAttribute("existingVideoResources", existingResources);
        model.addAttribute("sourceMode", SOURCE_MODE_UPLOAD);
        model.addAttribute("nativeUploadMaxBytes", getConfiguredMaxNativeUploadBytes());
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
        model.addAttribute("hlsUploadEnabled", isHlsUploadEnabled());
        List<ExistingResourceOption> existingResources = getExistingSiteVideoResources(siteId);
        model.addAttribute("existingVideoResources", existingResources);
        model.addAttribute("sourceMode", determineSourceMode(video, existingResources));
        model.addAttribute("nativeUploadMaxBytes", getConfiguredMaxNativeUploadBytes());
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
        VideoVisibilityScope parsedVisibilityScope;
        VideoPublicationStatus parsedPublicationStatus;
        try {
            parsedVisibilityScope = parseVisibilityScope(visibilityScope);
            parsedPublicationStatus = parsePublicationStatus(publicationStatus);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

        String siteId = currentSiteId();
        String uploadedSourceReference = null;
        String stagedTempFilePath = null;
        String resolvedSourceReference;
        Long resolvedFileSizeBytes = null;

        if (SOURCE_MODE_EXTERNAL.equals(effectiveSourceMode)) {
            resolvedSourceReference = normalizeExternalSourceReference(sourceReference);
            if (StringUtils.isBlank(resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidExternalSource", null, locale));
                return "redirect:/videos/new";
            }

            boolean wantTitle = StringUtils.isNotBlank(inheritTitleMetadata);
            boolean wantDescription = StringUtils.isNotBlank(inheritDescriptionMetadata);
            if (wantTitle || wantDescription) {
                String videoProvider = ExternalMetadataFetcher.retrieveVideoProvider(resolvedSourceReference);
                try {
                    ExternalMetadataFetcher.MetadataFetchResult meta = ExternalMetadataFetcher.fetchExternalMetadata(resolvedSourceReference);
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
                } catch (Exception ex) {
                    redirectAttributes.addFlashAttribute("error", "La API de " + videoProvider + " no funciona; introduce los datos manualmente.");
                    return "redirect:/videos/new";
                }
            }
        } else if (SOURCE_MODE_RESOURCES.equals(effectiveSourceMode)) {
            if (parsedVisibilityScope == VideoVisibilityScope.GLOBAL) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidGlobalResourceScope", null, locale));
                return "redirect:/videos/new";
            }
            resolvedSourceReference = StringUtils.trimToEmpty(existingResourceReference);
            if (StringUtils.isBlank(resolvedSourceReference) || !isExistingSiteVideoResourceReference(siteId, resolvedSourceReference)) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidResourceReference", null, locale));
                return "redirect:/videos/new";
            }
            resolvedFileSizeBytes = resolveNativeResourceSizeBytes(resolvedSourceReference);
            parsedProviderType = VideoProviderType.RESOURCES;
        } else {
            if (nativeFile == null || nativeFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadRequired", null, locale));
                return "redirect:/videos/new";
            }
            if (!isValidNativeUpload(nativeFile)) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadInvalidType", null, locale));
                return "redirect:/videos/new";
            }

            Long maxNativeUploadBytes = getConfiguredMaxNativeUploadBytes();
            Long margin = 64L * 1024L;
            if (nativeFile.getSize() > (maxNativeUploadBytes + margin)) {
                redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadTooLarge", null, locale));
                return "redirect:/videos/new";
            }

            if (isHlsUploadEnabled()) {
                try {
                    stagedTempFilePath = stageTemporaryHlsUpload(nativeFile);
                    resolvedSourceReference = stagedTempFilePath;
                    resolvedFileSizeBytes = nativeFile.getSize();
                    parsedProviderType = VideoProviderType.HLS_UPLOAD;
                    parsedPublicationStatus = VideoPublicationStatus.DRAFT;
                } catch (Exception ex) {
                    cleanupTemporaryUpload(stagedTempFilePath);
                    redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadFailed", null, locale));
                    return "redirect:/videos/new";
                }
            } else {
                try {
                    uploadedSourceReference = uploadNativeVideo(siteId, currentUserId(), parsedVisibilityScope, nativeFile);
                    resolvedSourceReference = uploadedSourceReference;
                    resolvedFileSizeBytes = nativeFile.getSize();
                    parsedProviderType = VideoProviderType.NATIVE;
                } catch (OverQuotaException ex) {
                    redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadOverQuota", null, locale));
                    return "redirect:/videos/new";
                } catch (Exception ex) {
                    redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.nativeUploadFailed", null, locale));
                    return "redirect:/videos/new";
                }
            }
        }

        if (StringUtils.isBlank(title)
                || (parsedProviderType == VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))
                || (parsedProviderType != VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

        Instant parsedReleaseDate;
        Instant parsedRetractDate;
        try {
            parsedReleaseDate = parseInputDateTime(releaseDate);
            parsedRetractDate = parseInputDateTime(retractDate);
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidDateTime", null, locale));
            return "redirect:/videos/new";
        }

        if (!isValidVisibilityWindow(parsedReleaseDate, parsedRetractDate)) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidVisibilityWindow", null, locale));
            return "redirect:/videos/new";
        }

        VideoTrainingVideo video = new VideoTrainingVideo(
                siteId,
                currentUserId(),
                StringUtils.trimToEmpty(title),
                StringUtils.isNotBlank(inheritTitleMetadata),
                StringUtils.isNotBlank(inheritDescriptionMetadata),
                StringUtils.trimToEmpty(description),
                parsedProviderType,
                resolvedSourceReference,
                resolvedFileSizeBytes,
                parsedVisibilityScope,
                parsedPublicationStatus,
                parsedReleaseDate,
                parsedRetractDate,
                VideoTrainingConstants.PERMISSION_VIEW);

        try {
            VideoTrainingVideo savedVideo = videoTrainingService.saveVideo(video);
            if (savedVideo.getProviderType() == VideoProviderType.HLS_UPLOAD) {
                VideoTrainingProcessJob processJob = new VideoTrainingProcessJob();
                processJob.setVideoId(savedVideo.getId());
                processJob.setSubmitterUserId(savedVideo.getOwnerId());
                processJob.setStatus(VideoTrainingProcessJobStatus.PENDING);
                processJob.setTempFilePath(stagedTempFilePath);
                processJob.setErrorMessage(null);
                processJob.setCreatedOn(Instant.now());
                processJob.setModifiedOn(Instant.now());
                processJobRepository.save(processJob);
                log.info("Created HLS process job {} for video {} (submitter={})", processJob.getId(), processJob.getVideoId(), processJob.getSubmitterUserId());
                scheduleHlsProcessing(savedVideo.getId());
                redirectAttributes.addFlashAttribute("success", messageSource.getMessage("video.training.createdProcessing", null, locale));
            } else {
                redirectAttributes.addFlashAttribute("success", messageSource.getMessage("video.training.created", null, locale));
            }
        } catch (SecurityException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        } catch (RuntimeException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            if (StringUtils.isNotBlank(video.getId())) {
                try {
                    videoTrainingService.deleteVideo(video.getId());
                } catch (Exception ignored) {
                }
            }
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/new";
        }

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
        VideoTrainingVideo originalVideo = copyVideo(existing);

        String effectiveSourceMode = resolveSourceMode(sourceMode, providerType);
        VideoProviderType parsedProviderType = providerTypeForSourceMode(effectiveSourceMode);
        VideoVisibilityScope parsedVisibilityScope;
        VideoPublicationStatus parsedPublicationStatus;
        try {
            parsedVisibilityScope = parseVisibilityScope(visibilityScope);
            parsedPublicationStatus = parsePublicationStatus(publicationStatus);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        }
        String previousSourceReference = StringUtils.trimToEmpty(existing.getSourceReference());
        VideoProviderType previousProviderType = existing.getProviderType();

        String uploadedSourceReference = null;
        String stagedTempFilePath = null;
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
                String videoProvider = ExternalMetadataFetcher.retrieveVideoProvider(resolvedSourceReference);
                try {
                    ExternalMetadataFetcher.MetadataFetchResult meta = ExternalMetadataFetcher.fetchExternalMetadata(resolvedSourceReference);

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
            if (parsedVisibilityScope == VideoVisibilityScope.GLOBAL) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.invalidGlobalResourceScope", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
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

                Long maxNativeUploadBytes = getConfiguredMaxNativeUploadBytes();
                Long margin = 64L * 1024L; // 64KB margin for multipart overhead
                if (nativeFile.getSize() > (maxNativeUploadBytes + margin)) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadTooLarge", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }
                try {
                    if (isHlsUploadEnabled()) {
                        stagedTempFilePath = stageTemporaryHlsUpload(nativeFile);
                        uploadedSourceReference = stagedTempFilePath;
                        resolvedSourceReference = stagedTempFilePath;
                        resolvedFileSizeBytes = nativeFile.getSize();
                        parsedProviderType = VideoProviderType.HLS_UPLOAD;
                        parsedPublicationStatus = VideoPublicationStatus.DRAFT;
                    } else {
                        uploadedSourceReference = uploadNativeVideo(existing.getSiteId(), currentUserId(), parsedVisibilityScope, nativeFile);
                        resolvedSourceReference = uploadedSourceReference;
                        resolvedFileSizeBytes = nativeFile.getSize();
                        parsedProviderType = VideoProviderType.NATIVE;
                    }
                } catch (OverQuotaException ex) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadOverQuota", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                } catch (Exception ex) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("video.training.nativeUploadFailed", null, locale));
                    return "redirect:/videos/" + videoId + "/edit";
                }
            } else if (existing.getProviderType() == VideoProviderType.HLS_UPLOAD) {
                resolvedSourceReference = previousSourceReference;
                if (resolvedFileSizeBytes == null) {
                    resolvedFileSizeBytes = existing.getFileSizeBytes();
                }
                parsedProviderType = VideoProviderType.HLS_UPLOAD;
            } else if (existing.getProviderType() == VideoProviderType.NATIVE) {
                resolvedSourceReference = previousSourceReference;
                if (resolvedFileSizeBytes == null) {
                    resolvedFileSizeBytes = resolveNativeResourceSizeBytes(previousSourceReference);
                }

                String relocatedSourceReference = relocateManagedNativeResourceIfNeeded(
                        previousSourceReference,
                        existing.getSiteId(),
                    StringUtils.defaultIfBlank(existing.getOwnerId(), currentUserId()),
                        parsedVisibilityScope);
                if (!StringUtils.equals(relocatedSourceReference, previousSourceReference)) {
                    uploadedSourceReference = relocatedSourceReference;
                    resolvedSourceReference = relocatedSourceReference;
                    if (resolvedFileSizeBytes == null) {
                        resolvedFileSizeBytes = resolveNativeResourceSizeBytes(relocatedSourceReference);
                    }
                }
            } else {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("video.training.nativeUploadRequired", null, locale));
                return "redirect:/videos/" + videoId + "/edit";
            }
        }

        if (StringUtils.isBlank(title)
                || (parsedProviderType == VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))
                || (parsedProviderType != VideoProviderType.EXTERNAL && StringUtils.isBlank(resolvedSourceReference))) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
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
        existing.setInheritTitleMetadata(StringUtils.isNotBlank(inheritTitleMetadata));
        existing.setInheritDescriptionMetadata(StringUtils.isNotBlank(inheritDescriptionMetadata));
        existing.setDescription(StringUtils.trimToEmpty(description));
        existing.setProviderType(parsedProviderType);
        existing.setSourceReference(resolvedSourceReference);
        existing.setFileSizeBytes(parsedProviderType == VideoProviderType.EXTERNAL ? null : resolvedFileSizeBytes);
        existing.setVisibilityScope(parsedVisibilityScope);
        existing.setPublicationStatus(parsedPublicationStatus);
        existing.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        existing.setReleaseDate(parsedReleaseDate);
        existing.setRetractDate(parsedRetractDate);

        try {
            VideoTrainingVideo savedVideo = videoTrainingService.saveVideo(existing);
            if (savedVideo.getProviderType() == VideoProviderType.HLS_UPLOAD && StringUtils.isNotBlank(stagedTempFilePath)) {
                VideoTrainingProcessJob processJob = new VideoTrainingProcessJob();
                processJob.setVideoId(savedVideo.getId());
                processJob.setSubmitterUserId(savedVideo.getOwnerId());
                processJob.setStatus(VideoTrainingProcessJobStatus.PENDING);
                processJob.setTempFilePath(stagedTempFilePath);
                processJob.setErrorMessage(null);
                processJob.setCreatedOn(Instant.now());
                processJob.setModifiedOn(Instant.now());
                processJobRepository.save(processJob);
                log.info("Queued HLS process job {} for video {} (submitter={})", processJob.getId(), processJob.getVideoId(), processJob.getSubmitterUserId());
                scheduleHlsProcessing(savedVideo.getId());
            }
        } catch (SecurityException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        } catch (IllegalArgumentException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("video.training.invalidInput", null, locale));
            return "redirect:/videos/" + videoId + "/edit";
        } catch (RuntimeException ex) {
            cleanupManagedNativeResource(uploadedSourceReference);
            cleanupTemporaryUpload(stagedTempFilePath);
            try {
                videoTrainingService.saveVideo(originalVideo);
            } catch (Exception ignored) {
            }
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
        model.addAttribute("isProcessingUpload", video.getProviderType() == VideoProviderType.HLS_UPLOAD
            && video.getPublicationStatus() != VideoPublicationStatus.PUBLISHED);
        model.addAttribute("externalEmbedUrl", externalEmbedUrl);
        model.addAttribute("nativePlaybackUrl", resolveNativePlaybackUrl(video));
        model.addAttribute("nativeContentType", resolveNativeContentType(video));
        model.addAttribute("moderationEnabled", isModerationEnabled());
        model.addAttribute("isVisibleNow", videoTrainingService.canViewVideo(video, userId, Instant.now()));
        model.addAttribute("releaseDateDisplay", formatInstantForDisplay(video.getReleaseDate(), effectiveLocale));
        model.addAttribute("retractDateDisplay", formatInstantForDisplay(video.getRetractDate(), effectiveLocale));
        model.addAttribute("newCaption", new VideoTrainingCaption());
        model.addAttribute("captions", videoTrainingService.getCaptionsForVideo(videoId));
        model.addAttribute("isFavorite",
            videoTrainingService.getUserVideoPreference(video.getSiteId(), videoId, userId)
                .map(pref -> pref.isFavorite())
                .orElse(false));
        model.addAttribute("isWatchLater",
            videoTrainingService.getUserVideoPreference(video.getSiteId(), videoId, userId)
                .map(pref -> pref.isWatchLater())
                .orElse(false));
        model.addAttribute("currentPath", "/videos/" + videoId);
        return "video-training/details";
    }

    @PostMapping("/videos/{videoId}/favorite")
    public String setFavorite(@PathVariable String videoId,
        @RequestParam(name = "favorite", defaultValue = "true") boolean favorite,
        @RequestParam(name = "returnTo", required = false) String returnTo,
        RedirectAttributes redirectAttributes,
        Locale locale) {

        String siteId = currentSiteId();
        String userId = currentUserId();
        try {
            videoTrainingService.setUserFavorite(siteId, videoId, userId, favorite);
            redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage(favorite ? "video.training.favorites.added" : "video.training.favorites.removed", null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.accessDenied", null, locale));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.notFound", null, locale));
        }

        return "redirect:" + resolveReturnPath(returnTo, "/videos/" + videoId);
    }

    @PostMapping("/videos/{videoId}/watch-later")
    public String setWatchLater(@PathVariable String videoId,
        @RequestParam(name = "watchLater", defaultValue = "true") boolean watchLater,
        @RequestParam(name = "returnTo", required = false) String returnTo,
        RedirectAttributes redirectAttributes,
        Locale locale) {

        String siteId = currentSiteId();
        String userId = currentUserId();
        try {
            videoTrainingService.setUserWatchLater(siteId, videoId, userId, watchLater);
            redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage(watchLater ? "video.training.watchLater.added" : "video.training.watchLater.removed", null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.accessDenied", null, locale));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("video.training.notFound", null, locale));
        }

        return "redirect:" + resolveReturnPath(returnTo, "/videos/" + videoId);
    }

    @GetMapping(FAVORITES_PATH)
    public String favorites(@RequestParam(name = "viewMode", required = false) String viewMode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "size", required = false, defaultValue = "15") int size,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDir", required = false) String sortDir,
            Locale locale,
            RedirectAttributes redirectAttributes,
            Model model) {
        return renderPreferredVideosList(true, FAVORITES_PATH, "favorites", "video.training.favorites.title",
                "video.training.favorites.empty", viewMode, query, size, page, offset, sortBy, sortDir,
                locale, redirectAttributes, model);
    }

    @GetMapping(WATCH_LATER_PATH)
    public String watchLater(@RequestParam(name = "viewMode", required = false) String viewMode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "size", required = false, defaultValue = "15") int size,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDir", required = false) String sortDir,
            Locale locale,
            RedirectAttributes redirectAttributes,
            Model model) {
        return renderPreferredVideosList(false, WATCH_LATER_PATH, "watch-later", "video.training.watchLater.title",
            "video.training.watchLater.empty", viewMode, query, size, page, offset, sortBy, sortDir,
                locale, redirectAttributes, model);
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
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.notFound", null, locale));
            return "redirect:/videos";
        }

        try {
            videoTrainingService.deleteCaption(captionId);
            redirectAttributes.addFlashAttribute("success", messageSource.getMessage("video.training.caption.deleted", null, locale));
        } catch (SecurityException ex) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.accessDenied", null, locale));
        }

        return "redirect:/videos/" + videoId;
    }

    @GetMapping("/upload-jobs")
    public String uploadJobs(RedirectAttributes redirectAttributes, Locale locale, Model model) {
        String siteId = currentSiteId();
        String userId = currentUserId();

        if (!(videoTrainingService.canManageLibrary(siteId, userId) || videoTrainingService.hasManagePermission(siteId, userId))) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.accessDenied", null, locale));
            return "redirect:/videos";
        }

        populateNavigationFlags(model, siteId, userId);
        model.addAttribute("jobs", buildUploadJobViews(siteId, userId, locale));
        return "video-training/upload-jobs";
    }

    private void populateNavigationFlags(Model model, String siteId, String userId) {
        boolean canManageAll = videoTrainingService.canManageLibrary(siteId, userId);
        boolean canManageOwn = videoTrainingService.hasManagePermission(siteId, userId);
        model.addAttribute("canManage", canManageAll || canManageOwn);
        model.addAttribute("canAnalytics", videoTrainingService.canViewAnalytics(siteId, userId));
        model.addAttribute("canView", videoTrainingService.hasViewPermission(siteId, userId)
                || canManageAll
                || canManageOwn);
    }

    private void scheduleHlsProcessing(String videoId) {
        try {
            Instant fireTime = Instant.now().plusSeconds(5L);
            String invocationId = scheduledInvocationManager.createDelayedInvocation(fireTime, "HlsTranscodingJob", videoId);
            log.info("Scheduled HLS trigger {} for video {} at {}", invocationId, videoId, fireTime);
        } catch (RuntimeException ex) {
            cleanupProcessJobs(videoId);
            throw ex;
        }
    }

    private void cleanupProcessJobs(String videoId) {
        if (StringUtils.isBlank(videoId)) {
            return;
        }

        try {
            List<VideoTrainingProcessJob> jobs = processJobRepository.findByVideoIdOrderByModifiedOnDesc(videoId);
            for (VideoTrainingProcessJob job : jobs) {
                processJobRepository.delete(job);
            }
        } catch (Exception ex) {
            log.warn("Failed to cleanup process jobs for video {}", videoId, ex);
        }
    }

    private List<UploadJobView> buildUploadJobViews(String siteId, String userId, Locale locale) {
        List<UploadJobView> jobViews = new ArrayList<>();
        for (VideoTrainingProcessJob job : processJobRepository.findBySubmitterUserIdOrderByModifiedOnDesc(userId)) {
            VideoTrainingVideo video = videoTrainingService.getVideoById(job.getVideoId()).orElse(null);
            if (video == null || !Objects.equals(video.getSiteId(), siteId)) {
                continue;
            }

            jobViews.add(new UploadJobView(
                    job,
                    video,
                    formatInstantForDisplay(job.getCreatedOn(), locale),
                    formatInstantForDisplay(job.getModifiedOn(), locale),
                    localizeJobStatus(job.getStatus(), locale)));
        }
        return jobViews;
    }

    private String localizeJobStatus(VideoTrainingProcessJobStatus status, Locale locale) {
        if (status == null) {
            return "";
        }
        String key = "video.training.job.status." + status.name();
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception ex) {
            return status.name();
        }
    }

    private String renderPreferredVideosList(boolean favoritesOnly, String listPath, String menuCurrent,
            String titleMessageKey, String emptyMessageKey,
            String viewMode, String query, int size, Integer page, Integer offset, String sortBy, String sortDir,
            Locale locale, RedirectAttributes redirectAttributes, Model model) {
        String siteId = currentSiteId();
        String userId = currentUserId();
        Locale effectiveLocale = locale != null ? locale : Locale.getDefault();

        if (!videoTrainingService.hasViewPermission(siteId, userId)
            && !videoTrainingService.canManageLibrary(siteId, userId)
            && !videoTrainingService.hasManagePermission(siteId, userId)) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("video.training.accessDenied", null, effectiveLocale));
            return "redirect:/videos";
        }

        boolean isUserSite = siteService.isUserSite(siteId);
        boolean canManage = videoTrainingService.canManageLibrary(siteId, userId)
                || videoTrainingService.hasManagePermission(siteId, userId);
        String normalizedQuery = StringUtils.trimToEmpty(query);
        int safeSize = normalizePageSize(size);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        String sortField = mapSortField(normalizedSortBy, isUserSite);
        boolean ascending = "asc".equals(normalizedSortDir);
        String effectiveViewMode = resolveEffectiveViewMode(siteId, canManage, viewMode);

        List<VideoTrainingVideo> preferredVideos = favoritesOnly
                ? videoTrainingService.getUserFavoriteVideos(siteId, userId, Instant.now())
                : videoTrainingService.getUserWatchLaterVideos(siteId, userId, Instant.now());
        List<VideoTrainingVideo> filteredVideos = new ArrayList<>();
        for (VideoTrainingVideo video : preferredVideos) {
            if (matchesSearchText(video, normalizedQuery)) {
                filteredVideos.add(video);
            }
        }
        filteredVideos.sort((left, right) -> comparePreferredVideos(left, right, sortField, ascending));

        long totalCount = filteredVideos.size();
        int requestedPage = page != null ? page : ((offset != null ? offset : 0) / safeSize) + 1;
        int safePage = normalizePage(requestedPage, safeSize, totalCount);
        int pageOffset = (safePage - 1) * safeSize;
        int endIndex = Math.min(filteredVideos.size(), pageOffset + safeSize);
        List<VideoTrainingVideo> videos = pageOffset >= filteredVideos.size()
                ? new ArrayList<>()
                : new ArrayList<>(filteredVideos.subList(pageOffset, endIndex));
        int totalPages = (int) Math.max(1, Math.ceil((double) totalCount / safeSize));

        populateNavigationFlags(model, siteId, userId);
        populateVideoPresentationModel(model, videos, siteId, userId, effectiveLocale, isUserSite);
        model.addAttribute("videos", videos);
        model.addAttribute("isUserSite", isUserSite);
        model.addAttribute("viewMode", effectiveViewMode);
        model.addAttribute("isCardsView", VIEW_MODE_CARDS.equals(effectiveViewMode));
        model.addAttribute("isTableView", VIEW_MODE_TABLE.equals(effectiveViewMode));
        model.addAttribute("q", normalizedQuery);
        model.addAttribute("size", safeSize);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("offset", pageOffset);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("siteId", siteId);
        model.addAttribute("siteRef", siteService.siteReference(siteId));
        model.addAttribute("currentPath", listPath);
        model.addAttribute("listPath", listPath);
        model.addAttribute("currentMenu", menuCurrent);
        model.addAttribute("showManageColumns", true);
        model.addAttribute("showManageButtons", false);
        model.addAttribute("showAccessModeSwitch", false);
        model.addAttribute("isManageableList", false);
        model.addAttribute("isViewableList", false);
        model.addAttribute("accessMode", menuCurrent);
        populatePagerModel(model, safePage, safeSize, totalCount);
        model.addAttribute("title", messageSource.getMessage(titleMessageKey, null, effectiveLocale));
        model.addAttribute("emptyMessage", messageSource.getMessage(emptyMessageKey, null, effectiveLocale));
        return favoritesOnly ? "video-training/favorites" : "video-training/watch-later";
    }

    private boolean matchesSearchText(VideoTrainingVideo video, String searchText) {
        if (video == null || StringUtils.isBlank(searchText)) {
            return true;
        }

        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(searchText));
        return StringUtils.contains(StringUtils.lowerCase(StringUtils.defaultString(video.getTitle())), normalized)
                || StringUtils.contains(StringUtils.lowerCase(StringUtils.defaultString(video.getDescription())), normalized)
                || StringUtils.contains(StringUtils.lowerCase(StringUtils.defaultString(video.getSourceReference())), normalized);
    }

    private int comparePreferredVideos(VideoTrainingVideo left, VideoTrainingVideo right, String sortField, boolean ascending) {
        int primaryComparison = comparePreferredVideoField(left, right, sortField, ascending);
        if (primaryComparison != 0) {
            return primaryComparison;
        }

        int modifiedComparison = compareNullable(left.getModifiedOn(), right.getModifiedOn(), false);
        if (modifiedComparison != 0) {
            return modifiedComparison;
        }

        return compareNullable(left.getId(), right.getId(), false);
    }

    private int comparePreferredVideoField(VideoTrainingVideo left, VideoTrainingVideo right, String sortField, boolean ascending) {
        switch (sortField) {
            case "title":
                return compareNullable(left.getTitle(), right.getTitle(), ascending);
            case "siteId":
                return compareNullable(left.getSiteId(), right.getSiteId(), ascending);
            case "providerType":
                return compareNullable(enumName(left.getProviderType()), enumName(right.getProviderType()), ascending);
            case "visibilityScope":
                return compareNullable(enumName(left.getVisibilityScope()), enumName(right.getVisibilityScope()), ascending);
            case "publicationStatus":
                return compareNullable(enumName(left.getPublicationStatus()), enumName(right.getPublicationStatus()), ascending);
            case "releaseDate":
                return compareNullable(left.getReleaseDate(), right.getReleaseDate(), ascending);
            case "retractDate":
                return compareNullable(left.getRetractDate(), right.getRetractDate(), ascending);
            case "modifiedOn":
            default:
                return compareNullable(left.getModifiedOn(), right.getModifiedOn(), ascending);
        }
    }

    private int compareNullable(String left, String right, boolean ascending) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        int comparison = String.CASE_INSENSITIVE_ORDER.compare(left, right);
        return ascending ? comparison : -comparison;
    }

    private <T extends Comparable<? super T>> int compareNullable(T left, T right, boolean ascending) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        int comparison = left.compareTo(right);
        return ascending ? comparison : -comparison;
    }

    private String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private String resolveReturnPath(String returnTo, String fallback) {
        String candidate = StringUtils.trimToEmpty(returnTo);
        if (StringUtils.isBlank(candidate)) {
            return fallback;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return fallback;
        }
        return candidate;
    }

    private void populateVideoPresentationModel(Model model,
            List<VideoTrainingVideo> videos,
            String siteId,
            String userId,
            Locale locale,
            boolean isUserSite) {

        Map<String, String> releaseDisplayById = new HashMap<>();
        Map<String, String> retractDisplayById = new HashMap<>();
        Map<String, String> thumbnailUrlById = new HashMap<>();
        Map<String, Boolean> thumbnailIsVideoById = new HashMap<>();
        Map<String, String> siteNameBySiteId = new HashMap<>();

        for (VideoTrainingVideo video : videos) {
            releaseDisplayById.put(video.getId(), formatInstantForDisplay(video.getReleaseDate(), locale));
            retractDisplayById.put(video.getId(), formatInstantForDisplay(video.getRetractDate(), locale));
            thumbnailUrlById.put(video.getId(), buildThumbnailUrl(video));
            thumbnailIsVideoById.put(video.getId(), isNativeVideoThumbnail(video));
            if (isUserSite && !siteNameBySiteId.containsKey(video.getSiteId())) {
                siteNameBySiteId.put(video.getSiteId(), getSiteName(video.getSiteId()));
            }
        }

        Map<String, Boolean> isFavoriteById = new HashMap<>();
        Map<String, Boolean> isWatchLaterById = new HashMap<>();
        List<String> videoIds = videos.stream().map(VideoTrainingVideo::getId).toList();
        videoTrainingService.getUserVideoPreferences(siteId, userId, videoIds)
                .forEach((videoId, pref) -> {
                    isFavoriteById.put(videoId, pref.isFavorite());
                    isWatchLaterById.put(videoId, pref.isWatchLater());
                });

        model.addAttribute("videos", videos);
        model.addAttribute("releaseDisplayById", releaseDisplayById);
        model.addAttribute("retractDisplayById", retractDisplayById);
        model.addAttribute("thumbnailUrlById", thumbnailUrlById);
        model.addAttribute("thumbnailIsVideoById", thumbnailIsVideoById);
        model.addAttribute("isFavoriteById", isFavoriteById);
        model.addAttribute("isWatchLaterById", isWatchLaterById);
        model.addAttribute("siteNameBySiteId", siteNameBySiteId);
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
        if ("RESOURCES".equals(normalizedProviderType)) {
            return SOURCE_MODE_RESOURCES;
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
        return serverConfigurationService.getBoolean(MODERATION_ENABLED_PROPERTY, false);
    }

    private VideoProviderType providerTypeForSourceMode(String sourceMode) {
        if (SOURCE_MODE_EXTERNAL.equals(sourceMode)) {
            return VideoProviderType.EXTERNAL;
        }
        if (SOURCE_MODE_RESOURCES.equals(sourceMode)) {
            return VideoProviderType.RESOURCES;
        }
        return isHlsUploadEnabled() ? VideoProviderType.HLS_UPLOAD : VideoProviderType.NATIVE;
    }

    private boolean isHlsUploadEnabled() {
        return serverConfigurationService.getBoolean("video.training.hls.enabled", true);
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
        if (video == null || video.getProviderType() == VideoProviderType.EXTERNAL) {
            return "video/mp4";
        }

        try {
            ContentResource resource = ContentResourceHelper.getContentResource(video.getSourceReference());
            return StringUtils.defaultIfBlank(resource.getContentType(), "video/mp4");
        } catch (Exception e) {
            return "video/mp4";
        }
    }

    private String resolveNativePlaybackUrl(VideoTrainingVideo video) {
        if (video == null || video.getProviderType() == VideoProviderType.EXTERNAL) {
            return "";
        }

        return resolveContentReferenceFromSourceId(video.getSourceReference());
    }

    private Long resolveNativeResourceSizeBytes(String sourceReference) {
        if (StringUtils.isBlank(sourceReference)) {
            return null;
        }

        try {
            ContentResource resource = ContentResourceHelper.getContentResource(sourceReference);
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
            ContentResource resource = ContentResourceHelper.getContentResource(sourceReference);
            ResourceProperties properties = resource.getProperties();
            String managedFlag = properties != null ? properties.getProperty(MANAGED_UPLOAD_PROPERTY) : null;
            if (!"true".equalsIgnoreCase(managedFlag)) {
                return;
            }

            contentHostingService.removeResource(ContentResourceHelper.toContentResourceId(sourceReference));
        } catch (Exception e) {
            // best effort cleanup path
        }
    }

    private String relocateManagedNativeResourceIfNeeded(String sourceReference,
            String siteId,
            String ownerId,
            VideoVisibilityScope visibilityScope) {
        if (StringUtils.isBlank(sourceReference) || visibilityScope == null) {
            return sourceReference;
        }

        try {
            ContentResource resource = ContentResourceHelper.getContentResource(sourceReference);
            ResourceProperties properties = resource.getProperties();
            String managedFlag = properties != null ? properties.getProperty(MANAGED_UPLOAD_PROPERTY) : null;
            if (!"true".equalsIgnoreCase(managedFlag)) {
                return sourceReference;
            }

            if (visibilityScope == VideoVisibilityScope.GLOBAL) {
                return sourceReference;
            }

            String targetCollectionId = resolveManagedUploadCollectionId(siteId, ownerId, visibilityScope);
            String currentResourceId = ContentResourceHelper.toContentResourceId(sourceReference);
            if (StringUtils.startsWith(currentResourceId, targetCollectionId)) {
                return sourceReference;
            }

            String originalFilename = properties != null
                    ? properties.getProperty(ResourceProperties.PROP_DISPLAY_NAME)
                    : null;
            String resolvedFilename = StringUtils.defaultIfBlank(originalFilename, resource.getId());
            String extension = "";
            String baseName = resolvedFilename;
            int extensionIndex = resolvedFilename.lastIndexOf('.');
            if (extensionIndex > 0 && extensionIndex < resolvedFilename.length() - 1) {
                extension = resolvedFilename.substring(extensionIndex);
                baseName = resolvedFilename.substring(0, extensionIndex);
            }

            String safeBaseName = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(baseName), "video")
                    + "-"
                    + UUID.randomUUID();

            ContentResourceEdit edit = contentHostingService.addResource(targetCollectionId, safeBaseName, extension, 1);
            boolean committed = false;
            try {
                edit.setContent(resource.getContent());
                edit.setContentLength(resource.getContentLength());
                edit.setContentType(StringUtils.defaultIfBlank(resource.getContentType(), "application/octet-stream"));
                edit.setAvailability(resource.isHidden(), resource.getReleaseDate(), resource.getRetractDate());
                edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, resolvedFilename);
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_PROPERTY, "true");
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_SITE_PROPERTY, siteId);
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_OWNER_PROPERTY, ownerId);
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_SCOPE_PROPERTY, visibilityScope.name());
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
        } catch (Exception e) {
            return sourceReference;
        }
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

    private void populatePagerModel(Model model, int page, int pageSize, long totalCount) {
        int totalPages = (int) Math.max(1, Math.ceil((double) totalCount / pageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        long topMsgPos = totalCount == 0 ? 0 : ((long) (safePage - 1) * pageSize) + 1;
        long btmMsgPos = totalCount == 0 ? 0 : Math.min(totalCount, (long) safePage * pageSize);

        List<Integer> pageSizes = new ArrayList<>(List.of(5, 10, 15, 20, 24, 50, 100));
        if (!pageSizes.contains(pageSize)) {
            pageSizes.add(pageSize);
            Collections.sort(pageSizes);
        }

        model.addAttribute("allMsgNumber", totalCount);
        model.addAttribute("topMsgPos", topMsgPos);
        model.addAttribute("btmMsgPos", btmMsgPos);
        model.addAttribute("goFPButton", safePage > 1);
        model.addAttribute("goPPButton", safePage > 1);
        model.addAttribute("goNPButton", safePage < totalPages);
        model.addAttribute("goLPButton", safePage < totalPages);
        model.addAttribute("pagesizes", pageSizes);
        model.addAttribute("pagesize", pageSize);
        model.addAttribute("totalPages", totalPages);
    }

    private long getConfiguredMaxNativeUploadBytes() {
        String configuredValue = StringUtils.trimToEmpty(serverConfigurationService.getString(
                MAX_NATIVE_UPLOAD_SIZE_PROPERTY,
                String.valueOf(DEFAULT_MAX_NATIVE_UPLOAD_MB)));
        try {
            long parsedMegabytes = Long.parseLong(configuredValue);
            long safeMegabytes = parsedMegabytes > 0 ? parsedMegabytes : DEFAULT_MAX_NATIVE_UPLOAD_MB;
            return safeMegabytes * BYTES_PER_MB;
        } catch (NumberFormatException ex) {
            return DEFAULT_MAX_NATIVE_UPLOAD_MB * BYTES_PER_MB;
        }
    }

    private boolean isValidViewMode(String viewMode) {
        return VIEW_MODE_CARDS.equals(viewMode) || VIEW_MODE_TABLE.equals(viewMode);
    }

    private String buildThumbnailUrl(VideoTrainingVideo video) {
        if (video == null) {
            return "";
        }

        if (video.getProviderType() == VideoProviderType.NATIVE || video.getProviderType() == VideoProviderType.RESOURCES) {
            return resolveContentReferenceFromSourceId(video.getSourceReference());
        }

        if (video.getProviderType() != VideoProviderType.EXTERNAL) {
            return "";
        }

        String source = normalizeExternalSourceReference(video.getSourceReference());
        String youtubeVideoId = ExternalMetadataFetcher.extractYoutubeVideoId(source);
        if (StringUtils.isNotBlank(youtubeVideoId)) {
            return "https://img.youtube.com/vi/" + youtubeVideoId + "/hqdefault.jpg";
        }

        String vimeoVideoId = ExternalMetadataFetcher.extractVimeoVideoId(source);
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
            return ContentResourceHelper.getContentUrl(sourceId);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isNativeVideoThumbnail(VideoTrainingVideo video) {
        return video != null
                && (video.getProviderType() == VideoProviderType.NATIVE || video.getProviderType() == VideoProviderType.RESOURCES)
                && StringUtils.isNotBlank(video.getSourceReference());
    }

    private String normalizeExternalSourceReference(String sourceReference) {
        String normalized = StringUtils.trimToEmpty(sourceReference);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        normalized = normalized.replace("&amp;", "&");

        String youtubeVideoId = ExternalMetadataFetcher.extractYoutubeVideoId(normalized);
        if (StringUtils.isNotBlank(youtubeVideoId)) {
            return "https://www.youtube.com/embed/" + youtubeVideoId;
        }

        String vimeoVideoId = ExternalMetadataFetcher.extractVimeoVideoId(normalized);
        if (StringUtils.isNotBlank(vimeoVideoId)) {
            return "https://player.vimeo.com/video/" + vimeoVideoId;
        }

        return "";
    }

    private String determineSourceMode(VideoTrainingVideo video, List<ExistingResourceOption> existingResources) {
        if (video == null) {
            return SOURCE_MODE_UPLOAD;
        }

        if (video.getProviderType() == VideoProviderType.EXTERNAL) {
            return SOURCE_MODE_EXTERNAL;
        }

        if (video.getProviderType() == VideoProviderType.RESOURCES) {
            return SOURCE_MODE_RESOURCES;
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

    private static class UploadJobView {

        private final VideoTrainingProcessJob job;
        private final VideoTrainingVideo video;
        private final String createdOnDisplay;
        private final String modifiedOnDisplay;
        private final String statusLabel;

        private UploadJobView(VideoTrainingProcessJob job,
                VideoTrainingVideo video,
                String createdOnDisplay,
                String modifiedOnDisplay,
                String statusLabel) {
            this.job = job;
            this.video = video;
            this.createdOnDisplay = createdOnDisplay;
            this.modifiedOnDisplay = modifiedOnDisplay;
            this.statusLabel = statusLabel;
        }

        public VideoTrainingProcessJob getJob() {
            return job;
        }

        public VideoTrainingVideo getVideo() {
            return video;
        }

        public String getCreatedOnDisplay() {
            return createdOnDisplay;
        }

        public String getModifiedOnDisplay() {
            return modifiedOnDisplay;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public String getErrorMessage() {
            return job.getErrorMessage();
        }

        public String getStatus() {
            return job.getStatus() != null ? job.getStatus().name() : "";
        }

        public String getJobId() {
            return job.getId();
        }
    }

    private VideoTrainingVideo copyVideo(VideoTrainingVideo source) {
        VideoTrainingVideo copy = new VideoTrainingVideo();
        copy.setId(source.getId());
        copy.setSiteId(source.getSiteId());
        copy.setOwnerId(source.getOwnerId());
        copy.setTitle(source.getTitle());
        copy.setInheritTitleMetadata(source.isInheritTitleMetadata());
        copy.setInheritDescriptionMetadata(source.isInheritDescriptionMetadata());
        copy.setDescription(source.getDescription());
        copy.setProviderType(source.getProviderType());
        copy.setSourceReference(source.getSourceReference());
        copy.setFileSizeBytes(source.getFileSizeBytes());
        copy.setVisibilityScope(source.getVisibilityScope());
        copy.setPublicationStatus(source.getPublicationStatus());
        copy.setReleaseDate(source.getReleaseDate());
        copy.setRetractDate(source.getRetractDate());
        copy.setRequiredViewPermission(source.getRequiredViewPermission());
        copy.setCreatedOn(source.getCreatedOn());
        copy.setModifiedOn(source.getModifiedOn());
        copy.setCategories(new HashSet<>(source.getCategories()));
        return copy;
    }

    private String uploadNativeVideo(String siteId,
            String ownerId,
            VideoVisibilityScope visibilityScope,
            MultipartFile nativeFile) throws Exception {
        String collectionId = resolveManagedUploadCollectionId(siteId, ownerId, visibilityScope);

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
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_OWNER_PROPERTY, ownerId);
                edit.getPropertiesEdit().addProperty(MANAGED_UPLOAD_SCOPE_PROPERTY,
                    visibilityScope != null ? visibilityScope.name() : VideoVisibilityScope.COURSE.name());
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

    private String stageTemporaryHlsUpload(MultipartFile nativeFile) throws Exception {
        Path baseDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "video-training");
        Files.createDirectories(baseDirectory);
        Path workingDirectory = Files.createTempDirectory(baseDirectory, "hls-");
        Path tempFile = Files.createTempFile(workingDirectory, "upload-", ".mp4");
        try (java.io.InputStream inputStream = nativeFile.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile.toAbsolutePath().toString();
    }

    private void cleanupTemporaryUpload(String tempFilePath) {
        if (StringUtils.isBlank(tempFilePath)) {
            return;
        }

        try {
            Path tempFile = Paths.get(tempFilePath);
            Path workingDirectory = tempFile.getParent();
            if (workingDirectory != null && Files.exists(workingDirectory)) {
                Files.walk(workingDirectory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            } else {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception ignored) {
        }
    }

    private String resolveManagedUploadCollectionId(String siteId,
            String ownerId,
            VideoVisibilityScope visibilityScope) throws Exception {
        String collectionId;
        VideoVisibilityScope effectiveScope = visibilityScope != null ? visibilityScope : VideoVisibilityScope.COURSE;

        if (effectiveScope == VideoVisibilityScope.GLOBAL) {
            String globalRoot = StringUtils.trimToEmpty(ServerConfigurationService.getString(MANAGED_GLOBAL_ROOT_PROPERTY, DEFAULT_MANAGED_GLOBAL_ROOT));
            if (!globalRoot.startsWith("/")) {
                globalRoot = "/" + globalRoot;
            }
            if (!globalRoot.endsWith("/")) {
                globalRoot = globalRoot + "/";
            }

            String normalizedOwner = Validator.escapeResourceName(StringUtils.defaultIfBlank(ownerId, currentUserId()));
            if (StringUtils.isBlank(normalizedOwner)) {
                normalizedOwner = "owner";
            }
            collectionId = globalRoot + normalizedOwner + "/";
            ensureManagedCollectionPath(globalRoot, StringUtils.trimToEmpty(ServerConfigurationService.getString(MANAGED_GLOBAL_ROOT_BASE_FOLDER_PROPERTY , DEFAULT_MANAGED_GLOBAL_ROOT_BASE_FOLDER_PROPERTY)), true);
            ensureManagedCollectionPath(collectionId, normalizedOwner, true);
            return collectionId;
        }

        String siteCollectionId = contentHostingService.getSiteCollection(siteId);
        String baseFolderProperty = effectiveScope == VideoVisibilityScope.LESSON
                ? MANAGED_LESSON_BASE_FOLDER_PROPERTY
                : MANAGED_BASE_FOLDER_PROPERTY;
        String baseFolderDefault = effectiveScope == VideoVisibilityScope.LESSON
                ? DEFAULT_LESSON_FOLDER_FALLBACK
                : DEFAULT_MANAGED_BASE_FOLDER;
        String baseFolder = StringUtils.trimToEmpty(ServerConfigurationService.getString(baseFolderProperty, baseFolderDefault));

        String normalizedBaseFolder = Validator.escapeResourceName(baseFolder);
        if (StringUtils.isBlank(normalizedBaseFolder)) {
            normalizedBaseFolder = baseFolderDefault;
        }

        collectionId = siteCollectionId + normalizedBaseFolder + "/";
        ensureManagedCollectionPath(collectionId, normalizedBaseFolder, false);
        return collectionId;
    }

    private void ensureManagedCollectionPath(String collectionId, String displayName, boolean forceVisible) throws Exception {
        boolean hiddenWithAccessibleContent = !forceVisible && ServerConfigurationService.getBoolean(MANAGED_FOLDER_HIDDEN_WITH_ACCESS_PROPERTY, true);

        try {
            contentHostingService.checkCollection(collectionId);
        } catch (IdUnusedException idUnusedException) {
            ContentCollectionEdit edit = contentHostingService.addCollection(collectionId);
            if (StringUtils.isNotBlank(displayName)) {
                edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, displayName);
            }
            edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_HIDDEN_WITH_ACCESSIBLE_CONTENT,
                    String.valueOf(hiddenWithAccessibleContent));
            contentHostingService.commitCollection(edit);
        }
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

}
