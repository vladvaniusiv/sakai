package org.sakaiproject.videotraining.impl.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.memory.api.SimpleConfiguration;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.videotraining.api.VideoTrainingConstants;
import static org.sakaiproject.videotraining.api.VideoTrainingConstants.PERMISSION_ANALYTICS;
import static org.sakaiproject.videotraining.api.VideoTrainingConstants.PERMISSION_CAPTIONS_MANAGE;
import static org.sakaiproject.videotraining.api.VideoTrainingConstants.PERMISSION_MANAGE;
import static org.sakaiproject.videotraining.api.VideoTrainingConstants.PERMISSION_VIEW;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsEvent;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsSummary;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.model.VideoTrainingCategory;
import org.sakaiproject.videotraining.api.model.VideoTrainingCourseGroup;
import org.sakaiproject.videotraining.api.model.VideoTrainingLessonLink;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.repository.VideoTrainingAnalyticsEventRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCaptionRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCategoryRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingLessonLinkRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoCategoryRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;
import org.sakaiproject.videotraining.api.service.VideoTrainingService;
import org.springframework.transaction.annotation.Transactional;

import lombok.Setter;

@Transactional
public class VideoTrainingServiceImpl implements VideoTrainingService {

    private static final long LIST_CACHE_TTL_MILLIS = 30_000L;
    private static final String COUNT_CACHE_NAME = "org.sakaiproject.videotraining.cache.list.count";
    private static final String FIRST_PAGE_CACHE_NAME = "org.sakaiproject.videotraining.cache.list.firstPageIds";
    private static final String MODERATION_ENABLED_PROPERTY = "video.training.moderation.enabled";

    @Setter private VideoTrainingVideoRepository videoRepository;
    @Setter private VideoTrainingCaptionRepository captionRepository;
    @Setter private VideoTrainingAnalyticsEventRepository analyticsEventRepository;
    @Setter private VideoTrainingCategoryRepository categoryRepository;
    @Setter private VideoTrainingVideoCategoryRepository videoCategoryRepository;
    @Setter private VideoTrainingLessonLinkRepository lessonLinkRepository;
    @Setter private ContentHostingService contentHostingService;
    @Setter private EventTrackingService eventTrackingService;
    @Setter private FunctionManager functionManager;
    @Setter private SecurityService securityService;
    @Setter private SessionManager sessionManager;
    @Setter private SiteService siteService;
    @Setter private MemoryService memoryService;

    private final ConcurrentMap<ListCacheKey, CacheEntry<Long>> countCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<ListCacheKey, CacheEntry<List<String>>> firstPageCache = new ConcurrentHashMap<>();
    private Cache<String, Long> distributedCountCache;
    private Cache<String, List<String>> distributedFirstPageCache;

    public void init() {
        functionManager.registerFunction(PERMISSION_VIEW, true);
        functionManager.registerFunction(PERMISSION_MANAGE, true);
        functionManager.registerFunction(PERMISSION_ANALYTICS, true);
        functionManager.registerFunction(PERMISSION_CAPTIONS_MANAGE, true);
        initializeDistributedCaches();
    }

    @Override
    public VideoTrainingVideo saveVideo(VideoTrainingVideo video) {
        if (video == null) {
            throw new IllegalArgumentException("video must not be null");
        }
        if (StringUtils.isBlank(video.getSiteId()) || StringUtils.isBlank(video.getTitle()) || StringUtils.isBlank(video.getSourceReference())) {
            throw new IllegalArgumentException("siteId, title and sourceReference are required");
        }

        if (video.getProviderType() == null) {
            video.setProviderType(VideoProviderType.NATIVE);
        }

        String sourceReference = StringUtils.trimToEmpty(video.getSourceReference());
        if (video.getProviderType() == VideoProviderType.EXTERNAL
                && !(StringUtils.startsWithIgnoreCase(sourceReference, "http://")
                || StringUtils.startsWithIgnoreCase(sourceReference, "https://"))) {
            throw new IllegalArgumentException("external provider requires an absolute HTTP(S) sourceReference");
        }

        String currentUserId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(video.getSiteId(), currentUserId)) {
            throw new SecurityException("User cannot manage video library for site " + video.getSiteId());
        }

        Instant now = Instant.now();
        VideoTrainingVideo existing = null;
        if (StringUtils.isBlank(video.getId())) {
            video.setCreatedOn(now);
            if (StringUtils.isBlank(video.getOwnerId())) {
                video.setOwnerId(currentUserId);
            }
        } else {
            existing = getVideoById(video.getId()).orElse(null);
            if (existing != null && StringUtils.isBlank(video.getOwnerId())) {
                video.setOwnerId(existing.getOwnerId());
            }
        }

        if (StringUtils.isBlank(video.getRequiredViewPermission())) {
            video.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        }

        if (video.getVisibilityScope() == null) {
            video.setVisibilityScope(VideoVisibilityScope.COURSE);
        }

