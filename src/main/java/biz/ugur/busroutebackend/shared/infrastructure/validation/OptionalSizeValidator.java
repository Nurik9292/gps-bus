package biz.ugur.busroutebackend.shared.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OptionalSizeValidator implements ConstraintValidator<OptionalSize, String> {

    private int min;
    private int max;

    @Override
    public void initialize(OptionalSize constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        int length = value.length();
        return length >= min && length <= max;
    }
}