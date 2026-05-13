package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffRouteAlertPropertiesTest {

    @Test
    void recipientListSplitsByCommaTrimsSpacesSkipsEmpty() {
        OffRouteAlertProperties props = new OffRouteAlertProperties();
        props.setRecipients("ops@example.com,  admin@example.com , , another@x.io");

        assertEquals(
                List.of("ops@example.com", "admin@example.com", "another@x.io"),
                props.recipientList());
    }

    @Test
    void recipientListEmptyWhenBlank() {
        OffRouteAlertProperties props = new OffRouteAlertProperties();
        props.setRecipients("");
        assertEquals(List.of(), props.recipientList());
    }

    @Test
    void recipientListEmptyWhenNull() {
        OffRouteAlertProperties props = new OffRouteAlertProperties();
        props.setRecipients(null);
        assertEquals(List.of(), props.recipientList());
    }
}
