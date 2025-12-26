'use strict';

var usernamePage = document.querySelector('#username-page');
var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');

var stompClient = null;
var username = null;
var avatarUrl = null;
var selectedUser = null; // null means public chat
var userList = [];
var chatHistory = { 'public': [] };

// WebRTC variables
var peerConnection;
var localStream;
var rtcConfig = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }
    ]
};

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

function connect(event) {
    username = document.querySelector('#name').value.trim();
    avatarUrl = document.querySelector('#avatarUrl').value.trim();

    if (username) {
        usernamePage.classList.add('hidden');
        chatPage.classList.remove('hidden');

        var socket = new SockJS('/chat-websocket?username=' + username);
        stompClient = Stomp.over(socket);

        stompClient.connect({}, onConnected, onError);
    }
    event.preventDefault();
}


function onConnected() {
    // Subscribe to the Public Topic
    stompClient.subscribe('/topic/public', onMessageReceived);
    stompClient.subscribe('/topic/users', onUserListReceived);
    stompClient.subscribe('/user/queue/messages', onPrivateMessageReceived);

    // Tell your username to the server
    stompClient.send("/app/chat.addUser",
        {},
        JSON.stringify({ sender: username, avatarUrl: avatarUrl, type: 'JOIN' })
    )

    connectingElement.classList.add('hidden');

    // Fetch public history on initial connection
    fetch('/messages/public')
        .then(response => response.json())
        .then(messages => {
            messages.forEach(renderMessage);
            messageArea.scrollTop = messageArea.scrollHeight;
        });
}


function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}


function sendMessage(event) {
    var messageContent = messageInput.value.trim();
    if (messageContent && stompClient) {
        var chatMessage = {
            sender: username,
            content: messageInput.value,
            avatarUrl: avatarUrl,
            type: 'CHAT'
        };

        if (selectedUser) {
            chatMessage.recipient = selectedUser;
            stompClient.send("/app/chat.private", {}, JSON.stringify(chatMessage));
            displayMessage(chatMessage); // Display own private message
        } else {
            stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        }
        messageInput.value = '';
    }
    event.preventDefault();
}


function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    displayMessage(message);
}

function onPrivateMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    if (selectedUser && message.sender === selectedUser) {
        if (message.type === 'OFFER') {
            handleOffer(message);
        } else if (message.type === 'ANSWER') {
            handleAnswer(message);
        } else if (message.type === 'ICE') {
            handleCandidate(message);
        } else {
            displayMessage(message);
        }
    } else {
        // Notification logic could go here
        // For now, if we are not chatting with them, we might miss it in this simple UI
        // Ideally we should show a badge or switch to them
        if (message.type === 'OFFER') {
            alert("Incoming video call from " + message.sender + ". Switch to their chat to answer.");
        } else {
            alert("New private message from " + message.sender);
        }
    }
}

function onUserListReceived(payload) {
    try {
        if (payload.body) {
            userList = JSON.parse(payload.body);
        } else {
            userList = [];
        }
    } catch (e) {
        console.error("Error parsing user list:", e);
        userList = [];
    }
    updateUserList();
}

function updateUserList() {
    var userListElement = document.getElementById('user-list');
    userListElement.innerHTML = '';

    var searchInput = document.getElementById('user-search');
    var filter = searchInput.value.toLowerCase();

    // Filter out self
    userList.forEach(function (userObj) {
        var user = null;
        var status = 'OFFLINE'; // Default status

        if (typeof userObj === 'string') {
            user = userObj;
        } else if (userObj && typeof userObj === 'object' && userObj.username) {
            user = userObj.username;
            if (userObj.status) {
                status = userObj.status;
            }
        } else {
            console.warn("Invalid user object received:", userObj);
            return; // Skip this entry if it's malformed
        }

        if (user && user !== username && user.toLowerCase().includes(filter)) {
            var li = document.createElement('li');
            li.textContent = user;

            // Add status indicator class
            if (status) {
                li.classList.add(status.toLowerCase());
            }

            if (user === selectedUser) {
                li.classList.add('active');
            }
            li.onclick = function () {
                selectedUser = user;
                updateUserList(); // To highlight selected
                document.querySelector('.chat-header h2').textContent = "Chat with " + user;
                document.getElementById('messageArea').innerHTML = ''; // Clear chat area for new context
                document.getElementById('video-call-btn').classList.remove('hidden');

                // Fetch private history from server
                fetch('/messages/' + username + '/' + selectedUser)
                    .then(response => response.json())
                    .then(messages => {
                        messages.forEach(renderMessage);
                        messageArea.scrollTop = messageArea.scrollHeight;
                    });
            };
            userListElement.appendChild(li);
        }
    });

    // Add Public Chat option
    var publicLi = document.createElement('li');
    publicLi.textContent = "Public Chat";
    if (!selectedUser) {
        publicLi.classList.add('active');
    }
    publicLi.onclick = function () {
        selectedUser = null;
        updateUserList();
        document.querySelector('.chat-header h2').textContent = "Spring WebSocket Chat";
        document.getElementById('messageArea').innerHTML = '';
        document.getElementById('video-call-btn').classList.add('hidden');
        document.getElementById('video-container').classList.add('hidden');
        if (localStream) {
            localStream.getTracks().forEach(track => track.stop());
        }

        // Fetch public history from server
        fetch('/messages/public')
            .then(response => response.json())
            .then(messages => {
                messages.forEach(renderMessage);
                messageArea.scrollTop = messageArea.scrollHeight;
            });
    };
    userListElement.insertBefore(publicLi, userListElement.firstChild);
}

