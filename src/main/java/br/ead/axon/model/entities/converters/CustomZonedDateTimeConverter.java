package br.ead.axon.model.entities.converters;

import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CustomZonedDateTimeConverter implements PropertyValueConverter {

    private final DateTimeFormatter formatterWithZone = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX");
    private final DateTimeFormatter formatterWithoutZone = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS");

    @Override
    public Object write(Object value) {
        if (value instanceof ZonedDateTime zonedDateTime) {
            return formatterWithZone.format(zonedDateTime);
        } else {
            return value;
        }
    }

    @Override
    public Object read(Object value) {
        if (value instanceof String s) {
            try {
                return formatterWithZone.parse(s, ZonedDateTime::from);
            } catch (Exception e) {
                return formatterWithoutZone.parse(s, LocalDateTime::from).atZone(ZoneId.of("UTC"));
            }
        } else {
            return value;
        }
    }
}