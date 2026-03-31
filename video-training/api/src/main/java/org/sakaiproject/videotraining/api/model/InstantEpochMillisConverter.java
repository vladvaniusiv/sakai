package org.sakaiproject.videotraining.api.model;

import java.time.Instant;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class InstantEpochMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Instant.ofEpochMilli(dbData);
    }
}
