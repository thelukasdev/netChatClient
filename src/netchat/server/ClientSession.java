/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.shared.model.User;
import netchat.shared.protocol.Packet;
import netchat.shared.protocol.PacketCodec;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientSession implements Closeable {
    private final String sessionId;
    private final Socket socket;
    private final BufferedWriter writer;
    private User user;
    private String currentRoom = "general";
    private boolean authenticated;
    private boolean closing;
    private int mediaAudioPort;
    private int mediaVideoPort;

    public ClientSession(String sessionId, Socket socket) throws IOException {
        this.sessionId = sessionId;
        this.socket = socket;
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public synchronized void send(PacketCodec codec, Packet packet) throws IOException {
        writer.write(codec.encode(packet));
        writer.newLine();
        writer.flush();
    }

    public void attachUser(User user) {
        this.user = user;
        this.authenticated = true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Socket getSocket() {
        return socket;
    }

    public User getUser() {
        return user;
    }

    public String getUsername() {
        return user == null ? null : user.getUsername();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public synchronized boolean markClosing() {
        if (closing) {
            return false;
        }
        closing = true;
        return true;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom;
    }

    public String getRemoteAddress() {
        return socket.getInetAddress() == null ? "unknown" : socket.getInetAddress().getHostAddress();
    }

    public String getReverseHost() {
        return socket.getInetAddress() == null ? "unknown" : socket.getInetAddress().getHostName();
    }

    public int getMediaAudioPort() {
        return mediaAudioPort;
    }

    public void setMediaAudioPort(int mediaAudioPort) {
        this.mediaAudioPort = mediaAudioPort;
    }

    public int getMediaVideoPort() {
        return mediaVideoPort;
    }

    public void setMediaVideoPort(int mediaVideoPort) {
        this.mediaVideoPort = mediaVideoPort;
    }

    @Override
    public void close() throws IOException {
        writer.close();
        socket.close();
    }

    public void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // No additional handling required for shutdown.
        }
    }
}

