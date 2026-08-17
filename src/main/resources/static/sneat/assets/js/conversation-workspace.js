console.log("Conversation Workspace v2 cargado");

window.ConversationWorkspace = (() => {
    const tabsStorageKey = "conversationTabs";
    const activeTabStorageKey = "activeConversationTabId";
    const workspaceStorage = window.localStorage;
    let tabs = readStoredTabs();
    let activeTabId = workspaceStorage.getItem(activeTabStorageKey) || sessionStorage.getItem(activeTabStorageKey);
    let timerInterval = null;
    let sessionPromptTimeout = null;
    let sessionExpired = false;
    let redirectingToLogin = false;
    let validationActive = false;
    let sessionRenewalPending = false;
    let sessionTitleInterval = null;
    let sessionNotificationSent = false;
    let sessionToastShown = false;
    let pipOpenAttempted = false;
    let zohoClientSuggestions = [];
    let clientSearchTimeout = null;
    const originalDocumentTitle = document.title;

    const formId = "conversationForm";
    const tabsContainerId = "conversationTabs";
    const timerDisplayId = "timerDisplay";
    const timerInputId = "tiempoGestionMinutos";
    const pauseBtnId = "pauseTimerBtn";
    const sessionPromptDelayMs = 8 * 60 * 1000;
    const recentClientsKey = "conversationRecentClients";
    const workspaceChannelName = "conversation-workspace";
    const sessionAlertTitle = "Sesion por expirar";
    const sessionAlertMessage = "Tu sesion esta por vencer. Vuelve a Reporteria para continuar trabajando.";

    function readStoredTabs() {
        try {
            return JSON.parse(
                workspaceStorage.getItem(tabsStorageKey) ||
                sessionStorage.getItem(tabsStorageKey) ||
                "[]"
            );
        } catch (error) {
            console.warn("No fue posible leer conversaciones abiertas", error);
            return [];
        }
    }

    function saveState() {
        const serializedTabs = JSON.stringify(tabs);
        workspaceStorage.setItem(tabsStorageKey, serializedTabs);
        sessionStorage.setItem(tabsStorageKey, serializedTabs);
        if (activeTabId) {
            workspaceStorage.setItem(activeTabStorageKey, activeTabId);
            sessionStorage.setItem(activeTabStorageKey, activeTabId);
        }

        updateDraftStatus();
    }

    function getForm() {
        return document.getElementById(formId);
    }

    function isPopupMode() {
        return document.body?.dataset?.popupMode === "true";
    }

    function isPipMode() {
        return document.body?.dataset?.pipMode === "true";
    }

    function shouldOpenPipOnTabLeave() {
        return !isPopupMode()
            && !isPipMode()
            && !pipOpenAttempted
            && tabs.some(tab => !tab.saved);
    }

    function openPipOnTabLeave() {
        if (!shouldOpenPipOnTabLeave()) {
            return;
        }
        pipOpenAttempted = true;
        saveActiveTabData();
        saveState();
        if (window.ConversationPopup?.openPictureInPicture) {
            window.ConversationPopup.openPictureInPicture(window.location.href, {
                fallbackToPopup: false
            });
        }
    }

    function syncWorkspaceFromStorage() {
        const storedTabs = readStoredTabs().map(normalizeTab);
        const storedActiveTabId = workspaceStorage.getItem(activeTabStorageKey)
            || sessionStorage.getItem(activeTabStorageKey);

        if (!storedTabs.length) {
            return;
        }

        tabs = storedTabs;
        activeTabId = storedActiveTabId && tabs.some(tab => tab.id === storedActiveTabId)
            ? storedActiveTabId
            : tabs[0].id;

        const tab = getActiveTab();
        if (tab) {
            setFormData(tab.data || {});
        }

        renderTabs();
        startTimer();
        updateCounters();
        validateForm(false);
        updateDraftStatus();
    }

    function closePipOnTabReturn() {
        if (isPopupMode() || isPipMode()) {
            return;
        }

        window.ConversationPopup?.closePictureInPicture?.();
        pipOpenAttempted = false;
        syncWorkspaceFromStorage();
    }

    function closeFloatingWindow() {
        if (isPipMode() && window.parent && window.parent !== window) {
            window.parent.close();
            return;
        }

        window.close();
        setTimeout(() => {
            if (!window.closed) {
                window.location.href = "/conversations";
            }
        }, 120);
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
        clearSessionAttention();
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

    function requestSessionNotificationPermission() {
        if (!("Notification" in window) || Notification.permission !== "default") {
            return;
        }

        Notification.requestPermission().catch(() => {});
    }

    function notifySessionAttention() {
        if (!document.hidden || sessionNotificationSent || !("Notification" in window)) {
            return;
        }

        if (Notification.permission === "granted") {
            new Notification("Sesion por expirar", {
                body: sessionAlertMessage,
                tag: "skill-soporte-session",
                requireInteraction: true
            });
            sessionNotificationSent = true;
        }
    }

    function startSessionTitleAlert() {
        if (sessionTitleInterval) {
            return;
        }

        let showAlert = true;
        document.title = sessionAlertTitle;

        sessionTitleInterval = setInterval(() => {
            document.title = showAlert
                ? sessionAlertTitle
                : originalDocumentTitle;
            showAlert = !showAlert;
        }, 1200);
    }

    function clearSessionAttention() {
        sessionRenewalPending = false;
        sessionNotificationSent = false;
        sessionToastShown = false;

        if (sessionTitleInterval) {
            clearInterval(sessionTitleInterval);
            sessionTitleInterval = null;
        }

        document.title = originalDocumentTitle;
    }

    function hideSessionPrompt() {
        const prompt = document.getElementById("sessionContinuePrompt");

        if (prompt) {
            prompt.classList.add("hidden");
        }
    }

    function hideCommerceNamePrompt() {
        const prompt = document.getElementById("commerceNamePrompt");

        if (prompt) {
            prompt.classList.add("hidden");
        }

        const field = getField("nombreComercio");
        if (field) {
            field.focus();
        }
    }

    function showCommerceNamePrompt() {
        const prompt = document.getElementById("commerceNamePrompt");

        if (!prompt) {
            return;
        }

        prompt.classList.remove("hidden");
    }

    function showSessionPrompt() {
        const prompt = document.getElementById("sessionContinuePrompt");

        if (!prompt) {
            return;
        }

        sessionRenewalPending = true;
        startSessionTitleAlert();
        notifySessionAttention();
        if (!sessionToastShown) {
            showToast("Tu sesion esta por vencer. Presiona Continuar trabajando.", true);
            sessionToastShown = true;
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
            clearSessionAttention();
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

    function hasMeaningfulData(data = {}) {
        const ignoredFields = new Set([
            "_csrf",
            "fechaInicio",
            "tiempoGestionMinutos",
            "channelId",
            "statusId",
            "priorityId",
            "ticketAperturado",
            "conversacionTransferida"
        ]);

        return Object.entries(data).some(([key, value]) => {
            if (ignoredFields.has(key)) return false;
            if (typeof value === "boolean") return value;
            return String(value || "").trim().length > 0;
        });
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
        tab.dirty = hasMeaningfulData(tab.data);
        tab.lastDraftSave = Date.now();
        saveState();
    }

    function updateDraftStatus(message) {
        const draftStatus = document.getElementById("draftStatus");
        if (!draftStatus) return;

        const tab = getActiveTab();
        if (!tab || !tab.dirty) {
            draftStatus.innerText = "Borrador listo";
            return;
        }

        const savedAt = tab.lastDraftSave
            ? new Date(tab.lastDraftSave).toLocaleTimeString("es-GT", {
                hour: "2-digit",
                minute: "2-digit"
            })
            : "";

        draftStatus.innerText = message || `Borrador guardado ${savedAt}`;
    }

    function getTabLabel(tab) {
        if (tab.saved && tab.codigo) {
            return `Guardada ${tab.codigo}`;
        }

        const status = tab.running
            ? "En gestion"
            : "Pausada";

        const name =
            tab.data?.clienteNombre ||
            tab.data?.cliente_nombre ||
            tab.data?.cliente ||
            "Nueva conversacion";

        return `${status} - ${name}`;
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
                "conversation-tab" +
                (tab.id === activeTabId ? " active" : "");

            button.innerHTML = `
                <span>${getTabLabel(tab)}</span>
                <span class="conversation-tab-close" data-close-tab="true">x</span>
            `;

            button.onclick = (event) => {
                if (event.target.dataset.closeTab === "true") {
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
            btn.innerText = "Pausar";
            btn.className = "timer-action running";

            if (status) {
                status.innerText = "En gestion";
                status.className = "timer-status running mt-2";
            }
        } else {
            btn.innerText = "Reanudar";
            btn.className = "timer-action paused";

            if (status) {
                status.innerText = "Pausado";
                status.className = "timer-status paused mt-2";
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

    function resumeTimerForMissingCommerceName() {
        const tab = getActiveTab();
        if (!tab) return;

        normalizeTab(tab);

        if (!tab.running) {
            tab.running = true;
            tab.lastResume = Date.now();
        }

        saveState();
        renderTabs();
        startTimer();
    }

    function closeTab(id) {
        const tabToClose = tabs.find(t => t.id === id);
        if (tabToClose && tabToClose.dirty && !tabToClose.saved) {
            const shouldClose = confirm("Hay datos capturados sin guardar. ¿Deseas cerrar este borrador?");
            if (!shouldClose) return;
        }

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

    function getField(name) {
        const form = getForm();
        return form ? form.querySelector(`[name="${name}"]`) : null;
    }

    function setFieldError(name, message, showErrors) {
        const field = getField(name);
        const error = document.querySelector(`[data-error-for="${name}"]`);

        if (field) {
            field.classList.toggle("field-error", Boolean(message) && showErrors);
        }

        if (error) {
            error.innerText = message || "";
            error.classList.toggle("visible", Boolean(message) && showErrors);
        }
    }

    function valueOf(name) {
        const field = getField(name);
        return field ? String(field.value || "").trim() : "";
    }

    function isResolvedOrClosed() {
        const status = valueOf("statusId");
        return status === "3" || status === "5";
    }

    function normalizedText(value) {
        return String(value || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();
    }

    function selectedIssueText() {
        const issue = getField("issueTypeId");
        if (!issue || !issue.options || issue.selectedIndex < 0) {
            return "";
        }

        return issue.options[issue.selectedIndex].textContent || "";
    }

    function isMiscQuestionIssue() {
        return normalizedText(selectedIssueText()) === "dudas varias";
    }

    function isCommerceNameMissing() {
        return !valueOf("nombreComercio");
    }

    function validateForm(showErrors = false) {
        let valid = true;
        const requiredFields = [
            ["clienteNombre", "Ingresa el nombre del cliente."],
            ["channelId", "Selecciona el canal de origen."],
            ["issueTypeId", "Selecciona el asunto."],
            ["statusId", "Selecciona el estado."],
            ["priorityId", "Selecciona la prioridad."]
        ];

        requiredFields.forEach(([name, message]) => {
            const missing = !valueOf(name);
            setFieldError(name, missing ? message : "", showErrors);
            valid = valid && !missing;
        });

        const missingCommerceName = isCommerceNameMissing();
        setFieldError("nombreComercio", missingCommerceName ? "Ingresa el nombre comercio." : "", showErrors);
        valid = valid && !missingCommerceName;

        const rejectionBox = document.getElementById("rejectionCodeBox");
        const requiresRejection = rejectionBox && !rejectionBox.classList.contains("hidden");
        const missingRejection = requiresRejection && !valueOf("rejectionCodeId");
        setFieldError("rejectionCodeId", missingRejection ? "Selecciona el codigo de rechazo." : "", showErrors);
        valid = valid && !missingRejection;

        setFieldError("numeroTicket", "", showErrors);

        const transferCheck = document.getElementById("conversacionTransferida");
        const missingDepartment = transferCheck && transferCheck.checked && !valueOf("departmentId");
        setFieldError("departmentId", missingDepartment ? "Selecciona el departamento destino." : "", showErrors);
        valid = valid && !missingDepartment;

        const descriptionLength = valueOf("descripcion").length;
        const observationsLength = valueOf("observaciones").length;
        const missingMiscQuestionDescription = isMiscQuestionIssue() && descriptionLength === 0;
        const missingClosureDetail = isResolvedOrClosed() && descriptionLength < 30 && observationsLength < 15;
        const closureMessage = "Para resolver o cerrar, agrega una descripcion clara o una observacion interna.";
        const descriptionMessage = missingMiscQuestionDescription
            ? "Para Dudas varias, la descripcion de la conversacion es obligatoria."
            : (missingClosureDetail ? closureMessage : "");
        setHint("descriptionHint", descriptionMessage, showErrors);
        setHint("observationsHint", missingClosureDetail ? "Minimo sugerido: descripcion de 30 caracteres u observacion de 15." : "", showErrors);
        if (descripcion) {
            descripcion.classList.toggle("field-error", missingMiscQuestionDescription && showErrors);
        }
        valid = valid && !missingMiscQuestionDescription;
        valid = valid && !missingClosureDetail;

        updateCounters();
        return valid;
    }

    function setHint(id, message, showErrors) {
        const hint = document.getElementById(id);
        if (!hint) return;

        hint.innerText = message || "";
        hint.classList.toggle("visible", Boolean(message) && showErrors);
    }

    function updateCounters() {
        const description = document.getElementById("descripcion");
        const observations = document.getElementById("observaciones");
        const descriptionCounter = document.getElementById("descriptionCounter");
        const observationsCounter = document.getElementById("observationsCounter");

        if (description && descriptionCounter) {
            descriptionCounter.innerText = description.value.length;
            descriptionCounter.parentElement?.classList.toggle(
                "counter-warning",
                isResolvedOrClosed() && description.value.trim().length < 30
            );
        }

        if (observations && observationsCounter) {
            observationsCounter.innerText = observations.value.length;
            observationsCounter.parentElement?.classList.toggle(
                "counter-warning",
                isResolvedOrClosed() && observations.value.trim().length < 15
            );
        }
    }

    function applyTemplate(text) {
        const description = document.getElementById("descripcion");
        if (!description || !text) return;

        const separator = description.value.trim() ? "\n" : "";
        description.value = `${description.value.trim()}${separator}${text}`;
        description.dispatchEvent(new Event("input", { bubbles: true }));
        description.focus();
    }

    function getRecentClients() {
        try {
            return JSON.parse(localStorage.getItem(recentClientsKey) || "[]");
        } catch (error) {
            return [];
        }
    }

    function hydrateRecentClientSuggestions() {
        const clients = getRecentClients();
        renderClientSuggestions(clients);
    }

    function renderClientSuggestions(clients = []) {
        const mappings = [
            ["recentClients", "name"],
            ["recentPhones", "phone"],
            ["recentEmails", "email"]
        ];

        mappings.forEach(([id, key]) => {
            const list = document.getElementById(id);
            if (!list) return;

            list.innerHTML = "";
            clients
                .map(client => client[key])
                .filter(Boolean)
                .slice(0, 10)
                .forEach(value => {
                    const option = document.createElement("option");
                    option.value = value;
                    list.appendChild(option);
                });
        });
    }

    function activeClientSearchTerm() {
        return [
            valueOf("clienteNombre"),
            valueOf("clienteTelefono"),
            valueOf("clienteCorreo")
        ].find(value => value && value.trim().length >= 2);
    }

    async function searchZohoClients() {
        const term = activeClientSearchTerm();
        if (!term) {
            zohoClientSuggestions = [];
            hydrateRecentClientSuggestions();
            return;
        }

        try {
            const response = await fetch(`/api/clients/search?q=${encodeURIComponent(term)}`);
            if (!response.ok) {
                return;
            }

            zohoClientSuggestions = await response.json();
            renderClientSuggestions([
                ...zohoClientSuggestions,
                ...getRecentClients()
            ]);
        } catch (error) {
            console.warn("No fue posible buscar clientes sincronizados", error);
        }
    }

    function scheduleClientSearch() {
        if (clientSearchTimeout) {
            clearTimeout(clientSearchTimeout);
        }

        clientSearchTimeout = setTimeout(searchZohoClients, 280);
    }

    function fillClientFromSuggestion() {
        const currentValues = [
            valueOf("clienteNombre"),
            valueOf("clienteTelefono"),
            valueOf("clienteCorreo")
        ]
            .filter(Boolean)
            .map(value => String(value).trim().toLowerCase());

        const selected = zohoClientSuggestions.find(client =>
            [client.name, client.phone, client.mobile, client.email]
                .filter(Boolean)
                .some(value => currentValues.includes(String(value).trim().toLowerCase()))
        );

        if (!selected) {
            return;
        }

        const form = getForm();
        const fields = {
            zohoContactId: selected.zohoContactId,
            clienteNombre: selected.name,
            clienteTelefono: selected.phone || selected.mobile,
            clienteCorreo: selected.email
        };

        Object.entries(fields).forEach(([fieldName, value]) => {
            if (!value) return;
            const field = form.elements[fieldName];
            if (field) {
                field.value = value;
            }
        });

        saveActiveTabData();
        validateForm(validationActive);
    }

    function rememberCurrentClient() {
        const name = valueOf("clienteNombre");
        const phone = valueOf("clienteTelefono");
        const email = valueOf("clienteCorreo");

        if (!name && !phone && !email) return;

        const clients = getRecentClients()
            .filter(client => client.name !== name && client.phone !== phone && client.email !== email);

        clients.unshift({ name, phone, email, savedAt: Date.now() });
        localStorage.setItem(recentClientsKey, JSON.stringify(clients.slice(0, 10)));
        hydrateRecentClientSuggestions();
    }

    function publishWorkspaceEvent(payload) {
        const message = {
            ...payload,
            source: "conversation-popup",
            sentAt: Date.now()
        };

        try {
            if ("BroadcastChannel" in window) {
                const channel = new BroadcastChannel(workspaceChannelName);
                channel.postMessage(message);
                channel.close();
            }
        } catch (error) {
            console.warn("No fue posible publicar evento BroadcastChannel", error);
        }

        try {
            localStorage.setItem("conversationWorkspaceEvent", JSON.stringify(message));
        } catch (error) {
            console.warn("No fue posible publicar evento localStorage", error);
        }

        if (window.opener && !window.opener.closed) {
            window.opener.postMessage(message, window.location.origin);
        }
    }

    function setFormBusy(isBusy) {
        const form = getForm();
        if (!form) return;

        form.classList.toggle("is-saving", isBusy);
        updateDraftStatus(isBusy ? "Guardando..." : undefined);
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
            showToast("Esta conversacion ya se esta guardando.", true);
            return;
        }

        if (isCommerceNameMissing()) {
            validationActive = true;
            validateForm(true);
            resumeTimerForMissingCommerceName();
            showCommerceNamePrompt();
            return;
        }

        if (!validateForm(true)) {
            validationActive = true;
            showToast("Revisa los campos marcados antes de guardar.", true);
            return;
        }

        const submitButtons = document.querySelectorAll(
            'button[type="submit"][form="conversationForm"], #conversationForm button[type="submit"]'
        );

        try {
            tab.saving = true;
            validationActive = false;

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

            submitButtons.forEach(button => {
                button.disabled = true;
                button.dataset.originalText = button.innerHTML;
                button.innerHTML = "Guardando...";
            });
            setFormBusy(true);

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
            tab.dirty = false;
            tab.codigo = data.codigo;
            tab.conversationId = data.id;
            tab.saving = false;
            rememberCurrentClient();
            publishWorkspaceEvent({
                type: "conversation-saved",
                id: data.id,
                codigo: data.codigo
            });

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
            setFormBusy(false);
            submitButtons.forEach(button => {
                button.disabled = false;
                button.innerHTML =
                    button.dataset.originalText ||
                    "Guardar conversacion";
            });
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

            workspaceStorage.removeItem(activeTabStorageKey);
            sessionStorage.removeItem(activeTabStorageKey);

            workspaceStorage.setItem(tabsStorageKey, JSON.stringify([]));
            sessionStorage.setItem(tabsStorageKey, JSON.stringify([]));

            console.log(
                "No quedan conversaciones activas. Redirigiendo..."
            );

            setTimeout(() => {
                if (isPopupMode()) {
                    closeFloatingWindow();
                    return;
                }

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

        const commerceNameAcceptButton = document.getElementById("commerceNameAcceptButton");
        if (commerceNameAcceptButton) {
            commerceNameAcceptButton.addEventListener("click", hideCommerceNamePrompt);
        }

        form.addEventListener("input", () => {
            saveActiveTabData();
            validateForm(validationActive);
            updateCounters();
            renderTabs();
        });

        form.addEventListener("change", () => {
            saveActiveTabData();
            validateForm(validationActive);
            updateCounters();
            renderTabs();
        });

        form.querySelectorAll("[required], #numeroTicket, #departmentId").forEach(field => {
            field.addEventListener("blur", () => {
                validateForm(true);
            });
        });

        form.querySelectorAll('[name="clienteNombre"], [name="clienteTelefono"], [name="clienteCorreo"]').forEach(field => {
            field.addEventListener("input", scheduleClientSearch);
            field.addEventListener("change", fillClientFromSuggestion);
        });

        document.querySelectorAll("[data-template-text]").forEach(button => {
            button.addEventListener("click", () => {
                applyTemplate(button.dataset.templateText);
            });
        });

        document.addEventListener("keydown", event => {
            if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
                event.preventDefault();
                form.requestSubmit();
            }

            if (event.altKey && event.key.toLowerCase() === "n") {
                event.preventDefault();
                addTab();
            }

            if (event.key === "Escape") {
                if (!sessionRenewalPending) {
                    hideSessionPrompt();
                }
            }
        });

        document.addEventListener("visibilitychange", () => {
            if (document.hidden) {
                openPipOnTabLeave();
                notifySessionAttention();
                return;
            }

            closePipOnTabReturn();

            if (sessionRenewalPending && !sessionExpired) {
                showSessionPrompt();
            }
        });

        window.addEventListener("focus", closePipOnTabReturn);
        window.addEventListener("pageshow", closePipOnTabReturn);

        window.addEventListener("beforeunload", event => {
            if (window.__conversationSilentClose) {
                return;
            }

            const hasUnsavedDrafts = tabs.some(tab => tab.dirty && !tab.saved);
            if (!hasUnsavedDrafts) return;

            event.preventDefault();
            event.returnValue = "";
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

        requestSessionNotificationPermission();
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
        hydrateRecentClientSuggestions();
        updateCounters();
        validateForm(false);
        updateDraftStatus();

        console.log("Conversation Workspace v2 inicializado");
    }

    return {
        init,
        addTab,
        switchTab,
        closeTab,
        toggleTimer,
        continueWorking,
        closeFloatingWindow,
        persistActiveTabData: saveActiveTabData
    };
})();

document.addEventListener("DOMContentLoaded", () => {
    ConversationWorkspace.init();
});
