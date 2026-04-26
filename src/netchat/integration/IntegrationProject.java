/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integration;

public class IntegrationProject {
    private final String projectId;
    private final String projectName;
    private final String apiKeyHash;
    private final boolean readOnly;

    public IntegrationProject(String projectId, String projectName, String apiKeyHash, boolean readOnly) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.apiKeyHash = apiKeyHash;
        this.readOnly = readOnly;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}

