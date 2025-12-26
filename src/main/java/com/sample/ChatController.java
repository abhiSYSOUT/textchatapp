package com.sample;

import com.sample.model.ChatMessage;
import com.sample.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatService chatService;

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        if (ChatMessage.MessageType.CHAT.name().equals(chatMessage.getType())) {
            chatService.save(chatMessage);
        }
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage,
            SimpMessageHeaderAccessor headerAccessor) {
        // Add username in web socket session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        chatService.addUser(chatMessage.getSender());

        // Deliver pending messages
        List<ChatMessage> pendingMessages = chatService.getPendingMessages(chatMessage.getSender());
        if (!pendingMessages.isEmpty()) {
            pendingMessages.forEach(msg -> {
                messagingTemplate.convertAndSendToUser(
                        msg.getRecipient(), "/queue/messages", msg);
            });
            chatService.markMessagesAsDelivered(pendingMessages);
        }

        broadcastUserList();
        return chatMessage;
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessage chatMessage) {
        // We can optimize this by checking DB status in service, but saving already
        // does it.
        // Just save and trigger send if recipient is potentially online?
        // Actually, send to queue regardless. If they are offline, STOMP won't deliver
        // it to a session,
        // but it's fine. The persistence logic handles the status.
        // Wait, if they are offline, we shouldn't rely on STOMP delivery.
        // But for "Online" users, STOMP delivers.
        // We will rely on the "save" method setting the status.

        ChatMessage saved = chatService.save(chatMessage);

        // Attempt to send via WebSocket. If user is offline, this might just drop, but
        // that's ok
        // because we saved it as RECEIVED (if offline) or DELIVERED (if
        // online/connected).
        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipient(), "/queue/messages", saved);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username != null) {
            chatService.removeUser(username); // Sets status to OFFLINE
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(ChatMessage.MessageType.LEAVE.name());
            chatMessage.setSender(username);

            // We might not want to save LEAVE messages in DB, or maybe we do?
            // Current req doesn't specify. Let's just broadcast.
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            broadcastUserList();
        }
    }

    @GetMapping("/messages/public")
    @ResponseBody
    public List<ChatMessage> getPublicMessages() {
        return chatService.getPublicMessages();
    }

    @GetMapping("/messages/{sender}/{recipient}")
    @ResponseBody
    public List<ChatMessage> getPrivateMessages(@PathVariable String sender, @PathVariable String recipient) {
        return chatService.getPrivateMessages(sender, recipient);
    }

    private void broadcastUserList() {
        messagingTemplate.convertAndSend("/topic/users", chatService.getUsers());
    }
}
