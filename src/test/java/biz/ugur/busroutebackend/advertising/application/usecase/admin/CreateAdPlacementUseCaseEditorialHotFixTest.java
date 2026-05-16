package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAdPlacementUseCaseEditorialHotFixTest {

    @Test
    void editorial_without_window_goes_directly_active() {
        AdPlacement approved = mock(AdPlacement.class);
        when(approved.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(approved.getStatus()).thenReturn(PlacementStatus.PENDING_PAYMENT);
        AdPlacement scheduled = mock(AdPlacement.class);
        when(scheduled.getWindow()).thenReturn(null);
        AdPlacement active = mock(AdPlacement.class);
        when(active.getStatus()).thenReturn(PlacementStatus.ACTIVE);
        when(approved.markAsScheduled()).thenReturn(scheduled);
        when(scheduled.markAsActive()).thenReturn(active);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(approved);

        assertEquals(PlacementStatus.ACTIVE, result.getStatus());
        verify(scheduled).markAsActive();
    }

    @Test
    void editorial_with_past_starts_at_goes_directly_active() {
        AdPlacement approved = mock(AdPlacement.class);
        when(approved.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(approved.getStatus()).thenReturn(PlacementStatus.PENDING_PAYMENT);
        AdPlacement scheduled = mock(AdPlacement.class);
        when(scheduled.getWindow()).thenReturn(PlacementWindow.of(LocalDateTime.now().minusHours(1), null));
        AdPlacement active = mock(AdPlacement.class);
        when(active.getStatus()).thenReturn(PlacementStatus.ACTIVE);
        when(approved.markAsScheduled()).thenReturn(scheduled);
        when(scheduled.markAsActive()).thenReturn(active);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(approved);

        assertEquals(PlacementStatus.ACTIVE, result.getStatus());
        verify(scheduled).markAsActive();
    }

    @Test
    void editorial_with_future_starts_at_stays_scheduled() {
        AdPlacement approved = mock(AdPlacement.class);
        when(approved.getKind()).thenReturn(PlacementKind.EDITORIAL);
        when(approved.getStatus()).thenReturn(PlacementStatus.PENDING_PAYMENT);
        AdPlacement scheduled = mock(AdPlacement.class);
        when(scheduled.getWindow()).thenReturn(PlacementWindow.of(LocalDateTime.now().plusDays(1), null));
        when(scheduled.getStatus()).thenReturn(PlacementStatus.SCHEDULED);
        when(approved.markAsScheduled()).thenReturn(scheduled);

        AdPlacement result = CreateAdPlacementUseCase.hotFixEditorialStatus(approved);

        assertEquals(PlacementStatus.SCHEDULED, result.getStatus());
        verify(scheduled, never()).markAsActive();
        assertSame(scheduled, result);
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
