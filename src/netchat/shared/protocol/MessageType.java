/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.protocol;

public enum MessageType {
    REGISTER_REQUEST,
    LOGIN_REQUEST,
    AUTH_RESPONSE,
    CHAT_MESSAGE,
    PRIVATE_MESSAGE,
    SYSTEM_MESSAGE,
    ROOM_JOIN,
    ROOM_LIST,
    USER_LIST,
    HISTORY_RESPONSE,
    MEDIA_SIGNAL,
    COMMAND,
    MODERATION_ACTION,
    LOGOUT
}

