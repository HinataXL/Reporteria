(function () {
    "use strict";

    if (window.__insightsErrorMonitorLoaded) {
        return;
    }
    window.__insightsErrorMonitorLoaded = true;

    const debugEnabled = window.INSIGHTS_DEBUG === true
        || window.localStorage?.getItem("insightsDebug") === "true";
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
    const sentSignatures = new Set();
    const maxReportsPerPage = 25;
    let reportsSent = 0;

    const originalConsole = {
        log: console.log?.bind(console),
        info: console.info?.bind(console),
        debug: console.debug?.bind(console),
        warn: console.warn?.bind(console),
        error: console.error?.bind(console)
    };

    function trim(value, maxLength) {
        if (value === undefined || value === null) {
            return "";
        }

        const text = String(value);
        return text.length > maxLength ? text.slice(0, maxLength) : text;
    }

    function serialize(value) {
        if (value instanceof Error) {
            return value.stack || value.message || value.name;
        }

        if (typeof value === "string") {
            return value;
        }

        try {
            return JSON.stringify(value);
        } catch (ignored) {
            return String(value);
        }
    }

    function buildSignature(payload) {
        return [
            payload.level,
            payload.eventType,
            trim(payload.message, 250),
            payload.source,
            payload.lineNumber,
            payload.columnNumber,
            location.pathname
        ].join("|");
    }

    function report(payload) {
        if (!csrfToken || reportsSent >= maxReportsPerPage) {
            return;
        }

        const cleanPayload = {
            level: trim(payload.level || "ERROR", 30),
            eventType: trim(payload.eventType || "browser", 80),
            message: trim(payload.message || "Error sin mensaje", 4000),
            source: trim(payload.source || "", 4000),
            stackTrace: trim(payload.stackTrace || "", 4000),
            pageUrl: trim(payload.pageUrl || location.href, 4000),
            lineNumber: Number.isFinite(payload.lineNumber) ? payload.lineNumber : null,
            columnNumber: Number.isFinite(payload.columnNumber) ? payload.columnNumber : null
        };
        const signature = buildSignature(cleanPayload);

        if (sentSignatures.has(signature)) {
            return;
        }

        sentSignatures.add(signature);
        reportsSent += 1;

        fetch("/api/frontend-errors", {
            method: "POST",
            credentials: "same-origin",
            keepalive: true,
            headers: {
                "Content-Type": "application/json",
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(cleanPayload)
        }).catch(function () {
            // El monitor nunca debe generar ruido adicional si falla el reporte.
        });
    }

    window.addEventListener("error", function (event) {
        report({
            level: "ERROR",
            eventType: "window.error",
            message: event.message || serialize(event.error),
            source: event.filename || "",
            stackTrace: event.error?.stack || "",
            lineNumber: event.lineno,
            columnNumber: event.colno
        });
    }, true);

    window.addEventListener("unhandledrejection", function (event) {
        report({
            level: "ERROR",
            eventType: "unhandledrejection",
            message: serialize(event.reason),
            source: "Promise",
            stackTrace: event.reason?.stack || ""
        });
    });

    ["warn", "error"].forEach(function (method) {
        console[method] = function () {
            const args = Array.from(arguments);
            const firstError = args.find(function (arg) {
                return arg instanceof Error;
            });

            report({
                level: method === "warn" ? "WARN" : "ERROR",
                eventType: "console." + method,
                message: args.map(serialize).join(" "),
                source: "console",
                stackTrace: firstError?.stack || ""
            });

            if (debugEnabled && originalConsole[method]) {
                originalConsole[method].apply(console, args);
            }
        };
    });

    ["log", "info", "debug"].forEach(function (method) {
        console[method] = function () {
            if (debugEnabled && originalConsole[method]) {
                originalConsole[method].apply(console, arguments);
            }
        };
    });
})();
