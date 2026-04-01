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
@Table(name = "VTM_ANALYTICS_EVENT")
public class VideoTrainingAnalyticsEvent implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "EVENT_ID", nullable = false, length = 36)
    private String id;

    @Column(name = "VIDEO_ID", nullable = false, length = 36)
    private String videoId;

    @Column(name = "SITE_ID", nullable = false, length = 99)
    private String siteId;

    @Column(name = "USER_ID", nullable = false, length = 99)
    private String userId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 32)
    private String eventType;

    @Column(name = "EVENT_TIME", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant eventTime = Instant.now();

}
