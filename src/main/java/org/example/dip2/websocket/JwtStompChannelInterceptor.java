package org.example.dip2.websocket;

import java.security.Principal;
import java.util.List;
import org.example.dip2.exception.ApiException;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public JwtStompChannelInterceptor(JwtService jwtService, WebSocketSessionRegistry webSocketSessionRegistry) {
        this.jwtService = jwtService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            AuthenticatedUser authenticatedUser = jwtService.parseToken(extractBearerToken(accessor));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    List.of(new SimpleGrantedAuthority(authenticatedUser.role()))
            );
            accessor.setUser(authentication);
            if (accessor.getSessionId() != null) {
                webSocketSessionRegistry.registerAuthenticatedSession(accessor.getSessionId(), authenticatedUser.id().toString());
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (user == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "WebSocket subscription requires authentication");
            }
            String roomPin = extractRoomPin(accessor.getDestination());
            if (roomPin != null && accessor.getSessionId() != null) {
                webSocketSessionRegistry.registerRoomSubscription(accessor.getSessionId(), roomPin);
            }
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand()) && accessor.getSessionId() != null) {
            webSocketSessionRegistry.unregisterSession(accessor.getSessionId());
        }

        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing WebSocket bearer token");
        }
        return header.substring(7);
    }

    private String extractRoomPin(String destination) {
        if (destination == null || !destination.startsWith("/topic/rooms/")) {
            return null;
        }
        String remainder = destination.substring("/topic/rooms/".length());
        int slashIndex = remainder.indexOf('/');
        return (slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder).toUpperCase();
    }
}
