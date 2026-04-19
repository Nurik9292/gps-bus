package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationExceptionsTest {

    @Test
    void blockedByIdCarriesIdValue() {
        ExternalServiceId id = ExternalServiceId.generate();
        ExternalServiceBlockedException ex = ExternalServiceBlockedException.byId(id);
        assertEquals(id.getValue(), ex.getServiceIdentifier());
        assertNull(ex.getBlockReason());
        assertTrue(ex.getMessage().contains(id.getValue()));
    }

    @Test
    void blockedByNameCarriesName() {
        ExternalServiceBlockedException ex = ExternalServiceBlockedException.byName("partner");
        assertEquals("partner", ex.getServiceIdentifier());
        assertTrue(ex.getMessage().contains("partner"));
    }

    @Test
    void blockedWithReasonCarriesReason() {
        ExternalServiceBlockedException ex = ExternalServiceBlockedException
                .withReason("partner", "abuse");
        assertEquals("partner", ex.getServiceIdentifier());
        assertEquals("abuse", ex.getBlockReason());
        assertTrue(ex.getMessage().contains("abuse"));
    }

    @Test
    void notFoundByIdCarriesId() {
        ExternalServiceId id = ExternalServiceId.generate();
        ExternalServiceNotFoundException ex = ExternalServiceNotFoundException.byId(id);
        assertEquals(id.getValue(), ex.getServiceIdentifier());
        assertEquals("id", ex.getIdentifierType());
    }

    @Test
    void notFoundByTokenMasksValue() {
        String rawToken = "brt_supersecrettoken1234567890abcd";
        ExternalServiceNotFoundException ex = ExternalServiceNotFoundException.byToken(rawToken);
        assertEquals("token", ex.getIdentifierType());
        assertFalse(ex.getServiceIdentifier().contains("supersecret"));
        assertTrue(ex.getServiceIdentifier().contains("..."));
    }

    @Test
    void notFoundByShortTokenShowsMaskFallback() {
        ExternalServiceNotFoundException ex = ExternalServiceNotFoundException.byToken("short");
        assertEquals("****", ex.getServiceIdentifier());
    }

    @Test
    void rateLimitExceededCarriesLimits() {
        RateLimitExceededException ex = RateLimitExceededException.forService("partner", 60, 70);
        assertEquals("partner", ex.getServiceName());
        assertEquals(60, ex.getLimit());
        assertEquals(70, ex.getCurrent());
        assertTrue(ex.isRetryable());
    }

    @Test
    void unauthorizedEndpointCarriesEndpoint() {
        UnauthorizedEndpointException ex = UnauthorizedEndpointException
                .forEndpoint("partner", "/api/admin");
        assertEquals("partner", ex.getServiceName());
        assertEquals("/api/admin", ex.getEndpoint());
        assertTrue(ex.getMessage().contains("/api/admin"));
    }

    @Test
    void clientManagementNotAllowedCarriesServiceId() {
        ClientManagementNotAllowedException ex =
                new ClientManagementNotAllowedException("svc-1");
        assertEquals("svc-1", ex.getServiceId());
        assertTrue(ex.getMessage().contains("canManageClients"));
    }

    @Test
    void clientNotBelongsToServiceCarriesIds() {
        ClientNotBelongsToServiceException ex =
                new ClientNotBelongsToServiceException("c-1", "s-1");
        assertEquals("c-1", ex.getClientId());
        assertEquals("s-1", ex.getServiceId());
        assertTrue(ex.getMessage().contains("c-1"));
        assertTrue(ex.getMessage().contains("s-1"));
    }
}
