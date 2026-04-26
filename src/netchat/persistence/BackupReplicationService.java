/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.persistence;

import netchat.shared.security.CryptoUtils;
import netchat.transmission.TransmissionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BackupReplicationService {
    private final List<BackupNode> backupNodes = new ArrayList<>();
    private final String secret;
    private TransmissionService transmissionService;

    public BackupReplicationService(String secret) {
        this.secret = secret;
    }

    public void registerNode(BackupNode node) {
        backupNodes.add(node);
    }

    public void attachTransmissionService(TransmissionService transmissionService) {
        this.transmissionService = transmissionService;
    }

    public void replicateSnapshot(String snapshotName, String content) {
        for (BackupNode node : backupNodes) {
            if (node.acceptsClientTraffic()) {
                continue;
            }
            try {
                Files.createDirectories(node.getBaseDirectory());
                String encrypted = CryptoUtils.encrypt(content, secret);
                Path target = node.getBaseDirectory().resolve(snapshotName + ".replica");
                Files.writeString(target, encrypted, StandardCharsets.UTF_8);
                Files.writeString(node.getBaseDirectory().resolve("replica.audit"),
                        LocalDateTime.now() + " SYNCHRONIZED " + snapshotName + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        Files.exists(node.getBaseDirectory().resolve("replica.audit"))
                                ? java.nio.file.StandardOpenOption.APPEND
                                : java.nio.file.StandardOpenOption.CREATE);
                if (transmissionService != null) {
                    transmissionService.archiveReplicaSync("primary-server", node.getNodeId(), snapshotName);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Backup replication failed for node " + node.getNodeId(), exception);
            }
        }
    }
}

