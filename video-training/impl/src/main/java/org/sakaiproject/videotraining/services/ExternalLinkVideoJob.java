package org.sakaiproject.videotraining.services;

import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;
import org.sakaiproject.videotraining.api.util.ExternalMetadataFetcher;
import org.sakaiproject.videotraining.api.util.ExternalMetadataFetcher.MetadataFetchResult;

import org.sakaiproject.videotraining.api.util.ContentResourceHelper;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.util.ResourceLoader;

@Slf4j
public class ExternalLinkVideoJob implements Job {

    private static final ResourceLoader rb = new ResourceLoader("video-training");

    @Setter
    private VideoTrainingVideoRepository videoRepository;

    @Setter
    private SessionManager sessionManager;

    @Setter
    private EmailService emailService;

    @Setter
    private UserDirectoryService userDirectoryService;

    @Setter
    private ServerConfigurationService serverConfigurationService;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        final long start = System.currentTimeMillis();
        log.info("ExternalLinkVideoJob - start");
        try {
            logIn();

            final int pageSize = 200;
            long total = videoRepository.countAll("");
            int pageIndex = 0;
            while ((long) pageIndex * pageSize < total) {
                int offset = pageIndex * pageSize;
                List<VideoTrainingVideo> page = videoRepository.findAll("", offset, pageSize);
                if (page == null || page.isEmpty()) {
                    break;
                }
                for (VideoTrainingVideo v : page) {
                    if (v.getProviderType() == VideoProviderType.EXTERNAL) {
                        boolean changed = false;
                        if (v.isInheritTitleMetadata() || v.isInheritDescriptionMetadata()) {
                            try {
                                if (v.isInheritTitleMetadata() || v.isInheritDescriptionMetadata()) {
                                    MetadataFetchResult meta = ExternalMetadataFetcher.fetchExternalMetadata(v.getSourceReference());

                                    if (meta == null) {
                                        log.warn("ExternalLinkVideoJob - metadata not found or link broken for {}", v.getSourceReference());
                                        try {
                                            sendEmail(v, "broken");
                                        } catch (Exception e) {
                                            log.warn("ExternalLinkVideoJob - failed to send broken-link email for {}: {}", v.getSourceReference(), e.toString());
                                        }
                                    } else {
                                        if (v.isInheritTitleMetadata()) {
                                            String newTitle = meta.getTitle();
                                            if (StringUtils.isNotBlank(newTitle) && !newTitle.equals(v.getTitle())) {
                                                v.setTitle(newTitle);
                                                changed = true;
                                                sendEmail(v, "title");
                                            }
                                        }

                                        if (v.isInheritDescriptionMetadata()) {
                                            String newDesc = meta.getDescription();
                                            if (StringUtils.isNotBlank(newDesc) && !newDesc.equals(v.getDescription())) {
                                                v.setDescription(newDesc);
                                                changed = true;
                                                sendEmail(v, "description");
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("ExternalLinkVideoJob - failed to fetch metadata for {}: {}", v.getSourceReference(), e.toString());
                            }
                        }

                        if (changed) {
                            try {
                                videoRepository.save(v);
                            } catch (RuntimeException e) {
                                log.warn("ExternalLinkVideoJob - failed to save updated video {}", v.getId(), e);
                            }
                        }
                    } else if (v.getProviderType() == VideoProviderType.NATIVE) {
                        try {
                            ContentResource resource = ContentResourceHelper.getContentResource(v.getSourceReference());
                            if (resource == null) {
                                System.out.println("ExternalLinkVideoJob - could not resolve content resource for {}");
                            }
                        }
                        catch (IdUnusedException e) {
                            log.warn("ExternalLinkVideoJob - failed to resolve content resource for {}: {}", v.getSourceReference(), e.toString());
                        }
                        catch (Exception e) {
                            log.warn("ExternalLinkVideoJob - failed to resolve content resource for {}: {}", v.getSourceReference(), e.toString());
                        }
                    }
                }
                pageIndex++;
            }

            log.info("ExternalLinkVideoJob - finished in {} ms", (System.currentTimeMillis() - start));
        } catch (RuntimeException e) {
            log.error("ExternalLinkVideoJob - failed", e);
            throw new JobExecutionException(e);
        } finally {
            try {
                logOut();
            } catch (Exception e) {
                log.warn("ExternalLinkVideoJob - failed to cleanup Sakai session.", e);
            }
        }
    }

    private void sendEmail(VideoTrainingVideo v, String type) {
        String from = serverConfigurationService.getSmtpFrom();
        String to = null;

        try {
            User user = userDirectoryService.getUser(v.getOwnerId());
            if (user != null) {
                to = user.getEmail();
                try {
                    rb.setContextLocale(rb.getLocale(user.getId()));
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            log.warn("sendEmail - could not resolve user {}: {}", v.getOwnerId(), e.toString());
        }

        if (StringUtils.isBlank(to)) {
            log.debug("sendEmail - no recipient for video {} (owner {})", v.getId(), v.getOwnerId());
            return;
        }

        String serviceName = serverConfigurationService.getString("ui.service", "Video Training");
        String subjectKey;
        String bodyKey;
        if ("title".equals(type)) {
            subjectKey = "video.training.email.titleUpdated.subject";
            bodyKey = "video.training.email.titleUpdated.body";
        } else if ("description".equals(type)) {
            subjectKey = "video.training.email.descriptionUpdated.subject";
            bodyKey = "video.training.email.descriptionUpdated.body";
        } else if ("broken".equals(type)) {
            subjectKey = "video.training.email.broken.subject";
            bodyKey = "video.training.email.broken.body";
        } else {
            subjectKey = "video.training.email.updated.subject";
            bodyKey = "video.training.email.updated.body";
        }

        String subject;
        String body;
        if ("broken".equals(type)) {
            subject = rb.getFormattedMessage(subjectKey, new Object[]{v.getSourceReference()});
            body = rb.getFormattedMessage(bodyKey, new Object[]{v.getSourceReference(), v.getSiteId(), serviceName});
        } else {
            subject = rb.getFormattedMessage(subjectKey, new Object[]{v.getTitle()});
            body = rb.getFormattedMessage(bodyKey, new Object[]{v.getTitle(), StringUtils.defaultString(v.getDescription()), v.getSiteId(), serviceName});
        }

        try {
            emailService.send(from, to, subject, body, null, null, null);
            log.debug("sendEmail - enviado correo a {} sobre {} de video {}", to, type, v.getId());
        } catch (Exception e) {
            log.warn("sendEmail - fallo al enviar correo a {}: {}", to, e.toString());
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
