package com.sample;

import com.sample.model.ChatMessage;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PrivateChatTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
    public void testPrivateMessage() throws ExecutionException, InterruptedException, TimeoutException {
        // User A connects
        StompSession sessionA = stompClient
                .connect("ws://localhost:" + port + "/chat-websocket?username=UserA", new StompSessionHandlerAdapter() {
                })
                .get(1, TimeUnit.SECONDS);

        // User B connects
        StompSession sessionB = stompClient
                .connect("ws://localhost:" + port + "/chat-websocket?username=UserB", new StompSessionHandlerAdapter() {
                })
                .get(1, TimeUnit.SECONDS);

        CompletableFuture<ChatMessage> completableFuture = new CompletableFuture<>();

        // User B subscribes to their private queue
        sessionB.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((ChatMessage) payload);
            }
        });

        // User A sends private message to User B
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender("UserA");
        chatMessage.setRecipient("UserB");
        chatMessage.setContent("Secret Message");
        chatMessage.setType(ChatMessage.MessageType.CHAT.name());

        sessionA.send("/app/chat.private", chatMessage);

        // Verify User B received it
        ChatMessage received = completableFuture.get(10, TimeUnit.SECONDS);
        assertEquals("Secret Message", received.getContent());
        assertEquals("UserA", received.getSender());
        assertEquals("UserB", received.getRecipient());
    }
}
