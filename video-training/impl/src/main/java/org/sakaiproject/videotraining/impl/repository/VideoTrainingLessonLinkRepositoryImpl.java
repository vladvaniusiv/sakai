package org.sakaiproject.videotraining.impl.repository;

import java.util.Collections;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoTrainingLessonLink;
import org.sakaiproject.videotraining.api.repository.VideoTrainingLessonLinkRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingLessonLinkRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingLessonLink, String>
        implements VideoTrainingLessonLinkRepository {

    @Override
    public List<VideoTrainingLessonLink> findByVideoIdOrderByCreatedOnDesc(String videoId) {
        if (videoId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingLessonLink> query = cb.createQuery(VideoTrainingLessonLink.class);
        Root<VideoTrainingLessonLink> root = query.from(VideoTrainingLessonLink.class);

        query.select(root)
                .where(cb.equal(root.get("videoId"), videoId))
                .orderBy(cb.desc(root.get("createdOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public List<VideoTrainingLessonLink> findBySiteIdAndLessonPageId(String siteId, String lessonPageId) {
        if (siteId == null || lessonPageId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingLessonLink> query = cb.createQuery(VideoTrainingLessonLink.class);
        Root<VideoTrainingLessonLink> root = query.from(VideoTrainingLessonLink.class);

        query.select(root)
                .where(cb.and(cb.equal(root.get("siteId"), siteId), cb.equal(root.get("lessonPageId"), lessonPageId)))
                .orderBy(cb.desc(root.get("createdOn")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public void deleteByVideoId(String videoId) {
        if (videoId == null) {
            return;
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaDelete<VideoTrainingLessonLink> delete = cb.createCriteriaDelete(VideoTrainingLessonLink.class);
        Root<VideoTrainingLessonLink> root = delete.from(VideoTrainingLessonLink.class);
        delete.where(cb.equal(root.get("videoId"), videoId));

        sessionFactory.getCurrentSession().createQuery(delete).executeUpdate();
    }
}
