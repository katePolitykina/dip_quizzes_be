package org.example.dip2.dto.ws;

public record SocketEnvelope(
        String type,
        Object data
) {
}