document.getElementById('user-search').addEventListener('input', updateUserList);

function displayMessage(message) {
    // message is already rendered, no need to push to local history if we rely on server for hydration
    // But we might want to keep it to avoid re-fetching if we switch tabs? 
    // For now, let's just render. The 'push to history' logic is removed as server is SOT.

    // Render only if it belongs to the current view
    var shouldRender = false;
    if (selectedUser) {
        // Private chat view
        if (message.type === 'CHAT' && (message.sender === selectedUser || (message.sender === username && message.recipient === selectedUser))) {
            shouldRender = true;
        }
    } else {
        // Public chat view
        if (!message.recipient) { // Render public messages and events
            shouldRender = true;
        }
    }

    if (shouldRender) {
        renderMessage(message);
    }
}

function renderMessage(message) {
    var messageElement = document.createElement('li');

    if (message.type === 'JOIN') {
        messageElement.classList.add('event-message');
        message.content = message.sender + ' joined!';
    } else if (message.type === 'LEAVE') {
        messageElement.classList.add('event-message');
        message.content = message.sender + ' left!';
    } else {
        messageElement.classList.add('chat-message');
        if (message.sender === username) {
            messageElement.classList.add('self');
        } else {
            messageElement.classList.add('other');
        }

        var avatarElement = document.createElement('i');

        if (message.avatarUrl) {
            var imgElement = document.createElement('img');
            imgElement.src = message.avatarUrl;
            imgElement.style.width = '100%';
            imgElement.style.height = '100%';
            imgElement.style.borderRadius = '50%';
            avatarElement.appendChild(imgElement);
            avatarElement.style.backgroundColor = 'transparent';
        } else {
            var initial = (message.sender && message.sender.length > 0) ? message.sender[0].toUpperCase() : '?';
            var avatarText = document.createTextNode(initial);
            avatarElement.appendChild(avatarText);
            avatarElement.style['background-color'] = getAvatarColor(message.sender || 'default');
        }

        messageElement.appendChild(avatarElement);

        var contentElement = document.createElement('div');
        contentElement.classList.add('message-content');

        var usernameElement = document.createElement('span');
        var usernameText = document.createTextNode(message.sender);
        usernameElement.appendChild(usernameText);
        contentElement.appendChild(usernameElement);

        var textElement = document.createElement('p');
        var messageText = document.createTextNode(message.content);
        textElement.appendChild(messageText);
        contentElement.appendChild(textElement);

        messageElement.appendChild(contentElement);
    }

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        var textElement = document.createElement('p');
        var messageText = document.createTextNode(message.content);
        textElement.appendChild(messageText);
        messageElement.appendChild(textElement);
    }

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}

// WebRTC Functions

document.getElementById('video-call-btn').addEventListener('click', startCall);

function startCall() {
    document.getElementById('video-container').classList.remove('hidden');
    navigator.mediaDevices.getUserMedia({ video: true, audio: true })
        .then(stream => {
            localStream = stream;
            document.getElementById('localVideo').srcObject = stream;
            createPeerConnection();
            localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

            peerConnection.createOffer()
                .then(offer => peerConnection.setLocalDescription(offer))
                .then(() => {
                    sendSignal('OFFER', JSON.stringify(peerConnection.localDescription));
                });
        })
        .catch(error => console.error('Error accessing media devices:', error));
}

function handleOffer(message) {
    document.getElementById('video-container').classList.remove('hidden');
    navigator.mediaDevices.getUserMedia({ video: true, audio: true })
        .then(stream => {
            localStream = stream;
            document.getElementById('localVideo').srcObject = stream;
            createPeerConnection();
            localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

            var offer = JSON.parse(message.content);
            peerConnection.setRemoteDescription(new RTCSessionDescription(offer));

            peerConnection.createAnswer()
                .then(answer => peerConnection.setLocalDescription(answer))
                .then(() => {
                    sendSignal('ANSWER', JSON.stringify(peerConnection.localDescription));
                });
        })
        .catch(error => console.error('Error accessing media devices:', error));
}

function handleAnswer(message) {
    var answer = JSON.parse(message.content);
    peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
}

function handleCandidate(message) {
    var candidate = JSON.parse(message.content);
    peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
}

function createPeerConnection() {
    peerConnection = new RTCPeerConnection(rtcConfig);

    peerConnection.onicecandidate = event => {
        if (event.candidate) {
            sendSignal('ICE', JSON.stringify(event.candidate));
        }
    };

    peerConnection.ontrack = event => {
        document.getElementById('remoteVideo').srcObject = event.streams[0];
    };
}

function sendSignal(type, content) {
    var chatMessage = {
        sender: username,
        recipient: selectedUser,
        content: content,
        type: type
    };
    stompClient.send("/app/chat.private", {}, JSON.stringify(chatMessage));
}


function getAvatarColor(messageSender) {
    var hash = 0;
    for (var i = 0; i < messageSender.length; i++) {
        hash = 31 * hash + messageSender.charCodeAt(i);
    }
    var index = Math.abs(hash % colors.length);
    return colors[index];
}

usernameForm.addEventListener('submit', connect, true)
messageForm.addEventListener('submit', sendMessage, true)
