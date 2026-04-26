/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.server;

import netchat.integrations.ExternalPlatformHub;
import netchat.integration.IntegrationService;
import netchat.persistence.BackupNode;
import netchat.persistence.BackupReplicationService;
import netchat.persistence.PersistenceService;
import netchat.security.ProxyRoutingService;
import netchat.security.VpnDetectionService;
import netchat.shared.model.ChatRoom;
import netchat.shared.model.User;
import netchat.shared.model.UserRole;
import netchat.shared.security.PasswordUtils;
import netchat.transmission.TransmissionService;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, RealtimeCallSession> callSessions = new ConcurrentHashMap<>();
    private final Set<String> blockedWords = ConcurrentHashMap.newKeySet();
    private final Deque<String> auditTrail = new ArrayDeque<>();
    private final PersistenceService persistenceService;
    private final BackupReplicationService backupReplicationService;
    private final ProxyRoutingService proxyRoutingService;
    private final VpnDetectionService vpnDetectionService;
    private final IntegrationService integrationService;
    private final TransmissionService transmissionService;
    private final ExternalPlatformHub externalPlatformHub;

    public ServerState(String secret) {
        this.persistenceService = new PersistenceService(Path.of("storage", "primary"), secret);
        this.backupReplicationService = new BackupReplicationService(secret);
        this.proxyRoutingService = new ProxyRoutingService();
        this.vpnDetectionService = new VpnDetectionService();
        this.integrationService = new IntegrationService(persistenceService);
        this.transmissionService = new TransmissionService(persistenceService);
        this.backupReplicationService.attachTransmissionService(transmissionService);
        this.externalPlatformHub = new ExternalPlatformHub();
        this.persistenceService.setCloudArchivePublisher(externalPlatformHub.getCloudArchivePublisher());

        backupReplicationService.registerNode(new BackupNode("backup-eu-secure", Path.of("storage", "backup-eu-secure"), false));
        backupReplicationService.registerNode(new BackupNode("backup-offsite-cold", Path.of("storage", "backup-offsite-cold"), false));

        rooms.put("general", new ChatRoom("general", "General", "Open public chat for everyone."));
        rooms.put("study", new ChatRoom("study", "Study", "Focused room for homework, Informatik and revision."));
        rooms.put("support", new ChatRoom("support", "Support", "Help desk for technical questions and moderation."));

        blockedWords.add("malware");
        blockedWords.add("phishing");
        blockedWords.add("ddos");

        seedAdminAccount();
    }

    public Map<String, User> getUsers() {
        return users;
    }

    public Map<String, ClientSession> getSessions() {
        return sessions;
    }

    public Map<String, ChatRoom> getRooms() {
        return rooms;
    }

    public Map<String, RealtimeCallSession> getCallSessions() {
        return callSessions;
    }

    public Set<String> getBlockedWords() {
        return blockedWords;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public BackupReplicationService getBackupReplicationService() {
        return backupReplicationService;
    }

    public ProxyRoutingService getProxyRoutingService() {
        return proxyRoutingService;
    }

    public VpnDetectionService getVpnDetectionService() {
        return vpnDetectionService;
    }

    public IntegrationService getIntegrationService() {
        return integrationService;
    }

    public TransmissionService getTransmissionService() {
        return transmissionService;
    }

    public ExternalPlatformHub getExternalPlatformHub() {
        return externalPlatformHub;
    }

    public ClientSession findSessionByUsername(String username) {
        return sessions.values().stream()
                .filter(session -> username.equals(session.getUsername()))
                .findFirst()
                .orElse(null);
    }

    public synchronized void addAuditEntry(String entry) {
        auditTrail.addLast(entry);
        while (auditTrail.size() > 100) {
            auditTrail.removeFirst();
        }
        persistenceService.appendAuditRecord(entry);
    }

    public synchronized Deque<String> getAuditTrail() {
        return new ArrayDeque<>(auditTrail);
    }

    private void seedAdminAccount() {
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("Admin1234", salt);
        User admin = new User("admin", "Administrator", hash, salt, UserRole.ADMIN);
        users.put("admin", admin);
        persistenceService.appendUserRecord(admin);
    }
}