        VideoPublicationStatus requestedStatus = normalizePublicationStatus(video.getPublicationStatus());
        video.setPublicationStatus(requestedStatus);

        if (existing == null) {
            if (requestedStatus != VideoPublicationStatus.DRAFT) {
                throw new IllegalArgumentException("New videos must start in DRAFT status");
            }
        } else {
            VideoPublicationStatus currentStatus = normalizePublicationStatus(existing.getPublicationStatus());
            validatePublicationStatusTransition(currentStatus, requestedStatus, video.getVisibilityScope());
        }

        if (video.getLessonOriginRestricted() == null) {
            video.setLessonOriginRestricted(Boolean.FALSE);
        }

        if (video.getProviderType() == VideoProviderType.NATIVE) {
            long currentSize = existing != null && existing.getFileSizeBytes() != null ? existing.getFileSizeBytes() : 0L;
            long requestedSize = video.getFileSizeBytes() != null ? Math.max(video.getFileSizeBytes(), 0L) : currentSize;
            video.setFileSizeBytes(requestedSize);
        }

        video.setModifiedOn(now);
        VideoTrainingVideo saved = videoRepository.save(video);
        String action = existing == null ? "VIDEO_CREATED" : "VIDEO_UPDATED";
        registerAudit(saved.getSiteId(), currentUserId, action, saved.getId(), saved.getTitle());
        invalidateListCaches();
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VideoTrainingVideo> getVideoById(String videoId) {
        if (StringUtils.isBlank(videoId)) {
            return Optional.empty();
        }
        return videoRepository.findById(videoId);
    }

    @Override
    public void deleteVideo(String videoId) {
        VideoTrainingVideo existing = getVideoById(videoId).orElse(null);
        if (existing == null) {
            return;
        }

        String currentUserId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(existing.getSiteId(), currentUserId)) {
            throw new SecurityException("User cannot delete video " + videoId);
        }

        List<VideoTrainingCaption> captions = captionRepository.findByVideoIdOrderByLanguageTagAsc(videoId);
        for (VideoTrainingCaption caption : captions) {
            captionRepository.delete(caption);
        }

        List<VideoTrainingAnalyticsEvent> events = analyticsEventRepository.findByVideoIdOrderByEventTimeDesc(videoId);
        for (VideoTrainingAnalyticsEvent event : events) {
            analyticsEventRepository.delete(event);
        }

        videoCategoryRepository.deleteByVideoId(videoId);
        lessonLinkRepository.deleteByVideoId(videoId);

