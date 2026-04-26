/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebAppAssetRepository {
    private final Path rootDirectory;

    public WebAppAssetRepository(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public String loadText(String relativePath) {
        try {
            return Files.readString(rootDirectory.resolve(relativePath), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load web asset " + relativePath, exception);
        }
    }
}

