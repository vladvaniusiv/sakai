package org.sakaiproject.videotraining.impl.repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoPublicationStatus;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideo;
import org.sakaiproject.videotraining.api.model.VideoVisibilityScope;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingVideoRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingVideo, String>
        implements VideoTrainingVideoRepository {

    @Override
    public List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId) {
        if (siteId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        query.select(root)
                .where(cb.equal(root.get("siteId"), siteId))
                .orderBy(cb.desc(root.get("modifiedOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDesc(String siteId, String searchText, int offset, int limit) {
        if (siteId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate predicate = buildSiteSearchPredicate(cb, root, siteId, searchText);

        query.select(root)
                .where(predicate)
                .orderBy(cb.desc(root.get("modifiedOn")));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (offset > 0) {
            typedQuery.setFirstResult(offset);
        }
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findBySiteIdSorted(String siteId, String searchText, int offset, int limit,
            String sortField, boolean ascending) {
        if (siteId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate predicate = buildSiteSearchPredicate(cb, root, siteId, searchText);

        query.select(root)
                .where(predicate)
                .orderBy(buildSortOrders(cb, root, sortField, ascending));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (offset > 0) {
            typedQuery.setFirstResult(offset);
        }
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findBySiteIdOrderByModifiedOnDescCursor(String siteId, String searchText,
            Instant cursorModifiedOn, String cursorVideoId, int limit) {
        if (siteId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate predicate = buildSiteSearchPredicate(cb, root, siteId, searchText);
        predicate = appendCursorPredicate(cb, root, predicate, cursorModifiedOn, cursorVideoId);

        query.select(root)
                .where(predicate)
                .orderBy(cb.desc(root.get("modifiedOn")), cb.desc(root.get("id")));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public long countBySiteId(String siteId, String searchText) {
        if (siteId == null) {
            return 0;
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate predicate = buildSiteSearchPredicate(cb, root, siteId, searchText);

        query.select(cb.count(root)).where(predicate);
        return sessionFactory.getCurrentSession().createQuery(query).getSingleResult();
    }

    @Override
    public long countByGlobal(String searchText) {
        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate scopePredicate = cb.equal(root.get("visibilityScope"), VideoVisibilityScope.GLOBAL);
        Predicate publicationPredicate = cb.equal(root.get("publicationStatus"), VideoPublicationStatus.PUBLISHED);

        Predicate releasePredicate = cb.or(
                cb.isNull(root.get("releaseDate")),
                cb.lessThanOrEqualTo(root.get("releaseDate"), Instant.now())
        );

        Predicate retractPredicate = cb.or(
                cb.isNull(root.get("retractDate")),
                cb.greaterThan(root.get("retractDate"), Instant.now())
        );

        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);

        Predicate finalPredicate = searchPredicate == null
                ? cb.and(scopePredicate, publicationPredicate, releasePredicate, retractPredicate)
                : cb.and(scopePredicate, publicationPredicate, releasePredicate, retractPredicate, searchPredicate);

        query.select(cb.count(root)).where(finalPredicate);

        return sessionFactory.getCurrentSession().createQuery(query).getSingleResult();
    }

    @Override
    public List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now) {
        if (siteId == null || now == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate sitePredicate = cb.equal(root.get("siteId"), siteId);
        Predicate releasePredicate = cb.or(
                cb.isNull(root.get("releaseDate")),
                cb.lessThanOrEqualTo(root.get("releaseDate"), now)
        );
        Predicate retractPredicate = cb.or(
                cb.isNull(root.get("retractDate")),
                cb.greaterThan(root.get("retractDate"), now)
        );

        query.select(root)
                .where(cb.and(sitePredicate, releasePredicate, retractPredicate))
                .orderBy(cb.desc(root.get("modifiedOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findVisibleBySiteIdAt(String siteId, Instant now, String searchText, int offset, int limit) {
        if (siteId == null || now == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate visibilityPredicate = buildVisiblePredicate(cb, root, siteId, now);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate finalPredicate = searchPredicate == null ? visibilityPredicate : cb.and(visibilityPredicate, searchPredicate);

        query.select(root)
                .where(finalPredicate)
                .orderBy(cb.desc(root.get("modifiedOn")));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (offset > 0) {
            typedQuery.setFirstResult(offset);
        }
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findVisibleBySiteIdAtSorted(String siteId, Instant now, String searchText, int offset, int limit,
            String sortField, boolean ascending) {
        if (siteId == null || now == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate visibilityPredicate = buildVisiblePredicate(cb, root, siteId, now);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate finalPredicate = searchPredicate == null ? visibilityPredicate : cb.and(visibilityPredicate, searchPredicate);

        query.select(root)
                .where(finalPredicate)
                .orderBy(buildSortOrders(cb, root, sortField, ascending));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (offset > 0) {
            typedQuery.setFirstResult(offset);
        }
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findVisibleBySiteIdAtCursor(String siteId, Instant now, String searchText,
            Instant cursorModifiedOn, String cursorVideoId, int limit) {
        if (siteId == null || now == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate visibilityPredicate = buildVisiblePredicate(cb, root, siteId, now);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate predicate = searchPredicate == null ? visibilityPredicate : cb.and(visibilityPredicate, searchPredicate);
        predicate = appendCursorPredicate(cb, root, predicate, cursorModifiedOn, cursorVideoId);

        query.select(root)
                .where(predicate)
                .orderBy(cb.desc(root.get("modifiedOn")), cb.desc(root.get("id")));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findVisibleByGlobal(String searchText, int offset, int size) {
        if (size <= 0) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate globalPredicate = cb.equal(root.get("visibilityScope"),
            VideoVisibilityScope.GLOBAL);

        Predicate publishedPredicate = cb.equal(root.get("publicationStatus"),
            VideoPublicationStatus.PUBLISHED);

        Predicate releasePredicate = cb.or(
                cb.isNull(root.get("releaseDate")),
                cb.lessThanOrEqualTo(root.get("releaseDate"), Instant.now())
        );

        Predicate retractPredicate = cb.or(
                cb.isNull(root.get("retractDate")),
                cb.greaterThan(root.get("retractDate"), Instant.now())
        );

        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate finalPredicate = searchPredicate == null
            ? cb.and(globalPredicate, publishedPredicate, releasePredicate, retractPredicate)
            : cb.and(globalPredicate, publishedPredicate, releasePredicate, retractPredicate, searchPredicate);

        query.select(root)
            .where(finalPredicate)
            .orderBy(cb.desc(root.get("modifiedOn")));

        return sessionFactory.getCurrentSession()
                .createQuery(query)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();
    }

    @Override
    public long countVisibleBySiteIdAt(String siteId, Instant now, String searchText) {
        if (siteId == null || now == null) {
            return 0;
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate visibilityPredicate = buildVisiblePredicate(cb, root, siteId, now);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate finalPredicate = searchPredicate == null ? visibilityPredicate : cb.and(visibilityPredicate, searchPredicate);

        query.select(cb.count(root)).where(finalPredicate);
        return sessionFactory.getCurrentSession().createQuery(query).getSingleResult();
    }

    @Override
    public long sumNativeStorageBytesBySiteId(String siteId) {
        if (siteId == null) {
            return 0L;
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate sitePredicate = cb.equal(root.get("siteId"), siteId);
        Predicate nativePredicate = cb.equal(root.get("providerType"), VideoProviderType.NATIVE);
        Expression<Long> fileSize = root.get("fileSizeBytes");

        query.select(cb.coalesce(cb.sum(fileSize), 0L)).where(cb.and(sitePredicate, nativePredicate));
        return sessionFactory.getCurrentSession().createQuery(query).getSingleResult();
    }

    private Predicate buildSiteSearchPredicate(CriteriaBuilder cb, Root<VideoTrainingVideo> root, String siteId, String searchText) {
        Predicate sitePredicate = cb.equal(root.get("siteId"), siteId);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        return searchPredicate == null ? sitePredicate : cb.and(sitePredicate, searchPredicate);
    }

    private Predicate buildVisiblePredicate(CriteriaBuilder cb, Root<VideoTrainingVideo> root, String siteId, Instant now) {
        Predicate sitePredicate = cb.equal(root.get("siteId"), siteId);
        Predicate publicationPredicate = cb.or(
            cb.isNull(root.get("publicationStatus")),
            cb.equal(root.get("publicationStatus"), VideoPublicationStatus.PUBLISHED)
        );
        Predicate scopePredicate = cb.or(
            cb.isNull(root.get("visibilityScope")),
            cb.notEqual(root.get("visibilityScope"), VideoVisibilityScope.LESSON)
        );
        Predicate releasePredicate = cb.or(
                cb.isNull(root.get("releaseDate")),
                cb.lessThanOrEqualTo(root.get("releaseDate"), now)
        );
        Predicate retractPredicate = cb.or(
                cb.isNull(root.get("retractDate")),
                cb.greaterThan(root.get("retractDate"), now)
        );
        return cb.and(sitePredicate, publicationPredicate, scopePredicate, releasePredicate, retractPredicate);
    }

    private Predicate buildSearchPredicate(CriteriaBuilder cb, Root<VideoTrainingVideo> root, String searchText) {
        String trimmed = StringUtils.trimToEmpty(searchText);
        if (StringUtils.isBlank(trimmed)) {
            return null;
        }

        String pattern = "%" + trimmed.toLowerCase() + "%";
        Predicate titlePredicate = cb.like(cb.lower(root.get("title")), pattern);
        Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), pattern);
        Predicate sourcePredicate = cb.like(cb.lower(root.get("sourceReference")), pattern);
        return cb.or(titlePredicate, descriptionPredicate, sourcePredicate);
    }

    private Predicate appendCursorPredicate(CriteriaBuilder cb, Root<VideoTrainingVideo> root,
            Predicate basePredicate, Instant cursorModifiedOn, String cursorVideoId) {
        if (cursorModifiedOn == null || StringUtils.isBlank(cursorVideoId)) {
            return basePredicate;
        }

        Predicate earlierModified = cb.lessThan(root.get("modifiedOn"), cursorModifiedOn);
        Predicate sameModifiedEarlierId = cb.and(
                cb.equal(root.get("modifiedOn"), cursorModifiedOn),
                cb.lessThan(root.get("id"), cursorVideoId)
        );
        return cb.and(basePredicate, cb.or(earlierModified, sameModifiedEarlierId));
    }

    @Override
    public List<VideoTrainingVideo> findGlobalPublishedCursor(String searchText, Instant cursorModifiedOn, String cursorVideoId, int limit) {
        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate scopePredicate = cb.equal(root.get("visibilityScope"), VideoVisibilityScope.GLOBAL);
        Predicate publicationPredicate = cb.equal(root.get("publicationStatus"), VideoPublicationStatus.PUBLISHED);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate predicate = searchPredicate == null ? cb.and(scopePredicate, publicationPredicate) : cb.and(scopePredicate, publicationPredicate, searchPredicate);
        predicate = appendCursorPredicate(cb, root, predicate, cursorModifiedOn, cursorVideoId);

        query.select(root)
                .where(predicate)
                .orderBy(cb.desc(root.get("modifiedOn")), cb.desc(root.get("id")));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    @Override
    public List<VideoTrainingVideo> findGlobalPublishedSorted(String searchText, int offset, int limit,
            String sortField, boolean ascending) {
        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideo> query = cb.createQuery(VideoTrainingVideo.class);
        Root<VideoTrainingVideo> root = query.from(VideoTrainingVideo.class);

        Predicate scopePredicate = cb.equal(root.get("visibilityScope"), VideoVisibilityScope.GLOBAL);
        Predicate publicationPredicate = cb.equal(root.get("publicationStatus"), VideoPublicationStatus.PUBLISHED);
        Predicate searchPredicate = buildSearchPredicate(cb, root, searchText);
        Predicate predicate = searchPredicate == null ? cb.and(scopePredicate, publicationPredicate) : cb.and(scopePredicate, publicationPredicate, searchPredicate);

        query.select(root)
                .where(predicate)
                .orderBy(buildSortOrders(cb, root, sortField, ascending));

        TypedQuery<VideoTrainingVideo> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        if (offset > 0) {
            typedQuery.setFirstResult(offset);
        }
        if (limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        return typedQuery.getResultList();
    }

    private List<Order> buildSortOrders(CriteriaBuilder cb, Root<VideoTrainingVideo> root,
            String sortField, boolean ascending) {
        String effectiveSortField = normalizeSortField(sortField);
        Order primaryOrder = ascending ? cb.asc(root.get(effectiveSortField)) : cb.desc(root.get(effectiveSortField));
        Order tieBreakModified = cb.desc(root.get("modifiedOn"));
        Order tieBreakId = cb.desc(root.get("id"));
        return List.of(primaryOrder, tieBreakModified, tieBreakId);
    }

    private String normalizeSortField(String sortField) {
        if (StringUtils.isBlank(sortField)) {
            return "modifiedOn";
        }

        switch (sortField) {
            case "title":
            case "siteId":
            case "providerType":
            case "visibilityScope":
            case "publicationStatus":
            case "releaseDate":
            case "retractDate":
            case "modifiedOn":
                return sortField;
            default:
                return "modifiedOn";
        }
    }
}
