package org.sakaiproject.videotraining.impl.repository;

import java.util.Collections;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoTrainingCaption;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCaptionRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingCaptionRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingCaption, String>
        implements VideoTrainingCaptionRepository {

    @Override
    public List<VideoTrainingCaption> findByVideoIdOrderByLanguageTagAsc(String videoId) {
        if (videoId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingCaption> query = cb.createQuery(VideoTrainingCaption.class);
        Root<VideoTrainingCaption> root = query.from(VideoTrainingCaption.class);

        query.select(root)
                .where(cb.equal(root.get("videoId"), videoId))
                .orderBy(cb.asc(root.get("languageTag")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }
}
