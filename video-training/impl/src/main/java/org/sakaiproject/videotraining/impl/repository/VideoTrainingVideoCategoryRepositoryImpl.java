package org.sakaiproject.videotraining.impl.repository;

import java.util.Collections;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoTrainingVideoCategory;
import org.sakaiproject.videotraining.api.repository.VideoTrainingVideoCategoryRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingVideoCategoryRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingVideoCategory, String>
        implements VideoTrainingVideoCategoryRepository {

    @Override
    public List<VideoTrainingVideoCategory> findByVideoId(String videoId) {
        if (videoId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingVideoCategory> query = cb.createQuery(VideoTrainingVideoCategory.class);
        Root<VideoTrainingVideoCategory> root = query.from(VideoTrainingVideoCategory.class);

        query.select(root).where(cb.equal(root.get("videoId"), videoId));
        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }

    @Override
    public void deleteByVideoId(String videoId) {
        if (videoId == null) {
            return;
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaDelete<VideoTrainingVideoCategory> delete = cb.createCriteriaDelete(VideoTrainingVideoCategory.class);
        Root<VideoTrainingVideoCategory> root = delete.from(VideoTrainingVideoCategory.class);
        delete.where(cb.equal(root.get("videoId"), videoId));

        sessionFactory.getCurrentSession().createQuery(delete).executeUpdate();
    }
}
