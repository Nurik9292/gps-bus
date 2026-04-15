package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class ContactInfo extends ValueObject {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()\\-]{6,32}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String contactPerson;
    private final String phone;
    private final String email;
    private final String websiteUrl;

    private ContactInfo(String contactPerson, String phone, String email, String websiteUrl) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessValidationException("contactPhone", "must not be blank");
        }
        String normalizedPhone = phone.trim();
        if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            throw new BusinessValidationException("contactPhone", "invalid phone format");
        }
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessValidationException("contactEmail", "invalid email format");
        }
        this.contactPerson = contactPerson != null ? contactPerson.trim() : null;
        this.phone = normalizedPhone;
        this.email = email != null && !email.isBlank() ? email.trim() : null;
        this.websiteUrl = websiteUrl != null && !websiteUrl.isBlank() ? websiteUrl.trim() : null;
    }

    public static ContactInfo of(String contactPerson, String phone, String email, String websiteUrl) {
        return new ContactInfo(contactPerson, phone, email, websiteUrl);
    }
}
