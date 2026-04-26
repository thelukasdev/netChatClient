/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String sender;
    private final String roomId;
    private final String recipient;
    private final String content;
    private final LocalDateTime createdAt;
    private final boolean system;

    public Message(String sender, String roomId, String recipient, String content, LocalDateTime createdAt, boolean system) {
        this.sender = sender;
        this.roomId = roomId;
        this.recipient = recipient;
        this.content = content;
        this.createdAt = createdAt;
        this.system = system;
    }

    public String getSender() {
        return sender;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isSystem() {
        return system;
    }

    public String toDisplayLine(String displayName) {
        return "[" + FORMATTER.format(createdAt) + "] " + displayName + ": " + content;
    }

    public String toPrivateDisplayLine(String senderDisplayName, String recipientDisplayName, boolean outgoingCopy) {
        String direction = outgoingCopy ? "to " + recipientDisplayName : "from " + senderDisplayName;
        return "[" + FORMATTER.format(createdAt) + "] [Private " + direction + "] " + content;
    }
}

