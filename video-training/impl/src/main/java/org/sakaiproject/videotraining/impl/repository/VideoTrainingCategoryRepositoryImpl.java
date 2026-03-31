package org.sakaiproject.videotraining.impl.repository;

import java.util.Collections;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoTrainingCategory;
import org.sakaiproject.videotraining.api.repository.VideoTrainingCategoryRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingCategoryRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingCategory, String>
        implements VideoTrainingCategoryRepository {

    @Override
    public List<VideoTrainingCategory> findBySiteIdOrderBySortOrderAscNameAsc(String siteId) {
        if (siteId == null) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingCategory> query = cb.createQuery(VideoTrainingCategory.class);
        Root<VideoTrainingCategory> root = query.from(VideoTrainingCategory.class);

        query.select(root)
                .where(cb.equal(root.get("siteId"), siteId))
                .orderBy(cb.asc(root.get("sortOrder")), cb.asc(root.get("name")));

        return sessionFactory.getCurrentSession().createQuery(query).getResultList();
    }
}
