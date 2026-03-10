// background.js - Service Worker for SupaPhone

const STORAGE_KEYS = {
    devices: "devices",
    logs: "logs",
    pairingCode: "pairingCode",
    browserClientId: "browserClientId",
    browserClientSecret: "browserClientSecret",
    browserSecretRotatedAt: "browserSecretRotatedAt"
};

const MENU_SEND_PARENT = "supaphone-send-link-parent";
const MENU_CALL_PARENT = "supaphone-call-number-parent";
const LOG_RETENTION_MS = 24 * 60 * 60 * 1000;
const MAX_LOG_ENTRIES = 200;
const MAX_DEVICE_COUNT = 5;
const MAX_LOG_MESSAGE_LENGTH = 220;
const MAX_USER_ERROR_LENGTH = 140;
const PAIRING_CODE_MAX_AGE_MS = 150 * 1000;
const BROWSER_SECRET_ROTATION_MS = 24 * 60 * 60 * 1000;
const VALID_ICON_STATUSES = new Set(["default", "success", "error-server", "error-client"]);
let backendConfigLoadError = null;
let pairingCodeRefreshInFlight = null;
let browserIdentityCache = null;
let browserIdentityLoadInFlight = null;
let browserSecretRotationInFlight = null;

function hasBackendConfigValues(config) {
    return Boolean(
        config &&
        typeof config === "object" &&
        (
            String(config.supabaseUrl || "").trim() ||
            String(config.supabaseAnonKey || "").trim() ||
            String(config.edgeBaseUrl || "").trim()
        )
    );
}

try {
    importScripts("config.runtime.js");
} catch (_error) {
    // Store packages may include config.runtime.js while local development uses config.local.js.
}

const hasRuntimeBackendConfig =
    typeof self !== "undefined" &&
    hasBackendConfigValues(self.SUPAPHONE_BACKEND_CONFIG);

if (!hasRuntimeBackendConfig) {
    try {
        importScripts("config.local.js");
    } catch (error) {
        backendConfigLoadError = error instanceof Error ? error.message : String(error || "Unknown config load error");
    }
}

const localBackendConfig =
    typeof self !== "undefined" && self.SUPAPHONE_BACKEND_CONFIG
        ? self.SUPAPHONE_BACKEND_CONFIG
        : {};

// Manual intervention required:
// Local development uses browser-extension/config.local.js (git-ignored).
// Store builds inject config.runtime.js during the release packaging step.
const BACKEND_CONFIG = {
    supabaseUrl: localBackendConfig.supabaseUrl || "__SUPABASE_URL__",
    supabaseAnonKey: localBackendConfig.supabaseAnonKey || "__SUPABASE_ANON_KEY__",
    edgeBaseUrl: localBackendConfig.edgeBaseUrl || "__SUPABASE_EDGE_BASE_URL__"
};

function backendUnavailableReason() {
    if (backendConfigLoadError) {
        return `Backend configuration could not be loaded: ${backendConfigLoadError}`;
    }
    if (!BACKEND_CONFIG.supabaseUrl || BACKEND_CONFIG.supabaseUrl.includes("__")) {
        return "Missing backend Supabase URL in extension configuration.";
    }
    if (!BACKEND_CONFIG.supabaseAnonKey || BACKEND_CONFIG.supabaseAnonKey.includes("__")) {
        return "Missing backend anon key in extension configuration.";
    }
    if (!BACKEND_CONFIG.edgeBaseUrl || BACKEND_CONFIG.edgeBaseUrl.includes("__")) {
        return "Missing backend edge base URL in extension configuration.";
    }
    return "";
}

function backendConfigured() {
    return backendUnavailableReason() === "";
}

function edgeBaseUrl() {
    return BACKEND_CONFIG.edgeBaseUrl.replace(/\/+$/, "");
}

function compactText(value, maxLength) {
    const compact = String(value ?? "").replace(/\s+/g, " ").trim();
    if (!compact) {
        return "";
    }
    if (!Number.isFinite(maxLength) || maxLength <= 0 || compact.length <= maxLength) {
        return compact;
    }
    return `${compact.slice(0, maxLength - 3)}...`;
}

function toUserErrorMessage(error, fallback = "Request failed. Please try again.") {
    const raw =
        error instanceof Error
            ? error.message
            : typeof error === "string"
                ? error
                : "";
    const compact = compactText(raw, MAX_USER_ERROR_LENGTH);
    if (!compact) {
        return fallback;
    }
    if (/credentials are not registered|is not registered|re-pair and try again/i.test(compact)) {
        return "This browser is no longer registered. Pair it again from the extension.";
    }
    if (/credentials do not match this installation/i.test(compact)) {
        return "This browser identity no longer matches the backend. Reset browser identity and pair again.";
    }
    if (/failed to fetch|networkerror|network error|timed out|timeout/i.test(compact)) {
        return "Network issue detected. Check connection and retry.";
    }
    if (/unauthorized|401|403/i.test(compact)) {
        return "Service authentication failed. Please verify setup.";
    }
    if (/missing backend|backend configuration could not be loaded/i.test(compact)) {
        return "Extension backend setup is incomplete. Check the release or local backend configuration.";
    }
    return compact;
}

