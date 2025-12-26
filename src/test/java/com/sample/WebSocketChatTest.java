package com.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.sample.model.ChatMessage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketChatTest {

    @LocalServerPort
    private int port;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;

    @BeforeEach
    public void setup() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        this.stompClient = new WebSocketStompClient(sockJsClient);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        this.stompClient.setMessageConverter(converter);
    }

    @Test
    public void verifyGreetingIsReceived() throws Exception {
        CompletableFuture<ChatMessage> completableFuture = new CompletableFuture<>();

        StompSession session = stompClient
                .connect("ws://localhost:" + port + "/chat-websocket", new StompSessionHandlerAdapter() {
                })
                .get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((ChatMessage) payload);
            }
        });

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender("TestUser");
        chatMessage.setContent("Hello, World!");
        chatMessage.setAvatarUrl("http://example.com/avatar.png");
        chatMessage.setType(ChatMessage.MessageType.CHAT.name());

        session.send("/app/chat.sendMessage", chatMessage);

        ChatMessage receivedMessage = completableFuture.get(10, TimeUnit.SECONDS);

        assertEquals("Hello, World!", receivedMessage.getContent());
        assertEquals("TestUser", receivedMessage.getSender());
        assertEquals("http://example.com/avatar.png", receivedMessage.getAvatarUrl());
    }
}
