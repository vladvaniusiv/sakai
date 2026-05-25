package org.sakaiproject.videotraining.impl.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;
import org.sakaiproject.videotraining.api.repository.VideoTrainingOAuthCredentialsRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VideoTrainingOAuthCredentialsRepositoryImpl extends SpringCrudRepositoryImpl<VideoTrainingOAuthCredentials, String>
        implements VideoTrainingOAuthCredentialsRepository {

    @Override
    public Optional<VideoTrainingOAuthCredentials> findByProviderType(VideoProviderType providerType) {
        if (providerType == null) {
            return Optional.empty();
        }

        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingOAuthCredentials> query = cb.createQuery(VideoTrainingOAuthCredentials.class);
        Root<VideoTrainingOAuthCredentials> root = query.from(VideoTrainingOAuthCredentials.class);

        query.select(root).where(cb.equal(root.get("providerType"), providerType));

        List<VideoTrainingOAuthCredentials> results = sessionFactory.getCurrentSession().createQuery(query).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<VideoTrainingOAuthCredentials> findAllByOrderByProviderTypeAsc() {
        CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        CriteriaQuery<VideoTrainingOAuthCredentials> query = cb.createQuery(VideoTrainingOAuthCredentials.class);
        Root<VideoTrainingOAuthCredentials> root = query.from(VideoTrainingOAuthCredentials.class);

        query.select(root).orderBy(cb.asc(root.get("providerType")));
        TypedQuery<VideoTrainingOAuthCredentials> typedQuery = sessionFactory.getCurrentSession().createQuery(query);
        List<VideoTrainingOAuthCredentials> results = typedQuery.getResultList();
        return results == null ? Collections.emptyList() : results;
    }
}