function clampNumber(value, min, max) {
    const number = Number(value);
    if (Number.isNaN(number)) {
        return min;
    }
    return Math.max(min, Math.min(max, Math.round(number)));
}

function isValidClientId(value) {
    return /^[a-zA-Z0-9][a-zA-Z0-9._:-]{2,127}$/.test(String(value || "").trim());
}

function isValidClientSecret(value) {
    return /^[A-Za-z0-9][A-Za-z0-9._:-]{31,255}$/.test(String(value || "").trim());
}

function randomHex(bytesLength = 16) {
    const bytes = new Uint8Array(bytesLength);
    crypto.getRandomValues(bytes);
    return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
}

function generateBrowserClientId() {
    if (typeof crypto?.randomUUID === "function") {
        return `browser-${crypto.randomUUID()}`;
    }
    return `browser-${randomHex(16)}`;
}

function generateBrowserClientSecret() {
    const bytes = new Uint8Array(32);
    crypto.getRandomValues(bytes);
    let binary = "";
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    const base64 = btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
    return `sec_${base64}`;
}

async function getOrCreateBrowserIdentity() {
    if (browserIdentityCache) {
        return browserIdentityCache;
    }
    if (browserIdentityLoadInFlight) {
        return browserIdentityLoadInFlight;
    }

    browserIdentityLoadInFlight = (async () => {
        const data = await chrome.storage.local.get([
            STORAGE_KEYS.browserClientId,
            STORAGE_KEYS.browserClientSecret,
            STORAGE_KEYS.devices,
            STORAGE_KEYS.pairingCode
        ]);
        let browserClientId = String(data[STORAGE_KEYS.browserClientId] ?? "").trim();
        let browserClientSecret = String(data[STORAGE_KEYS.browserClientSecret] ?? "").trim();
        const existingDevices = Array.isArray(data[STORAGE_KEYS.devices])
            ? data[STORAGE_KEYS.devices]
            : [];
        let mustResetPairingState = false;

        if (!isValidClientId(browserClientId)) {
            browserClientId = generateBrowserClientId();
            mustResetPairingState = existingDevices.length > 0;
        }
        if (!isValidClientSecret(browserClientSecret)) {
            browserClientSecret = generateBrowserClientSecret();
            mustResetPairingState = mustResetPairingState || existingDevices.length > 0;
        }

        const updates = {
            [STORAGE_KEYS.browserClientId]: browserClientId,
            [STORAGE_KEYS.browserClientSecret]: browserClientSecret
        };
        if (!data[STORAGE_KEYS.browserSecretRotatedAt]) {
            updates[STORAGE_KEYS.browserSecretRotatedAt] = new Date().toISOString();
        }
        if (mustResetPairingState) {
            updates[STORAGE_KEYS.devices] = [];
            updates[STORAGE_KEYS.pairingCode] = null;
        }

        await chrome.storage.local.set(updates);
        if (mustResetPairingState) {
            await addLog(
                "info",
                "Security upgrade applied for this browser identity. Re-pair devices from the extension."
            );
        }

        browserIdentityCache = { browserClientId, browserClientSecret };
        return browserIdentityCache;
    })();

    try {
        return await browserIdentityLoadInFlight;
    } finally {
        browserIdentityLoadInFlight = null;
    }
}

function toSafeDeviceName(name) {
    const text = typeof name === "string" ? name.trim() : "";
    if (!text) {
        return "Paired Device";
    }
    return text.slice(0, 32);
}

function minutesSince(isoTimestamp) {
    const parsed = new Date(isoTimestamp || "");
    if (!Number.isFinite(parsed.getTime())) {
        return 0;
    }
    return Math.max(0, Math.round((Date.now() - parsed.getTime()) / 60000));
}

function normalizeDevice(device) {
    const rawId = typeof device?.id === "string" ? device.id.trim() : "";
    return {
        id: isValidClientId(rawId) ? rawId : "",
        name: toSafeDeviceName(device?.name),
        online: Boolean(device?.online),
        hasPushToken: Boolean(device?.hasPushToken),
        battery: null,
        lastSeenMinutesAgo: clampNumber(device?.lastSeenMinutesAgo, 0, 24 * 60)
    };
}

