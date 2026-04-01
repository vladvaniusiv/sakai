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
@Table(name = "VTM_CATEGORY")
public class VideoTrainingCategory implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "CATEGORY_ID", nullable = false, length = 36)
    private String id;

    @Column(name = "SITE_ID", nullable = false, length = 99)
    private String siteId;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "PARENT_CATEGORY_ID", length = 36)
    private String parentCategoryId;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "CREATED_ON", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(name = "MODIFIED_ON", nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();

}
