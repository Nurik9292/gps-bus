package biz.ugur.busroutebackend.notification.domain.exceptions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationExceptionsTest {

    @Test
    void notFoundByIdCarriesId() {
        NotificationNotFoundException ex = NotificationNotFoundException.byId("n-1");
        assertEquals("n-1", ex.getNotificationId());
        assertTrue(ex.getMessage().contains("n-1"));
    }

    @Test
    void validationMessageOnlyConstructorHasEmptyFieldErrors() {
        NotificationValidationException ex = new NotificationValidationException("bad");
        assertTrue(ex.getMessage().endsWith("bad"));
        assertTrue(ex.getFieldErrors().isEmpty());
        assertNull(ex.getFailedField());
    }

    @Test
    void validationSingleFieldFactory() {
        NotificationValidationException ex = NotificationValidationException
                .singleField("title", "must not be empty");
        assertTrue(ex.getMessage().contains("title"));
        assertEquals("title", ex.getFailedField());
        assertEquals(List.of("must not be empty"), ex.getFieldErrors().get("title"));
        assertTrue(ex.hasSingleFieldError());
    }

    @Test
    void validationFromMapAggregatesErrors() {
        Map<String, List<String>> errors = Map.of(
                "title", List.of("too short"),
                "content", List.of("required"));
        NotificationValidationException ex = new NotificationValidationException(errors);
        assertFalse(ex.hasSingleFieldError());
        assertEquals(2, ex.getFieldErrors().size());
        assertNotNull(ex.getFailedField());
    }
}
