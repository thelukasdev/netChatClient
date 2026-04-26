/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private static final int MAX_HISTORY = 25;

    private final String id;
    private final String displayName;
    private final String description;
    private final Set<String> members = ConcurrentHashMap.newKeySet();
    private final List<Message> history = new ArrayList<>();

    public ChatRoom(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public synchronized void addMessage(Message message) {
        history.add(message);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public void addMember(String username) {
        members.add(username);
    }

    public void removeMember(String username) {
        members.remove(username);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getMembers() {
        return members;
    }

    public synchronized List<Message> getHistory() {
        return List.copyOf(history);
    }
}

