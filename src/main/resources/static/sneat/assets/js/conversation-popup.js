(function () {
    const popupName = "insightsDeskConversationPopup";
    const popupFeatures = [
        "width=1040",
        "height=820",
        "left=120",
        "top=60",
        "resizable=yes",
        "scrollbars=yes"
    ].join(",");
    const workspaceChannelName = "conversation-workspace";
    let refreshScheduled = false;

    function scheduleRefresh() {
        if (refreshScheduled) {
            return;
        }

        refreshScheduled = true;
        setTimeout(() => {
            window.location.reload();
        }, 700);
    }

    function handleWorkspaceEvent(message) {
        if (!message || message.type !== "conversation-saved") {
            return;
        }

        if (window.location.pathname === "/conversations/create") {
            return;
        }

        scheduleRefresh();
    }

    function buildFloatingCreateUrl(url, pipMode) {
        const target = new URL(url || "/conversations/create", window.location.origin);
        target.searchParams.set("popup", "true");
        if (pipMode) {
            target.searchParams.set("pip", "true");
        } else {
            target.searchParams.delete("pip");
        }

        return target.href;
    }

    function buildStandardCreateUrl(url) {
        const target = new URL(url || "/conversations/create", window.location.origin);
        target.searchParams.delete("popup");
        target.searchParams.delete("pip");
        return target.href;
    }

    function openRegularPopup(url) {
        const popupUrl = buildFloatingCreateUrl(url, false);
        const popup = window.open(popupUrl, popupName, popupFeatures);

        if (!popup) {
            window.location.href = popupUrl;
            return;
        }

        popup.focus();
    }

    function writeLoadingDocument(pipWindow) {
        pipWindow.document.open();
        pipWindow.document.write(`<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Nueva conversacion</title>
    <style>
        html,
        body {
            width: 100%;
            height: 100%;
            margin: 0;
            display: grid;
            place-items: center;
            background: #f5f7fb;
            color: #07113f;
            font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }
        .pip-loading {
            display: grid;
            gap: 10px;
            text-align: center;
            font-size: 14px;
            font-weight: 900;
        }
        .pip-loading::before {
            content: "";
            width: 36px;
            height: 36px;
            justify-self: center;
            border: 4px solid #e3e7f2;
            border-top-color: #6236ff;
            border-radius: 999px;
            animation: spin .8s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
    </style>
</head>
<body>
    <div class="pip-loading">Abriendo nueva conversacion...</div>
</body>
</html>`);
        pipWindow.document.close();
    }

    function injectBaseHref(html) {
        const base = `<base href="${window.location.origin}/">`;
        if (/<head[^>]*>/i.test(html)) {
            return html.replace(/<head([^>]*)>/i, `<head$1>${base}`);
        }
        return html.replace(/<html([^>]*)>/i, `<html$1><head>${base}</head>`);
    }

    async function writeCreatePageIntoPip(pipWindow, url) {
        const response = await fetch(url, {
            credentials: "same-origin",
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        });

        if (!response.ok) {
            throw new Error("No fue posible cargar la ventana de nueva conversacion.");
        }

        const html = injectBaseHref(await response.text());
        pipWindow.document.open();
        pipWindow.document.write(html);
        pipWindow.document.close();
        pipWindow.focus();
    }

    async function openPictureInPicture(url, options = {}) {
        const fallbackToPopup = options.fallbackToPopup !== false;
        if (!("documentPictureInPicture" in window)) {
            if (fallbackToPopup) {
                openRegularPopup(url);
            }
            return;
        }

        if (navigator.userActivation?.isActive !== true) {
            if (fallbackToPopup) {
                openRegularPopup(url);
            }
            return;
        }

        let pipWindow = null;
        try {
            if (window.documentPictureInPicture.window && !window.documentPictureInPicture.window.closed) {
                window.documentPictureInPicture.window.close();
            }

            pipWindow = await window.documentPictureInPicture.requestWindow({
                width: 1040,
                height: 820
            });
            const pipUrl = buildFloatingCreateUrl(url, true);
            writeLoadingDocument(pipWindow);
            await writeCreatePageIntoPip(pipWindow, pipUrl);
        } catch (error) {
            if (pipWindow && !pipWindow.closed) {
                pipWindow.close();
            }
            if (fallbackToPopup) {
                console.warn("Document Picture-in-Picture no disponible, usando popup normal", error);
                openRegularPopup(url);
            } else if (error?.name !== "NotAllowedError") {
                console.warn("No fue posible abrir Picture-in-Picture automaticamente", error);
            }
        }
    }

    function closePictureInPicture() {
        if (!("documentPictureInPicture" in window)) {
            return false;
        }

        const pipWindow = window.documentPictureInPicture.window;
        if (!pipWindow || pipWindow.closed) {
            return false;
        }

        try {
            pipWindow.ConversationWorkspace?.persistActiveTabData?.();
            pipWindow.__conversationSilentClose = true;
        } catch (error) {
            console.warn("No fue posible sincronizar la ventana flotante antes de cerrarla", error);
        }

        pipWindow.close();
        return true;
    }

    window.ConversationPopup = {
        openPictureInPicture,
        openRegularPopup,
        buildStandardCreateUrl,
        closePictureInPicture
    };

    window.openConversationPopup = function (url) {
        window.location.href = buildStandardCreateUrl(url);
        return false;
    };

    if ("BroadcastChannel" in window) {
        const channel = new BroadcastChannel(workspaceChannelName);
        channel.addEventListener("message", event => handleWorkspaceEvent(event.data));
    }

    window.addEventListener("message", event => {
        if (event.origin !== window.location.origin) {
            return;
        }

        handleWorkspaceEvent(event.data);
    });

    window.addEventListener("storage", event => {
        if (event.key !== "conversationWorkspaceEvent" || !event.newValue) {
            return;
        }

        try {
            handleWorkspaceEvent(JSON.parse(event.newValue));
        } catch (error) {
            console.warn("No fue posible leer evento de conversacion", error);
        }
    });
})();