function normalizeDevices(rawDevices) {
    const list = Array.isArray(rawDevices) ? rawDevices.slice(0, MAX_DEVICE_COUNT) : [];
    return list
        .map(device => normalizeDevice(device))
        .filter(device => device.id)
        .sort((a, b) => {
            if (a.hasPushToken !== b.hasPushToken) {
                return a.hasPushToken ? -1 : 1;
            }
            return a.lastSeenMinutesAgo - b.lastSeenMinutesAgo;
        });
}

async function loadStoredDevices() {
    const data = await chrome.storage.local.get(STORAGE_KEYS.devices);
    const stored = normalizeDevices(data[STORAGE_KEYS.devices]);
    await chrome.storage.local.set({ [STORAGE_KEYS.devices]: stored });
    return stored;
}

async function fetchPairedDevicesFromBackend() {
    if (!backendConfigured()) {
        return { ok: false, error: backendUnavailableReason() };
    }

    const identity = await getOrCreateBrowserIdentity();
    const endpoint = `${edgeBaseUrl()}/paired-devices`;
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                apikey: BACKEND_CONFIG.supabaseAnonKey,
                Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
            },
            body: JSON.stringify({
                clientType: "browser",
                clientId: identity.browserClientId,
                clientSecret: identity.browserClientSecret
            })
        });

        let data = null;
        try {
            data = await response.json();
        } catch (_error) {
            data = null;
        }

        if (!response.ok || data?.ok === false) {
            return {
                ok: false,
                error: toUserErrorMessage(
                    data?.error || `Unable to load paired devices (${response.status}).`
                )
            };
        }

        const devices = Array.isArray(data?.devices) ? data.devices : [];
        const mapped = devices
            .map(device => ({
                id: String(device?.id ?? "").trim(),
                name: toSafeDeviceName(device?.label),
                online: Boolean(device?.online),
                hasPushToken: Boolean(device?.hasPushToken),
                battery: null,
                lastSeenMinutesAgo: minutesSince(device?.lastSeenAt)
            }))
            .filter(device => isValidClientId(device.id));

        return { ok: true, devices: mapped };
    } catch (error) {
        return {
            ok: false,
            error: toUserErrorMessage(error, "Unable to reach backend.")
        };
    }
}

async function syncDevicesFromBackend() {
    if (!backendConfigured()) {
        return { ok: false, devices: await loadStoredDevices(), error: backendUnavailableReason() };
    }

    const result = await fetchPairedDevicesFromBackend();
    if (!result.ok) {
        const shouldClearDevices = /no longer registered|reset browser identity and pair again/i.test(
            String(result.error || "")
        );
        if (shouldClearDevices) {
            return { ok: false, devices: await saveDevices([]), error: result.error };
        }
        return { ok: false, devices: await loadStoredDevices(), error: result.error };
    }

    const syncedDevices = await saveDevices(result.devices ?? []);
    return { ok: true, devices: syncedDevices };
}

async function getDevices() {
    if (!backendConfigured()) {
        return loadStoredDevices();
    }

    const synced = await syncDevicesFromBackend();
    return synced.devices;
}

async function saveDevices(devices) {
    const normalized = normalizeDevices(devices);
    await chrome.storage.local.set({ [STORAGE_KEYS.devices]: normalized });
    await setupContextMenus(normalized);
    return normalized;
}

async function resetBrowserIdentity() {
    browserIdentityCache = null;
    browserIdentityLoadInFlight = null;
    pairingCodeRefreshInFlight = null;
    browserSecretRotationInFlight = null;

    await chrome.storage.local.remove([
        STORAGE_KEYS.browserClientId,
        STORAGE_KEYS.browserClientSecret,
        STORAGE_KEYS.browserSecretRotatedAt,
        STORAGE_KEYS.devices,
        STORAGE_KEYS.pairingCode
    ]);
    await setupContextMenus([]);
}