        videoRepository.delete(existing);
        registerAudit(existing.getSiteId(), currentUserId, "VIDEO_DELETED", videoId, existing.getTitle());
        invalidateListCaches();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getSiteLibrary(String siteId) {
        return videoRepository.findBySiteIdOrderByModifiedOnDesc(siteId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getSiteLibraryPage(String siteId, String searchText, int page, int size) {
        int safeSize = sanitizePageSize(size);
        int safePage = sanitizePage(page);
        String normalizedSearchText = normalizeSearchText(searchText);

        if (safePage == 1) {
            ListCacheKey cacheKey = ListCacheKey.firstPageManage(siteId, normalizedSearchText, safeSize);
            List<VideoTrainingVideo> cached = readCachedList(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        int offset = (safePage - 1) * safeSize;
        List<VideoTrainingVideo> result = videoRepository.findBySiteIdOrderByModifiedOnDesc(siteId, normalizedSearchText, offset, safeSize);
        if (safePage == 1) {
            ListCacheKey cacheKey = ListCacheKey.firstPageManage(siteId, normalizedSearchText, safeSize);
            writeCachedList(cacheKey, result);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getSiteLibraryCursor(String siteId, String searchText, Instant cursorModifiedOn, String cursorVideoId, int size) {
        int safeSize = sanitizePageSize(size);
        String normalizedSearchText = normalizeSearchText(searchText);
        return videoRepository.findBySiteIdOrderByModifiedOnDescCursor(siteId, normalizedSearchText, cursorModifiedOn, cursorVideoId, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getSiteLibrarySorted(String siteId, String searchText, int offset, int size,
            String sortField, boolean ascending) {
        int safeSize = sanitizePageSize(size);
        int safeOffset = Math.max(0, offset);
        String normalizedSearchText = normalizeSearchText(searchText);
        return videoRepository.findBySiteIdSorted(siteId, normalizedSearchText, safeOffset, safeSize, sortField, ascending);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSiteLibrary(String siteId, String searchText) {
        String normalizedSearchText = normalizeSearchText(searchText);
        ListCacheKey cacheKey = ListCacheKey.countManage(siteId, normalizedSearchText);
        Long cached = readCachedCount(cacheKey);
        if (cached != null) {
            return cached;
        }

        long count = videoRepository.countBySiteId(siteId, normalizedSearchText);
        writeCachedCount(cacheKey, count);
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getVisibleVideosForUser(String siteId, String userId, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        List<VideoTrainingVideo> visible = new ArrayList<>();
        for (VideoTrainingVideo video : videoRepository.findVisibleBySiteIdAt(siteId, effectiveNow)) {
            if (canViewVideo(video, userId, effectiveNow)) {
                visible.add(video);
            }
        }
        visible.sort(Comparator.comparing(VideoTrainingVideo::getModifiedOn, Comparator.nullsLast(Comparator.reverseOrder())));
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getVisibleVideosForUserPage(String siteId, String userId, Instant now, String searchText, int page, int size) {
        Instant effectiveNow = now != null ? now : Instant.now();
        int safeSize = sanitizePageSize(size);
        int safePage = sanitizePage(page);
        String normalizedSearchText = normalizeSearchText(searchText);
        long visibilityBucket = visibilityBucket(effectiveNow);

        if (safePage == 1) {
            ListCacheKey cacheKey = ListCacheKey.firstPageVisible(siteId, userId, normalizedSearchText, safeSize, visibilityBucket);
            List<VideoTrainingVideo> cached = readCachedList(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        int offset = (safePage - 1) * safeSize;

        List<VideoTrainingVideo> results = new ArrayList<>();
        for (VideoTrainingVideo video : videoRepository.findVisibleBySiteIdAt(siteId, effectiveNow, normalizedSearchText, offset, safeSize)) {
            if (canViewVideo(video, userId, effectiveNow)) {
                results.add(video);
            }
        }

        if (safePage == 1) {
            ListCacheKey cacheKey = ListCacheKey.firstPageVisible(siteId, userId, normalizedSearchText, safeSize, visibilityBucket);
            writeCachedList(cacheKey, results);
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getVisibleVideosForUserCursor(String siteId, String userId, Instant now, String searchText,
            Instant cursorModifiedOn, String cursorVideoId, int size) {
        Instant effectiveNow = now != null ? now : Instant.now();
        int safeSize = sanitizePageSize(size);
        String normalizedSearchText = normalizeSearchText(searchText);

        List<VideoTrainingVideo> results = new ArrayList<>();
        for (VideoTrainingVideo video : videoRepository.findVisibleBySiteIdAtCursor(siteId, effectiveNow, normalizedSearchText,
                cursorModifiedOn, cursorVideoId, safeSize)) {
            if (canViewVideo(video, userId, effectiveNow)) {
                results.add(video);
            }
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getVisibleVideosForUserSorted(String siteId, String userId, Instant now, String searchText,
            int offset, int size, String sortField, boolean ascending) {
        Instant effectiveNow = now != null ? now : Instant.now();
        int safeSize = sanitizePageSize(size);
        int safeOffset = Math.max(0, offset);
        String normalizedSearchText = normalizeSearchText(searchText);

        List<VideoTrainingVideo> results = new ArrayList<>();
        for (VideoTrainingVideo video : videoRepository.findVisibleBySiteIdAtSorted(siteId, effectiveNow, normalizedSearchText,
                safeOffset, safeSize, sortField, ascending)) {
            if (canViewVideo(video, userId, effectiveNow)) {
                results.add(video);
            }
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public long countVisibleVideosForUser(String siteId, String userId, Instant now, String searchText) {
        Instant effectiveNow = now != null ? now : Instant.now();
        String normalizedSearchText = normalizeSearchText(searchText);
        long visibilityBucket = visibilityBucket(effectiveNow);

        String siteRef = siteService.siteReference(siteId);
        if (!securityService.isSuperUser()
                && !securityService.unlock(userId, PERMISSION_MANAGE, siteRef)
                && !securityService.unlock(userId, PERMISSION_VIEW, siteRef)) {
            return 0;
        }

        ListCacheKey cacheKey = ListCacheKey.countVisible(siteId, userId, normalizedSearchText, visibilityBucket);
        Long cached = readCachedCount(cacheKey);
        if (cached != null) {
            return cached;
        }

        long count = videoRepository.countVisibleBySiteIdAt(siteId, effectiveNow, normalizedSearchText);
        writeCachedCount(cacheKey, count);
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getGlobalVideosCursor(String searchText, Instant cursorModifiedOn, String cursorVideoId, int size) {
        int safeSize = sanitizePageSize(size);
        String normalizedSearchText = normalizeSearchText(searchText);
        return videoRepository.findGlobalPublishedCursor(normalizedSearchText, cursorModifiedOn, cursorVideoId, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingVideo> getGlobalVideosSorted(String searchText, int offset, int size,
            String sortField, boolean ascending) {
        int safeSize = sanitizePageSize(size);
        int safeOffset = Math.max(0, offset);
        String normalizedSearchText = normalizeSearchText(searchText);
        return videoRepository.findGlobalPublishedSorted(normalizedSearchText, safeOffset, safeSize, sortField, ascending);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingCaption> getCaptionsForVideo(String videoId) {
        return captionRepository.findByVideoIdOrderByLanguageTagAsc(videoId);
    }

    @Override
    public VideoTrainingCaption saveCaption(VideoTrainingCaption caption) {
        if (caption == null || StringUtils.isBlank(caption.getVideoId()) || StringUtils.isBlank(caption.getLanguageTag())) {
            throw new IllegalArgumentException("videoId and languageTag are required");
        }

        VideoTrainingVideo video = getVideoById(caption.getVideoId()).orElseThrow(() -> new IllegalArgumentException("Unknown video"));
        String currentUserId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(video.getSiteId(), currentUserId)
                && !securityService.unlock(currentUserId, PERMISSION_CAPTIONS_MANAGE, siteService.siteReference(video.getSiteId()))) {
            throw new SecurityException("User cannot manage captions for video " + caption.getVideoId());
        }

        if (caption.getCreatedOn() == null) {
            caption.setCreatedOn(Instant.now());
        }
        return captionRepository.save(caption);
    }

    @Override
    public void deleteCaption(String captionId) {
        VideoTrainingCaption caption = captionRepository.findById(captionId).orElse(null);
        if (caption == null) {
            return;
        }

        VideoTrainingVideo video = getVideoById(caption.getVideoId()).orElse(null);
        if (video == null) {
            captionRepository.delete(caption);
            return;
        }

        String currentUserId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(video.getSiteId(), currentUserId)
                && !securityService.unlock(currentUserId, PERMISSION_CAPTIONS_MANAGE, siteService.siteReference(video.getSiteId()))) {
            throw new SecurityException("User cannot delete caption " + captionId);
        }
        captionRepository.delete(caption);
    }

    @Override
    public void registerView(String siteId, String videoId, String userId, Instant when) {
        if (StringUtils.isAnyBlank(siteId, videoId, userId)) {
            return;
        }

        VideoTrainingVideo video = getVideoById(videoId).orElse(null);
        if (video == null || !Objects.equals(video.getSiteId(), siteId)) {
            return;
        }

        if (!canViewVideo(video, userId, when)) {
            return;
        }

        VideoTrainingAnalyticsEvent event = new VideoTrainingAnalyticsEvent();
        event.setSiteId(siteId);
        event.setVideoId(videoId);
        event.setUserId(userId);
        event.setEventType("view");
        event.setEventTime(when != null ? when : Instant.now());
        analyticsEventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingAnalyticsEvent> getEventsForVideo(String videoId) {
        return analyticsEventRepository.findByVideoIdOrderByEventTimeDesc(videoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingAnalyticsSummary> getSiteAnalyticsSummary(String siteId) {
        List<VideoTrainingAnalyticsEvent> events = analyticsEventRepository.findBySiteIdAndEventType(siteId, "view");

        java.util.Map<String, Long> totalViewsByVideo = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<String>> usersByVideo = new java.util.HashMap<>();

        for (VideoTrainingAnalyticsEvent event : events) {
            totalViewsByVideo.merge(event.getVideoId(), 1L, Long::sum);
            usersByVideo.computeIfAbsent(event.getVideoId(), key -> new java.util.HashSet<>()).add(event.getUserId());
        }

        List<VideoTrainingAnalyticsSummary> summaries = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> entry : totalViewsByVideo.entrySet()) {
            java.util.Set<String> users = usersByVideo.getOrDefault(entry.getKey(), java.util.Set.of());
            summaries.add(new VideoTrainingAnalyticsSummary(entry.getKey(), entry.getValue(), users.size()));
        }

        summaries.sort(Comparator.comparing(VideoTrainingAnalyticsSummary::getViewCount).reversed());
        return summaries;
    }

    @Override
    public boolean canManageLibrary(String siteId, String userId) {
        if (securityService.isSuperUser()) {
            return true;
        }
        return securityService.unlock(userId, PERMISSION_MANAGE, siteService.siteReference(siteId));
    }

    @Override
    public boolean canViewVideo(VideoTrainingVideo video, String userId, Instant now) {
        if (video == null || StringUtils.isBlank(userId)) {
            return false;
        }

        String siteRef = siteService.siteReference(video.getSiteId());
        if (securityService.isSuperUser() || securityService.unlock(userId, PERMISSION_MANAGE, siteRef)) {
            return true;
        }

        if (!isPublishedForEndUsers(video) || !isCatalogVisibleScope(video) || isLessonOriginRestricted(video)) {
            return false;
        }

        Instant effectiveNow = now != null ? now : Instant.now();
        if (video.getReleaseDate() != null && effectiveNow.isBefore(video.getReleaseDate())) {
            return false;
        }
        if (video.getRetractDate() != null && !effectiveNow.isBefore(video.getRetractDate())) {
            return false;
        }

        if (!securityService.unlock(userId, PERMISSION_VIEW, siteRef)) {
            return false;
        }

        String requiredPermission = StringUtils.defaultIfBlank(video.getRequiredViewPermission(), VideoTrainingConstants.PERMISSION_VIEW);
        return securityService.unlock(userId, requiredPermission, siteRef);
    }

    @Override
    public boolean canViewAnalytics(String siteId, String userId) {
        if (securityService.isSuperUser()) {
            return true;
        }
        return securityService.unlock(userId, PERMISSION_ANALYTICS, siteService.siteReference(siteId));
    }

    @Override
    public boolean canManageCaptions(String siteId, String userId) {
        if (securityService.isSuperUser()) {
            return true;
        }
        String siteRef = siteService.siteReference(siteId);
        return securityService.unlock(userId, PERMISSION_MANAGE, siteRef)
                || securityService.unlock(userId, PERMISSION_CAPTIONS_MANAGE, siteRef);
    }

    @Override
    public Long getSiteStorageQuotaBytes(String siteId) {
        if (StringUtils.isBlank(siteId)) {
            throw new IllegalArgumentException("siteId is required");
        }

        try {
            String siteCollectionId = contentHostingService.getSiteCollection(siteId);
            ContentCollection siteCollection = contentHostingService.getCollection(siteCollectionId);
            long quotaKb = contentHostingService.getQuota(siteCollection);
            if (quotaKb > 0) {
                return quotaKb * 1024L;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public long getSiteStorageUsageBytes(String siteId) {
        if (StringUtils.isBlank(siteId)) {
            return 0L;
        }

        try {
            String siteCollectionId = contentHostingService.getSiteCollection(siteId);
            ContentCollection siteCollection = contentHostingService.getCollection(siteCollectionId);
            return siteCollection.getBodySizeK() * 1024L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public void registerAudit(String siteId, String userId, String action, String videoId, String details) {
        if (StringUtils.isAnyBlank(siteId, userId, action)) {
            return;
        }

        if (eventTrackingService == null) {
            return;
        }

        String normalizedAction = action.toLowerCase(Locale.ROOT).replace('_', '.');
        String eventName = "video.training." + normalizedAction;
        String reference = "/video-training/" + siteId + "/" + StringUtils.defaultIfBlank(videoId, "-");
        eventTrackingService.post(eventTrackingService.newEvent(
                eventName,
                reference,
                siteId,
                true,
            NotificationService.NOTI_OPTIONAL));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingCategory> getCategories(String siteId) {
        return categoryRepository.findBySiteIdOrderBySortOrderAscNameAsc(siteId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VideoTrainingCategory> getCategoryById(String categoryId) {
        return categoryRepository.findById(categoryId);
    }

    @Override
    public VideoTrainingCategory saveCategory(VideoTrainingCategory category) {
        if (category == null || StringUtils.isBlank(category.getSiteId()) || StringUtils.isBlank(category.getName())) {
            throw new IllegalArgumentException("Invalid category payload");
        }

        String userId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(category.getSiteId(), userId)) {
            throw new SecurityException("User cannot manage categories for site " + category.getSiteId());
        }

        Instant now = Instant.now();
        if (category.getCreatedOn() == null) {
            category.setCreatedOn(now);
        }
        category.setModifiedOn(now);
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        VideoTrainingCategory saved = categoryRepository.save(category);
        registerAudit(saved.getSiteId(), userId, "CATEGORY_SAVED", null, saved.getName());
        return saved;
    }

    @Override
    public void deleteCategory(String categoryId) {
        VideoTrainingCategory category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            return;
        }

        String userId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(category.getSiteId(), userId)) {
            throw new SecurityException("User cannot manage categories for site " + category.getSiteId());
        }

        for (VideoTrainingVideo video : getSiteLibrary(category.getSiteId())) {
            List<String> ids = getVideoCategoryIds(video.getId());
            if (ids.remove(categoryId)) {
                setVideoCategoryIds(video.getId(), ids);
            }
        }

        categoryRepository.delete(category);
        registerAudit(category.getSiteId(), userId, "CATEGORY_DELETED", null, category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getVideoCategoryIds(String videoId) {
        List<String> ids = new ArrayList<>();
        for (org.sakaiproject.videotraining.api.model.VideoTrainingVideoCategory row : videoCategoryRepository.findByVideoId(videoId)) {
            ids.add(row.getCategoryId());
        }
        return ids;
    }

    @Override
    public void setVideoCategoryIds(String videoId, List<String> categoryIds) {
        VideoTrainingVideo video = getVideoById(videoId).orElseThrow(() -> new IllegalArgumentException("Unknown video"));
        String userId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(video.getSiteId(), userId)) {
            throw new SecurityException("User cannot manage categories for this video");
        }

        videoCategoryRepository.deleteByVideoId(videoId);
        if (categoryIds != null) {
            for (String categoryId : categoryIds) {
                if (StringUtils.isBlank(categoryId)) {
                    continue;
                }
                VideoTrainingCategory category = categoryRepository.findById(categoryId).orElse(null);
                if (category == null || !StringUtils.equals(category.getSiteId(), video.getSiteId())) {
                    continue;
                }
                org.sakaiproject.videotraining.api.model.VideoTrainingVideoCategory row = new org.sakaiproject.videotraining.api.model.VideoTrainingVideoCategory();
                row.setVideoId(videoId);
                row.setCategoryId(categoryId);
                row.setCreatedOn(Instant.now());
                videoCategoryRepository.save(row);
            }
        }
        registerAudit(video.getSiteId(), userId, "VIDEO_CATEGORIES_UPDATED", videoId, String.join(",", getVideoCategoryIds(videoId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingCourseGroup> getCourseGroupsForSites(List<String> siteIds, String userId, Instant now, int limitPerSite) {
        Instant effectiveNow = now != null ? now : Instant.now();
        int safeLimit = Math.max(1, Math.min(100, limitPerSite <= 0 ? 10 : limitPerSite));
        List<VideoTrainingCourseGroup> groups = new ArrayList<>();
        if (siteIds == null) {
            return groups;
        }

        for (String siteId : siteIds) {
            if (StringUtils.isBlank(siteId)) {
                continue;
            }

            boolean canManage = canManageLibrary(siteId, userId);
            if (!canManage && !securityService.unlock(userId, PERMISSION_VIEW, siteService.siteReference(siteId))) {
                continue;
            }

            VideoTrainingCourseGroup group = new VideoTrainingCourseGroup();
            group.setSiteId(siteId);
            try {
                group.setSiteTitle(siteService.getSite(siteId).getTitle());
            } catch (Exception e) {
                group.setSiteTitle(siteId);
            }

            if (canManage) {
                group.setTotalVideos(countSiteLibrary(siteId, ""));
                group.setVideos(getSiteLibraryPage(siteId, "", 1, safeLimit));
            } else {
                group.setTotalVideos(countVisibleVideosForUser(siteId, userId, effectiveNow, ""));
                group.setVideos(getVisibleVideosForUserPage(siteId, userId, effectiveNow, "", 1, safeLimit));
            }
            groups.add(group);
        }

        return groups;
    }

    @Override
    public VideoTrainingLessonLink saveLessonLink(VideoTrainingLessonLink lessonLink) {
        if (lessonLink == null || StringUtils.isAnyBlank(lessonLink.getSiteId(), lessonLink.getVideoId(), lessonLink.getLessonPageId())) {
            throw new IllegalArgumentException("Invalid lesson link payload");
        }

        String userId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(lessonLink.getSiteId(), userId)) {
            throw new SecurityException("User cannot manage lesson links for site " + lessonLink.getSiteId());
        }

        VideoTrainingVideo video = getVideoById(lessonLink.getVideoId()).orElseThrow(() -> new IllegalArgumentException("Unknown video"));
        if (!StringUtils.equals(video.getSiteId(), lessonLink.getSiteId())) {
            throw new IllegalArgumentException("Video does not belong to site");
        }

        lessonLink.setCreatedOn(Instant.now());
        VideoTrainingLessonLink saved = lessonLinkRepository.save(lessonLink);
        registerAudit(saved.getSiteId(), userId, "LESSON_LINK_SAVED", saved.getVideoId(), saved.getLessonPageId());
        return saved;
    }

    @Override
    public void deleteLessonLink(String lessonLinkId) {
        if (StringUtils.isBlank(lessonLinkId)) {
            return;
        }

        VideoTrainingLessonLink link = lessonLinkRepository.findById(lessonLinkId).orElse(null);
        if (link == null) {
            return;
        }

        String userId = sessionManager.getCurrentSessionUserId();
        if (!canManageLibrary(link.getSiteId(), userId)) {
            throw new SecurityException("User cannot manage lesson links for site " + link.getSiteId());
        }

        lessonLinkRepository.delete(link);
        registerAudit(link.getSiteId(), userId, "LESSON_LINK_DELETED", link.getVideoId(), link.getLessonPageId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoTrainingLessonLink> getLessonLinksForVideo(String videoId) {
        return lessonLinkRepository.findByVideoIdOrderByCreatedOnDesc(videoId);
    }

    @Override
    public VideoTrainingVideo promoteLessonResource(String siteId, String lessonPageId, String lessonItemId,
            String resourceReference, String title, String description, Long fileSizeBytes) {
        if (StringUtils.isAnyBlank(siteId, lessonPageId, resourceReference, title)) {
            throw new IllegalArgumentException("siteId, lessonPageId, resourceReference and title are required");
        }

        VideoTrainingVideo video = new VideoTrainingVideo();
        video.setSiteId(siteId);
        video.setTitle(StringUtils.trimToEmpty(title));
        video.setDescription(StringUtils.trimToEmpty(description));
        video.setProviderType(VideoProviderType.NATIVE);
        video.setSourceReference(StringUtils.trimToEmpty(resourceReference));
        video.setVisibilityScope(VideoVisibilityScope.LESSON);
        video.setPublicationStatus(VideoPublicationStatus.DRAFT);
        video.setLessonOriginRestricted(Boolean.TRUE);
        video.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        video.setFileSizeBytes(fileSizeBytes);

        VideoTrainingVideo saved = saveVideo(video);

        VideoTrainingLessonLink link = new VideoTrainingLessonLink();
        link.setSiteId(siteId);
        link.setVideoId(saved.getId());
        link.setLessonPageId(lessonPageId);
        link.setLessonItemId(StringUtils.trimToNull(lessonItemId));
        saveLessonLink(link);

        String userId = sessionManager.getCurrentSessionUserId();
        registerAudit(siteId, userId, "LESSON_RESOURCE_PROMOTED", saved.getId(), resourceReference);
        return saved;
    }

    private int sanitizePage(int page) {
        return Math.max(page, 1);
    }

    private int sanitizePageSize(int size) {
        int safe = size <= 0 ? 24 : size;
        return Math.max(1, Math.min(100, safe));
    }

    private String normalizeSearchText(String searchText) {
        return StringUtils.trimToEmpty(searchText);
    }

    private boolean isPublishedForEndUsers(VideoTrainingVideo video) {
        VideoPublicationStatus status = video.getPublicationStatus();
        return status == VideoPublicationStatus.PUBLISHED;
    }

    private VideoPublicationStatus normalizePublicationStatus(VideoPublicationStatus status) {
        return status != null ? status : VideoPublicationStatus.DRAFT;
    }

    @Override
    public VideoPublicationStatus[] getValidPublicationStatusTransitions(VideoPublicationStatus currentStatus,
            VideoVisibilityScope visibilityScope) {
        VideoPublicationStatus normalized = normalizePublicationStatus(currentStatus);
        boolean moderationRequired = isModerationRequired(visibilityScope);
        List<VideoPublicationStatus> validTargets = new ArrayList<>();

        switch (normalized) {
            case DRAFT:
                if (moderationRequired) {
                    validTargets.add(VideoPublicationStatus.DRAFT);
                    validTargets.add(VideoPublicationStatus.PENDING_APPROVAL);
                } else {
                    validTargets.add(VideoPublicationStatus.DRAFT);
                    validTargets.add(VideoPublicationStatus.PUBLISHED);
                }
                break;
            case PENDING_APPROVAL:
                validTargets.add(VideoPublicationStatus.PENDING_APPROVAL);
                validTargets.add(VideoPublicationStatus.PUBLISHED);
                validTargets.add(VideoPublicationStatus.DRAFT);
                break;
            case PUBLISHED:
                validTargets.add(VideoPublicationStatus.PUBLISHED);
                validTargets.add(VideoPublicationStatus.WITHDRAWN);
                validTargets.add(VideoPublicationStatus.ARCHIVED);
                break;
            case WITHDRAWN:
                if (moderationRequired) {
                    validTargets.add(VideoPublicationStatus.WITHDRAWN);
                    validTargets.add(VideoPublicationStatus.PENDING_APPROVAL);
                } else {
                    validTargets.add(VideoPublicationStatus.WITHDRAWN);
                    validTargets.add(VideoPublicationStatus.PUBLISHED);
                }
                break;
            case ARCHIVED:
                validTargets.add(VideoPublicationStatus.ARCHIVED);
                validTargets.add(VideoPublicationStatus.DRAFT);
                break;
            default:
                break;
        }

        return validTargets.toArray(new VideoPublicationStatus[0]);
    }

    private void validatePublicationStatusTransition(VideoPublicationStatus currentStatus,
            VideoPublicationStatus targetStatus,
            VideoVisibilityScope visibilityScope) {
        if (currentStatus == targetStatus) {
            return;
        }

        boolean moderationRequired = isModerationRequired(visibilityScope);
        boolean valid;

        switch (currentStatus) {
            case DRAFT:
                valid = moderationRequired
                        ? targetStatus == VideoPublicationStatus.PENDING_APPROVAL
                        : targetStatus == VideoPublicationStatus.PUBLISHED;
                break;
            case PENDING_APPROVAL:
                valid = targetStatus == VideoPublicationStatus.PUBLISHED
                        || targetStatus == VideoPublicationStatus.DRAFT;
                break;
            case PUBLISHED:
                valid = targetStatus == VideoPublicationStatus.WITHDRAWN
                        || targetStatus == VideoPublicationStatus.ARCHIVED;
                break;
            case WITHDRAWN:
                valid = moderationRequired
                        ? targetStatus == VideoPublicationStatus.PENDING_APPROVAL
                        : targetStatus == VideoPublicationStatus.PUBLISHED;
                break;
            case ARCHIVED:
                valid = targetStatus == VideoPublicationStatus.DRAFT;
                break;
            default:
                valid = false;
        }

        if (!valid) {
            throw new IllegalArgumentException("Invalid publication status transition from "
                    + currentStatus + " to " + targetStatus);
        }
    }

    private boolean isModerationRequired(VideoVisibilityScope visibilityScope) {
        return visibilityScope == VideoVisibilityScope.GLOBAL
                && ServerConfigurationService.getBoolean(MODERATION_ENABLED_PROPERTY, false);
    }

    private boolean isCatalogVisibleScope(VideoTrainingVideo video) {
        VideoVisibilityScope scope = video.getVisibilityScope();
        return scope == null || scope != VideoVisibilityScope.LESSON;
    }

    private boolean isLessonOriginRestricted(VideoTrainingVideo video) {
        return Boolean.TRUE.equals(video.getLessonOriginRestricted());
    }

    private long visibilityBucket(Instant now) {
        return now.toEpochMilli() / 60_000L;
    }

    private void invalidateListCaches() {
        countCache.clear();
        firstPageCache.clear();
    }

    private void initializeDistributedCaches() {
        if (memoryService == null) {
            return;
        }

        SimpleConfiguration<String, Long> countConfig = new SimpleConfiguration<>(20000, 30, 30);
        SimpleConfiguration<String, List<String>> firstPageConfig = new SimpleConfiguration<>(20000, 30, 30);

        try {
            distributedCountCache = memoryService.createCache(COUNT_CACHE_NAME, countConfig);
        } catch (Exception e) {
            distributedCountCache = memoryService.getCache(COUNT_CACHE_NAME);
        }

        try {
            distributedFirstPageCache = memoryService.createCache(FIRST_PAGE_CACHE_NAME, firstPageConfig);
        } catch (Exception e) {
            distributedFirstPageCache = memoryService.getCache(FIRST_PAGE_CACHE_NAME);
        }
    }

    private Long readCachedCount(ListCacheKey key) {
        String cacheKey = key.asCacheKey();
        if (distributedCountCache != null) {
            Long cached = distributedCountCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        CacheEntry<Long> entry = countCache.get(key);
        if (entry == null || entry.isExpired()) {
            countCache.remove(key);
            return null;
        }
        return entry.value();
    }

    private void writeCachedCount(ListCacheKey key, long value) {
        String cacheKey = key.asCacheKey();
        if (distributedCountCache != null) {
            distributedCountCache.put(cacheKey, value);
        }
        countCache.put(key, new CacheEntry<>(value));
    }

    private List<VideoTrainingVideo> readCachedList(ListCacheKey key) {
        String cacheKey = key.asCacheKey();
        if (distributedFirstPageCache != null) {
            List<String> ids = distributedFirstPageCache.get(cacheKey);
            if (ids != null) {
                return restoreVideosFromIds(ids);
            }
        }

        CacheEntry<List<String>> entry = firstPageCache.get(key);
        if (entry == null || entry.isExpired()) {
            firstPageCache.remove(key);
            return null;
        }
        return restoreVideosFromIds(entry.value());
    }

    private void writeCachedList(ListCacheKey key, List<VideoTrainingVideo> value) {
        String cacheKey = key.asCacheKey();
        List<String> ids = value.stream().map(VideoTrainingVideo::getId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toList());
        List<String> immutableIds = Collections.unmodifiableList(new ArrayList<>(ids));
        if (distributedFirstPageCache != null) {
            distributedFirstPageCache.put(cacheKey, immutableIds);
        }
        firstPageCache.put(key, new CacheEntry<>(immutableIds));
    }

    private List<VideoTrainingVideo> restoreVideosFromIds(List<String> ids) {
        List<VideoTrainingVideo> videos = new ArrayList<>();
        for (String id : ids) {
            videoRepository.findById(id).ifPresent(videos::add);
        }
        return videos;
    }

    private record CacheEntry<T>(T value, long createdAtMillis) {

        private CacheEntry(T value) {
            this(value, System.currentTimeMillis());
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAtMillis > LIST_CACHE_TTL_MILLIS;
        }
    }

    private record ListCacheKey(String mode, String siteId, String userId, String searchText, int size, long bucket) implements Serializable {

        private String asCacheKey() {
            return mode + "|" + StringUtils.defaultString(siteId) + "|" + StringUtils.defaultString(userId)
                    + "|" + StringUtils.defaultString(searchText) + "|" + size + "|" + bucket;
        }

        private static ListCacheKey countManage(String siteId, String searchText) {
            return new ListCacheKey("count-manage", siteId, "", searchText, 0, 0L);
        }

        private static ListCacheKey firstPageManage(String siteId, String searchText, int size) {
            return new ListCacheKey("first-manage", siteId, "", searchText, size, 0L);
        }

        private static ListCacheKey countVisible(String siteId, String userId, String searchText, long bucket) {
            return new ListCacheKey("count-visible", siteId, StringUtils.defaultString(userId), searchText, 0, bucket);
        }

        private static ListCacheKey firstPageVisible(String siteId, String userId, String searchText, int size, long bucket) {
            return new ListCacheKey("first-visible", siteId, StringUtils.defaultString(userId), searchText, size, bucket);
        }
    }

}
