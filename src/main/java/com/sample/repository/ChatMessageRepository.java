package com.sample.repository;

import com.sample.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Fetch public messages (recipient is null)
    List<ChatMessage> findByRecipientIsNullOrderByTimestampAsc();

    // Fetch private messages between two users (bidirectional)
    // (sender = A AND recipient = B) OR (sender = B AND recipient = A)
    List<ChatMessage> findBySenderAndRecipientOrSenderAndRecipientOrderByTimestampAsc(
            String sender1, String recipient1, String sender2, String recipient2);

    List<ChatMessage> findByRecipientAndStatus(String recipient, String status);

    // For simpler fetching of private messages for a specific user to display in
    // their history
    // This might be complex to query simply.
    // Let's stick to: findByRecipientOrSender... but we usually want conversation
    // history.
    // simpler: findByRecipientAndSenderOrSenderAndRecipient
}