async function maybeRotateBrowserSecret() {
    if (!backendConfigured()) {
        return;
    }
    if (browserSecretRotationInFlight) {
        return browserSecretRotationInFlight;
    }

    browserSecretRotationInFlight = (async () => {
        const identity = await getOrCreateBrowserIdentity();
        const storage = await chrome.storage.local.get(STORAGE_KEYS.browserSecretRotatedAt);
        const lastRotatedAtMs = new Date(storage[STORAGE_KEYS.browserSecretRotatedAt] || "").getTime();
        if (Number.isFinite(lastRotatedAtMs) && Date.now() - lastRotatedAtMs < BROWSER_SECRET_ROTATION_MS) {
            return;
        }

        const newBrowserClientSecret = generateBrowserClientSecret();
        const endpoint = `${edgeBaseUrl()}/rotate-client-secret`;
        try {
            const response = await fetch(endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    apikey: BACKEND_CONFIG.supabaseAnonKey,
                    Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
                },
                body: JSON.stringify({
                    clientType: "browser",
                    clientId: identity.browserClientId,
                    currentClientSecret: identity.browserClientSecret,
                    newClientSecret: newBrowserClientSecret
                })
            });

            let data = null;
            try {
                data = await response.json();
            } catch (_error) {
                data = null;
            }

            if (!response.ok || data?.ok === false) {
                const normalizedError = toUserErrorMessage(
                    data?.error || `Rotate client secret failed (${response.status}).`
                );
                if (/no longer registered|pair it again|reset browser identity/i.test(normalizedError)) {
                    await addLog("error-client", `Browser secret rotation failed: ${normalizedError}`);
                }
                return;
            }

            browserIdentityCache = {
                browserClientId: identity.browserClientId,
                browserClientSecret: newBrowserClientSecret
            };
            await chrome.storage.local.set({
                [STORAGE_KEYS.browserClientSecret]: newBrowserClientSecret,
                [STORAGE_KEYS.browserSecretRotatedAt]: new Date().toISOString()
            });
            await addLog("info", "Browser secret rotated.");
        } catch (_error) {
            // Keep the existing secret and retry on a later launch.
        }
    })();

    try {
        await browserSecretRotationInFlight;
    } finally {
        browserSecretRotationInFlight = null;
    }
}

function pruneLogs(logs) {
    const threshold = Date.now() - LOG_RETENTION_MS;
    return (Array.isArray(logs) ? logs : []).filter(entry => {
        const timestampMs = new Date(entry?.timestamp || "").getTime();
        return Number.isFinite(timestampMs) && timestampMs >= threshold;
    });
}

async function getLogs() {
    const data = await chrome.storage.local.get(STORAGE_KEYS.logs);
    const original = Array.isArray(data[STORAGE_KEYS.logs]) ? data[STORAGE_KEYS.logs] : [];
    const pruned = pruneLogs(original).slice(0, MAX_LOG_ENTRIES);
    if (pruned.length !== original.length) {
        await chrome.storage.local.set({ [STORAGE_KEYS.logs]: pruned });
    }
    return pruned;
}

