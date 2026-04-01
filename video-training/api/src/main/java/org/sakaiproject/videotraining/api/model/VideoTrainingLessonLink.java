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
@Table(name = "VTM_LESSON_LINK")
public class VideoTrainingLessonLink implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "LESSON_LINK_ID", nullable = false, length = 36)
    private String id;

    @Column(name = "SITE_ID", nullable = false, length = 99)
    private String siteId;

    @Column(name = "VIDEO_ID", nullable = false, length = 36)
    private String videoId;

    @Column(name = "LESSON_PAGE_ID", nullable = false, length = 99)
    private String lessonPageId;

    @Column(name = "LESSON_ITEM_ID", length = 99)
    private String lessonItemId;

    @Column(name = "CREATED_ON", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

}
