/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

import netchat.client.ChatClient;
import netchat.server.ChatServer;

import java.util.Scanner;

public class Main {
    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_SECRET = "NetChat-Lukas-Pellny-2026";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        printBanner();
        System.out.println("1. Start server");
        System.out.println("2. Start client");
        System.out.println("3. Show project vision");
        System.out.print("Select an option: ");

        String selection = scanner.nextLine().trim();
        int port = askForPort(scanner);
        String secret = askForSecret(scanner);

        switch (selection) {
            case "1" -> new ChatServer(port, secret).start();
            case "2" -> {
                String host = ask(scanner, "Server host", "127.0.0.1");
                new ChatClient(host, port, secret).start();
            }
            case "3" -> printVision();
            default -> System.out.println("Unknown selection. Please restart and choose 1, 2 or 3.");
        }
    }

    private static void printBanner() {
        System.out.println("==============================================");
        System.out.println("            NetChat Communications");
        System.out.println("==============================================");
        System.out.println("Encrypted console platform with Unicode support: Ã¤ Ã¶ Ã¼ ÃŸ");
        System.out.println();
    }

    private static int askForPort(Scanner scanner) {
        String portValue = ask(scanner, "Port", String.valueOf(DEFAULT_PORT));
        try {
            return Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            System.out.println("Invalid port. Falling back to " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
    }

    private static String askForSecret(Scanner scanner) {
        return ask(scanner, "Shared encryption secret", DEFAULT_SECRET);
    }

    private static String ask(Scanner scanner, String label, String fallback) {
        System.out.print(label + " [" + fallback + "]: ");
        String value = scanner.nextLine().trim();
        return value.isEmpty() ? fallback : value;
    }

    private static void printVision() {
        System.out.println();
        System.out.println("NetChat is designed as a large communication platform.");
        System.out.println("Possible use cases:");
        System.out.println("- School classes, study groups and project teams");
        System.out.println("- Clubs, communities and local event chats");
        System.out.println("- LAN parties, workshops and classroom demonstrations");
        System.out.println();
        System.out.println("Core feature set:");
        System.out.println("- Registration and secure login");
        System.out.println("- Room based chat, direct messages and system announcements");
        System.out.println("- Moderation commands, rate limiting and content filtering");
        System.out.println("- AES encrypted packet transport and salted password hashing");
        System.out.println("- Message history, audit logging and Unicode ready text handling");
        System.out.println("- Persistent storage, replica backup sync and project integrations");
    }
}

