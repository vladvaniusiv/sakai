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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "vtm_video_process_job")
public class VideoTrainingProcessJob implements PersistableEntity<String> {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String videoId;

    @Column(nullable = false, length = 99)
    private String submitterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoTrainingProcessJobStatus status = VideoTrainingProcessJobStatus.PENDING;

    @Column(length = 4000)
    private String errorMessage;

    @Column(nullable = false, length = 1024)
    private String tempFilePath;

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();
}