/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.shared.media.MediaMode;

public class RealtimeCallSession {
    private final String callId;
    private final String callerUsername;
    private final String calleeUsername;
    private final MediaMode mediaMode;
    private boolean accepted;

    public RealtimeCallSession(String callId, String callerUsername, String calleeUsername, MediaMode mediaMode) {
        this.callId = callId;
        this.callerUsername = callerUsername;
        this.calleeUsername = calleeUsername;
        this.mediaMode = mediaMode;
    }

    public String getCallId() {
        return callId;
    }

    public String getCallerUsername() {
        return callerUsername;
    }

    public String getCalleeUsername() {
        return calleeUsername;
    }

    public MediaMode getMediaMode() {
        return mediaMode;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean involves(String username) {
        return callerUsername.equals(username) || calleeUsername.equals(username);
    }

    public String otherParty(String username) {
        return callerUsername.equals(username) ? calleeUsername : callerUsername;
    }
}

