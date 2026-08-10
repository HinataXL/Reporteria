(function () {
    if (window.crmRefreshRealtimeLoaded) {
        return;
    }
    window.crmRefreshRealtimeLoaded = true;

    const state = {
        stompClient: null,
        socketReady: false
    };

    function meta(name) {
        return document.querySelector(`meta[name="${name}"]`)?.content || "";
    }

    function hiddenCsrf(name) {
        return document.querySelector(`input[name="${name}"]`)?.value || "";
    }

    function userRole() {
        return (meta("app-role") || document.body.dataset.role || "").toUpperCase();
    }

    function userEmail() {
        return (meta("app-email") || document.body.dataset.email || "").toLowerCase();
    }

    function userName() {
        return (meta("app-user") || document.body.dataset.user || "").toLowerCase();
    }

    function isAdmin() {
        return userRole() === "ADMIN";
    }

    function isAgentTarget(event) {
        const email = (event.agentEmail || "").toLowerCase();
        const name = (event.agentName || "").toLowerCase();
        return (email && email === userEmail()) || (name && name === userName());
    }

    function loadScript(src) {
        return new Promise((resolve, reject) => {
            if (src.includes("sockjs") && window.SockJS) {
                resolve();
                return;
            }
            if (src.includes("stomp") && window.Stomp) {
                resolve();
                return;
            }
            const script = document.createElement("script");
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    async function ensureSocketLibraries() {
        await loadScript("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
        await loadScript("https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js");
    }

    function ensureNotificationPermission() {
        if (!("Notification" in window) || Notification.permission !== "default") {
            return;
        }
        Notification.requestPermission().catch(() => {});
    }

    function desktopNotification(title, body) {
        if (!("Notification" in window) || Notification.permission !== "granted") {
            return;
        }
        new Notification(title, {
            body,
            icon: "/sneat/assets/img/brand/insights-desk-logo.png",
            tag: "insights-desk-crm-refresh"
        });
    }

    function ensureStyles() {
        if (document.getElementById("crmRealtimeStyles")) {
            return;
        }
        const style = document.createElement("style");
        style.id = "crmRealtimeStyles";
        style.textContent = `
            .crm-rt-modal{position:fixed;inset:0;z-index:9999;display:none;place-items:center;background:rgba(6,11,63,.42);padding:18px}
            .crm-rt-modal.show{display:grid}
            .crm-rt-card{width:min(460px,100%);border:1px solid #e3e7f2;border-radius:20px;background:#fff;color:#060b3f;padding:22px;box-shadow:0 26px 80px rgba(6,11,63,.24);font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
            .crm-rt-eyebrow{color:#6236ff;font-size:11px;font-weight:900;text-transform:uppercase;letter-spacing:.12em}
            .crm-rt-title{margin:8px 0 8px;font-size:22px;font-weight:950;line-height:1.15}
            .crm-rt-text{margin:0 0 16px;color:#5f6b8a;font-size:14px;font-weight:700;line-height:1.5}
            .crm-rt-detail{display:grid;gap:6px;margin:0 0 18px;padding:12px;border-radius:14px;background:#f8faff;color:#5f6b8a;font-size:12px;font-weight:800}
            .crm-rt-actions{display:flex;flex-wrap:wrap;gap:10px;justify-content:flex-end}
            .crm-rt-btn{height:40px;border-radius:10px;border:1px solid #e3e7f2;background:#fff;color:#060b3f;padding:0 14px;font-weight:900;cursor:pointer}
            .crm-rt-btn.primary{border-color:#6236ff;background:#6236ff;color:#fff}
            .crm-rt-btn.danger{border-color:#ff3f66;background:#fff0f3;color:#ff3f66}
        `;
        document.head.appendChild(style);
    }

    function getCsrfHeaders() {
        const header = meta("_csrf_header") || "X-CSRF-TOKEN";
        const token = meta("_csrf") || hiddenCsrf("_csrf");
        return token ? { [header]: token } : {};
    }

    async function postAction(url, modal) {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                ...getCsrfHeaders(),
                "X-Requested-With": "XMLHttpRequest"
            },
            credentials: "same-origin"
        });
        if (!response.ok) {
            throw new Error("No fue posible procesar la solicitud.");
        }
        const payload = await response.json().catch(() => ({ success: true }));
        if (payload.success === false) {
            throw new Error(payload.message || "No fue posible procesar la solicitud.");
        }
        closeModal(modal);
    }

    function closeModal(modal) {
        modal?.classList.remove("show");
    }

    function showAdminModal(event) {
        if (!isAdmin()) {
            return;
        }
        ensureStyles();
        desktopNotification("Solicitud CRM pendiente", `${event.agentName || "Un agente"} solicita actualizar CRM.`);
        const modal = document.createElement("div");
        modal.className = "crm-rt-modal show";
        modal.innerHTML = `
            <div class="crm-rt-card" role="dialog" aria-modal="true">
                <div class="crm-rt-eyebrow">Autorizacion CRM</div>
                <h3 class="crm-rt-title">Solicitud de actualizacion</h3>
                <p class="crm-rt-text">${escapeHtml(event.agentName || "Agente")} solicita consultar Zoho CRM para actualizar sus metricas.</p>
                <div class="crm-rt-detail">
                    <span>Rango: ${escapeHtml(event.from || "-")} al ${escapeHtml(event.to || "-")}</span>
                    <span>Tipo: ${escapeHtml(event.taskType || "Todos")} | Pais: ${escapeHtml(event.operationCountry || "Todos")}</span>
                    <span>Estado: ${escapeHtml(event.status || "Todos")}</span>
                </div>
                <div class="crm-rt-actions">
                    <button class="crm-rt-btn" data-close>Cerrar</button>
                    <button class="crm-rt-btn danger" data-reject>Rechazar</button>
                    <button class="crm-rt-btn primary" data-approve>Autorizar</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        modal.querySelector("[data-close]").addEventListener("click", () => closeModal(modal));
        modal.querySelector("[data-reject]").addEventListener("click", async () => {
            await postAction(event.rejectUrl, modal).catch((error) => alert(error.message));
        });
        modal.querySelector("[data-approve]").addEventListener("click", async () => {
            await postAction(event.approveUrl, modal).catch((error) => alert(error.message));
        });
    }

    function showAgentModal(event) {
        if (!isAgentTarget(event)) {
            return;
        }
        ensureStyles();
        const approved = event.approved === true;
        desktopNotification(event.title || "Respuesta CRM", event.message || "Hay una respuesta para tu solicitud CRM.");
        const modal = document.createElement("div");
        modal.className = "crm-rt-modal show";
        modal.innerHTML = `
            <div class="crm-rt-card" role="dialog" aria-modal="true">
                <div class="crm-rt-eyebrow">CRM</div>
                <h3 class="crm-rt-title">${escapeHtml(event.title || "Respuesta CRM")}</h3>
                <p class="crm-rt-text">${escapeHtml(event.message || "")}</p>
                <div class="crm-rt-actions">
                    <button class="crm-rt-btn" data-close>Cerrar</button>
                    ${approved ? `<a class="crm-rt-btn primary" href="${escapeHtml(event.refreshUrl || "/agent/zoho-crm/tasks/dashboard")}">Actualizar ahora</a>` : ""}
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        modal.querySelector("[data-close]").addEventListener("click", () => closeModal(modal));
        if (approved && window.location.pathname === "/agent/zoho-crm/tasks/dashboard") {
            setTimeout(() => {
                window.location.href = event.refreshUrl || "/agent/zoho-crm/tasks/dashboard";
            }, 1200);
        }
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    async function connect() {
        if (state.socketReady || (!isAdmin() && !userEmail() && !userName())) {
            return;
        }
        await ensureSocketLibraries();
        const socket = new SockJS("/ws", null, {
            transports: ["websocket", "xhr-streaming", "xhr-polling"]
        });
        state.stompClient = Stomp.over(socket);
        state.stompClient.debug = null;
        state.stompClient.connect({}, function () {
            state.socketReady = true;
            state.stompClient.subscribe("/topic/crm-refresh-requests", function (message) {
                showAdminModal(JSON.parse(message.body));
            });
            state.stompClient.subscribe("/topic/crm-refresh-authorizations", function (message) {
                showAgentModal(JSON.parse(message.body));
            });
        });
    }

    ensureNotificationPermission();
    connect().catch(() => {});
})();
