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
@Table(name = "VTM_VIDEO")
public class VideoTrainingVideo implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "VIDEO_ID", nullable = false, length = 36)
    private String id;

    @Column(name = "SITE_ID", nullable = false, length = 99)
    private String siteId;

    @Column(name = "OWNER_ID", nullable = false, length = 99)
    private String ownerId;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "DESCRIPTION", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "PROVIDER_TYPE", nullable = false, length = 16)
    private VideoProviderType providerType = VideoProviderType.NATIVE;

    @Column(name = "SOURCE_ID", nullable = false, length = 1024)
    private String sourceReference;

    @Column(name = "FILE_SIZE_BYTES")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY_SCOPE", nullable = false, length = 16)
    private VideoVisibilityScope visibilityScope = VideoVisibilityScope.COURSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "PUBLICATION_STATUS", nullable = false, length = 16)
    private VideoPublicationStatus publicationStatus = VideoPublicationStatus.DRAFT;

    @Column(name = "RELEASE_DATE")
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant releaseDate;

    @Column(name = "RETRACT_DATE")
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant retractDate;

    @Column(name = "REQUIRED_VIEW_PERMISSION", nullable = false, length = 99)
    private String requiredViewPermission = VideoTrainingConstants.PERMISSION_VIEW;

    @Column(name = "CREATED_ON", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(name = "MODIFIED_ON", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();

}
