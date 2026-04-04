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
        String json = "{\"date\":\"2025-12-08T09:03:00.000Z\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

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
        String json = "{\"date\":\"2025-12-08T09:03:00+05:00\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNotNull();
        assertThat(result.date.getYear()).isEqualTo(2025);
        assertThat(result.date.getMonthValue()).isEqualTo(12);
        assertThat(result.date.getDayOfMonth()).isEqualTo(8);
        assertThat(result.date.getHour()).isEqualTo(9);
        assertThat(result.date.getMinute()).isEqualTo(3);
    }

    @Test
    void shouldDeserializeStandardFormat() throws IOException {
        String json = "{\"date\":\"2025-12-08T09:03:00\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 9, 3, 0));
    }

    @Test
    void shouldDeserializeFormatWithoutSeconds() throws IOException {
        String json = "{\"date\":\"2025-12-08T09:03\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 9, 3, 0));
    }

    @Test
    void shouldDeserializeDateOnly() throws IOException {
        String json = "{\"date\":\"2025-12-08\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNotNull();
        assertThat(result.date).isEqualTo(LocalDateTime.of(2025, 12, 8, 0, 0, 0));
    }

    @Test
    void shouldHandleNullValue() throws IOException {
        String json = "{\"date\":null}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNull();
    }

    @Test
    void shouldHandleEmptyString() throws IOException {
        String json = "{\"date\":\"\"}";

        TestDto result = objectMapper.readValue(json, TestDto.class);

        assertThat(result.date).isNull();
    }

    @Test
    void shouldThrowExceptionForInvalidFormat() {
        String json = "{\"date\":\"invalid-date\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, TestDto.class))
                .hasMessageContaining("Unable to parse date: invalid-date");
    }

    static class TestDto {
        public LocalDateTime date;
    }
}
