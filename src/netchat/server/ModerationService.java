/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.shared.media.MediaMode;
import netchat.shared.model.UserRole;
import netchat.shared.util.TextUtils;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationService {
    private static final int MAX_MESSAGES_IN_WINDOW = 5;
    private static final long WINDOW_MILLIS = 10_000L;
    private final ServerState state;
    private final Map<String, Deque<Long>> messageTimestamps = new ConcurrentHashMap<>();

    public ModerationService(ServerState state) {
        this.state = state;
    }

    public String validateMessage(ClientSession session, String content) {
        if (content.isBlank()) {
            return "Empty messages are not allowed.";
        }
        if (session.getUser().isMuted()) {
            return "You are muted for another " + session.getUser().remainingMuteSeconds() + " seconds.";
        }
        if (containsBlockedWord(content)) {
            return "Message blocked by the safety filter.";
        }
        if (state.getExternalPlatformHub().shouldBlockContent(content)) {
            return "Message blocked by external AI moderation.";
        }
        if (isRateLimited(session.getUsername())) {
            return "You are sending messages too fast. Please slow down.";
        }
        return null;
    }

    public void executeCommand(ClientSession session, String rawCommand, ChatServer server) throws IOException {
        if (!rawCommand.startsWith("/")) {
            server.sendHelp(session);
            return;
        }

        String[] parts = rawCommand.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/help" -> server.sendHelp(session);
            case "/rooms" -> server.sendRoomList(session);
            case "/join" -> {
                if (parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.joinRoom(session, parts[1]);
            }
            case "/users" -> server.sendUserList(session);
            case "/history" -> server.sendHistory(session);
            case "/storage" -> server.sendStorageStatus(session);
            case "/call" -> {
                if (parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.initiateCall(session, parts[1], MediaMode.AUDIO_ONLY);
            }
            case "/videocall" -> {
                if (parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.initiateCall(session, parts[1], MediaMode.AUDIO_VIDEO);
            }
            case "/accept" -> {
                if (parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.acceptCall(session, parts[1]);
            }
            case "/decline" -> {
                if (parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.declineCall(session, parts[1]);
            }
            case "/hangup" -> server.hangupCall(session);
            case "/w" -> {
                String payload = rawCommand.length() <= 3 ? "" : rawCommand.substring(3).trim();
                String[] dmParts = payload.split("\\s+", 2);
                if (dmParts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.sendPrivateMessage(session, dmParts[0], TextUtils.normalizeMessage(dmParts[1]));
            }
            case "/kick" -> {
                if (!session.getUser().getRole().canModerate(UserRole.USER) || parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.kickUser(session, parts[1]);
            }
            case "/mute" -> {
                if (!session.getUser().getRole().canModerate(UserRole.USER) || parts.length < 3) {
                    server.sendHelp(session);
                    return;
                }
                long seconds = parsePositiveLong(parts[2]);
                server.muteUser(session, parts[1], seconds);
            }
            case "/ban" -> {
                if (session.getUser().getRole() != UserRole.ADMIN || parts.length < 2) {
                    server.sendHelp(session);
                    return;
                }
                server.banUser(session, parts[1]);
            }
            default -> server.sendHelp(session);
        }
    }

    private boolean containsBlockedWord(String content) {
        String lower = content.toLowerCase();
        for (String blockedWord : state.getBlockedWords()) {
            if (lower.contains(blockedWord)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRateLimited(String username) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = messageTimestamps.computeIfAbsent(username, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_MESSAGES_IN_WINDOW) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    private long parsePositiveLong(String rawValue) {
        try {
            return Math.max(1L, Long.parseLong(rawValue));
        } catch (NumberFormatException exception) {
            return 60L;
        }
    }
}

