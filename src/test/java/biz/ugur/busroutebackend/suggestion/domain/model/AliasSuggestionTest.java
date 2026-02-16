package biz.ugur.busroutebackend.suggestion.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AliasSuggestion Domain Model Tests")
class AliasSuggestionTest {

    private static final String ENTITY_ID = "place-123";
    private static final String ALIAS = "Русский базар";
    private static final String LANGUAGE = "ru";
    private static final String CLIENT_ID = "client-456";
    private static final String REVIEWER_ID = "admin-789";

    @Test
    @DisplayName("Успешное создание предложения")
    void createSuggestionSuccessfully() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID);

        assertNotNull(suggestion);
        assertNotNull(suggestion.getId());
        assertEquals(SuggestionEntityType.PLACE, suggestion.getEntityType());
        assertEquals(ENTITY_ID, suggestion.getEntityId());
        assertEquals(ALIAS, suggestion.getSuggestedAlias());
        assertEquals(LANGUAGE, suggestion.getLanguage());
        assertEquals(SuggestionStatus.PENDING, suggestion.getStatus());
        assertEquals(CLIENT_ID, suggestion.getClientId());
        assertNull(suggestion.getReviewerId());
        assertNull(suggestion.getReviewComment());
    }

    @Test
    @DisplayName("Создание с language=null использует 'ru' по умолчанию")
    void createSuggestionDefaultLanguage() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.STREET, ENTITY_ID, ALIAS, null, CLIENT_ID);

        assertEquals("ru", suggestion.getLanguage());
    }

    @Test
    @DisplayName("Создание с null entityType вызывает исключение")
    void createSuggestionFailsWhenEntityTypeNull() {
        assertThrows(IllegalArgumentException.class, () ->
                AliasSuggestion.create(null, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID));
    }

    @Test
    @DisplayName("Создание с пустым entityId вызывает исключение")
    void createSuggestionFailsWhenEntityIdEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                AliasSuggestion.create(SuggestionEntityType.PLACE, "", ALIAS, LANGUAGE, CLIENT_ID));
    }

    @Test
    @DisplayName("Создание с пустым suggestedAlias вызывает исключение")
    void createSuggestionFailsWhenAliasEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                AliasSuggestion.create(SuggestionEntityType.PLACE, ENTITY_ID, "  ", LANGUAGE, CLIENT_ID));
    }

    @Test
    @DisplayName("Создание с null clientId вызывает исключение")
    void createSuggestionFailsWhenClientIdNull() {
        assertThrows(IllegalArgumentException.class, () ->
                AliasSuggestion.create(SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, null));
    }

    @Test
    @DisplayName("Одобрение предложения")
    void approveSuggestionSuccessfully() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID);

        AliasSuggestion approved = suggestion.approve(REVIEWER_ID);

        assertEquals(SuggestionStatus.APPROVED, approved.getStatus());
        assertEquals(REVIEWER_ID, approved.getReviewerId());
        assertEquals(ALIAS, approved.getSuggestedAlias());
    }

    @Test
    @DisplayName("Одобрение с null reviewerId вызывает исключение")
    void approveSuggestionFailsWhenReviewerIdNull() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID);

        assertThrows(IllegalArgumentException.class, () -> suggestion.approve(null));
    }

    @Test
    @DisplayName("Отклонение предложения")
    void rejectSuggestionSuccessfully() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID);

        String comment = "Некорректное название";
        AliasSuggestion rejected = suggestion.reject(REVIEWER_ID, comment);

        assertEquals(SuggestionStatus.REJECTED, rejected.getStatus());
        assertEquals(REVIEWER_ID, rejected.getReviewerId());
        assertEquals(comment, rejected.getReviewComment());
    }

    @Test
    @DisplayName("Отклонение с null reviewerId вызывает исключение")
    void rejectSuggestionFailsWhenReviewerIdNull() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, ENTITY_ID, ALIAS, LANGUAGE, CLIENT_ID);

        assertThrows(IllegalArgumentException.class, () -> suggestion.reject(null, "comment"));
    }

    @Test
    @DisplayName("Создание обрезает пробелы")
    void createSuggestionTrimsStrings() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, "  " + ENTITY_ID + "  ", "  " + ALIAS + "  ", LANGUAGE, "  " + CLIENT_ID + "  ");

        assertEquals(ENTITY_ID, suggestion.getEntityId());
        assertEquals(ALIAS, suggestion.getSuggestedAlias());
        assertEquals(CLIENT_ID, suggestion.getClientId());
    }
}
