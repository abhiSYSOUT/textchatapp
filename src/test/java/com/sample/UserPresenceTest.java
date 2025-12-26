package com.sample;

import com.sample.model.User;
import com.sample.repository.UserRepository;
import com.sample.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class UserPresenceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testUserPresenceFlow() {
        String username = "PresenceUser";

        // 1. Add User (Connect)
        chatService.addUser(username);

        // Verify User is ONLINE
        User user = userRepository.findById(username).orElse(null);
        assertNotNull(user);
        assertEquals(User.Status.ONLINE.name(), user.getStatus());

        // Verify in list
        List<User> users = chatService.getUsers();
        boolean found = users.stream()
                .anyMatch(u -> u.getUsername().equals(username) && u.getStatus().equals(User.Status.ONLINE.name()));
        assertEquals(true, found);

        // 2. Remove User (Disconnect)
        chatService.removeUser(username);

        // Verify User is OFFLINE
        user = userRepository.findById(username).orElse(null);
        assertNotNull(user);
        assertEquals(User.Status.OFFLINE.name(), user.getStatus());

        // Verify still in list (but offline)
        users = chatService.getUsers();
        found = users.stream().anyMatch(u -> u.getUsername().equals(username));
        assertEquals(true, found);
    }
}
