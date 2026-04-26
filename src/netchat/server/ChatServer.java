/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.security.ConnectionRiskProfile;
import netchat.shared.media.MediaMode;
import netchat.shared.model.ChatRoom;
import netchat.shared.model.Message;
import netchat.shared.model.User;
import netchat.shared.model.UserRole;
import netchat.shared.protocol.MessageType;
import netchat.shared.protocol.Packet;
import netchat.shared.protocol.PacketCodec;
import netchat.shared.util.TextUtils;
import netchat.transmission.DeliveryChannel;
import netchat.transmission.DeliveryRecord;
import netchat.web.WebAdminServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ChatServer {
    private final int port;
    private final int webPort;
    private final PacketCodec codec;
    private final ServerState state;
    private final AuthService authService;
    private final ModerationService moderationService;
    private final WebAdminServer webAdminServer;

    public ChatServer(int port, String secret) {
        this.port = port;
        this.webPort = port + 1000;
        this.codec = new PacketCodec(secret);
        this.state = new ServerState(secret);
        this.authService = new AuthService(state);
        this.moderationService = new ModerationService(state);
        this.webAdminServer = new WebAdminServer(webPort, this, state);
    }

    public void start() {
        System.out.println("NetChat server starting on port " + port + ".");
        System.out.println("Transport protection: AES/GCM encrypted packets.");
        System.out.println("Bootstrap admin account: admin / Admin1234");
        System.out.println("Unicode ready: Ã¤ Ã¶ Ã¼ ÃŸ");
        System.out.println(state.getPersistenceService().describeStorageLayout());
        System.out.println(state.getProxyRoutingService().describeTopology());
        System.out.println(state.getExternalPlatformHub().describeEnabledIntegrations());
        webAdminServer.start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(UUID.randomUUID().toString(), socket);
                state.getSessions().put(session.getSessionId(), session);
                new Thread(() -> handleClient(session), "netchat-client-" + session.getSessionId()).start();
            }
        } catch (IOException exception) {
            System.out.println("Server error: " + exception.getMessage());
        }
    }

    private void handleClient(ClientSession session) {
        try (session; BufferedReader reader = new BufferedReader(new InputStreamReader(session.getSocket().getInputStream(), StandardCharsets.UTF_8))) {
            ConnectionRiskProfile riskProfile = state.getVpnDetectionService().analyze(session.getRemoteAddress(), session.getReverseHost());
            state.getPersistenceService().appendNetworkRecord("CONNECTION;" + session.getSessionId() + ";" + riskProfile.serialize());
            audit("Incoming session " + session.getSessionId() + " riskScore=" + riskProfile.getRiskScore() + " reason=" + riskProfile.getReason());

            trackedSend("primary-server", session, DeliveryChannel.SERVER_TO_USER, "system-message",
                    Packet.of(MessageType.SYSTEM_MESSAGE)
                            .with("message", "Secure connection established. Register or login to continue."),
                    "initial secure handshake");
            trackedSend("primary-server", session, DeliveryChannel.SERVER_TO_USER, "system-message",
                    Packet.of(MessageType.SYSTEM_MESSAGE)
                            .with("message", "Traffic routed through proxy node " + state.getProxyRoutingService().selectProxy().getNodeId() + "."),
                    "proxy route notice");

            if (riskProfile.isHighRisk()) {
                trackedSend("primary-server", session, DeliveryChannel.SERVER_TO_USER, "moderation-warning",
                        Packet.of(MessageType.MODERATION_ACTION)
                                .with("message", "Warning: your connection was flagged as high risk by VPN/proxy heuristics."),
                        "high-risk connection warning");
            }

            String encryptedLine;
            while ((encryptedLine = reader.readLine()) != null) {
                Packet packet = codec.decode(encryptedLine);
                processPacket(session, packet);
            }
        } catch (Exception exception) {
            audit("Session " + session.getSessionId() + " disconnected: " + exception.getMessage());
        } finally {
            disconnectSession(session, "Disconnected from server.");
        }
    }

    private void processPacket(ClientSession session, Packet packet) throws IOException {
        switch (packet.type()) {
            case REGISTER_REQUEST -> completeAuthentication(session, authService.register(session, packet));
            case LOGIN_REQUEST -> completeAuthentication(session, authService.login(session, packet));
            default -> {
                if (!session.isAuthenticated()) {
                    session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE)
                            .with("message", "Authentication is required before using chat features."));
                    return;
                }
                handleAuthenticatedPacket(session, packet);
            }
        }
    }

    private void completeAuthentication(ClientSession session, Packet response) throws IOException {
        session.send(codec, response);
        if (Boolean.parseBoolean(response.getOrDefault("success", "false"))) {
            joinRoom(session, "general");
            broadcastSystem(session.getCurrentRoom(),
                    session.getUser().getDisplayName() + " joined the room.");
            audit("Authenticated user " + session.getUsername() + " from " + session.getSocket().getInetAddress());
        }
    }

    private void handleAuthenticatedPacket(ClientSession session, Packet packet) throws IOException {
        switch (packet.type()) {
            case CHAT_MESSAGE -> handleChatMessage(session, packet);
            case MEDIA_SIGNAL -> handleMediaSignal(session, packet);
            case COMMAND -> moderationService.executeCommand(session, packet.getOrDefault("command", ""), this);
            case LOGOUT -> disconnectSession(session, "Signed out.");
            default -> session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE)
                    .with("message", "Unsupported packet type: " + packet.type().name()));
        }
    }

    private void handleChatMessage(ClientSession session, Packet packet) throws IOException {
        String content = TextUtils.normalizeMessage(packet.getOrDefault("content", ""));
        String problem = moderationService.validateMessage(session, content);

        if (problem != null) {
            session.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", problem));
            return;
        }

        Message message = new Message(session.getUsername(), session.getCurrentRoom(), null, content, LocalDateTime.now(), false);
        ChatRoom room = state.getRooms().get(session.getCurrentRoom());
        if (room == null) {
            session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "Your current room no longer exists."));
            return;
        }

        room.addMessage(message);
        state.getPersistenceService().appendMessageRecord(message);
        state.getIntegrationService().publishEvent("analytics-core", "room_message", session.getCurrentRoom() + ";" + session.getUsername());
        state.getExternalPlatformHub().onArchiveRecord("room_message", session.getUsername(), content);
        state.getExternalPlatformHub().onMessageIndexed("room-" + System.nanoTime(),
                "{\"sender\":\"" + escapeJson(session.getUsername()) + "\",\"room\":\"" + escapeJson(session.getCurrentRoom()) + "\",\"content\":\"" + escapeJson(content) + "\"}");
        String display = message.toDisplayLine(session.getUser().getDisplayName());
        broadcastToRoom(room.getId(), Packet.of(MessageType.CHAT_MESSAGE).with("message", display), session.getUsername(), "room message");
        audit("Room message in " + room.getId() + " from " + session.getUsername());
    }

    private void handleMediaSignal(ClientSession session, Packet packet) throws IOException {
        String action = packet.getOrDefault("action", "");
        if ("register".equalsIgnoreCase(action)) {
            session.setMediaAudioPort(parsePort(packet.getOrDefault("audioPort", "0")));
            session.setMediaVideoPort(parsePort(packet.getOrDefault("videoPort", "0")));
            trackedSend("primary-server", session, DeliveryChannel.USER_MEDIA_SIGNAL, "media-signal",
                    Packet.of(MessageType.MEDIA_SIGNAL)
                            .with("action", "registered")
                            .with("message", "Realtime media endpoints registered."),
                    "media registration confirmation");
            audit("Media endpoints registered for " + session.getUsername());
        }
    }

    public void joinRoom(ClientSession session, String roomId) throws IOException {
        String normalizedRoomId = TextUtils.normalizeIdentifier(roomId);
        ChatRoom nextRoom = state.getRooms().get(normalizedRoomId);
        if (nextRoom == null) {
            session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE)
                    .with("message", "Unknown room: " + roomId));
            return;
        }

        String previousRoomId = session.getCurrentRoom();
        if (previousRoomId != null) {
            ChatRoom previousRoom = state.getRooms().get(previousRoomId);
            if (previousRoom != null) {
                previousRoom.removeMember(session.getUsername());
            }
        }

        nextRoom.addMember(session.getUsername());
        session.setCurrentRoom(nextRoom.getId());
        session.send(codec, Packet.of(MessageType.ROOM_JOIN)
                .with("roomId", nextRoom.getId())
                .with("message", "Joined room " + nextRoom.getDisplayName() + "."));
        sendHistory(session);
    }

    public void sendRoomList(ClientSession session) throws IOException {
        StringBuilder builder = new StringBuilder("Available rooms:\n");
        for (ChatRoom room : state.getRooms().values()) {
            builder.append("- ")
                    .append(room.getId())
                    .append(" : ")
                    .append(room.getDisplayName())
                    .append(" (")
                    .append(room.getDescription())
                    .append(")\n");
        }
        session.send(codec, Packet.of(MessageType.ROOM_LIST).with("message", builder.toString().trim()));
    }

    public void sendUserList(ClientSession session) throws IOException {
        ChatRoom room = state.getRooms().get(session.getCurrentRoom());
        StringBuilder builder = new StringBuilder("Users in ").append(session.getCurrentRoom()).append(":\n");
        if (room != null) {
            for (String username : room.getMembers()) {
                User user = state.getUsers().get(username);
                if (user != null) {
                    builder.append("- ")
                            .append(user.getDisplayName())
                            .append(" [")
                            .append(user.getRole())
                            .append("]")
                            .append(user.isMuted() ? " muted" : "")
                            .append("\n");
                }
            }
        }
        session.send(codec, Packet.of(MessageType.USER_LIST).with("message", builder.toString().trim()));
    }

    public void sendHistory(ClientSession session) throws IOException {
        ChatRoom room = state.getRooms().get(session.getCurrentRoom());
        StringBuilder builder = new StringBuilder("Recent history for ").append(session.getCurrentRoom()).append(":\n");
        if (room != null) {
            for (Message message : room.getHistory()) {
                User sender = state.getUsers().get(message.getSender());
                String displayName = sender == null ? message.getSender() : sender.getDisplayName();
                builder.append(message.toDisplayLine(displayName)).append("\n");
            }
        }
        session.send(codec, Packet.of(MessageType.HISTORY_RESPONSE).with("message", builder.toString().trim()));
    }

    public void sendPrivateMessage(ClientSession sender, String recipientName, String content) throws IOException {
        String recipientKey = TextUtils.normalizeIdentifier(recipientName);
        ClientSession recipient = state.findSessionByUsername(recipientKey);
        if (recipient == null) {
            sender.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "User not online: " + recipientName));
            return;
        }

        String problem = moderationService.validateMessage(sender, content);
        if (problem != null) {
            sender.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", problem));
            return;
        }

        Message message = new Message(sender.getUsername(), null, recipientKey, content, LocalDateTime.now(), false);
        state.getPersistenceService().appendMessageRecord(message);
        state.getIntegrationService().publishEvent("analytics-core", "private_message", sender.getUsername() + ";" + recipientKey);
        state.getExternalPlatformHub().onArchiveRecord("private_message", sender.getUsername() + "->" + recipientKey, content);
        state.getExternalPlatformHub().onMessageIndexed("dm-" + System.nanoTime(),
                "{\"sender\":\"" + escapeJson(sender.getUsername()) + "\",\"recipient\":\"" + escapeJson(recipientKey) + "\",\"content\":\"" + escapeJson(content) + "\"}");
        String senderLine = message.toPrivateDisplayLine(sender.getUser().getDisplayName(), recipient.getUser().getDisplayName(), true);
        String recipientLine = message.toPrivateDisplayLine(sender.getUser().getDisplayName(), recipient.getUser().getDisplayName(), false);

        trackedSend(sender.getUsername(), sender, DeliveryChannel.USER_TO_USER, "private-message",
                Packet.of(MessageType.PRIVATE_MESSAGE).with("message", senderLine),
                "private message self-copy to " + recipient.getUsername());
        trackedSend(sender.getUsername(), recipient, DeliveryChannel.USER_TO_USER, "private-message",
                Packet.of(MessageType.PRIVATE_MESSAGE).with("message", recipientLine),
                "private message to " + recipient.getUsername());
        audit("Private message from " + sender.getUsername() + " to " + recipient.getUsername());
    }

    public void kickUser(ClientSession moderator, String username) throws IOException {
        ClientSession target = state.findSessionByUsername(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            moderator.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "User not online: " + username));
            return;
        }
        if (!canModerate(moderator, target)) {
            moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "You may not moderate this account."));
            return;
        }

        target.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "You were kicked by " + moderator.getUser().getDisplayName() + "."));
        disconnectSession(target, "Kicked by moderator.");
        moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "User kicked: " + username));
        audit("User " + username + " kicked by " + moderator.getUsername());
    }

    public void muteUser(ClientSession moderator, String username, long seconds) throws IOException {
        User target = state.getUsers().get(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            moderator.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "Unknown account: " + username));
            return;
        }
        if (!canModerate(moderator, target)) {
            moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "You may not moderate this account."));
            return;
        }

        target.muteForSeconds(seconds);
        moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION)
                .with("message", "Muted " + target.getDisplayName() + " for " + seconds + " seconds."));
        ClientSession liveSession = state.findSessionByUsername(target.getUsername());
        if (liveSession != null) {
            liveSession.send(codec, Packet.of(MessageType.MODERATION_ACTION)
                    .with("message", "You have been muted for " + seconds + " seconds."));
        }
        audit("User " + username + " muted by " + moderator.getUsername() + " for " + seconds + " seconds");
    }

    public void banUser(ClientSession moderator, String username) throws IOException {
        User target = state.getUsers().get(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            moderator.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "Unknown account: " + username));
            return;
        }
        if (!canBan(moderator, target)) {
            moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "Only an admin can ban this account."));
            return;
        }

        target.setBanned(true);
        ClientSession liveSession = state.findSessionByUsername(target.getUsername());
        if (liveSession != null) {
            liveSession.send(codec, Packet.of(MessageType.MODERATION_ACTION).with("message", "Your account has been banned."));
            disconnectSession(liveSession, "Banned by administrator.");
        }
        moderator.send(codec, Packet.of(MessageType.MODERATION_ACTION)
                .with("message", "Banned account: " + target.getDisplayName()));
        audit("User " + username + " banned by " + moderator.getUsername());
    }

    public void sendHelp(ClientSession session) throws IOException {
        session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message",
                "Commands:\n" +
                        "/help\n" +
                        "/rooms\n" +
                        "/join <roomId>\n" +
                        "/users\n" +
                        "/history\n" +
                        "/w <username> <message>\n" +
                        "/call <username>\n" +
                        "/videocall <username>\n" +
                        "/accept <username>\n" +
                        "/decline <username>\n" +
                        "/hangup\n" +
                        "/kick <username>\n" +
                        "/mute <username> <seconds>\n" +
                        "/ban <username>\n" +
                        "/storage"));
    }

    public void sendStorageStatus(ClientSession session) throws IOException {
        session.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message",
                state.getPersistenceService().describeStorageLayout() + "\n"
                        + "Backup nodes run in outbound-only replica mode and do not accept client requests."));
    }

    public synchronized String muteUserBySystem(String username, long seconds) {
        User target = state.getUsers().get(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            return "Unknown account: " + username;
        }
        target.muteForSeconds(seconds);
        ClientSession liveSession = state.findSessionByUsername(target.getUsername());
        if (liveSession != null) {
            try {
                trackedSend("web-admin", liveSession, DeliveryChannel.SERVER_TO_USER, "moderation-action",
                        Packet.of(MessageType.MODERATION_ACTION)
                                .with("message", "You have been muted by the web administration panel for " + seconds + " seconds."),
                        "web admin mute notice");
            } catch (IOException ignored) {
                // Best effort admin notification.
            }
        }
        audit("System web admin muted " + username + " for " + seconds + " seconds");
        return "Muted " + target.getDisplayName() + ".";
    }

    public synchronized String banUserBySystem(String username) {
        User target = state.getUsers().get(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            return "Unknown account: " + username;
        }
        target.setBanned(true);
        ClientSession liveSession = state.findSessionByUsername(target.getUsername());
        if (liveSession != null) {
            try {
                trackedSend("web-admin", liveSession, DeliveryChannel.SERVER_TO_USER, "moderation-action",
                        Packet.of(MessageType.MODERATION_ACTION)
                                .with("message", "Your account has been banned by the web administration panel."),
                        "web admin ban notice");
            } catch (IOException ignored) {
                // Best effort admin notification.
            }
            disconnectSession(liveSession, "Banned by web administration.");
        }
        audit("System web admin banned " + username);
        return "Banned " + target.getDisplayName() + ".";
    }

    public synchronized String kickUserBySystem(String username) {
        ClientSession target = state.findSessionByUsername(TextUtils.normalizeIdentifier(username));
        if (target == null) {
            return "User not online: " + username;
        }
        try {
            trackedSend("web-admin", target, DeliveryChannel.SERVER_TO_USER, "moderation-action",
                    Packet.of(MessageType.MODERATION_ACTION)
                            .with("message", "You were kicked by the web administration panel."),
                    "web admin kick notice");
        } catch (IOException ignored) {
            // Best effort admin notification.
        }
        disconnectSession(target, "Kicked by web administration.");
        audit("System web admin kicked " + username);
        return "Kicked " + username + ".";
    }

    public synchronized String broadcastAnnouncement(String message) {
        String cleaned = TextUtils.normalizeMessage(message);
        if (cleaned.isBlank()) {
            return "Announcement message was empty.";
        }
        try {
            for (ClientSession session : state.getSessions().values()) {
                trackedSend("web-admin", session, DeliveryChannel.SERVER_TO_USER, "announcement",
                        Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "[Announcement] " + cleaned),
                        "broadcast announcement");
            }
            state.getExternalPlatformHub().pushAnnouncement("NetChat Announcement", cleaned);
            state.getExternalPlatformHub().onArchiveRecord("announcement", "web-admin", cleaned);
            audit("System announcement sent");
            return "Announcement sent.";
        } catch (IOException exception) {
            return "Announcement failed: " + exception.getMessage();
        }
    }

    public synchronized String createSystemSnapshot() {
        state.getPersistenceService().createFullSnapshot(state, state.getBackupReplicationService());
        audit("System snapshot requested from web admin");
        return "Encrypted snapshot created and replicated.";
    }

    public void broadcastSystem(String roomId, String content) throws IOException {
        broadcastToRoom(roomId, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "[System] " + content), "primary-server", "system room broadcast");
    }

    public void initiateCall(ClientSession caller, String targetUsername, MediaMode mediaMode) throws IOException {
        ClientSession callee = state.findSessionByUsername(TextUtils.normalizeIdentifier(targetUsername));
        if (callee == null) {
            caller.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "User not online: " + targetUsername));
            return;
        }
        if (caller.getMediaAudioPort() <= 0 || callee.getMediaAudioPort() <= 0) {
            caller.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE)
                    .with("message", "Both clients must register realtime media ports before calling."));
            return;
        }
        if (findCallByUser(caller.getUsername()) != null || findCallByUser(callee.getUsername()) != null) {
            caller.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE)
                    .with("message", "One of the users is already in another call."));
            return;
        }

        RealtimeCallSession callSession = new RealtimeCallSession(UUID.randomUUID().toString(), caller.getUsername(), callee.getUsername(), mediaMode);
        state.getCallSessions().put(callSession.getCallId(), callSession);

        trackedSend(caller.getUsername(), callee, DeliveryChannel.USER_MEDIA_SIGNAL, "call-invite",
                Packet.of(MessageType.MEDIA_SIGNAL)
                        .with("action", "invite")
                        .with("callId", callSession.getCallId())
                        .with("from", caller.getUsername())
                        .with("displayName", caller.getUser().getDisplayName())
                        .with("mode", mediaMode.name())
                        .with("message", caller.getUser().getDisplayName() + " invited you to a " + describeMode(mediaMode) + ". Use /accept " + caller.getUsername() + " or /decline " + caller.getUsername() + "."),
                "call invite to " + callee.getUsername());

        trackedSend("primary-server", caller, DeliveryChannel.SERVER_TO_USER, "call-state",
                Packet.of(MessageType.MEDIA_SIGNAL)
                        .with("action", "ringing")
                        .with("callId", callSession.getCallId())
                        .with("message", "Call invitation sent to " + callee.getUser().getDisplayName() + "."),
                "call ringing update");
        state.getExternalPlatformHub().onExternalVoiceBridge(caller.getUsername(), callee.getUsername(), "https://example.invalid/twiml");
        audit("Call invite from " + caller.getUsername() + " to " + callee.getUsername() + " mode=" + mediaMode);
    }

    public void acceptCall(ClientSession callee, String callerUsername) throws IOException {
        RealtimeCallSession session = findPendingCall(callee.getUsername(), TextUtils.normalizeIdentifier(callerUsername));
        if (session == null) {
            callee.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "No pending call from " + callerUsername + "."));
            return;
        }
        ClientSession caller = state.findSessionByUsername(session.getCallerUsername());
        if (caller == null) {
            state.getCallSessions().remove(session.getCallId());
            callee.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "Caller is no longer online."));
            return;
        }

        session.setAccepted(true);
        trackedSend("primary-server", caller, DeliveryChannel.USER_MEDIA_SIGNAL, "call-start",
                buildCallStartPacket("start", session, callee, caller),
                "call start for caller");
        trackedSend("primary-server", callee, DeliveryChannel.USER_MEDIA_SIGNAL, "call-start",
                buildCallStartPacket("start", session, caller, callee),
                "call start for callee");
        audit("Call accepted between " + caller.getUsername() + " and " + callee.getUsername());
    }

    public void declineCall(ClientSession callee, String callerUsername) throws IOException {
        RealtimeCallSession session = findPendingCall(callee.getUsername(), TextUtils.normalizeIdentifier(callerUsername));
        if (session == null) {
            callee.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "No pending call from " + callerUsername + "."));
            return;
        }
        ClientSession caller = state.findSessionByUsername(session.getCallerUsername());
        if (caller != null) {
            trackedSend(callee.getUsername(), caller, DeliveryChannel.USER_MEDIA_SIGNAL, "call-ended",
                    Packet.of(MessageType.MEDIA_SIGNAL)
                            .with("action", "ended")
                            .with("message", callee.getUser().getDisplayName() + " declined your call."),
                    "call declined notice");
        }
        state.getCallSessions().remove(session.getCallId());
        trackedSend("primary-server", callee, DeliveryChannel.SERVER_TO_USER, "call-ended",
                Packet.of(MessageType.MEDIA_SIGNAL).with("action", "ended").with("message", "Call declined."),
                "local call decline confirmation");
        audit("Call declined between " + callerUsername + " and " + callee.getUsername());
    }

    public void hangupCall(ClientSession source) throws IOException {
        RealtimeCallSession session = findCallByUser(source.getUsername());
        if (session == null) {
            source.send(codec, Packet.of(MessageType.SYSTEM_MESSAGE).with("message", "You are not in an active or pending call."));
            return;
        }
        ClientSession other = state.findSessionByUsername(session.otherParty(source.getUsername()));
        if (other != null) {
            trackedSend(source.getUsername(), other, DeliveryChannel.USER_MEDIA_SIGNAL, "call-ended",
                    Packet.of(MessageType.MEDIA_SIGNAL)
                            .with("action", "ended")
                            .with("message", source.getUser().getDisplayName() + " ended the call."),
                    "remote hangup notice");
        }
        trackedSend("primary-server", source, DeliveryChannel.SERVER_TO_USER, "call-ended",
                Packet.of(MessageType.MEDIA_SIGNAL).with("action", "ended").with("message", "Call ended."),
                "local hangup confirmation");
        state.getCallSessions().remove(session.getCallId());
        audit("Call ended by " + source.getUsername());
    }

    private void broadcastToRoom(String roomId, Packet packet, String sourceNode, String payloadSummary) throws IOException {
        ChatRoom room = state.getRooms().get(roomId);
        if (room == null) {
            return;
        }

        for (String username : room.getMembers()) {
            ClientSession session = state.findSessionByUsername(username);
            if (session != null) {
                trackedSend(sourceNode, session, DeliveryChannel.USER_TO_ROOM, packet.type().name(), packet, payloadSummary + " -> " + roomId);
            }
        }
    }

    private void disconnectSession(ClientSession session, String reason) {
        if (!session.markClosing()) {
            return;
        }
        try {
            RealtimeCallSession callSession = findCallByUser(session.getUsername());
            if (callSession != null) {
                ClientSession other = state.findSessionByUsername(callSession.otherParty(session.getUsername()));
                if (other != null) {
                    trackedSend("primary-server", other, DeliveryChannel.USER_MEDIA_SIGNAL, "call-ended",
                            Packet.of(MessageType.MEDIA_SIGNAL)
                                    .with("action", "ended")
                                    .with("message", session.getUser().getDisplayName() + " disconnected. The call was closed."),
                            "call closed due to disconnect");
                }
                state.getCallSessions().remove(callSession.getCallId());
            }
            if (session.getUsername() != null) {
                ChatRoom room = state.getRooms().get(session.getCurrentRoom());
                if (room != null) {
                    room.removeMember(session.getUsername());
                }

                User user = state.getUsers().get(session.getUsername());
                if (user != null) {
                    user.setOnline(false);
                    state.getExternalPlatformHub().onUserPresence(user.getUsername(), false);
                }

                if (room != null) {
                    broadcastSystem(room.getId(), session.getUser().getDisplayName() + " left the room.");
                }
            }
        } catch (IOException ignored) {
            // Best effort notification during shutdown.
        } finally {
            state.getSessions().remove(session.getSessionId());
            session.closeQuietly();
            audit("Session closed: " + session.getSessionId() + " (" + reason + ")");
        }
    }

    private boolean canModerate(ClientSession moderator, ClientSession target) {
        return canModerate(moderator, target.getUser());
    }

    private boolean canModerate(ClientSession moderator, User target) {
        return moderator.getUser().getRole().canModerate(target.getRole()) && !moderator.getUsername().equals(target.getUsername());
    }

    private boolean canBan(ClientSession moderator, User target) {
        return moderator.getUser().getRole() == UserRole.ADMIN
                && target.getRole() != UserRole.ADMIN
                && !moderator.getUsername().equals(target.getUsername());
    }

    private void audit(String entry) {
        state.addAuditEntry(entry);
        System.out.println("[AUDIT] " + entry);
        state.getExternalPlatformHub().onAuditIndexed("audit-" + System.nanoTime(),
                "{\"entry\":\"" + escapeJson(entry) + "\"}");
        state.getPersistenceService().createFullSnapshot(state, state.getBackupReplicationService());
    }

    private Packet buildCallStartPacket(String action, RealtimeCallSession callSession, ClientSession peer, ClientSession recipient) {
        return Packet.of(MessageType.MEDIA_SIGNAL)
                .with("action", action)
                .with("callId", callSession.getCallId())
                .with("mode", callSession.getMediaMode().name())
                .with("peerUsername", peer.getUsername())
                .with("peerDisplayName", peer.getUser().getDisplayName())
                .with("peerAddress", peer.getRemoteAddress())
                .with("audioPort", String.valueOf(peer.getMediaAudioPort()))
                .with("videoPort", String.valueOf(peer.getMediaVideoPort()))
                .with("message", "Realtime " + describeMode(callSession.getMediaMode()) + " started with " + peer.getUser().getDisplayName() + ".");
    }

    private RealtimeCallSession findPendingCall(String calleeUsername, String callerUsername) {
        return state.getCallSessions().values().stream()
                .filter(session -> !session.isAccepted())
                .filter(session -> session.getCalleeUsername().equals(calleeUsername) && session.getCallerUsername().equals(callerUsername))
                .findFirst()
                .orElse(null);
    }

    private RealtimeCallSession findCallByUser(String username) {
        if (username == null) {
            return null;
        }
        return state.getCallSessions().values().stream()
                .filter(session -> session.involves(username))
                .findFirst()
                .orElse(null);
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String describeMode(MediaMode mediaMode) {
        return mediaMode == MediaMode.AUDIO_VIDEO ? "voice and live video session" : "voice call";
    }

    private void trackedSend(String sourceNode, ClientSession target, DeliveryChannel channel, String payloadType, Packet packet, String payloadSummary) throws IOException {
        DeliveryRecord record = state.getTransmissionService().queue(
                channel,
                sourceNode,
                target.getUsername() == null ? target.getSessionId() : target.getUsername(),
                payloadType,
                payloadSummary
        );
        try {
            target.send(codec, packet);
            state.getTransmissionService().markDelivered(record);
        } catch (IOException exception) {
            state.getTransmissionService().markFailed(record, exception.getMessage());
            throw exception;
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

