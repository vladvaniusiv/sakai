package org.sakaiproject.videotraining.api;

import org.sakaiproject.entity.api.Entity;

public final class VideoTrainingConstants {

    public static final String APPLICATION_ID = "sakai:video-training";
    public static final String REFERENCE_ROOT = Entity.SEPARATOR + "video-training";

    public static final String PERMISSION_PREFIX = "video.training";
    public static final String PERMISSION_VIEW = "video.training.view";
    public static final String PERMISSION_MANAGE = "video.training.manage";
    public static final String PERMISSION_MANAGE_ALL = "video.training.manage.all";
    public static final String PERMISSION_ANALYTICS = "video.training.analytics";
    public static final String PERMISSION_CAPTIONS_MANAGE = "video.training.captions.manage";

    private VideoTrainingConstants() {
        throw new IllegalStateException("Utility class");
    }
}
