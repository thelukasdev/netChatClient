/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.client;

import netchat.shared.media.MediaMode;
import netchat.shared.protocol.MessageType;
import netchat.shared.protocol.Packet;
import netchat.shared.protocol.PacketCodec;
import netchat.shared.util.TextUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ChatClient {
    private final String host;
    private final int port;
    private final PacketCodec codec;
    private final ClientState state;
    private final Scanner console;
    private final ClientMediaController mediaController;

    public ChatClient(String host, int port, String secret) {
        this.host = host;
        this.port = port;
        this.codec = new PacketCodec(secret);
        this.state = new ClientState();
        this.console = new Scanner(System.in);
        this.mediaController = new ClientMediaController();
    }

    public void start() {
        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            state.setConnected(true);
            Thread receiver = new Thread(() -> receiveLoop(reader), "netchat-client-receiver");
            receiver.setDaemon(true);
            receiver.start();

            runAuthentication(writer);
            runInputLoop(writer);
        } catch (IOException exception) {
            System.out.println("Connection error: " + exception.getMessage());
        } finally {
            state.setConnected(false);
        }
    }

    private void runAuthentication(BufferedWriter writer) throws IOException {
        while (!state.isAuthenticated()) {
            System.out.println();
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.print("Choose authentication action: ");
            String action = console.nextLine().trim();

            System.out.print("Username: ");
            String username = console.nextLine().trim();
            System.out.print("Display name: ");
            String displayName = console.nextLine().trim();
            System.out.print("Password: ");
            String password = console.nextLine();

            Packet packet = Packet.of("1".equals(action) ? MessageType.REGISTER_REQUEST : MessageType.LOGIN_REQUEST)
                    .with("username", username)
                    .with("displayName", displayName.isBlank() ? username : displayName)
                    .with("password", password);
            send(packet, writer);

            waitBriefly();
        }
        send(Packet.of(MessageType.MEDIA_SIGNAL)
                .with("action", "register")
                .with("audioPort", String.valueOf(mediaController.getLocalAudioPort()))
                .with("videoPort", String.valueOf(mediaController.getLocalVideoPort())), writer);
    }

    private void runInputLoop(BufferedWriter writer) throws IOException {
        printHelp();

        while (state.isConnected()) {
            String input = console.nextLine();
            if (input == null) {
                break;
            }

            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if ("/quit".equalsIgnoreCase(trimmed)) {
                mediaController.stopCall();
                send(Packet.of(MessageType.LOGOUT), writer);
                break;
            }

            if ("/voice on".equalsIgnoreCase(trimmed)) {
                mediaController.setVoiceEnabled(true);
                continue;
            }
            if ("/voice off".equalsIgnoreCase(trimmed)) {
                mediaController.setVoiceEnabled(false);
                continue;
            }
            if ("/video on".equalsIgnoreCase(trimmed)) {
                mediaController.setVideoEnabled(true);
                continue;
            }
            if ("/video off".equalsIgnoreCase(trimmed)) {
                mediaController.setVideoEnabled(false);
                continue;
            }

            if (trimmed.startsWith("/")) {
                send(Packet.of(MessageType.COMMAND).with("command", trimmed), writer);
            } else {
                send(Packet.of(MessageType.CHAT_MESSAGE)
                        .with("roomId", state.getCurrentRoom())
                        .with("content", TextUtils.normalizeMessage(trimmed)), writer);
            }
        }
    }

    private void receiveLoop(BufferedReader reader) {
        try {
            String encryptedLine;
            while ((encryptedLine = reader.readLine()) != null) {
                Packet packet = codec.decode(encryptedLine);
                handleServerPacket(packet);
            }
        } catch (Exception exception) {
            if (state.isConnected()) {
                System.out.println("Disconnected: " + exception.getMessage());
            }
        } finally {
            state.setConnected(false);
        }
    }

    private void handleServerPacket(Packet packet) {
        switch (packet.type()) {
            case AUTH_RESPONSE -> {
                boolean success = Boolean.parseBoolean(packet.getOrDefault("success", "false"));
                System.out.println(packet.getOrDefault("message", "Authentication response received."));
                if (success) {
                    state.setAuthenticated(true);
                    state.setUsername(packet.getOrDefault("username", ""));
                    state.setDisplayName(packet.getOrDefault("displayName", ""));
                    state.setCurrentRoom(packet.getOrDefault("roomId", "general"));
                }
            }
            case CHAT_MESSAGE, PRIVATE_MESSAGE, SYSTEM_MESSAGE, MODERATION_ACTION -> {
                System.out.println(packet.getOrDefault("message", ""));
            }
            case MEDIA_SIGNAL -> handleMediaSignal(packet);
            case ROOM_JOIN -> {
                state.setCurrentRoom(packet.getOrDefault("roomId", state.getCurrentRoom()));
                System.out.println(packet.getOrDefault("message", ""));
            }
            case ROOM_LIST, USER_LIST, HISTORY_RESPONSE -> System.out.println(packet.getOrDefault("message", ""));
            default -> System.out.println("Server message: " + packet.getOrDefault("message", packet.type().name()));
        }
    }

    private void handleMediaSignal(Packet packet) {
        String action = packet.getOrDefault("action", "");
        String message = packet.getOrDefault("message", "");
        if (!message.isBlank()) {
            System.out.println(message);
        }

        switch (action) {
            case "invite" -> state.setActiveCallPeer(packet.getOrDefault("from", ""));
            case "start" -> {
                String peerAddress = packet.getOrDefault("peerAddress", "");
                int audioPort = parseInt(packet.getOrDefault("audioPort", "0"));
                int videoPort = parseInt(packet.getOrDefault("videoPort", "0"));
                MediaMode mode = MediaMode.valueOf(packet.getOrDefault("mode", MediaMode.AUDIO_ONLY.name()));
                String peerUsername = packet.getOrDefault("peerUsername", "");
                state.setActiveCallPeer(peerUsername);
                mediaController.startCall(peerAddress, audioPort, videoPort, mode, peerUsername);
            }
            case "ended" -> {
                state.setActiveCallPeer("");
                mediaController.stopCall();
            }
            default -> {
            }
        }
    }

    private void send(Packet packet, BufferedWriter writer) throws IOException {
        writer.write(codec.encode(packet));
        writer.newLine();
        writer.flush();
    }

    private void waitBriefly() {
        try {
            Thread.sleep(250L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void printHelp() {
        System.out.println();
        System.out.println("Commands:");
        System.out.println("/help");
        System.out.println("/rooms");
        System.out.println("/join <roomId>");
        System.out.println("/users");
        System.out.println("/history");
        System.out.println("/w <username> <message>");
        System.out.println("/call <username>");
        System.out.println("/videocall <username>");
        System.out.println("/accept <username>");
        System.out.println("/decline <username>");
        System.out.println("/hangup");
        System.out.println("/voice on|off");
        System.out.println("/video on|off");
        System.out.println("/kick <username>      (moderator/admin)");
        System.out.println("/mute <username> <seconds>  (moderator/admin)");
        System.out.println("/ban <username>       (admin)");
        System.out.println("/quit");
        System.out.println();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}

