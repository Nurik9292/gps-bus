package biz.ugur.busroutebackend.transport.application.dto;

import biz.ugur.busroutebackend.transport.domain.valueobject.NameChangeRecord;

import java.time.Instant;

public record NameChangeResult(String field, String oldValue, String newValue,
                               String changedBy, Instant changedAt) {

    public static NameChangeResult fromDomain(NameChangeRecord record) {
        return new NameChangeResult(record.field(), record.oldValue(), record.newValue(),
                record.changedBy(), record.changedAt());
    }
}
