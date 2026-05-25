package org.sakaiproject.videotraining.tool.mvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.api.app.scheduler.ScheduledInvocationManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.time.api.UserTimeService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.repository.VideoTrainingProcessJobRepository;
import org.sakaiproject.videotraining.api.service.VideoTrainingOAuthCredentialsService;
import org.sakaiproject.videotraining.api.service.VideoTrainingService;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public class VideoTrainingControllerExternalUploadTest {

    @Test
    public void createVideo_createsPendingProcessJob_whenYoutubeUpload() throws Exception {
        MessageSource messageSource = mock(MessageSource.class);
        ContentHostingService contentHostingService = mock(ContentHostingService.class);
        SessionManager sessionManager = mock(SessionManager.class);
        Placement placement = mock(Placement.class);
        SiteService siteService = mock(SiteService.class);
        ToolManager toolManager = mock(ToolManager.class);
        UserTimeService userTimeService = mock(UserTimeService.class);
        VideoTrainingService videoTrainingService = mock(VideoTrainingService.class);
        VideoTrainingOAuthCredentialsService oauthCredentialsService = mock(VideoTrainingOAuthCredentialsService.class);
        VideoTrainingProcessJobRepository processJobRepository = mock(VideoTrainingProcessJobRepository.class);
        ScheduledInvocationManager scheduledInvocationManager = mock(ScheduledInvocationManager.class);
        ServerConfigurationService serverConfigurationService = mock(ServerConfigurationService.class);
        SecurityService securityService = mock(SecurityService.class);

        when(toolManager.getCurrentPlacement()).thenReturn(placement);
        when(placement.getContext()).thenReturn("site-1");
        when(sessionManager.getCurrentSessionUserId()).thenReturn("user1");
        when(videoTrainingService.canManageLibrary("site-1", "user1")).thenReturn(false);
        when(videoTrainingService.hasManagePermission("site-1", "user1")).thenReturn(false);
        when(oauthCredentialsService.isConfigured(VideoProviderType.YOUTUBE_UPLOAD)).thenReturn(true);
        when(serverConfigurationService.getBoolean("video.training.hls.enabled", true)).thenReturn(true);
        when(serverConfigurationService.getString("video.training.max.upload.size", "512")).thenReturn("512");

        VideoTrainingController controller = new VideoTrainingController(messageSource, contentHostingService, sessionManager, siteService, toolManager, userTimeService, videoTrainingService, oauthCredentialsService, processJobRepository, scheduledInvocationManager, serverConfigurationService, securityService);

        MockMultipartFile multipart = new MockMultipartFile("nativeFile", "video.mp4", "video/mp4", "dummy".getBytes());

        VideoTrainingVideo saved = new VideoTrainingVideo();
        saved.setId("v1");
        saved.setOwnerId("user1");
        saved.setProviderType(VideoProviderType.YOUTUBE_UPLOAD);

        when(videoTrainingService.saveVideo(any(VideoTrainingVideo.class))).thenReturn(saved);
        when(scheduledInvocationManager.createDelayedInvocation(any(Instant.class), eq("ExternalUploadJob"), eq("v1"))).thenReturn("trigger-1");

        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
        String result = controller.createVideo("title", "desc", VideoProviderType.YOUTUBE_UPLOAD.name(), null, null, null, multipart, null, null, null, null, null, null, redirectAttributes, java.util.Locale.getDefault());

        Assert.assertEquals("redirect:/videos", result);
        verify(processJobRepository).save(any(VideoTrainingProcessJob.class));
        verify(scheduledInvocationManager).createDelayedInvocation(any(Instant.class), eq("ExternalUploadJob"), eq("v1"));
    }
}