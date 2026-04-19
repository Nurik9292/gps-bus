package biz.ugur.busroutebackend.integration.application.mapper;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalServiceDTOMapperTest {

    private ExternalService service;

    @BeforeEach
    void setUp() {
        service = ExternalService.create(
                "Partner",
                "description",
                AdminId.generate(),
                List.of("/api/v1/**"),
                120,
                true
        );
    }

    @Test
    void toDTOMasksTokenAndExposesPrimaryFields() {
        ExternalServiceDTO dto = ExternalServiceDTOMapper.toDTO(service);

        assertEquals(service.getId().getValue(), dto.id());
        assertEquals("Partner", dto.name());
        assertEquals("description", dto.description());
        assertNull(dto.apiToken());
        assertNotNull(dto.maskedToken());
        assertTrue(dto.maskedToken().startsWith("brt_"));
        assertNotEquals(service.getApiToken().getValue(), dto.maskedToken());
        assertEquals(List.of("/api/v1/**"), dto.allowedEndpoints());
        assertEquals(120, dto.rateLimitPerMinute());
        assertTrue(dto.isActive());
        assertTrue(dto.canManageClients());
    }

    @Test
    void toDTOWithTokenReturnsPlainTokenValue() {
        ExternalServiceDTO dto = ExternalServiceDTOMapper.toDTOWithToken(service);

        assertEquals(service.getApiToken().getValue(), dto.apiToken());
        assertEquals(service.getApiToken().getMaskedValue(), dto.maskedToken());
    }

    @Test
    void toDTOPreservesCreatedByAdminId() {
        ExternalServiceDTO dto = ExternalServiceDTOMapper.toDTO(service);

        assertEquals(service.getCreatedByAdminId().getValue(), dto.createdByAdminId());
    }
}
