/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.model;

public enum UserRole {
    USER,
    MODERATOR,
    ADMIN;

    public boolean canModerate(UserRole targetRole) {
        return switch (this) {
            case USER -> false;
            case MODERATOR -> targetRole == USER;
            case ADMIN -> targetRole != ADMIN;
        };
    }
}

