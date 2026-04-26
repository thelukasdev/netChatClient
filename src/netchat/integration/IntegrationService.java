/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integration;

import netchat.persistence.PersistenceService;
import netchat.shared.security.PasswordUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IntegrationService {
    private final Map<String, IntegrationProject> projects = new ConcurrentHashMap<>();
    private final PersistenceService persistenceService;

    public IntegrationService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        seedDefaultProjects();
    }

    public void registerProject(String projectId, String projectName, String plainApiKey, boolean readOnly) {
        String salt = PasswordUtils.generateSalt();
        String apiKeyHash = PasswordUtils.hashPassword(plainApiKey, salt) + ":" + salt;
        IntegrationProject project = new IntegrationProject(projectId, projectName, apiKeyHash, readOnly);
        projects.put(projectId, project);
        persistenceService.appendIntegrationRecord("REGISTER;" + projectId + ";" + projectName + ";" + readOnly);
    }

    public boolean authenticate(String projectId, String plainApiKey) {
        IntegrationProject project = projects.get(projectId);
        if (project == null) {
            return false;
        }
        String[] parts = project.getApiKeyHash().split(":");
        return parts.length == 2 && PasswordUtils.matches(plainApiKey, parts[0], parts[1]);
    }

    public void publishEvent(String projectId, String eventType, String payload) {
        IntegrationEvent event = new IntegrationEvent(eventType, projectId, payload, LocalDateTime.now());
        persistenceService.appendIntegrationRecord("EVENT;" + event.serialize());
    }

    private void seedDefaultProjects() {
        registerProject("analytics-core", "Analytics Core", "Analytics-Key-2026", true);
        registerProject("school-panel", "School Panel", "School-Panel-2026", false);
    }
}

