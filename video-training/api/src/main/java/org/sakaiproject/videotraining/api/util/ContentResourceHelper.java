package org.sakaiproject.videotraining.api.util;

import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;

/**
 * Helper utilities to work with Sakai ContentResource identifiers and URLs.
 * Methods are static so they can be used from jobs and controllers without
 * duplicating the logic for converting a stored sourceReference to a
 * ContentResource id or to obtain the resource itself.
 */
public final class ContentResourceHelper {

    private static final String CONTENT_REFERENCE_ROOT = ContentHostingService.REFERENCE_ROOT;
    private static ContentHostingService contentHostingService;

    private ContentResourceHelper() {}

    public static String toContentResourceId(String sourceReference) {
        String normalized = sourceReference == null ? "" : sourceReference.trim();
        if (normalized.startsWith(CONTENT_REFERENCE_ROOT + "/")) {
            return normalized.substring(CONTENT_REFERENCE_ROOT.length());
        }
        return normalized;
    }

    public static void setContentHostingService(ContentHostingService svc) {
        contentHostingService = svc;
    }

    public static ContentResource getContentResource(String sourceReference) throws IllegalStateException, IdUnusedException, PermissionException, TypeException {
        if (contentHostingService == null) {
            throw new IllegalStateException("ContentHostingService not initialized in ContentResourceHelper");
        }
        return contentHostingService.getResource(toContentResourceId(sourceReference));
    }

    public static String getContentUrl(String sourceReference) {
        if (contentHostingService == null) {
            return "";
        }
        try {
            String url = contentHostingService.getUrl(toContentResourceId(sourceReference));
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }
}
