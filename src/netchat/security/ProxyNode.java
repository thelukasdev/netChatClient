/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.security;

public class ProxyNode {
    private final String nodeId;
    private final String host;
    private final int port;
    private final int priority;
    private boolean online = true;

    public ProxyNode(String nodeId, String host, int port, int priority) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.priority = priority;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}

