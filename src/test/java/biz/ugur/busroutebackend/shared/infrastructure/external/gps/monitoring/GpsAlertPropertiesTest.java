package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpsAlertPropertiesTest {

    @Test
    void recipientListSplitsByCommaTrimsSpacesSkipsEmpty() {
        GpsAlertProperties props = new GpsAlertProperties();
        props.setRecipients("ops@example.com,  admin@example.com , , another@x.io");

        assertEquals(
                List.of("ops@example.com", "admin@example.com", "another@x.io"),
                props.recipientList());
    }

    @Test
    void recipientListEmptyWhenBlank() {
        GpsAlertProperties props = new GpsAlertProperties();
        props.setRecipients("");
        assertEquals(List.of(), props.recipientList());
    }

    @Test
    void recipientListEmptyWhenNull() {
        GpsAlertProperties props = new GpsAlertProperties();
        props.setRecipients(null);
        assertEquals(List.of(), props.recipientList());
    }
}
