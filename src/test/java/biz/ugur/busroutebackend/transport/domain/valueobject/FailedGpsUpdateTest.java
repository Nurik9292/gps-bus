package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailedGpsUpdateTest {

    private FailedGpsUpdate sample() {
        return FailedGpsUpdate.create(
                "dev-1", "AG-100",
                37.95, 58.35,
                25.0, 90.0,
                LocalDateTime.now(),
                "Connection refused",
                FailedGpsUpdate.FailureType.CONNECTION_ERROR
        );
    }

    @Test
    void createGeneratesIdAndZeroRetryCount() {
        FailedGpsUpdate update = sample();

        assertThat(update.id()).isNotNull();
        assertThat(update.retryCount()).isZero();
        assertThat(update.nextRetryAt()).isNull();
        assertThat(update.failedAt()).isNotNull();
        assertThat(update.failureType()).isEqualTo(FailedGpsUpdate.FailureType.CONNECTION_ERROR);
        assertThat(update.failureReason()).isEqualTo("Connection refused");
    }

    @Test
    void hasValidCoordinatesTrueWhenBothPresent() {
        FailedGpsUpdate update = sample();

        assertThat(update.hasValidCoordinates()).isTrue();
    }

    @Test
    void hasValidCoordinatesFalseWhenMissingLatOrLon() {
        FailedGpsUpdate noLat = FailedGpsUpdate.create(
                "dev-1", "AG-100", null, 58.35, 25.0, 90.0,
                LocalDateTime.now(), "x", FailedGpsUpdate.FailureType.UNKNOWN);
        FailedGpsUpdate noLon = FailedGpsUpdate.create(
                "dev-1", "AG-100", 37.95, null, 25.0, 90.0,
                LocalDateTime.now(), "x", FailedGpsUpdate.FailureType.UNKNOWN);

        assertThat(noLat.hasValidCoordinates()).isFalse();
        assertThat(noLon.hasValidCoordinates()).isFalse();
    }

    @Test
    void canRetryComparesAgainstMaxLimit() {
        FailedGpsUpdate update = sample();

        assertThat(update.canRetry(3)).isTrue();
        assertThat(update.canRetry(0)).isFalse();
    }

    @Test
    void withRetryIncrementsCountAndUpdatesNextRetry() {
        FailedGpsUpdate update = sample();
        LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(5);

        FailedGpsUpdate retried = update.withRetry(nextRetry);

        assertThat(retried.id()).isEqualTo(update.id());
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.nextRetryAt()).isEqualTo(nextRetry);

        FailedGpsUpdate twiceRetried = retried.withRetry(nextRetry.plusMinutes(5));
        assertThat(twiceRetried.retryCount()).isEqualTo(2);
    }

    @Test
    void canRetryReturnsFalseWhenLimitReached() {
        FailedGpsUpdate update = sample()
                .withRetry(LocalDateTime.now())
                .withRetry(LocalDateTime.now())
                .withRetry(LocalDateTime.now());

        assertThat(update.canRetry(3)).isFalse();
        assertThat(update.canRetry(4)).isTrue();
    }

    @Test
    void compactConstructorRejectsNegativeRetryCount() {
        assertThatThrownBy(() -> new FailedGpsUpdate(
                "id", "dev", "plate", null, null, null, null, null,
                LocalDateTime.now(), "reason",
                FailedGpsUpdate.FailureType.UNKNOWN, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compactConstructorRejectsNullMandatoryFields() {
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> new FailedGpsUpdate(
                null, "dev", "p", null, null, null, null, null,
                now, "r", FailedGpsUpdate.FailureType.UNKNOWN, 0, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new FailedGpsUpdate(
                "id", null, "p", null, null, null, null, null,
                now, "r", FailedGpsUpdate.FailureType.UNKNOWN, 0, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new FailedGpsUpdate(
                "id", "dev", "p", null, null, null, null, null,
                now, null, FailedGpsUpdate.FailureType.UNKNOWN, 0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
