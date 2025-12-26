package com.sample.service;

import com.sample.model.ChatMessage;
import com.sample.repository.ChatMessageRepository;

import com.sample.model.User;
import com.sample.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final UserRepository userRepository;

    public ChatService(ChatMessageRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public ChatMessage save(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());

        // precise status logic: check if recipient is online in DB
        if (message.getRecipient() != null) {
            User recipientUser = userRepository.findById(message.getRecipient()).orElse(null);
            if (recipientUser != null && User.Status.ONLINE.name().equals(recipientUser.getStatus())) {
                message.setStatus(ChatMessage.MessageStatus.DELIVERED.name());
            } else {
                message.setStatus(ChatMessage.MessageStatus.RECEIVED.name());
            }
        } else {
            message.setStatus(ChatMessage.MessageStatus.DELIVERED.name());
        }

        return repository.save(message);
    }

    public List<ChatMessage> getPublicMessages() {
        return repository.findByRecipientIsNullOrderByTimestampAsc();
    }

    public List<ChatMessage> getPrivateMessages(String user1, String user2) {
        return repository.findBySenderAndRecipientOrSenderAndRecipientOrderByTimestampAsc(
                user1, user2, user2, user1);
    }

    public List<ChatMessage> getPendingMessages(String recipient) {
        return repository.findByRecipientAndStatus(recipient, ChatMessage.MessageStatus.RECEIVED.name());
    }

    public void markMessagesAsDelivered(List<ChatMessage> messages) {
        messages.forEach(m -> m.setStatus(ChatMessage.MessageStatus.DELIVERED.name()));
        repository.saveAll(messages);
    }

    public void addUser(String username) {
        User user = userRepository.findById(username).orElse(new User(username, User.Status.OFFLINE.name()));
        user.setStatus(User.Status.ONLINE.name());
        userRepository.save(user);
    }

    public void removeUser(String username) {
        User user = userRepository.findById(username).orElse(null);
        if (user != null) {
            user.setStatus(User.Status.OFFLINE.name());
            userRepository.save(user);
        }
    }

    public List<User> getUsers() {
        return userRepository.findAllByOrderByStatusAscUsernameAsc();
    }
}
