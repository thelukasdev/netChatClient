# NetChat

NetChat is a large Java-based communication platform project with:

- encrypted client-server chat
- room-based messaging
- private messages
- moderation tools
- realtime voice and live video signaling
- browser-based admin dashboard
- persistent storage and backup replication
- transfer tracking and cloud-style archival
- optional external API integrations

## Highlights

- Console chat client and Java server
- Embedded browser admin app
- AES/GCM packet encryption
- PBKDF2 password hashing
- audit trail and delivery logging
- VPN/proxy risk heuristics
- room history and transfer persistence
- outbound-only backup replica structure

## Project Structure

```text
src/
  Main.java
  netchat/
    client/         Console client, local media controller, video window
    server/         Server, moderation, auth, sessions, realtime call signaling
    web/            Embedded admin web server
    persistence/    Storage, snapshots, backup replication
    transmission/   Delivery tracking and archive records
    security/       Risk profiling and proxy/VPN heuristics
    integration/    Internal integration events/projects
    integrations/   External API clients and activation hub
    shared/         Models, protocol, crypto, utilities
webapp/
  index.html       Browser admin app
  app.js           Dashboard rendering and actions
```

## Current Feature Set

### Core communication

- user registration and login
- public rooms
- private direct messages
- room history
- system announcements

### Security

- encrypted packet transport with AES/GCM
- salted password hashing with PBKDF2
- rate limiting
- blocked-word filtering
- external moderation hook support
- failed login lockout
- VPN/proxy heuristic detection

### Moderation

- mute
- kick
- ban
- audit logging
- admin web actions

### Realtime media

- voice call signaling
- live video session signaling
- UDP audio streaming
- live screen-video streaming

Note: the current "video" layer is implemented as live screen streaming with standard Java APIs.

### Web admin dashboard

- overview cards
- user management
- room inspection
- call status
- transfer feed
- audit feed
- storage and infrastructure panels
- announcement and moderation actions

### Persistence

- local persistent record files
- encrypted snapshots
- outbound replica sync directories
- transfer logs
- cloud-style encrypted archive objects

## Storage Layout

At runtime the project writes data into `storage/`, including:

- `users.db`
- `messages.db`
- `audit.db`
- `network.db`
- `integrations.db`
- `transfers.db`
- `snapshots/`
- `cloud-archive/`
- backup replica directories

## External Integrations

The codebase contains optional integration hooks for:

- PostgreSQL
- Redis
- MinIO / S3-style object storage
- Cloudflare Turnstile
- OpenAI Moderation
- Firebase Cloud Messaging
- Twilio Voice
- Elasticsearch

These integrations are compile-safe and optional. They become active only when valid configuration values are provided.

## Configuration

Integrations read configuration from Java system properties or matching environment variables.

Examples:

```text
netchat.postgres.url
netchat.postgres.user
netchat.postgres.password
netchat.redis.url
netchat.minio.endpoint
netchat.minio.accessKey
netchat.minio.secretKey
netchat.minio.bucket
netchat.turnstile.secret
netchat.openai.apiKey
netchat.firebase.projectId
netchat.firebase.bearerToken
netchat.twilio.accountSid
netchat.twilio.authToken
netchat.elastic.endpoint
netchat.elastic.apiKey
```

Environment variable form:

```text
NETCHAT_POSTGRES_URL
NETCHAT_OPENAI_APIKEY
NETCHAT_TWILIO_ACCOUNTSID
```

## Running the Project

### Requirements

- Java 21 recommended

### Compile

```powershell
& "C:\Users\lukas\.jdks\corretto-21\bin\javac.exe" -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```

### Run

Start the application through `Main.java`.

When launched, you can choose:

1. start server
2. start client
3. show project vision

### Default ports

- chat server: chosen port, default `5000`
- web admin app: `port + 1000`, for example `6000`

## Admin Access

Bootstrap admin account:

```text
username: admin
password: Admin1234
```

## Important Notes

- This project is a large prototype / platform foundation.
- Several advanced integrations are structurally implemented but still need real credentials and real infrastructure to operate externally.
- The browser app is currently focused on administration and monitoring.
- The main user chat flow is still centered on the Java client/server side.

## Ownership

Copyright (c) 2026 Lukas Pellny. All rights reserved.

NetChat and the source code in this repository are proprietary project material created by Lukas Pellny. Copying, publishing, or reusing substantial parts of this implementation requires prior written permission from the owner.
