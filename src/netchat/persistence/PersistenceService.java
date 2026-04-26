/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.persistence;

import netchat.integrations.CloudArchivePublisher;
import netchat.server.ServerState;
import netchat.shared.model.ChatRoom;
import netchat.shared.model.Message;
import netchat.shared.model.User;
import netchat.shared.security.CryptoUtils;
import netchat.transmission.DeliveryRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class PersistenceService {
    private final Path root;
    private final Path usersFile;
    private final Path messageFile;
    private final Path auditFile;
    private final Path integrationFile;
    private final Path networkFile;
    private final Path transferFile;
    private final Path snapshotDirectory;
    private final Path cloudArchiveDirectory;
    private final String secret;
    private CloudArchivePublisher cloudArchivePublisher;

    public PersistenceService(Path root, String secret) {
        this.root = root;
        this.usersFile = root.resolve("users.db");
        this.messageFile = root.resolve("messages.db");
        this.auditFile = root.resolve("audit.db");
        this.integrationFile = root.resolve("integrations.db");
        this.networkFile = root.resolve("network.db");
        this.transferFile = root.resolve("transfers.db");
        this.snapshotDirectory = root.resolve("snapshots");
        this.cloudArchiveDirectory = root.resolve("cloud-archive");
        this.secret = secret;
        initializeDirectories();
    }

    public void appendUserRecord(User user) {
        append(usersFile, "USER;" + user.getUsername() + ";" + user.getDisplayName() + ";" + user.getRole() + ";"
                + user.isOnline() + ";" + user.isBanned() + ";" + user.getPasswordHash() + ";" + user.getSalt());
    }

    public void appendMessageRecord(Message message) {
        append(messageFile, "MESSAGE;" + message.getSender() + ";" + nullSafe(message.getRoomId()) + ";"
                + nullSafe(message.getRecipient()) + ";" + message.getCreatedAt() + ";"
                + message.isSystem() + ";" + sanitize(message.getContent()));
    }

    public void appendAuditRecord(String auditEntry) {
        append(auditFile, LocalDateTime.now() + ";AUDIT;" + sanitize(auditEntry));
    }

    public void appendIntegrationRecord(String record) {
        append(integrationFile, LocalDateTime.now() + ";" + sanitize(record));
    }

    public void appendNetworkRecord(String record) {
        append(networkFile, LocalDateTime.now() + ";" + sanitize(record));
    }

    public void appendTransferRecord(DeliveryRecord record) {
        append(transferFile,
                "TRANSFER;"
                        + record.getTransferId() + ";"
                        + record.getCreatedAt() + ";"
                        + record.getChannel() + ";"
                        + sanitize(record.getSourceNode()) + ";"
                        + sanitize(record.getTargetNode()) + ";"
                        + sanitize(record.getPayloadType()) + ";"
                        + sanitize(record.getPayloadSummary()) + ";"
                        + record.getStatus());
    }

    public void archiveCloudObject(DeliveryRecord record, String lifecycleState) {
        String json = "{"
                + "\"transferId\":\"" + sanitize(record.getTransferId()) + "\","
                + "\"channel\":\"" + record.getChannel() + "\","
                + "\"source\":\"" + sanitize(record.getSourceNode()) + "\","
                + "\"target\":\"" + sanitize(record.getTargetNode()) + "\","
                + "\"payloadType\":\"" + sanitize(record.getPayloadType()) + "\","
                + "\"payloadSummary\":\"" + sanitize(record.getPayloadSummary()) + "\","
                + "\"createdAt\":\"" + record.getCreatedAt() + "\","
                + "\"status\":\"" + record.getStatus() + "\","
                + "\"lifecycle\":\"" + sanitize(lifecycleState) + "\""
                + "}";
        String encrypted = CryptoUtils.encrypt(json, secret);
        try {
            Files.createDirectories(cloudArchiveDirectory);
            String objectName = record.getTransferId() + ".cloud";
            Files.writeString(cloudArchiveDirectory.resolve(objectName), encrypted, StandardCharsets.UTF_8);
            if (cloudArchivePublisher != null) {
                cloudArchivePublisher.publish(objectName, encrypted);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cloud archive export failed.", exception);
        }
    }

    public void setCloudArchivePublisher(CloudArchivePublisher cloudArchivePublisher) {
        this.cloudArchivePublisher = cloudArchivePublisher;
    }

    public void createFullSnapshot(ServerState state, BackupReplicationService replicationService) {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("SNAPSHOT ").append(LocalDateTime.now()).append(System.lineSeparator());
        snapshot.append("[USERS]").append(System.lineSeparator());
        for (User user : state.getUsers().values()) {
            snapshot.append(user.getUsername()).append(";")
                    .append(user.getDisplayName()).append(";")
                    .append(user.getRole()).append(";")
                    .append(user.isOnline()).append(";")
                    .append(user.isBanned()).append(System.lineSeparator());
        }
        snapshot.append("[ROOMS]").append(System.lineSeparator());
        for (ChatRoom room : state.getRooms().values()) {
            snapshot.append(room.getId()).append(";")
                    .append(room.getDisplayName()).append(";")
                    .append(room.getDescription()).append(System.lineSeparator());
            for (Message message : room.getHistory()) {
                snapshot.append("ROOM_MESSAGE;")
                        .append(room.getId()).append(";")
                        .append(message.getSender()).append(";")
                        .append(message.getCreatedAt()).append(";")
                        .append(sanitize(message.getContent())).append(System.lineSeparator());
            }
        }
        snapshot.append("[AUDIT]").append(System.lineSeparator());
        for (String auditEntry : state.getAuditTrail()) {
            snapshot.append(sanitize(auditEntry)).append(System.lineSeparator());
        }

        String fileName = "snapshot-" + System.currentTimeMillis() + ".secure";
        String encrypted = CryptoUtils.encrypt(snapshot.toString(), secret);
        try {
            Files.createDirectories(snapshotDirectory);
            Files.writeString(snapshotDirectory.resolve(fileName), encrypted, StandardCharsets.UTF_8);
            replicationService.replicateSnapshot(fileName, snapshot.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Snapshot creation failed.", exception);
        }
    }

    public String describeStorageLayout() {
        return """
                Persistent layout:
                - users.db for account states and hashed credentials
                - messages.db for room and private message archives
                - audit.db for security and moderation auditing
                - network.db for proxy, VPN and connection risk telemetry
                - integrations.db for bridge registrations and integration events
                - transfers.db for durable user-to-user and server-to-server delivery logs
                - snapshots/ for encrypted full-state backups replicated outbound only
                - cloud-archive/ for encrypted cloud-format transfer objects
                """.trim();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(snapshotDirectory);
            Files.createDirectories(cloudArchiveDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize persistence directories.", exception);
        }
    }

    private void append(Path file, String record) {
        try {
            Files.writeString(file, record + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist record into " + file.getFileName(), exception);
        }
    }

    private String sanitize(String value) {
        return nullSafe(value).replace("\n", "\\n").replace("\r", "");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

