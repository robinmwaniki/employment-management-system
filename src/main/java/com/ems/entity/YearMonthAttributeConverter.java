package com.ems.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * Stores a {@link YearMonth} as readable text (for example "2026-10"), so the
 * database column is plain, sortable and independent of Hibernate internals.
 */
@Converter
public class YearMonthAttributeConverter
        implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {

        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {

        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
