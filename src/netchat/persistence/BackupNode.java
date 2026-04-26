/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.persistence;

import java.nio.file.Path;

public class BackupNode {
    private final String nodeId;
    private final Path baseDirectory;
    private final boolean acceptsClientTraffic;

    public BackupNode(String nodeId, Path baseDirectory, boolean acceptsClientTraffic) {
        this.nodeId = nodeId;
        this.baseDirectory = baseDirectory;
        this.acceptsClientTraffic = acceptsClientTraffic;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public boolean acceptsClientTraffic() {
        return acceptsClientTraffic;
    }
}

