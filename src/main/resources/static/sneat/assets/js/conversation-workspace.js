console.log("Conversation Workspace v2 cargado");

window.ConversationWorkspace = (() => {
    let tabs = JSON.parse(sessionStorage.getItem("conversationTabs") || "[]");
    let activeTabId = sessionStorage.getItem("activeConversationTabId");
    let timerInterval = null;

    const formId = "conversationForm";
    const tabsContainerId = "conversationTabs";
    const timerDisplayId = "timerDisplay";
    const timerInputId = "tiempoGestionMinutos";
    const pauseBtnId = "pauseTimerBtn";

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
        const status = tab.running ? "🟢" : "⏸";
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

    function removeActiveTabAfterSubmit() {
        if (!activeTabId) return;

        tabs = tabs.filter(t => t.id !== activeTabId);
        sessionStorage.setItem("conversationTabs", JSON.stringify(tabs));
        sessionStorage.removeItem("activeConversationTabId");
    }

    function triggerConditionalFields() {
        document.querySelectorAll("input, select, textarea").forEach(el => {
            el.dispatchEvent(new Event("change", { bubbles: true }));
        });
    }

    function bindEvents() {
        const form = getForm();
        if (!form) return;

        const btnNew = document.getElementById("btnNewConversation");
        if (btnNew) {
            btnNew.addEventListener("click", addTab);
        }

        form.addEventListener("input", () => {
            saveActiveTabData();
            renderTabs();
        });

        form.addEventListener("change", () => {
            saveActiveTabData();
            renderTabs();
        });

        form.addEventListener("submit", () => {
            const tab = getActiveTab();

            if (tab) {
                tab.elapsedSeconds = getCurrentElapsedSeconds(tab);
                tab.running = false;

                const timerInput = document.getElementById(timerInputId);
                if (timerInput) {
                    timerInput.value = Math.max(1, Math.ceil(tab.elapsedSeconds / 60));
                }
            }

            saveActiveTabData();
            removeActiveTabAfterSubmit();
        });
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

        console.log("Conversation Workspace v2 inicializado");
    }

    return {
        init,
        addTab,
        switchTab,
        closeTab,
        toggleTimer
    };
})();

document.addEventListener("DOMContentLoaded", () => {
    ConversationWorkspace.init();
});