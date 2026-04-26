/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.media;

public enum MediaMode {
    AUDIO_ONLY,
    AUDIO_VIDEO;

    public boolean includesVideo() {
        return this == AUDIO_VIDEO;
    }
}

