/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.util;

public final class TextUtils {
    private static final String USERNAME_REGEX = "^[\\p{L}0-9_]{3,16}$";

    private TextUtils() {
    }

    public static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static String normalizeDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "Guest" : normalized;
    }

    public static String normalizeMessage(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("\\p{Cntrl}", "");
    }

    public static boolean isValidUsername(String username) {
        return username != null && username.matches(USERNAME_REGEX);
    }

    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char current : password.toCharArray()) {
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}

