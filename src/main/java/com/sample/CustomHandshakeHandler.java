package com.sample;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class CustomHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        // Extract username from query parameter
        String query = request.getURI().getQuery();
        if (query != null && query.contains("username=")) {
            String username = query.split("username=")[1].split("&")[0];
            return new UserPrincipal(username);
        }
        return null;
    }
}