async function addLog(level, message) {
    const logs = await getLogs();
    logs.unshift({
        id: `log-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
        level,
        message: compactText(message, MAX_LOG_MESSAGE_LENGTH) || "No details provided.",
        timestamp: new Date().toISOString()
    });
    const capped = logs.slice(0, MAX_LOG_ENTRIES);
    await chrome.storage.local.set({ [STORAGE_KEYS.logs]: capped });
}

function formatPairingCode(rawCode) {
    const digits = String(rawCode ?? "").replace(/\D/g, "").slice(0, 6);
    if (digits.length !== 6) {
        return "";
    }
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
}

async function requestPairingCodeFromBackend() {
    if (!backendConfigured()) {
        return { ok: false, error: backendUnavailableReason() };
    }

    const identity = await getOrCreateBrowserIdentity();
    const endpoint = `${edgeBaseUrl()}/pairing-code`;

    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                apikey: BACKEND_CONFIG.supabaseAnonKey,
                Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
            },
            body: JSON.stringify({
                browserClientId: identity.browserClientId,
                browserClientSecret: identity.browserClientSecret,
                browserLabel: "SupaPhone Browser"
            })
        });

        let data = null;
        try {
            data = await response.json();
        } catch (_error) {
            data = null;
        }

        if (!response.ok || data?.ok === false) {
            return {
                ok: false,
                error: toUserErrorMessage(
                    data?.error || `Failed to generate pairing code (${response.status}).`
                )
            };
        }

        const formattedCode = formatPairingCode(data?.code);
        if (!formattedCode) {
            return { ok: false, error: "Backend returned invalid pairing code format." };
        }

        return {
            ok: true,
            pairingCode: {
                code: formattedCode,
                createdAt: new Date().toISOString()
            }
        };
    } catch (error) {
        return {
            ok: false,
            error: toUserErrorMessage(error, "Unable to reach backend.")
        };
    }
}

async function getPairingCode(forceRefresh = false) {
    if (!backendConfigured()) {
        return {
            code: "--- ---",
            createdAt: null,
            unavailableReason: backendUnavailableReason()
        };
    }

    const data = await chrome.storage.local.get(STORAGE_KEYS.pairingCode);
    const cachedPairingCode = data[STORAGE_KEYS.pairingCode];
    const createdAtMs = new Date(cachedPairingCode?.createdAt || "").getTime();
    const hasFreshCachedCode =
        !forceRefresh &&
        Boolean(cachedPairingCode?.code) &&
        Number.isFinite(createdAtMs) &&
        Date.now() - createdAtMs < PAIRING_CODE_MAX_AGE_MS;

    if (hasFreshCachedCode) {
        return cachedPairingCode;
    }

    if (pairingCodeRefreshInFlight) {
        return pairingCodeRefreshInFlight;
    }

    pairingCodeRefreshInFlight = (async () => {
        const backendResult = await requestPairingCodeFromBackend();
        if (backendResult.ok && backendResult.pairingCode) {
            await chrome.storage.local.set({ [STORAGE_KEYS.pairingCode]: backendResult.pairingCode });
            return backendResult.pairingCode;
        }
        await addLog("error-server", `Backend pairing code unavailable: ${backendResult.error}`);
        return {
            code: "--- ---",
            createdAt: null,
            unavailableReason: backendResult.error || "Unable to generate pairing code."
        };
    })();

    try {
        return await pairingCodeRefreshInFlight;
    } finally {
        pairingCodeRefreshInFlight = null;
    }
}

function createContextMenu(options) {
    return new Promise((resolve, reject) => {
        chrome.contextMenus.create(options, () => {
            if (chrome.runtime.lastError) {
                reject(new Error(chrome.runtime.lastError.message));
                return;
            }
            resolve();
        });
    });
}

function removeAllContextMenus() {
    return new Promise(resolve => {
        chrome.contextMenus.removeAll(() => resolve());
    });
}

async function setupContextMenus(devicesInput) {
    const devices = Array.isArray(devicesInput) ? normalizeDevices(devicesInput) : await loadStoredDevices();
    const contextMenuDevices = devices.filter(device => device.hasPushToken);
    await removeAllContextMenus();

    await createContextMenu({
        id: MENU_SEND_PARENT,
        title: "Send Link to...",
        contexts: ["page", "link"]
    });

    await createContextMenu({
        id: MENU_CALL_PARENT,
        title: "Send selection to...",
        contexts: ["selection"]
    });

    for (const device of contextMenuDevices) {
        const title = device.name;
        await createContextMenu({
            id: `send-link-${device.id}`,
            parentId: MENU_SEND_PARENT,
            title,
            contexts: ["page", "link"]
        });

        await createContextMenu({
            id: `call-num-${device.id}`,
            parentId: MENU_CALL_PARENT,
            title,
            contexts: ["selection"]
        });
    }
}

function classifySelectionPayload(rawSelection) {
    const value = typeof rawSelection === "string" ? rawSelection.trim() : "";
    if (!value) {
        return null;
    }

    const hasProtocol = /^https?:\/\//i.test(value);
    const hasUrlMarkers = /[/?#=&]/.test(value);
    const hasDomainLikePattern = value.includes(".") && /[a-z]/i.test(value);
    const ipLikePattern = /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/.*)?$/;

    let urlCandidate = value;
    if (!hasProtocol && (hasUrlMarkers || hasDomainLikePattern || ipLikePattern.test(value))) {
        urlCandidate = `https://${urlCandidate}`;
    }
    try {
        const parsed = new URL(urlCandidate);
        if (parsed.hostname) {
            return { type: "link", payload: urlCandidate };
        }
    } catch (_error) {
        // fall through to phone detection
    }

    const phonePattern = /^\+?[0-9()\-\s]{6,}$/;
    const digitsOnly = value.replace(/\D/g, "");
    if (phonePattern.test(value) && digitsOnly.length >= 6) {
        return { type: "call", payload: value };
    }

    return null;
}

function drawBaseIcon(ctx, size) {
    const radius = size * 0.2;
    ctx.beginPath();
    ctx.moveTo(radius, 0);
    ctx.lineTo(size - radius, 0);
    ctx.quadraticCurveTo(size, 0, size, radius);
    ctx.lineTo(size, size - radius);
    ctx.quadraticCurveTo(size, size, size - radius, size);
    ctx.lineTo(radius, size);
    ctx.quadraticCurveTo(0, size, 0, size - radius);
    ctx.lineTo(0, radius);
    ctx.quadraticCurveTo(0, 0, radius, 0);
    ctx.closePath();

    ctx.fillStyle = "#471dcf";
    ctx.fill();

    const drawNormalizedPolygon = (points, fillColor) => {
        if (!Array.isArray(points) || points.length < 3) {
            return;
        }
        ctx.beginPath();
        ctx.moveTo(points[0][0] * size, points[0][1] * size);
        for (let index = 1; index < points.length; index += 1) {
            ctx.lineTo(points[index][0] * size, points[index][1] * size);
        }
        ctx.closePath();
        ctx.fillStyle = fillColor;
        ctx.fill();
    };

    // White chat-bubble body.
    drawNormalizedPolygon([
        [0.16, 0.21],
        [0.86, 0.28],
        [0.86, 0.70],
        [0.56, 0.94],
        [0.56, 0.74],
        [0.16, 0.74]
    ], "#ececec");

    // Purple lightning cutout overlay.
    drawNormalizedPolygon([
        [0.26, 0.67],
        [0.72, 0.38],
        [0.56, 0.58],
        [0.77, 0.58],
        [0.56, 0.83],
        [0.56, 0.67]
    ], "#471dcf");
}

function drawStatusBadge(ctx, size, status) {
    const badgeRadius = size * 0.22;
    const cx = size - badgeRadius;
    const cy = size - badgeRadius;

    ctx.beginPath();
    ctx.arc(cx, cy, badgeRadius + size * 0.03, 0, Math.PI * 2);
    ctx.fillStyle = "#343434";
    ctx.fill();

    ctx.beginPath();
    ctx.arc(cx, cy, badgeRadius, 0, Math.PI * 2);

    if (status === "success") {
        ctx.fillStyle = "#10b981";
    } else if (status === "error-server") {
        ctx.fillStyle = "#ef4444";
    } else {
        ctx.fillStyle = "#f59e0b";
    }
    ctx.fill();

    ctx.fillStyle = "#ffffff";
    ctx.font = `bold ${Math.round(badgeRadius * 1.2)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    if (status === "success") {
        ctx.fillText("v", cx, cy + badgeRadius * 0.05);
    } else {
        ctx.fillText("!", cx, cy + badgeRadius * 0.05);
    }
}

function generateIconImageData(status = "default") {
    const sizes = [16, 32, 48, 128];
    const imageDataMap = {};

    for (const size of sizes) {
        const canvas = new OffscreenCanvas(size, size);
        const ctx = canvas.getContext("2d");

        drawBaseIcon(ctx, size);
        if (status !== "default") {
            drawStatusBadge(ctx, size, status);
        }
        imageDataMap[size] = ctx.getImageData(0, 0, size, size);
    }

    return imageDataMap;
}

async function setIcon(status = "default") {
    const safeStatus = VALID_ICON_STATUSES.has(status) ? status : "default";
    try {
        const imageData = generateIconImageData(safeStatus);
        await chrome.action.setIcon({ imageData });
    } catch (_error) {
        return;
    }
}

async function flashIcon(status, durationMs = 2500) {
    await setIcon(status);
    setTimeout(() => {
        void setIcon("default");
    }, durationMs);
}

async function initializeExtension() {
    await maybeRotateBrowserSecret();
    const devices = await getDevices();
    await getLogs();
    await setupContextMenus(devices);
    await setIcon("default");
}

async function sendPayloadToBackend(payloadType, payload, targetDeviceId) {
    const safeType = payloadType === "call" ? "call" : "link";
    const trimmedPayload = typeof payload === "string" ? payload.trim() : "";
    const trimmedTargetId = typeof targetDeviceId === "string" ? targetDeviceId.trim() : "";

    if (!trimmedPayload) {
        return { ok: false, status: "error-client", error: "Payload is empty." };
    }
    if (trimmedPayload.length > 2048) {
        return { ok: false, status: "error-client", error: "Payload exceeds 2048 characters." };
    }
    if (!trimmedTargetId) {
        return { ok: false, status: "error-client", error: "Target device is missing." };
    }
    if (!isValidClientId(trimmedTargetId)) {
        return { ok: false, status: "error-client", error: "Target device format is invalid." };
    }
    if (!backendConfigured()) {
        return {
            ok: false,
            status: "error-client",
            error: backendUnavailableReason()
        };
    }

    const identity = await getOrCreateBrowserIdentity();
    const endpoint = `${edgeBaseUrl()}/send-payload`;

    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                apikey: BACKEND_CONFIG.supabaseAnonKey,
                Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
            },
            body: JSON.stringify({
                sourceClientId: identity.browserClientId,
                sourceClientSecret: identity.browserClientSecret,
                targetClientId: trimmedTargetId,
                payloadType: safeType,
                payload: trimmedPayload
            })
        });

        let data = null;
        try {
            data = await response.json();
        } catch (_error) {
            data = null;
        }

        if (!response.ok || data?.ok === false) {
            return {
                ok: false,
                status: "error-server",
                error: toUserErrorMessage(
                    data?.error || `Backend request failed (${response.status}).`
                )
            };
        }

        return {
            ok: true,
            status: "success",
            message: data?.message || `Payload accepted for device ${trimmedTargetId}.`
        };
    } catch (error) {
        return {
            ok: false,
            status: "error-server",
            error: toUserErrorMessage(error, "Unable to reach backend.")
        };
    }
}

async function renamePairedDeviceOnBackend(targetDeviceId, newLabel) {
    const trimmedTargetId = typeof targetDeviceId === "string" ? targetDeviceId.trim() : "";
    const trimmedLabel = typeof newLabel === "string" ? newLabel.trim() : "";

    if (!trimmedTargetId || !trimmedLabel) {
        return { ok: false, status: "error-client", error: "Device and new label are required." };
    }
    if (!isValidClientId(trimmedTargetId)) {
        return { ok: false, status: "error-client", error: "Target device format is invalid." };
    }
    if (trimmedLabel.length < 2 || trimmedLabel.length > 48) {
        return {
            ok: false,
            status: "error-client",
            error: "Device name must be between 2 and 48 characters."
        };
    }
    if (!backendConfigured()) {
        return {
            ok: false,
            status: "error-client",
            error: backendUnavailableReason()
        };
    }

    const identity = await getOrCreateBrowserIdentity();
    const endpoint = `${edgeBaseUrl()}/rename-device`;
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                apikey: BACKEND_CONFIG.supabaseAnonKey,
                Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
            },
            body: JSON.stringify({
                requesterClientId: identity.browserClientId,
                requesterClientSecret: identity.browserClientSecret,
                requesterClientType: "browser",
                targetClientId: trimmedTargetId,
                newLabel: trimmedLabel
            })
        });

        let data = null;
        try {
            data = await response.json();
        } catch (_error) {
            data = null;
        }

        if (!response.ok || data?.ok === false) {
            return {
                ok: false,
                status: "error-server",
                error: toUserErrorMessage(
                    data?.error || `Rename request failed (${response.status}).`
                )
            };
        }

        return {
            ok: true,
            status: "success",
            message: `Renamed device to ${trimmedLabel}.`
        };
    } catch (error) {
        return {
            ok: false,
            status: "error-server",
            error: toUserErrorMessage(error, "Unable to reach backend.")
        };
    }
}

async function removePairedDeviceOnBackend(targetDeviceId) {
    const trimmedTargetId = typeof targetDeviceId === "string" ? targetDeviceId.trim() : "";
    if (!trimmedTargetId) {
        return { ok: false, status: "error-client", error: "Target device is missing." };
    }
    if (!isValidClientId(trimmedTargetId)) {
        return { ok: false, status: "error-client", error: "Target device format is invalid." };
    }
    if (!backendConfigured()) {
        return {
            ok: false,
            status: "error-client",
            error: backendUnavailableReason()
        };
    }

    const identity = await getOrCreateBrowserIdentity();
    const endpoint = `${edgeBaseUrl()}/remove-paired-device`;
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                apikey: BACKEND_CONFIG.supabaseAnonKey,
                Authorization: `Bearer ${BACKEND_CONFIG.supabaseAnonKey}`
            },
            body: JSON.stringify({
                requesterClientId: identity.browserClientId,
                requesterClientSecret: identity.browserClientSecret,
                requesterClientType: "browser",
                targetClientId: trimmedTargetId
            })
        });

        let data = null;
        try {
            data = await response.json();
        } catch (_error) {
            data = null;
        }

        if (!response.ok || data?.ok === false) {
            return {
                ok: false,
                status: "error-server",
                error: toUserErrorMessage(
                    data?.error || `Remove request failed (${response.status}).`
                )
            };
        }

        return {
            ok: true,
            status: "success",
            message: "Device removed."
        };
    } catch (error) {
        return {
            ok: false,
            status: "error-server",
            error: toUserErrorMessage(error, "Unable to reach backend.")
        };
    }
}

chrome.runtime.onInstalled.addListener(() => {
    (async () => {
        try {
            await initializeExtension();
            await addLog("info", "Extension installed and initialized.");
        } catch (error) {
            await addLog("error-client", `Initialization failed: ${toUserErrorMessage(error)}`);
            await setIcon("error-client");
        }
    })();
});

chrome.runtime.onStartup.addListener(() => {
    void initializeExtension();
});

chrome.contextMenus.onClicked.addListener(async info => {
    if (typeof info.menuItemId !== "string") {
        return;
    }

    const devices = await getDevices();

    if (info.menuItemId.startsWith("send-link-")) {
        const deviceId = info.menuItemId.replace("send-link-", "");
        const urlToShare = (info.linkUrl || info.pageUrl || "").trim();
        const result = await sendPayloadToBackend("link", urlToShare, deviceId);
        if (!result.ok) {
            await addLog(result.status, `Send link failed: ${result.error}`);
            await flashIcon(result.status);
            return;
        }
        await addLog("success", result.message);
        await flashIcon("success");
        return;
    }

    if (info.menuItemId.startsWith("call-num-")) {
        const deviceId = info.menuItemId.replace("call-num-", "");
        const parsed = classifySelectionPayload(info.selectionText || "");
        if (!parsed) {
            await addLog("error-client", "Send selection failed: selection is neither a valid link nor phone number.");
            await flashIcon("error-client");
            return;
        }
        const result = await sendPayloadToBackend(parsed.type, parsed.payload, deviceId);
        if (!result.ok) {
            await addLog(result.status, `Send selection failed: ${result.error}`);
            await flashIcon(result.status);
            return;
        }
        await addLog("success", result.message);
        await flashIcon("success");
    }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || typeof message.action !== "string") {
        return false;
    }

    (async () => {
        switch (message.action) {
            case "get-devices": {
                const devices = await getDevices();
                sendResponse({ ok: true, devices });
                break;
            }
            case "sync-devices": {
                const sync = await syncDevicesFromBackend();
                if (!sync.ok) {
                    sendResponse({ ok: false, error: sync.error || "Unable to sync devices." });
                    break;
                }
                sendResponse({ ok: true, devices: sync.devices });
                break;
            }
            case "quick-send": {
                const devices = await getDevices();
                const requestedId = typeof message.deviceId === "string" ? message.deviceId.trim() : "";
                const preferredRequested = devices.find(device => device.id === requestedId);
                const target =
                    (preferredRequested?.hasPushToken ? preferredRequested : null) ||
                    devices.find(device => device.hasPushToken) ||
                    devices.find(device => device.id === requestedId) ||
                    devices[0];

                const result = await sendPayloadToBackend(
                    message.payloadType,
                    message.payload,
                    target?.id || ""
                );
                if (!result.ok) {
                    await addLog(result.status, `Quick send failed: ${result.error}`);
                    await flashIcon(result.status);
                    sendResponse({ ok: false, status: result.status, error: result.error });
                    break;
                }
                await addLog("success", result.message);
                await flashIcon("success");
                sendResponse({ ok: true, status: "success", message: result.message, deviceId: target?.id });
                break;
            }
            case "get-logs": {
                const logs = await getLogs();
                sendResponse({ ok: true, logs });
                break;
            }
            case "rename-device": {
                const result = await renamePairedDeviceOnBackend(message.deviceId, message.newLabel);
                if (!result.ok) {
                    await addLog(result.status, `Rename device failed: ${result.error}`);
                    sendResponse({ ok: false, error: result.error, status: result.status });
                    break;
                }

                await addLog("info", result.message);
                const sync = await syncDevicesFromBackend();
                if (!sync.ok) {
                    sendResponse({ ok: false, error: sync.error || "Rename applied but sync failed." });
                    break;
                }

                sendResponse({ ok: true, message: result.message, devices: sync.devices });
                break;
            }
            case "remove-device": {
                const result = await removePairedDeviceOnBackend(message.deviceId);
                if (!result.ok) {
                    await addLog(result.status, `Remove device failed: ${result.error}`);
                    sendResponse({ ok: false, error: result.error, status: result.status });
                    break;
                }

                await addLog("info", result.message);
                const sync = await syncDevicesFromBackend();
                if (!sync.ok) {
                    sendResponse({ ok: false, error: sync.error || "Remove applied but sync failed." });
                    break;
                }

                sendResponse({ ok: true, message: result.message, devices: sync.devices });
                break;
            }
            case "clear-logs": {
                await chrome.storage.local.set({ [STORAGE_KEYS.logs]: [] });
                sendResponse({ ok: true });
                break;
            }
            case "reset-browser-identity": {
                await resetBrowserIdentity();
                sendResponse({ ok: true });
                break;
            }
            case "get-pairing-code": {
                const code = await getPairingCode(false);
                sendResponse({
                    ok: true,
                    pairingCode: code.code,
                    createdAt: code.createdAt,
                    unavailableReason: code.unavailableReason || null
                });
                break;
            }
            case "get-backend-status": {
                sendResponse({
                    ok: true,
                    backendConfigured: backendConfigured(),
                    backendUnavailableReason: backendUnavailableReason() || null
                });
                break;
            }
            case "refresh-pairing-code": {
                const code = await getPairingCode(true);
                if (!code.unavailableReason) {
                    await addLog("info", "Pairing code regenerated.");
                }
                sendResponse({
                    ok: true,
                    pairingCode: code.code,
                    createdAt: code.createdAt,
                    unavailableReason: code.unavailableReason || null
                });
                break;
            }
            default:
                sendResponse({ ok: false, error: `Unknown action: ${message.action}` });
        }
    })().catch(async error => {
        await addLog("error-client", `Background error: ${toUserErrorMessage(error)}`);
        sendResponse({ ok: false, error: toUserErrorMessage(error) });
    });

    return true;
});
