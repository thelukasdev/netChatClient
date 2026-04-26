/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integration;

import java.time.LocalDateTime;

public class IntegrationEvent {
    private final String eventType;
    private final String sourceSystem;
    private final String payload;
    private final LocalDateTime createdAt;

    public IntegrationEvent(String eventType, String sourceSystem, String payload, LocalDateTime createdAt) {
        this.eventType = eventType;
        this.sourceSystem = sourceSystem;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public String serialize() {
        return eventType + ";" + sourceSystem + ";" + createdAt + ";" + payload.replace("\n", "\\n");
    }

    public String getEventType() {
        return eventType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

