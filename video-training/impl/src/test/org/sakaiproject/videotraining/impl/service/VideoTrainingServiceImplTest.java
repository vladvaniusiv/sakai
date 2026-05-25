package org.sakaiproject.videotraining.impl.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.videotraining.api.VideoTrainingConstants;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoTrainingAnalyticsEvent;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.repository.VideoTrainingAnalyticsEventRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCaptionRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCategoryRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingLessonLinkRepository;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;

public class VideoTrainingServiceImplTest {

    private static final String SITE_ID = "site-1";
    private static final String SITE_REF = "/site/site-1";
    private static final String VIDEO_ID = "video-1";
    private static final String USER_ID = "user-1";

    private VideoTrainingServiceImpl service;
    private VideoTrainingVideoRepository videoRepository;
    private VideoTrainingCaptionRepository captionRepository;
    private VideoTrainingAnalyticsEventRepository analyticsEventRepository;
    private VideoTrainingCategoryRepository categoryRepository;
    private VideoTrainingLessonLinkRepository lessonLinkRepository;
    private ContentHostingService contentHostingService;
    private EventTrackingService eventTrackingService;
    private FunctionManager functionManager;
    private SecurityService securityService;
    private SessionManager sessionManager;
    private SiteService siteService;

    @Before
    public void setUp() throws Exception {
        videoRepository = Mockito.mock(VideoTrainingVideoRepository.class);
        captionRepository = Mockito.mock(VideoTrainingCaptionRepository.class);
        analyticsEventRepository = Mockito.mock(VideoTrainingAnalyticsEventRepository.class);
        categoryRepository = Mockito.mock(VideoTrainingCategoryRepository.class);
        lessonLinkRepository = Mockito.mock(VideoTrainingLessonLinkRepository.class);
        contentHostingService = Mockito.mock(ContentHostingService.class);
        eventTrackingService = Mockito.mock(EventTrackingService.class);
        functionManager = Mockito.mock(FunctionManager.class);
        securityService = Mockito.mock(SecurityService.class);
        sessionManager = Mockito.mock(SessionManager.class);
        siteService = Mockito.mock(SiteService.class);

        service = new VideoTrainingServiceImpl();
        service.setVideoRepository(videoRepository);
        service.setCaptionRepository(captionRepository);
        service.setAnalyticsEventRepository(analyticsEventRepository);
        service.setCategoryRepository(categoryRepository);
        service.setLessonLinkRepository(lessonLinkRepository);
        service.setContentHostingService(contentHostingService);
        service.setEventTrackingService(eventTrackingService);
        service.setFunctionManager(functionManager);
        service.setSecurityService(securityService);
        service.setSessionManager(sessionManager);
        service.setSiteService(siteService);

        when(siteService.siteReference(SITE_ID)).thenReturn(SITE_REF);
        when(securityService.isSuperUser()).thenReturn(false);
        when(sessionManager.getCurrentSessionUserId()).thenReturn(USER_ID);

        ContentCollection siteCollection = Mockito.mock(ContentCollection.class);
        when(contentHostingService.getSiteCollection(SITE_ID)).thenReturn("/group/" + SITE_ID + "/");
        when(contentHostingService.getCollection("/group/" + SITE_ID + "/")).thenReturn(siteCollection);
        when(contentHostingService.getQuota(siteCollection)).thenReturn(10_000_000L);
        when(siteCollection.getBodySizeK()).thenReturn(0L);
    }

    @Test
    public void canViewVideoShouldReturnFalseBeforeRelease() {
        Instant now = Instant.now();
        VideoTrainingVideo video = baseVideo();
        video.setReleaseDate(now.plus(1, ChronoUnit.MINUTES));

        boolean result = service.canViewVideo(video, USER_ID, now);

        assertFalse(result);
    }

    @Test
    public void canViewVideoShouldReturnFalseAfterRetract() {
        Instant now = Instant.now();
        VideoTrainingVideo video = baseVideo();
        video.setRetractDate(now.minus(1, ChronoUnit.MINUTES));

        boolean result = service.canViewVideo(video, USER_ID, now);

        assertFalse(result);
    }

    @Test
    public void canViewVideoShouldReturnTrueForSuperUser() {
        VideoTrainingVideo video = baseVideo();
        when(securityService.isSuperUser(USER_ID)).thenReturn(true);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertTrue(result);
    }

