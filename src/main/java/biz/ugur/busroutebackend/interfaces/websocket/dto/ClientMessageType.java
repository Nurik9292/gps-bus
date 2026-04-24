package biz.ugur.busroutebackend.interfaces.websocket.dto;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ClientMessageType {
    PING("ping"),
    SUBSCRIBE_ROUTES("subscribe_routes"),
    SUBSCRIBE_BOUNDS("subscribe_bounds");

    private final String wireName;

    ClientMessageType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    private static final Map<String, ClientMessageType> BY_WIRE_NAME =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(ClientMessageType::wireName, t -> t));

    public static Optional<ClientMessageType> fromWireName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(BY_WIRE_NAME.get(name));
    }
}
