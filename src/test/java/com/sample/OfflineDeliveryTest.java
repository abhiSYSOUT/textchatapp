package com.sample;

import com.sample.model.ChatMessage;
import com.sample.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class OfflineDeliveryTest {

    @Autowired
    private ChatService chatService;

    @Test
    public void testOfflineMessageDelivery() {
        // User A sends message to User B (who is offline)
        ChatMessage msg = new ChatMessage();
        msg.setSender("UserA");
        msg.setRecipient("UserB");
        msg.setContent("Hello Offline");
        msg.setType(ChatMessage.MessageType.CHAT.name());

        // Ensure User B is offline
        chatService.removeUser("UserB");

        // Save message (should be RECEIVED)
        chatService.save(msg);

        List<ChatMessage> pending = chatService.getPendingMessages("UserB");
        assertEquals(1, pending.size());
        assertEquals(ChatMessage.MessageStatus.RECEIVED.name(), pending.get(0).getStatus());

        // Simulate delivery
        chatService.markMessagesAsDelivered(pending);

        List<ChatMessage> pendingAfter = chatService.getPendingMessages("UserB");
        assertEquals(0, pendingAfter.size());
    }
}