    @Test
    public void canViewVideoShouldRequireViewPermission() {
        VideoTrainingVideo video = baseVideo();
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(false);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertFalse(result);
        verify(securityService).unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF);
        verify(securityService, never()).unlock(USER_ID, VideoTrainingConstants.PERMISSION_ANALYTICS, SITE_REF);
    }

    @Test
    public void canViewVideoShouldReturnFalseWhenPublicationStatusIsNotPublished() {
        VideoTrainingVideo video = baseVideo();
        video.setPublicationStatus(VideoPublicationStatus.WITHDRAWN);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertFalse(result);
        verify(securityService, never()).unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF);
    }

    @Test
    public void canViewVideoShouldReturnFalseWhenVisibilityScopeIsLesson() {
        VideoTrainingVideo video = baseVideo();
        video.setVisibilityScope(VideoVisibilityScope.LESSON);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertFalse(result);
        verify(securityService, never()).unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF);
    }

    @Test
    public void canViewVideoShouldAllowGlobalWithoutSiteViewPermission() {
        VideoTrainingVideo video = baseVideo();
        video.setVisibilityScope(VideoVisibilityScope.GLOBAL);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertTrue(result);
        verify(securityService, never()).unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF);
    }

    @Test
    public void canViewVideoShouldRequireVideoSpecificPermission() {
        VideoTrainingVideo video = baseVideo();
        video.setRequiredViewPermission("video.training.custom");
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(true);
        when(securityService.unlock(USER_ID, "video.training.custom", SITE_REF)).thenReturn(false);

        boolean result = service.canViewVideo(video, USER_ID, Instant.now());

        assertFalse(result);
        verify(securityService).unlock(USER_ID, "video.training.custom", SITE_REF);
    }

    @Test
    public void registerViewShouldPersistEventWhenUserCanView() {
        VideoTrainingVideo video = baseVideo();
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(true);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(true);

        service.registerView(SITE_ID, VIDEO_ID, USER_ID, Instant.now());

        verify(analyticsEventRepository).save(Mockito.any(VideoTrainingAnalyticsEvent.class));
    }

    @Test
    public void registerViewShouldNotPersistEventWhenUserCannotView() {
        VideoTrainingVideo video = baseVideo();
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(false);

        service.registerView(SITE_ID, VIDEO_ID, USER_ID, Instant.now());

        verify(analyticsEventRepository, never()).save(Mockito.any(VideoTrainingAnalyticsEvent.class));
    }

    @Test
    public void saveVideoShouldSetOwnerAndDefaultPermissionOnCreate() {
        VideoTrainingVideo video = baseVideo();
        video.setId(null);
        video.setOwnerId(null);
        video.setRequiredViewPermission(null);
        video.setVisibilityScope(null);
        video.setPublicationStatus(null);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(true);
        when(videoRepository.save(video)).thenReturn(video);

        VideoTrainingVideo saved = service.saveVideo(video);

        assertNotNull(saved.getCreatedOn());
        assertNotNull(saved.getModifiedOn());
        assertTrue(USER_ID.equals(saved.getOwnerId()));
        assertTrue(VideoTrainingConstants.PERMISSION_VIEW.equals(saved.getRequiredViewPermission()));
        assertTrue(VideoVisibilityScope.COURSE == saved.getVisibilityScope());
        assertTrue(VideoPublicationStatus.DRAFT == saved.getPublicationStatus());
        verify(videoRepository).save(video);
    }

    @Test
    public void saveVideoShouldRejectCreateWithNonDraftStatus() {
        VideoTrainingVideo video = baseVideo();
        video.setId(null);
        video.setPublicationStatus(VideoPublicationStatus.PUBLISHED);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(true);

        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.saveVideo(video));
        verify(videoRepository, never()).save(video);
    }

    @Test
    public void saveVideoShouldRejectInvalidTransitionFromPublishedToDraft() {
        VideoTrainingVideo existing = baseVideo();
        existing.setPublicationStatus(VideoPublicationStatus.PUBLISHED);

        VideoTrainingVideo update = baseVideo();
        update.setPublicationStatus(VideoPublicationStatus.DRAFT);

        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(true);
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(existing));

        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.saveVideo(update));
        verify(videoRepository, never()).save(update);
    }

    @Test
    public void saveVideoShouldRejectExternalSourceWithoutHttpScheme() {
        VideoTrainingVideo video = baseVideo();
        video.setProviderType(VideoProviderType.EXTERNAL);
        video.setSourceReference("kaltura:entry:123");

        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.saveVideo(video));
    }

    @Test
    public void saveVideoShouldRejectWhenUserCannotManage() {
        VideoTrainingVideo video = baseVideo();
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);

        org.junit.Assert.assertThrows(SecurityException.class, () -> service.saveVideo(video));
    }

    @Test
    public void saveVideoShouldAcceptExternalSourceWithHttps() {
        VideoTrainingVideo video = baseVideo();
        video.setProviderType(VideoProviderType.EXTERNAL);
        video.setSourceReference("https://example.org/video/123");
        video.setPublicationStatus(VideoPublicationStatus.DRAFT);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(true);
        when(videoRepository.save(video)).thenReturn(video);

        VideoTrainingVideo saved = service.saveVideo(video);

        assertTrue(VideoProviderType.EXTERNAL == saved.getProviderType());
        verify(videoRepository).save(video);
    }

    @Test
    public void deleteVideoShouldDeleteCaptionsAndEventsBeforeVideo() {
        VideoTrainingVideo video = baseVideo();
        VideoTrainingCaption caption = new VideoTrainingCaption();
        caption.setId("c1");
        VideoTrainingAnalyticsEvent event = new VideoTrainingAnalyticsEvent();
        event.setId("e1");

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(true);
        when(captionRepository.findByVideoIdOrderByLanguageTagAsc(VIDEO_ID)).thenReturn(List.of(caption));
        when(analyticsEventRepository.findByVideoIdOrderByEventTimeDesc(VIDEO_ID)).thenReturn(List.of(event));

        service.deleteVideo(VIDEO_ID);

        verify(captionRepository).delete(caption);
        verify(analyticsEventRepository).delete(event);
        verify(videoRepository).delete(video);
    }

    @Test
    public void deleteVideoShouldRejectWhenUserCannotManage() {
        VideoTrainingVideo video = baseVideo();
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);

        org.junit.Assert.assertThrows(SecurityException.class, () -> service.deleteVideo(VIDEO_ID));
    }

    @Test
    public void saveCaptionShouldAllowWhenCaptionPermissionGranted() {
        VideoTrainingVideo video = baseVideo();
        VideoTrainingCaption caption = new VideoTrainingCaption();
        caption.setVideoId(VIDEO_ID);
        caption.setLanguageTag("en");

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_CAPTIONS_MANAGE, SITE_REF)).thenReturn(true);
        when(captionRepository.save(caption)).thenReturn(caption);

        VideoTrainingCaption saved = service.saveCaption(caption);

        assertNotNull(saved.getCreatedOn());
        verify(captionRepository).save(caption);
    }

    @Test
    public void saveCaptionShouldRejectWithoutCaptionPermission() {
        VideoTrainingVideo video = baseVideo();
        VideoTrainingCaption caption = new VideoTrainingCaption();
        caption.setVideoId(VIDEO_ID);
        caption.setLanguageTag("en");

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_CAPTIONS_MANAGE, SITE_REF)).thenReturn(false);

        org.junit.Assert.assertThrows(SecurityException.class, () -> service.saveCaption(caption));
    }

    @Test
    public void deleteCaptionShouldDeleteWhenVideoNoLongerExists() {
        VideoTrainingCaption caption = new VideoTrainingCaption();
        caption.setId("caption-1");
        caption.setVideoId(VIDEO_ID);

        when(captionRepository.findById("caption-1")).thenReturn(Optional.of(caption));
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.empty());

        service.deleteCaption("caption-1");

        verify(captionRepository).delete(caption);
    }

    @Test
    public void getSiteLibraryPageShouldDelegateToRepositoryWithOffsetAndLimit() {
        VideoTrainingVideo video = baseVideo();
        when(videoRepository.findBySiteIdOrderByModifiedOnDesc(SITE_ID, "video", 24, 24)).thenReturn(List.of(video));

        List<VideoTrainingVideo> result = service.getSiteLibraryPage(SITE_ID, "video", 2, 24);

        assertEquals(1, result.size());
        verify(videoRepository).findBySiteIdOrderByModifiedOnDesc(SITE_ID, "video", 24, 24);
    }

    @Test
    public void getGlobalVideosForUserShouldDelegateToRepositoryWithOffsetAndLimit() {
        VideoTrainingVideo video = baseVideo();
        when(securityService.isSuperUser(USER_ID)).thenReturn(false);
        when(videoRepository.findVisibleByGlobal("video", 24, 24)).thenReturn(List.of(video));

        List<VideoTrainingVideo> result = service.getGlobalVideosForUser(USER_ID, "video", 2, 24);

        assertEquals(1, result.size());
        verify(videoRepository).findVisibleByGlobal("video", 24, 24);
    }

    @Test
    public void getSiteViewableVideosForUserPageShouldUseOwnerVisibilityAndOffset() {
        VideoTrainingVideo video = baseVideo();
        video.setOwnerId(USER_ID);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(true);
        when(videoRepository.findVisibleBySiteIdAt(Mockito.eq(SITE_ID), Mockito.any(Instant.class), Mockito.eq("video"), Mockito.eq(24), Mockito.eq(24)))
            .thenReturn(List.of(video));

        List<VideoTrainingVideo> result = service.getSiteViewableVideosForUserPage(SITE_ID, USER_ID, "video", 2, 24);

        assertEquals(1, result.size());
        verify(videoRepository).findVisibleBySiteIdAt(Mockito.eq(SITE_ID), Mockito.any(Instant.class), Mockito.eq("video"), Mockito.eq(24), Mockito.eq(24));
    }

    @Test
    public void countVisibleVideosForUserShouldReturnZeroWhenUserCannotViewSite() {
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(false);

        long result = service.countVisibleVideosForUser(SITE_ID, USER_ID, Instant.now(), "");

        assertEquals(0L, result);
        verify(videoRepository, never()).countVisibleBySiteIdAt(Mockito.eq(SITE_ID), Mockito.any(Instant.class), Mockito.anyString());
    }

    @Test
    public void countVisibleVideosForUserShouldDelegateWhenUserHasViewPermission() {
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE, SITE_REF)).thenReturn(false);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_VIEW, SITE_REF)).thenReturn(true);
        when(videoRepository.countVisibleBySiteIdAt(Mockito.eq(SITE_ID), Mockito.any(Instant.class), Mockito.eq("training"))).thenReturn(42L);

        long result = service.countVisibleVideosForUser(SITE_ID, USER_ID, Instant.now(), "training");

        assertEquals(42L, result);
        verify(videoRepository).countVisibleBySiteIdAt(Mockito.eq(SITE_ID), Mockito.any(Instant.class), Mockito.eq("training"));
    }

    @Test
    public void countSiteLibraryShouldUseCacheWithinTtl() {
        when(videoRepository.countBySiteId(SITE_ID, "video")).thenReturn(7L);

        long first = service.countSiteLibrary(SITE_ID, "video");
        long second = service.countSiteLibrary(SITE_ID, "video");

        assertEquals(7L, first);
        assertEquals(7L, second);
        verify(videoRepository, times(1)).countBySiteId(SITE_ID, "video");
    }

    @Test
    public void saveVideoShouldInvalidateListCaches() {
        when(videoRepository.countBySiteId(SITE_ID, "video")).thenReturn(4L);

        long beforeSave = service.countSiteLibrary(SITE_ID, "video");
        assertEquals(4L, beforeSave);

        VideoTrainingVideo video = baseVideo();
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(true);
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoRepository.save(video)).thenReturn(video);
        service.saveVideo(video);

        long afterSave = service.countSiteLibrary(SITE_ID, "video");
        assertEquals(4L, afterSave);
        verify(videoRepository, times(2)).countBySiteId(SITE_ID, "video");
    }

    @Test
    public void saveVideoShouldRegisterVisibilityScopeChangedEvent() {
        VideoTrainingVideo existing = baseVideo();
        existing.setVisibilityScope(VideoVisibilityScope.GLOBAL);

        VideoTrainingVideo update = baseVideo();
        update.setVisibilityScope(VideoVisibilityScope.COURSE);

        Event mockEvent = Mockito.mock(Event.class);
        when(securityService.unlock(USER_ID, VideoTrainingConstants.PERMISSION_MANAGE_ALL, SITE_REF)).thenReturn(true);
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(existing));
        when(videoRepository.save(update)).thenReturn(update);
        when(eventTrackingService.newEvent(Mockito.eq("video.training.visibility.scope.changed"),
                Mockito.anyString(), Mockito.eq(SITE_ID), Mockito.eq(true),
                Mockito.eq(NotificationService.NOTI_OPTIONAL))).thenReturn(mockEvent);

        service.saveVideo(update);

        verify(eventTrackingService).newEvent(Mockito.eq("video.training.visibility.scope.changed"),
                Mockito.anyString(), Mockito.eq(SITE_ID), Mockito.eq(true),
                Mockito.eq(NotificationService.NOTI_OPTIONAL));
        verify(eventTrackingService).post(mockEvent);
    }

    private VideoTrainingVideo baseVideo() {
        VideoTrainingVideo video = new VideoTrainingVideo();
        video.setId(VIDEO_ID);
        video.setSiteId(SITE_ID);
        video.setTitle("Video");
        video.setProviderType(VideoProviderType.NATIVE);
        video.setSourceReference("source");
        video.setPublicationStatus(VideoPublicationStatus.PUBLISHED);
        video.setRequiredViewPermission(VideoTrainingConstants.PERMISSION_VIEW);
        return video;
    }
}
