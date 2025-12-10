# Spring Boot WebSocket Chat Application

A real-time text chat application built with Spring Boot and WebSockets. It supports public group chat, one-to-one private messaging, and online user tracking.

## Architecture

The application follows a standard client-server architecture using WebSockets for real-time bidirectional communication.

### Backend (Spring Boot)
- **Framework**: Spring Boot 3.2.0 (Java 17+)
- **Protocol**: STOMP over WebSocket
- **Message Broker**: Simple in-memory broker provided by Spring.

#### Key Components
- **`WebSocketConfig`**: Configures the message broker and registers the STOMP endpoint (`/chat-websocket`). It enables a simple broker for `/topic` (public) and `/queue` (private) destinations.
- **`ChatController`**: The main controller handling WebSocket messages.
    - `@MessageMapping("/chat.sendMessage")`: Broadcasts public messages to `/topic/public`.
    - `@MessageMapping("/chat.addUser")`: Adds a user to the session and broadcasts the updated user list.
    - `@MessageMapping("/chat.private")`: Routes private messages to a specific user's queue (`/user/{username}/queue/messages`).
    - `@EventListener`: Listens for `SessionDisconnectEvent` to handle user disconnections.
- **`CustomHandshakeHandler` & `UserPrincipal`**: Custom components to assign a `Principal` to each WebSocket session based on the username passed during the handshake. This is crucial for enabling Spring's `convertAndSendToUser` functionality for private messaging.
- **`ChatMessage`**: The data model for chat messages, including content, sender, recipient, and type (CHAT, JOIN, LEAVE).

### Frontend
- **Technologies**: HTML5, CSS3, JavaScript (ES6).
- **Libraries**:
    - `SockJS`: Provides a WebSocket-like object for browsers that don't support native WebSockets.
    - `Stomp.js`: A STOMP client for the browser.

#### Key Logic (`main.js`)
- **Connection**: Establishes a connection to the backend using SockJS and Stomp.js, passing the username as a query parameter.
- **Subscriptions**:
    - `/topic/public`: Listens for public chat messages.
    - `/topic/users`: Listens for updates to the online user list.
    - `/user/queue/messages`: Listens for private messages sent specifically to the logged-in user.
- **State Management**: Maintains a local `chatHistory` to persist messages when switching between public and private chat views.

## Features
- **Public Chat**: Broadcast messages to all connected users.
- **Private Chat**: Send direct messages to a specific online user.
- **Online User List**: Real-time updates of who is currently connected.
- **User Search**: Filter the online user list.
- **Message Persistence**: Client-side history preserves messages when switching chat tabs.

## How to Run

1.  **Prerequisites**: Java 17+ and Maven.
2.  **Build**:
    ```bash
    mvn clean package
    ```
3.  **Run**:
    ```bash
    mvn spring-boot:run
    ```
4.  **Access**: Open `http://localhost:8080` in your browser. Open multiple tabs/windows to simulate different users.
