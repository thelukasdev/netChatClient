/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.shared.model.User;
import netchat.shared.model.UserRole;
import netchat.shared.protocol.MessageType;
import netchat.shared.protocol.Packet;
import netchat.shared.security.PasswordUtils;
import netchat.shared.util.TextUtils;

public class AuthService {
    private final ServerState state;

    public AuthService(ServerState state) {
        this.state = state;
    }

    public Packet register(ClientSession session, Packet packet) {
        String username = TextUtils.normalizeIdentifier(packet.getOrDefault("username", ""));
        String displayName = TextUtils.normalizeDisplayName(packet.getOrDefault("displayName", username));
        String password = packet.getOrDefault("password", "");

        if (!TextUtils.isValidUsername(username)) {
            return authFailure("Username must be 3-16 characters and may contain letters, digits, _ plus Ã¤ Ã¶ Ã¼.");
        }
        if (!TextUtils.isStrongPassword(password)) {
            return authFailure("Password must have at least 8 characters, one digit and one letter.");
        }
        if (state.getUsers().containsKey(username)) {
            return authFailure("This username is already registered.");
        }

        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        UserRole role = state.getUsers().isEmpty() ? UserRole.ADMIN : UserRole.USER;
        User user = new User(username, displayName, hash, salt, role);
        user.setOnline(true);
        state.getUsers().put(username, user);
        state.getPersistenceService().appendUserRecord(user);
        state.getIntegrationService().publishEvent("school-panel", "user_registered", username + ";" + displayName);
        state.getExternalPlatformHub().onArchiveRecord("user_registered", username, displayName);

        session.attachUser(user);
        return authSuccess(user, "Registration successful. Welcome to NetChat, " + user.getDisplayName() + ".");
    }

    public Packet login(ClientSession session, Packet packet) {
        String username = TextUtils.normalizeIdentifier(packet.getOrDefault("username", ""));
        String password = packet.getOrDefault("password", "");
        User user = state.getUsers().get(username);

        if (user == null) {
            return authFailure("Unknown account.");
        }
        if (user.isBanned()) {
            return authFailure("This account has been banned by an administrator.");
        }
        if (user.isLocked()) {
            return authFailure("Too many failed logins. Try again in " + user.remainingLockSeconds() + " seconds.");
        }
        if (!PasswordUtils.matches(password, user.getPasswordHash(), user.getSalt())) {
            user.registerFailedLogin();
            return authFailure("Invalid password.");
        }

        user.clearFailedLogins();
        user.setOnline(true);
        if (TextUtils.isNotBlank(packet.getOrDefault("displayName", ""))) {
            user.setDisplayName(TextUtils.normalizeDisplayName(packet.getOrDefault("displayName", user.getDisplayName())));
        }
        state.getPersistenceService().appendUserRecord(user);
        state.getIntegrationService().publishEvent("analytics-core", "user_login", username);
        state.getExternalPlatformHub().onUserPresence(username, true);
        session.attachUser(user);
        return authSuccess(user, "Login successful. Welcome back, " + user.getDisplayName() + ".");
    }

    private Packet authSuccess(User user, String message) {
        return Packet.of(MessageType.AUTH_RESPONSE)
                .with("success", "true")
                .with("username", user.getUsername())
                .with("displayName", user.getDisplayName())
                .with("roomId", "general")
                .with("message", message);
    }

    private Packet authFailure(String message) {
        return Packet.of(MessageType.AUTH_RESPONSE)
                .with("success", "false")
                .with("message", message);
    }
}

