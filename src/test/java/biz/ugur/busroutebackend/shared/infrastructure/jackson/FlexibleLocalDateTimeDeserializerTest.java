package biz.ugur.busroutebackend.shared.infrastructure.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlexibleLocalDateTimeDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    void shouldDeserializeIso8601WithMillisecondsAndTimezone() throws IOException {
        // Given: Date string from admin panel in ISO 8601 format with milliseconds and timezone
        String json = "{\"date\":\"2025-12-08T09:03:00.000Z\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNotNull();
        assertThat(result.date.getYear()).isEqualTo(2025);
        assertThat(result.date.getMonthValue()).isEqualTo(12);
        assertThat(result.date.getDayOfMonth()).isEqualTo(8);
        assertThat(result.date.getHour()).isEqualTo(9);
        assertThat(result.date.getMinute()).isEqualTo(3);
        assertThat(result.date.getSecond()).isEqualTo(0);
    }

    @Test
    void shouldDeserializeIso8601WithTimezoneOffset() throws IOException {
        // Given
        String json = "{\"date\":\"2025-12-08T09:03:00+05:00\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNotNull();
        assertThat(result.date.getYear()).isEqualTo(2025);
        assertThat(result.date.getMonthValue()).isEqualTo(12);
        assertThat(result.date.getDayOfMonth()).isEqualTo(8);
        assertThat(result.date.getHour()).isEqualTo(9);
        assertThat(result.date.getMinute()).isEqualTo(3);
    }

    @Test
    void shouldDeserializeStandardFormat() throws IOException {
        // Given
        String json = "{\"date\":\"2025-12-08T09:03:00\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 9, 3, 0));
    }

    @Test
    void shouldDeserializeFormatWithoutSeconds() throws IOException {
        // Given
        String json = "{\"date\":\"2025-12-08T09:03\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 9, 3, 0));
    }

    @Test
    void shouldDeserializeDateOnly() throws IOException {
        // Given
        String json = "{\"date\":\"2025-12-08\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 0, 0, 0));
    }

    @Test
    void shouldHandleNullValue() throws IOException {
        // Given
        String json = "{\"date\":null}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNull();
    }

    @Test
    void shouldHandleEmptyString() throws IOException {
        // Given
        String json = "{\"date\":\"\"}";

        // When
        TestDto result = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(result.date).isNull();
    }

    @Test
    void shouldThrowExceptionForInvalidFormat() {
        // Given
        String json = "{\"date\":\"invalid-date\"}";

        // When/Then
        assertThatThrownBy(() -> objectMapper.readValue(json, TestDto.class))
                .hasMessageContaining("Unable to parse date: invalid-date");
    }

    // Test DTO
    static class TestDto {
        public LocalDateTime date;
    }
}
