package biz.ugur.busroutebackend.banner.domain.exceptions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BannerExceptionsTest {

    @Nested
    class Conflict {

        @Test
        void messageOnlyConstructorStoresReason() {
            BannerConflictException ex = new BannerConflictException("overlap");
            assertNull(ex.getBannerId());
            assertEquals("overlap", ex.getConflictReason());
        }

        @Test
        void perBannerConstructorCombinesIdAndReason() {
            BannerConflictException ex = new BannerConflictException("b-1", "already there");
            assertEquals("b-1", ex.getBannerId());
            assertEquals("already there", ex.getConflictReason());
            assertTrue(ex.getMessage().contains("b-1"));
        }

        @Test
        void alreadyExistsFactory() {
            BannerConflictException ex = BannerConflictException.alreadyExists("b-1");
            assertEquals("b-1", ex.getBannerId());
            assertTrue(ex.getMessage().contains("exists"));
        }

        @Test
        void overlappingPeriodFactory() {
            BannerConflictException ex = BannerConflictException.overlappingPeriod("b-1");
            assertTrue(ex.getMessage().contains("overlap"));
        }
    }

    @Nested
    class NotFound {

        @Test
        void byIdFactory() {
            BannerNotFoundException ex = BannerNotFoundException.byId("b-1");
            assertEquals("b-1", ex.getBannerId());
            assertTrue(ex.getMessage().contains("b-1"));
        }
    }

    @Nested
    class Validation {

        @Test
        void messageOnlyConstructor() {
            BannerValidationException ex = new BannerValidationException("bad");
            assertTrue(ex.getMessage().endsWith("bad"));
            assertTrue(ex.getFieldErrors().isEmpty());
            assertNull(ex.getFailedField());
        }

        @Test
        void singleFieldFactoryIsSingular() {
            BannerValidationException ex = BannerValidationException.singleField("title", "too long");
            assertTrue(ex.hasSingleFieldError());
            assertEquals("title", ex.getFailedField());
            assertEquals(List.of("too long"), ex.getFieldErrors().get("title"));
        }

        @Test
        void multiFieldConstructor() {
            BannerValidationException ex = new BannerValidationException(Map.of(
                    "title", List.of("a"),
                    "period", List.of("b")));
            assertFalse(ex.hasSingleFieldError());
            assertEquals(2, ex.getFieldErrors().size());
            assertNotNull(ex.getFailedField());
        }
    }

    @Nested
    class PeriodValidation {

        @Test
        void startAfterEndFactory() {
            LocalDateTime start = LocalDateTime.of(2026, 5, 2, 10, 0);
            LocalDateTime end = LocalDateTime.of(2026, 5, 1, 10, 0);
            BannerPeriodValidationException ex = BannerPeriodValidationException.startAfterEnd(start, end);
            assertEquals(start, ex.getStartDate());
            assertEquals(end, ex.getEndDate());
            assertTrue(ex.getMessage().contains("before"));
        }

        @Test
        void startInPastFactory() {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            BannerPeriodValidationException ex = BannerPeriodValidationException.startInPast(start);
            assertEquals(start, ex.getStartDate());
            assertNull(ex.getEndDate());
        }

        @Test
        void periodTooLongFactoryIncludesMaxDays() {
            LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 12, 1, 0, 0);
            BannerPeriodValidationException ex = BannerPeriodValidationException.periodTooLong(start, end, 30);
            assertTrue(ex.getMessage().contains("30"));
        }

        @Test
        void messageOnlyConstructorHasNullDates() {
            BannerPeriodValidationException ex = new BannerPeriodValidationException("bad period");
            assertNull(ex.getStartDate());
            assertNull(ex.getEndDate());
        }
    }

    @Nested
    class ImageProcessing {

        @Test
        void messageOnlyConstructor() {
            BannerImageProcessingException ex = new BannerImageProcessingException("oops");
            assertNull(ex.getImagePath());
            assertNull(ex.getProcessingOperation());
        }

        @Test
        void withCauseInitialised() {
            RuntimeException cause = new RuntimeException("disk full");
            BannerImageProcessingException ex = new BannerImageProcessingException("oops", cause);
            assertSame(cause, ex.getCause());
        }

        @Test
        void uploadFailedFactory() {
            BannerImageProcessingException ex = BannerImageProcessingException.uploadFailed("/tmp/x.jpg", "too big");
            assertEquals("/tmp/x.jpg", ex.getImagePath());
            assertEquals("upload", ex.getProcessingOperation());
            assertTrue(ex.getMessage().contains("too big"));
        }

        @Test
        void resizeFailedFactory() {
            BannerImageProcessingException ex = BannerImageProcessingException.resizeFailed("a.jpg", "bad dims");
            assertEquals("resize", ex.getProcessingOperation());
        }

        @Test
        void deleteFailedFactory() {
            BannerImageProcessingException ex = BannerImageProcessingException.deleteFailed("a.jpg", "busy");
            assertEquals("delete", ex.getProcessingOperation());
        }
    }

    @Nested
    class EventSerialization {

        @Test
        void messageOnlyConstructor() {
            BannerEventSerializationException ex = new BannerEventSerializationException("oops");
            assertNull(ex.getEventType());
            assertNull(ex.getOperation());
        }

        @Test
        void withCauseInitialised() {
            RuntimeException cause = new RuntimeException("io error");
            BannerEventSerializationException ex = new BannerEventSerializationException("oops", cause);
            assertSame(cause, ex.getCause());
        }

        @Test
        void threeArgConstructorFormatsMessage() {
            BannerEventSerializationException ex = new BannerEventSerializationException(
                    "BannerCreatedEvent", "deserialize", "bad payload");
            assertTrue(ex.getMessage().contains("BannerCreatedEvent"));
            assertTrue(ex.getMessage().contains("deserialize"));
        }

        @Test
        void serializationFailedFactoryChainsCause() {
            RuntimeException cause = new RuntimeException("broken");
            BannerEventSerializationException ex = BannerEventSerializationException
                    .serializationFailed("BannerUpdatedEvent", cause);
            assertEquals("serialization", ex.getOperation());
            assertSame(cause, ex.getCause());
        }

        @Test
        void deserializationFailedFactoryChainsCause() {
            RuntimeException cause = new RuntimeException("malformed");
            BannerEventSerializationException ex = BannerEventSerializationException
                    .deserializationFailed("BannerCreatedEvent", cause);
            assertEquals("deserialization", ex.getOperation());
            assertSame(cause, ex.getCause());
        }
    }
}
