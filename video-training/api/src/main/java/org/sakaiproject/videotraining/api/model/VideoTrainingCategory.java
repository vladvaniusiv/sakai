package org.sakaiproject.videotraining.api.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.sakaiproject.springframework.data.PersistableEntity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "vtm_category")
public class VideoTrainingCategory implements PersistableEntity<String> {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 99)
    private String siteId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 36)
    private String parentCategoryId;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant createdOn = Instant.now();

    @Column(nullable = false)
    @Convert(converter = InstantEpochMillisConverter.class)
    private Instant modifiedOn = Instant.now();

    @ManyToMany(mappedBy = "categories")
    private Set<VideoTrainingVideo> videos = new HashSet<>();

    public VideoTrainingCategory(String siteId, String name, String parentCategoryId, Integer sortOrder) {
        this.siteId = siteId;
        this.name = name;
        this.parentCategoryId = parentCategoryId;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

}
