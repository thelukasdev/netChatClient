/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.model;

public class User {
    private static final int MAX_FAILED_LOGINS = 3;
    private static final long LOGIN_LOCK_MILLIS = 60_000L;

    private final String username;
    private final String passwordHash;
    private final String salt;
    private final UserRole role;
    private String displayName;
    private boolean online;
    private boolean banned;
    private long mutedUntil;
    private int failedLoginAttempts;
    private long lockedUntil;

    public User(String username, String displayName, String passwordHash, String salt, UserRole role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public void muteForSeconds(long seconds) {
        mutedUntil = System.currentTimeMillis() + (seconds * 1000L);
    }

    public boolean isMuted() {
        return System.currentTimeMillis() < mutedUntil;
    }

    public long remainingMuteSeconds() {
        return Math.max(0L, (mutedUntil - System.currentTimeMillis()) / 1000L);
    }

    public void registerFailedLogin() {
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_LOGINS) {
            lockedUntil = System.currentTimeMillis() + LOGIN_LOCK_MILLIS;
            failedLoginAttempts = 0;
        }
    }

    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = 0L;
    }

    public boolean isLocked() {
        return System.currentTimeMillis() < lockedUntil;
    }

    public long remainingLockSeconds() {
        return Math.max(0L, (lockedUntil - System.currentTimeMillis()) / 1000L);
    }
}

