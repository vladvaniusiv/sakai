package org.sakaiproject.videotraining.api.model;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.sakaiproject.springframework.data.PersistableEntity;

import lombok.Data;

@Entity
@Data
@Table(name = "vtm_user_video_pref")
public class VideoTrainingUserVideoPreference implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 99)
    private String siteId;

    @Column(nullable = false, length = 99)
    private String userId;

    @Column(nullable = false, length = 36)
    private String videoId;

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private boolean watchLater;

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();
}
