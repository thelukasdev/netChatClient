/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.transmission;

public enum DeliveryChannel {
    USER_TO_USER,
    USER_TO_ROOM,
    SERVER_TO_USER,
    SERVER_TO_SERVER,
    USER_MEDIA_SIGNAL,
    SYSTEM_BACKUP
}

