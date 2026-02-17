package biz.ugur.busroutebackend.complaint.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ComplaintId extends ValueObject {

    private final String value;

    public ComplaintId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static ComplaintId generate() {
        return new ComplaintId(UUID.randomUUID().toString());
    }

    public static ComplaintId of(String value) {
        return new ComplaintId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
