package org.sakaiproject.videotraining.api.model;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.sakaiproject.springframework.data.PersistableEntity;
import org.sakaiproject.videotraining.api.VideoTrainingConstants;

import lombok.Data;

@Entity
@Data
@Table(name = "vtm_video")
public class VideoTrainingVideo implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 99)
    private String siteId;

    @Column(nullable = false, length = 99)
    private String ownerId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column
    private boolean inheritTitleMetadata;

    @Column
    private boolean inheritDescriptionMetadata;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoProviderType providerType = VideoProviderType.NATIVE;

    @Column(nullable = false, length = 1024)
    private String sourceReference;

    @Column
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoVisibilityScope visibilityScope = VideoVisibilityScope.COURSE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoPublicationStatus publicationStatus = VideoPublicationStatus.DRAFT;

    @Column
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant releaseDate;

    @Column
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant retractDate;

    @Column(nullable = false, length = 99)
    private String requiredViewPermission = VideoTrainingConstants.PERMISSION_VIEW;

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();

}
