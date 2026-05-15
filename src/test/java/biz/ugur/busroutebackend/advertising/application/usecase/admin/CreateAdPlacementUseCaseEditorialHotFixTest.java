package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAdPlacementUseCaseEditorialHotFixTest {

    @Test
    void editorial_after_approve_is_scheduled() {
        AdPlacement approved = mock(AdPlacement.class);
        when(approved.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(approved.getStatus()).thenReturn(PlacementStatus.PENDING_PAYMENT);
        AdPlacement scheduled = mock(AdPlacement.class);
        when(scheduled.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(scheduled.getStatus()).thenReturn(PlacementStatus.SCHEDULED);
        when(approved.markAsScheduled()).thenReturn(scheduled);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(approved);

        assertEquals(PlacementStatus.SCHEDULED, result.getStatus());
        verify(approved).markAsScheduled();
    }

    @Test
    void commercial_after_approve_stays_pending_payment() {
        AdPlacement approved = mock(AdPlacement.class);
        when(approved.getKind()).thenReturn(PlacementKind.COMMERCIAL);
        when(approved.getStatus()).thenReturn(PlacementStatus.PENDING_PAYMENT);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(approved);

        assertEquals(PlacementStatus.PENDING_PAYMENT, result.getStatus());
        verify(approved, never()).markAsScheduled();
        assertSame(approved, result);
    }

    @Test
    void editorial_not_in_pending_payment_unchanged() {
        AdPlacement placement = mock(AdPlacement.class);
        when(placement.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(placement.getStatus()).thenReturn(PlacementStatus.ACTIVE);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(placement);

        verify(placement, never()).markAsScheduled();
        assertSame(placement, result);
    }
}
