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

        scheduleRefresh();
    }

    function buildCreateUrl(url, pipMode) {
        const target = new URL(url || "/conversations/create", window.location.origin);
        target.searchParams.set("popup", "true");
        if (pipMode) {
            target.searchParams.set("pip", "true");
        } else {
            target.searchParams.delete("pip");
        }

        return target.href;
    }

    function openRegularPopup(url) {
        const popupUrl = buildCreateUrl(url, false);
        const popup = window.open(popupUrl, popupName, popupFeatures);

        if (!popup) {
            window.location.href = popupUrl;
            return;
        }

        popup.focus();
    }

    async function openPictureInPicture(url) {
        if (!("documentPictureInPicture" in window)) {
            openRegularPopup(url);
            return;
        }

        try {
            if (window.documentPictureInPicture.window && !window.documentPictureInPicture.window.closed) {
                window.documentPictureInPicture.window.close();
            }

            const pipWindow = await window.documentPictureInPicture.requestWindow({
                width: 1040,
                height: 820
            });
            const pipUrl = buildCreateUrl(url, true);

            pipWindow.document.title = "Nueva conversacion";
            pipWindow.document.body.innerHTML = "";

            const style = pipWindow.document.createElement("style");
            style.textContent = `
                html,
                body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    overflow: hidden;
                    background: #f5f7fb;
                    font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }

                .pip-frame {
                    width: 100%;
                    height: 100%;
                    border: 0;
                    display: block;
                    background: #f5f7fb;
                }

                .pip-loading {
                    position: fixed;
                    inset: 0;
                    display: grid;
                    place-items: center;
                    color: #07113f;
                    font-size: 14px;
                    font-weight: 900;
                }
            `;

            const loading = pipWindow.document.createElement("div");
            loading.className = "pip-loading";
            loading.textContent = "Abriendo nueva conversacion...";

            const iframe = pipWindow.document.createElement("iframe");
            iframe.className = "pip-frame";
            iframe.src = pipUrl;
            iframe.title = "Nueva conversacion";
            iframe.addEventListener("load", () => loading.remove());

            pipWindow.document.head.appendChild(style);
            pipWindow.document.body.appendChild(loading);
            pipWindow.document.body.appendChild(iframe);
        } catch (error) {
            console.warn("Document Picture-in-Picture no disponible, usando popup normal", error);
            openRegularPopup(url);
        }
    }

    window.openConversationPopup = function (url) {
        openPictureInPicture(url);
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
