package org.sakaiproject.videotraining.impl.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJob;
import org.sakaiproject.videotraining.api.model.VideoTrainingProcessJobStatus;
import org.sakaiproject.videotraining.api.repository.VideoTrainingProcessJobRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingProcessJobRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingProcessJob, String>
        implements VideoTrainingProcessJobRepository {

    @Override
    public List<VideoTrainingProcessJob> findByVideoIdOrderByModifiedOnDesc(String videoId) {
        if (videoId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingProcessJob> query = cb.createQuery(VideoTrainingProcessJob.class);
        Root<VideoTrainingProcessJob> root = query.from(VideoTrainingProcessJob.class);

        query.select(root)
                .where(cb.equal(root.get("videoId"), videoId))
                .orderBy(cb.desc(root.get("modifiedOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public List<VideoTrainingProcessJob> findBySubmitterUserIdOrderByModifiedOnDesc(String submitterUserId) {
        if (submitterUserId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingProcessJob> query = cb.createQuery(VideoTrainingProcessJob.class);
        Root<VideoTrainingProcessJob> root = query.from(VideoTrainingProcessJob.class);

        query.select(root)
                .where(cb.equal(root.get("submitterUserId"), submitterUserId))
                .orderBy(cb.desc(root.get("modifiedOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public List<VideoTrainingProcessJob> findByStatusInOrderByModifiedOnAsc(Collection<VideoTrainingProcessJobStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingProcessJob> query = cb.createQuery(VideoTrainingProcessJob.class);
        Root<VideoTrainingProcessJob> root = query.from(VideoTrainingProcessJob.class);

        Predicate statusPredicate = root.get("status").in(statuses);
        query.select(root)
                .where(statusPredicate)
                .orderBy(cb.asc(root.get("modifiedOn")));

        TypedQuery<VideoTrainingProcessJob> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        return typedQuery.getResultList();
    }
}