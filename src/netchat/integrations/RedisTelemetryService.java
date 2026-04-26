/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RedisTelemetryService {
    private final IntegrationConfig config;

    public RedisTelemetryService(IntegrationConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return !config.getRedisUrl().isBlank() && config.getRedisUrl().startsWith("redis://");
    }

    public void publishPresence(String username, boolean online) {
        if (!isEnabled()) {
            return;
        }
        String address = config.getRedisUrl().substring("redis://".length());
        String[] hostParts = address.split(":", 2);
        String host = hostParts[0];
        int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 6379;
        String command = buildSetCommand("presence:" + username, online ? "online" : "offline");
        try (Socket socket = new Socket(host, port); OutputStream output = socket.getOutputStream()) {
            output.write(command.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Redis telemetry write failed.", exception);
        }
    }

    private String buildSetCommand(String key, String value) {
        return "*3\r\n$3\r\nSET\r\n$" + key.length() + "\r\n" + key + "\r\n$" + value.length() + "\r\n" + value + "\r\n";
    }
}
