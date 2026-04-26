const refreshStatus = document.getElementById("refreshStatus");
const overviewCards = document.getElementById("overviewCards");
const usersTable = document.getElementById("usersTable");
const roomsGrid = document.getElementById("roomsGrid");
const callsList = document.getElementById("callsList");
const transferFeed = document.getElementById("transferFeed");
const auditFeed = document.getElementById("auditFeed");
const storageBlock = document.getElementById("storageBlock");
const proxyBlock = document.getElementById("proxyBlock");
const integrationBlock = document.getElementById("integrationBlock");
const actionResult = document.getElementById("actionResult");

async function loadDashboard() {
    refreshStatus.textContent = "Refreshing live data...";
    try {
        const response = await fetch("/api/dashboard", { cache: "no-store" });
        const data = await response.json();
        renderOverview(data.overview);
        renderUsers(data.users);
        renderRooms(data.rooms);
        renderCalls(data.calls);
        renderTransfers(data.transfers);
        renderAudits(data.audits);
        storageBlock.textContent = data.storage;
        proxyBlock.textContent = data.proxyTopology;
        integrationBlock.textContent = data.integrations;
        refreshStatus.textContent = `Live as of ${new Date().toLocaleTimeString()}`;
    } catch (error) {
        refreshStatus.textContent = "Refresh failed";
        actionResult.textContent = `Dashboard error: ${error.message}`;
    }
}

function renderOverview(overview) {
    const metrics = [
        { label: "Registered Users", value: overview.userCount, tone: "teal" },
        { label: "Online Now", value: overview.onlineCount, tone: "gold" },
        { label: "Rooms", value: overview.roomCount, tone: "blue" },
        { label: "Active Calls", value: overview.activeCalls, tone: "teal" },
        { label: "Tracked Transfers", value: overview.recentTransfers, tone: "gold" }
    ];

    overviewCards.innerHTML = metrics.map(metric => `
        <article class="metric-card metric-${metric.tone}">
            <strong>${metric.value}</strong>
            <span>${escapeHtml(metric.label)}</span>
        </article>
    `).join("");
}

function renderUsers(users) {
    usersTable.innerHTML = users.map(user => `
        <tr>
            <td>
                <strong>${escapeHtml(user.displayName)}</strong><br>
                <span>${escapeHtml(user.username)}</span>
            </td>
            <td>${escapeHtml(user.role)}</td>
            <td>
                <span class="pill ${user.banned ? "warn" : ""}">
                    ${user.banned ? "Banned" : user.online ? "Online" : "Offline"}
                </span>
                ${user.muted ? `<span class="pill warn">Muted ${user.muteSeconds}s</span>` : ""}
            </td>
            <td>
                <div class="inline-actions">
                    <button class="mini ghost" onclick="runAction('mute', '${escapeAttribute(user.username)}', 60)">Mute</button>
                    <button class="mini ghost" onclick="runAction('kick', '${escapeAttribute(user.username)}')">Kick</button>
                    <button class="mini" onclick="runAction('ban', '${escapeAttribute(user.username)}')">Ban</button>
                </div>
            </td>
        </tr>
    `).join("");
}

function renderRooms(rooms) {
    roomsGrid.innerHTML = rooms.map(room => `
        <article class="room-card">
            <h4>${escapeHtml(room.displayName)}</h4>
            <p>${escapeHtml(room.description)}</p>
            <p class="pill">${room.members} members</p>
            <div class="message-list">
                ${room.messages.length === 0 ? "<div class='message-item'>No messages yet.</div>" : room.messages.map(message => `
                    <div class="message-item">
                        <strong>${escapeHtml(message.sender)}</strong>
                        <span>${escapeHtml(message.content)}</span>
                    </div>
                `).join("")}
            </div>
        </article>
    `).join("");
}

function renderCalls(calls) {
    callsList.innerHTML = calls.length === 0
        ? `<article class="call-card"><h4>No active or pending calls</h4><p>The realtime media layer is currently idle.</p></article>`
        : calls.map(call => `
            <article class="call-card">
                <h4>${escapeHtml(call.mode)}</h4>
                <p><strong>${escapeHtml(call.caller)}</strong> to <strong>${escapeHtml(call.callee)}</strong></p>
                <p class="pill ${call.accepted ? "" : "warn"}">${call.accepted ? "Accepted" : "Pending"}</p>
            </article>
        `).join("");
}

function renderTransfers(transfers) {
    transferFeed.innerHTML = transfers.length === 0
        ? `<div class="audit-item">No transfer records yet.</div>`
        : transfers.map(transfer => `
            <div class="audit-item">
                <strong>${escapeHtml(transfer.channel)}</strong>
                <span>${escapeHtml(transfer.source)} to ${escapeHtml(transfer.target)}</span><br>
                <span>${escapeHtml(transfer.payloadType)} | ${escapeHtml(transfer.status)}</span><br>
                <span>${escapeHtml(transfer.payloadSummary)}</span>
            </div>
        `).join("");
}

function renderAudits(audits) {
    auditFeed.innerHTML = audits.length === 0
        ? `<div class="audit-item">No audit entries yet.</div>`
        : audits.map(entry => `<div class="audit-item">${escapeHtml(entry)}</div>`).join("");
}

async function postAction(formData) {
    const response = await fetch("/api/admin/action", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams(formData)
    });
    const payload = await response.json();
    actionResult.textContent = payload.result;
    await loadDashboard();
}

async function runAction(action, username, seconds = 60) {
    await postAction({ action, username, seconds: String(seconds) });
}

document.getElementById("announcementForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await postAction({
        action: "announce",
        message: form.get("message")
    });
    event.currentTarget.reset();
});

document.getElementById("moderationForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await postAction({
        action: form.get("action"),
        username: form.get("username"),
        seconds: form.get("seconds")
    });
});

document.getElementById("snapshotButton").addEventListener("click", async () => {
    await postAction({ action: "snapshot" });
});

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;");
}

function escapeAttribute(value) {
    return String(value).replaceAll("'", "\\'");
}

loadDashboard();
setInterval(loadDashboard, 4000);
