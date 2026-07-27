console.log("Conversation Workspace v2 cargado");

window.ConversationWorkspace = (() => {
    let tabs = JSON.parse(sessionStorage.getItem("conversationTabs") || "[]");
    let activeTabId = sessionStorage.getItem("activeConversationTabId");
    let timerInterval = null;
    let sessionPromptTimeout = null;
    let sessionExpired = false;
    let redirectingToLogin = false;

    const formId = "conversationForm";
    const tabsContainerId = "conversationTabs";
    const timerDisplayId = "timerDisplay";
    const timerInputId = "tiempoGestionMinutos";
    const pauseBtnId = "pauseTimerBtn";
    const sessionPromptDelayMs = 8 * 60 * 1000;

    function saveState() {
        sessionStorage.setItem("conversationTabs", JSON.stringify(tabs));
        if (activeTabId) {
            sessionStorage.setItem("activeConversationTabId", activeTabId);
        }
    }

    function getForm() {
        return document.getElementById(formId);
    }

    function getActiveTab() {
        return tabs.find(t => t.id === activeTabId);
    }

    function getCsrfToken() {
        const form = getForm();
        if (!form) return null;

        const csrfInput = form.querySelector('input[name="_csrf"]');
        return csrfInput ? csrfInput.value : null;
    }

    function handleExpiredSession() {
        if (redirectingToLogin) {
            return;
        }

        sessionExpired = true;
        redirectingToLogin = true;

        if (sessionPromptTimeout) {
            clearTimeout(sessionPromptTimeout);
        }

        stopTimer();
        hideSessionPrompt();
        showToast("Sesion expirada. Vuelve a iniciar sesion para guardar.", true);

        const tab = getActiveTab();
        if (tab) {
            tab.saving = false;
            tab.saved = false;
            saveState();
        }

        setTimeout(() => {
            window.location.href = "/login?expired=true";
        }, 1200);
    }

    async function keepSessionAlive(showError = false) {
        if (sessionExpired) {
            return false;
        }
        const csrfToken = getCsrfToken();

        if (!csrfToken) {
            if (showError) {
                handleExpiredSession();
            }

            return false;
        }

        try {
            const response = await fetch("/api/session/keep-alive", {
                method: "POST",
                headers: {
                    "X-CSRF-TOKEN": csrfToken
                }
            });

            if (response.redirected || response.status === 401 || response.status === 403) {
                if (showError) {
                    handleExpiredSession();
                }

                return false;
            }

            return response.ok;
        } catch (error) {
            console.warn("No fue posible renovar la sesion", error);

            if (showError) {
                showToast("No fue posible renovar la sesion", true);
            }

            return false;
        }
    }

    function hideSessionPrompt() {
        const prompt = document.getElementById("sessionContinuePrompt");

        if (prompt) {
            prompt.classList.add("hidden");
        }
    }

    function showSessionPrompt() {
        const prompt = document.getElementById("sessionContinuePrompt");

        if (!prompt) {
            return;
        }

        prompt.classList.remove("hidden");
    }

    function scheduleSessionPrompt() {
        if (sessionPromptTimeout) {
            clearTimeout(sessionPromptTimeout);
        }

        sessionPromptTimeout = setTimeout(() => {
            showSessionPrompt();
        }, sessionPromptDelayMs);
    }

    async function continueWorking() {
        if (sessionExpired) {
            handleExpiredSession();
            return;
        }

        const button = document.getElementById("sessionContinueButton");
        const originalText = button ? button.innerText : "";

        if (button) {
            button.disabled = true;
            button.innerText = "Renovando...";
        }

        const renewed = await keepSessionAlive(true);

        if (button) {
            button.disabled = false;
            button.innerText = originalText || "Continuar trabajando";
        }

        if (renewed) {
            hideSessionPrompt();
            scheduleSessionPrompt();
            showToast("Sesion renovada correctamente");
        }
    }

    function normalizeTab(tab) {
        if (tab.elapsedSeconds == null) tab.elapsedSeconds = 0;
        if (tab.running == null) tab.running = true;
        if (tab.lastResume == null) tab.lastResume = Date.now();
        if (!tab.data) tab.data = {};
        return tab;
    }

    function getCurrentElapsedSeconds(tab) {
        normalizeTab(tab);

        if (!tab.running) {
            return tab.elapsedSeconds;
        }

        return tab.elapsedSeconds + Math.floor((Date.now() - tab.lastResume) / 1000);
    }

    function getFormData() {
        const form = getForm();
        const data = {};

        Array.from(form.elements).forEach(el => {
            if (!el.name) return;

            if (el.type === "checkbox") {
                data[el.name] = el.checked;
            } else {
                data[el.name] = el.value;
            }
        });

        return data;
    }

    function setFormData(data) {
        const form = getForm();

        Array.from(form.elements).forEach(el => {
            if (!el.name) return;

            if (el.type === "checkbox") {
                el.checked = Boolean(data[el.name]);
            } else {
                el.value = data[el.name] || "";
            }
        });

        triggerConditionalFields();
    }

    function clearForm() {
        const form = getForm();
        form.reset();

        const timerInput = document.getElementById(timerInputId);
        if (timerInput) timerInput.value = 0;

        triggerConditionalFields();
    }

    function saveActiveTabData() {
        const tab = getActiveTab();
        if (!tab) return;

        tab.data = getFormData();
        saveState();
    }

    function getTabLabel(tab) {
        if (tab.saved && tab.codigo) {
            return `✅ ${tab.codigo}`;
        }

        const status = tab.running
            ? "🟢"
            : "⏸";

        const name =
            tab.data?.clienteNombre ||
            tab.data?.cliente_nombre ||
            tab.data?.cliente ||
            "Nueva conversación";

        return `${status} ${name}`;
    }

    function renderTabs() {
        const container = document.getElementById(tabsContainerId);
        if (!container) return;

        container.innerHTML = "";

        tabs.forEach(tab => {
            normalizeTab(tab);

            const button = document.createElement("button");
            button.type = "button";

            button.className =
                "px-4 py-3 rounded-2xl font-black border transition " +
                (tab.id === activeTabId
                    ? "bg-[#ffd700] text-black border-[#ffd700]"
                    : "bg-white/10 text-white border-white/10");

            button.innerHTML = `
                <span>${getTabLabel(tab)}</span>
                <span style="margin-left:10px;color:#ff6b6b;font-weight:900;">✕</span>
            `;

            button.onclick = (event) => {
                if (event.target.innerText === "✕") {
                    closeTab(tab.id);
                } else {
                    switchTab(tab.id);
                }
            };

            container.appendChild(button);
        });
    }

    function updatePauseButton(tab) {
        const btn = document.getElementById(pauseBtnId);
        const status = document.getElementById("timerStatus");

        if (!btn || !tab) return;

        if (tab.running) {
            btn.innerText = "⏸ Pausar";
            btn.className =
                "mt-5 w-full bg-[#ffd700] hover:bg-yellow-300 text-black font-black py-2 rounded-xl transition";

            if (status) {
                status.innerText = "🟢 En gestión";
                status.className = "mt-2 text-sm font-black text-green-400";
            }
        } else {
            btn.innerText = "▶ Reanudar";
            btn.className =
                "mt-5 w-full bg-green-400 hover:bg-green-300 text-black font-black py-2 rounded-xl transition";

            if (status) {
                status.innerText = "⏸ Pausado";
                status.className = "mt-2 text-sm font-black text-yellow-300";
            }
        }
    }

    function stopTimer() {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
    }

    function startTimer() {
        stopTimer();

        const tab = getActiveTab();
        if (!tab) return;

        normalizeTab(tab);

        const timerDisplay = document.getElementById(timerDisplayId);
        const timerInput = document.getElementById(timerInputId);

        function updateTimer() {
            const seconds = getCurrentElapsedSeconds(tab);
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = seconds % 60;

            if (timerDisplay) {
                timerDisplay.innerText =
                    String(minutes).padStart(2, "0") + ":" +
                    String(remainingSeconds).padStart(2, "0");
            }

            if (timerInput) {
                timerInput.value = Math.max(1, Math.ceil(seconds / 60));
            }

            updatePauseButton(tab);
        }

        updateTimer();
        timerInterval = setInterval(updateTimer, 1000);
    }

    function addTab() {
        saveActiveTabData();

        const id = "tab-" + Date.now();

        const tab = {
            id,
            createdAt: Date.now(),
            elapsedSeconds: 0,
            running: true,
            lastResume: Date.now(),
            data: {}
        };

        tabs.push(tab);
        activeTabId = id;

        clearForm();
        saveState();
        renderTabs();
        startTimer();

        console.log("Nueva conversación creada:", id);
    }

    function switchTab(id) {
        saveActiveTabData();

        activeTabId = id;

        const tab = getActiveTab();

        if (tab) {
            normalizeTab(tab);
            setFormData(tab.data || {});
        }

        saveState();
        renderTabs();
        startTimer();
    }

    function toggleTimer() {
        const tab = getActiveTab();
        if (!tab) return;

        normalizeTab(tab);

        if (tab.running) {
            tab.elapsedSeconds = getCurrentElapsedSeconds(tab);
            tab.running = false;
        } else {
            tab.running = true;
            tab.lastResume = Date.now();
        }

        saveState();
        renderTabs();
        startTimer();

        console.log("Timer cambiado:", tab.running ? "activo" : "pausado");
    }

    function closeTab(id) {
        const shouldClose = confirm("¿Deseas cerrar esta conversación en borrador?");
        if (!shouldClose) return;

        tabs = tabs.filter(t => t.id !== id);

        if (activeTabId === id) {
            activeTabId = tabs.length ? tabs[0].id : null;

            if (activeTabId) {
                const tab = getActiveTab();
                setFormData(tab.data || {});
            } else {
                clearForm();
            }
        }

        saveState();
        renderTabs();
        startTimer();
    }



    function triggerConditionalFields() {
        document.querySelectorAll("input, select, textarea").forEach(el => {
            el.dispatchEvent(new Event("change", { bubbles: true }));
        });
    }

    function formatLocalDateTime(timestamp) {
        const date = new Date(timestamp);
        const pad = value => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    }

    async function saveConversationAjax() {
        if (sessionExpired) {
            handleExpiredSession();
            return;
        }

        const form = getForm();
        const tab = getActiveTab();

        if (!form || !tab) {
            return;
        }

        // Evita doble guardado
        if (tab.saving || tab.saved) {
            console.warn("La conversación ya se está guardando o ya fue guardada");
            return;
        }

        const submitButton = form.querySelector(
            'button[type="submit"]'
        );

        try {
            tab.saving = true;

            // Detener y calcular el tiempo final
            tab.elapsedSeconds = getCurrentElapsedSeconds(tab);
            tab.running = false;

            const timerInput =
                document.getElementById(timerInputId);

            if (timerInput) {
                timerInput.value = Math.max(
                    1,
                    Math.ceil(tab.elapsedSeconds / 60)
                );
            }

            const startInput = document.getElementById("fechaInicio");
            if (startInput && tab.createdAt) {
                startInput.value = formatLocalDateTime(tab.createdAt);
            }

            saveActiveTabData();
            saveState();

            if (submitButton) {
                submitButton.disabled = true;

                submitButton.dataset.originalText =
                    submitButton.innerHTML;

                submitButton.innerHTML = "Guardando...";
            }

            const sessionReady = await keepSessionAlive(true);

            if (!sessionReady) {
                if (sessionExpired) {
                    return;
                }

                throw new Error("No fue posible validar la sesion");
            }

            const formData = new FormData(form);

            const response = await fetch(
                "/api/conversations/save",
                {
                    method: "POST",
                    body: formData
                }
            );

            if (response.redirected || response.status === 401 || response.status === 403) {
                handleExpiredSession();
                return;
            }

            const data = await response.json();

            if (!response.ok || !data.success) {
                throw new Error(
                    data.message ||
                    "No fue posible guardar la conversación"
                );
            }

            tab.saved = true;
            tab.codigo = data.codigo;
            tab.conversationId = data.id;
            tab.saving = false;

            console.log(
                "Conversación guardada correctamente:",
                data
            );

            showToast(
                `✅ ${data.codigo} guardada correctamente`
            );

            closeActiveTabAfterSave();

        } catch (error) {
            if (sessionExpired) {
                return;
            }

            console.error(
                "Error guardando conversación:",
                error
            );

            tab.saving = false;
            tab.saved = false;

            // Reanudar contador si falló el guardado
            tab.running = true;
            tab.lastResume = Date.now();

            saveState();

            startTimer();

            showToast(
                "❌ Error al guardar la conversación",
                true
            );

        } finally {
            if (submitButton) {
                submitButton.disabled = false;

                submitButton.innerHTML =
                    submitButton.dataset.originalText ||
                    "Guardar conversación";
            }
        }
    }

    function closeActiveTabAfterSave() {
        if (!activeTabId) {
            return;
        }

        stopTimer();

        const savedTabId = activeTabId;

        tabs = tabs.filter(tab => tab.id !== savedTabId);

        console.log(
            "Pestaña guardada eliminada del Workspace:",
            savedTabId
        );

        // No quedan conversaciones abiertas
        if (tabs.length === 0) {
            activeTabId = null;

            sessionStorage.removeItem(
                "activeConversationTabId"
            );

            sessionStorage.setItem(
                "conversationTabs",
                JSON.stringify([])
            );

            console.log(
                "No quedan conversaciones activas. Redirigiendo..."
            );

            setTimeout(() => {
                window.location.href = "/conversations";
            }, 800);

            return;
        }

        // Abrir otra conversación existente
        activeTabId = tabs[0].id;

        const nextTab = getActiveTab();

        if (nextTab) {
            normalizeTab(nextTab);
            setFormData(nextTab.data || {});
        }

        saveState();
        renderTabs();
        startTimer();

        console.log(
            "Conversación activa cambiada a:",
            activeTabId
        );
    }

    function bindEvents() {
        const form = getForm();
        if (!form) return;

        const btnNew = document.getElementById("btnNewConversation");
        if (btnNew) {
            btnNew.addEventListener("click", addTab);
        }

        const sessionContinueButton = document.getElementById("sessionContinueButton");
        if (sessionContinueButton) {
            sessionContinueButton.addEventListener("click", continueWorking);
        }

        form.addEventListener("input", () => {
            saveActiveTabData();
            renderTabs();
        });

        form.addEventListener("change", () => {
            saveActiveTabData();
            renderTabs();
        });

        form.addEventListener("submit", async function (event) {
            event.preventDefault();

            await saveConversationAjax();
        });
    }
    function showToast(message, isError = false) {
        const toast =
            document.getElementById("workspaceToast");

        if (!toast) {
            return;
        }

        toast.innerText = message;

        toast.className =
            "fixed top-6 right-6 z-[9999] " +
            "px-6 py-4 rounded-2xl font-black shadow-2xl " +
            (isError
                ? "bg-red-500 text-white"
                : "bg-green-500 text-black");

        toast.classList.remove("hidden");

        setTimeout(() => {
            toast.classList.add("hidden");
        }, 3500);
    }

    function init() {
        const form = getForm();
        if (!form) return;

        tabs = tabs.map(normalizeTab);

        if (!activeTabId || !tabs.some(t => t.id === activeTabId)) {
            activeTabId = tabs.length ? tabs[0].id : null;
        }

        if (tabs.length === 0) {
            addTab();
        } else {
            const tab = getActiveTab();
            setFormData(tab.data || {});
            renderTabs();
            startTimer();
        }

        bindEvents();
        scheduleSessionPrompt();

        console.log("Conversation Workspace v2 inicializado");
    }

    return {
        init,
        addTab,
        switchTab,
        closeTab,
        toggleTimer,
        continueWorking
    };
})();

document.addEventListener("DOMContentLoaded", () => {
    ConversationWorkspace.init();
});
