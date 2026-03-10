document.addEventListener("DOMContentLoaded", () => {
    const MAX_PAIRED_DEVICES = 5;
    const MAX_USER_ERROR_LENGTH = 140;
    const byId = id => document.getElementById(id);
    const requiredIds = [
        "devices-view",
        "pair-view",
        "settings-view",
        "logs-view",
        "how-it-works-view",
        "paired-capacity-badge",
        "refresh-devices-btn",
        "device-list",
        "empty-device-message",
        "add-device-btn",
        "settings-btn",
        "view-logs-btn",
        "how-it-works-link",
        "back-to-settings",
        "quick-send-input",
        "quick-send-helper",
        "pairing-qr-box",
        "pairing-qr-image",
        "pairing-qr-skeleton",
        "pairing-code-value",
        "refresh-code-btn",
        "pair-device-btn",
        "pairing-helper",
        "reset-identity-btn",
        "logs-list",
        "clear-logs-btn"
    ];

    const missingId = requiredIds.find(id => !byId(id));
    if (missingId) {
        return;
    }

    const state = {
        devices: [],
        selectedDeviceId: null,
        openDeviceMenuId: null
    };

    const views = {
        devices: byId("devices-view"),
        pair: byId("pair-view"),
        settings: byId("settings-view"),
        logs: byId("logs-view"),
        howItWorks: byId("how-it-works-view")
    };

    const deviceListElement = byId("device-list");
    const pairedCapacityBadge = byId("paired-capacity-badge");
    const quickSendButton = document.querySelector(".quick-send-btn");
    const quickSendInput = byId("quick-send-input");
    const quickSendHelper = byId("quick-send-helper");
    const addDeviceButton = byId("add-device-btn");
    const pairDeviceButton = byId("pair-device-btn");
    const quickSendDefaultIcon = quickSendButton ? quickSendButton.innerHTML : "";
    const addDeviceDefaultHtml = addDeviceButton.innerHTML;
    const pairDeviceDefaultText = pairDeviceButton.textContent;
    const refreshDevicesButton = byId("refresh-devices-btn");
    const pairingHelper = byId("pairing-helper");
    const pairingCodeValue = byId("pairing-code-value");
    const pairingQrBox = byId("pairing-qr-box");
    const pairingQrImage = byId("pairing-qr-image");
    const refreshCodeButton = byId("refresh-code-btn");
    const resetIdentityButton = byId("reset-identity-btn");
    const logsList = byId("logs-list");
    let pairingCodeRequestInFlight = false;
    let pairingQrRenderToken = 0;
    let devicesRefreshInFlight = false;
    let deviceActionInFlight = false;
    let openPairViewInFlight = false;

    const switchView = (targetView, callback) => {
        Object.values(views).forEach(view => {
            view.classList.remove("active");
            view.classList.add("hidden");
        });
        targetView.classList.remove("hidden");
        setTimeout(() => {
            targetView.classList.add("active");
            if (callback) {
                callback();
            }
        }, 10);
    };

    function setHelperMessage(element, text, tone = "info") {
        element.textContent = text;
        element.classList.remove("helper-success", "helper-error", "helper-warning");
        if (tone === "success") {
            element.classList.add("helper-success");
        } else if (tone === "error") {
            element.classList.add("helper-error");
        } else if (tone === "warning") {
            element.classList.add("helper-warning");
        }
    }

    function toUserErrorMessage(error, fallback = "Something went wrong. Please try again.") {
        const raw = (error && typeof error.message === "string" ? error.message : "").trim();
        if (!raw) {
            return fallback;
        }

        if (/credentials are not registered|is not registered|re-pair and try again/i.test(raw)) {
            return "This browser is no longer registered. Pair it again from the extension.";
        }
        if (/credentials do not match this installation/i.test(raw)) {
            return "This browser identity no longer matches the backend. Reset browser identity and pair again.";
        }
        if (/failed to fetch|networkerror|network error|timed out|timeout/i.test(raw)) {
            return "Network issue detected. Check connection and retry.";
        }
        if (/unauthorized|401|403/i.test(raw)) {
            return "Service authentication failed. Please verify setup.";
        }
        if (/missing .*config\.local\.js|config\.local\.js has invalid syntax/i.test(raw)) {
            return "Extension backend setup is incomplete. Check local config.";
        }

        const compact = raw.replace(/\s+/g, " ");
        if (compact.length <= MAX_USER_ERROR_LENGTH) {
            return compact;
        }
        return `${compact.slice(0, MAX_USER_ERROR_LENGTH - 3)}...`;
    }

    function escapeHtml(value) {
        const element = document.createElement("div");
        element.textContent = typeof value === "string" ? value : "";
        return element.innerHTML;
    }

    function renderDevices() {
        deviceListElement.innerHTML = "";
        pairedCapacityBadge.textContent = `${state.devices.length}/${MAX_PAIRED_DEVICES} paired`;
        byId("empty-device-message").classList.toggle("hidden", state.devices.length > 0);

        if (state.devices.length === 0) {
            state.selectedDeviceId = null;
            state.openDeviceMenuId = null;
            return;
        }

        const selectedStillExists = state.devices.some(device => device.id === state.selectedDeviceId);
        if (!selectedStillExists) {
            state.selectedDeviceId = null;
        }
        const menuTargetExists = state.devices.some(device => device.id === state.openDeviceMenuId);
        if (!menuTargetExists) {
            state.openDeviceMenuId = null;
        }

        for (const device of state.devices) {
            const item = document.createElement("li");
            const activeClass = state.selectedDeviceId === device.id ? "active" : "";
            item.className = `device-item ${activeClass}`.trim();
            item.dataset.deviceId = device.id;
            const menuOpen = state.openDeviceMenuId === device.id;

            const statusText = !device.hasPushToken
                ? "Setup required &bull; Open Android app once"
                : "";
            const statusMarkup = statusText
                ? `<span class="device-status">${statusText}</span>`
                : "";
            const actionDisabledAttr = deviceActionInFlight ? "disabled" : "";

            item.innerHTML = `
                <div class="device-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                        <line x1="12" y1="18" x2="12.01" y2="18"></line>
                    </svg>
                </div>
                <div class="device-info">
                    <span class="device-name">${escapeHtml(device.name)}</span>
                    ${statusMarkup}
                </div>
                <div class="device-actions">
                    <button class="icon-btn device-menu-btn" data-device-id="${device.id}" title="Device actions" ${actionDisabledAttr}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                            <circle cx="12" cy="5" r="2"></circle>
                            <circle cx="12" cy="12" r="2"></circle>
                            <circle cx="12" cy="19" r="2"></circle>
                        </svg>
                    </button>
                    <div class="device-menu ${menuOpen ? "" : "hidden"}" data-device-menu-id="${device.id}">
                        <button class="device-menu-item" data-device-action="rename" data-device-id="${device.id}" ${actionDisabledAttr}>
                            Rename
                        </button>
                        <button class="device-menu-item danger" data-device-action="remove" data-device-id="${device.id}" ${actionDisabledAttr}>
                            Remove
                        </button>
                    </div>
                </div>
            `;
            deviceListElement.appendChild(item);
        }
    }

    function formatRelativeTime(isoString) {
        const timestamp = new Date(isoString);
        if (!Number.isFinite(timestamp.getTime())) {
            return "Unknown time";
        }
        const diffMs = Date.now() - timestamp.getTime();
        const diffMins = Math.floor(diffMs / 60000);
        if (diffMins < 1) {
            return "Just now";
        }
        if (diffMins < 60) {
            return `${diffMins}m ago`;
        }
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) {
            return `${diffHours}h ago`;
        }
        return timestamp.toLocaleDateString();
    }

    function renderLogs(logs) {
        logsList.innerHTML = "";
        if (!Array.isArray(logs) || logs.length === 0) {
            logsList.innerHTML = '<p class="logs-empty">No logs yet.</p>';
            return;
        }

        for (const log of logs) {
            const level = escapeHtml(log.level || "info");
            const entry = document.createElement("div");
            entry.className = "log-entry";
            entry.innerHTML = `
                <span class="log-badge ${level}"></span>
                <div class="log-content">
                    <p class="log-message">${escapeHtml(log.message)}</p>
                    <p class="log-time">${formatRelativeTime(log.timestamp)}</p>
                </div>
            `;
            logsList.appendChild(entry);
        }
    }

    function classifyQuickSendInput(rawValue) {
        const value = rawValue.trim();
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
            // fall through
        }

        const phonePattern = /^\+?[0-9()\-\s]{6,}$/;
        const digitsOnly = value.replace(/\D/g, "");
        if (phonePattern.test(value) && digitsOnly.length >= 6) {
            return { type: "call", payload: value };
        }

        return null;
    }

    function sendRuntimeMessage(payload) {
        return new Promise((resolve, reject) => {
            chrome.runtime.sendMessage(payload, response => {
                if (chrome.runtime.lastError) {
                    reject(new Error(chrome.runtime.lastError.message));
                    return;
                }
                if (!response || response.ok === false) {
                    reject(new Error(response?.error || "Unknown runtime response error"));
                    return;
                }
                resolve(response);
            });
        });
    }

    async function loadDevices() {
        const response = await sendRuntimeMessage({ action: "get-devices" });
        state.devices = response.devices || [];
        renderDevices();
    }

    function setDevicesRefreshLoading(isLoading) {
        devicesRefreshInFlight = isLoading;
        refreshDevicesButton.disabled = isLoading;
        refreshDevicesButton.classList.toggle("loading", isLoading);
        refreshDevicesButton.title = isLoading ? "Refreshing devices..." : "Refresh paired devices";
    }

    async function refreshDevicesFromBackend() {
        if (devicesRefreshInFlight) {
            return;
        }

        setDevicesRefreshLoading(true);
        setHelperMessage(quickSendHelper, "Refreshing paired devices...", "info");
        try {
            const response = await sendRuntimeMessage({ action: "sync-devices" });
            state.devices = response.devices || [];
            renderDevices();
            setHelperMessage(quickSendHelper, "Paired device list refreshed.", "success");
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
        } finally {
            setDevicesRefreshLoading(false);
        }
    }

    function setPairViewOpenLoading(isLoading) {
        openPairViewInFlight = isLoading;
        addDeviceButton.disabled = isLoading;
        addDeviceButton.innerHTML = isLoading
            ? "Opening..."
            : addDeviceDefaultHtml;
    }

    function setPairDeviceRefreshLoading(isLoading) {
        pairDeviceButton.disabled = isLoading;
        pairDeviceButton.textContent = isLoading ? "Refreshing..." : pairDeviceDefaultText;
    }

    function setDeviceActionLoading(isLoading, message = "") {
        deviceActionInFlight = isLoading;
        if (message) {
            setHelperMessage(quickSendHelper, message, "info");
        }
        renderDevices();
    }

    async function loadLogs() {
        const response = await sendRuntimeMessage({ action: "get-logs" });
        renderLogs(response.logs || []);
    }

    async function loadBackendStatus() {
        const response = await sendRuntimeMessage({ action: "get-backend-status" });
        if (!response.backendConfigured && response.backendUnavailableReason) {
            setHelperMessage(pairingHelper, response.backendUnavailableReason, "warning");
        }
    }

    function normalizePairingDigits(rawCode) {
        const digits = String(rawCode ?? "").replace(/\D/g, "").slice(0, 6);
        return digits.length === 6 ? digits : "";
    }

    function setPairingCodeLoading(isLoading) {
        pairingCodeValue.classList.toggle("pairing-code-loading", isLoading);
        if (isLoading) {
            pairingCodeValue.textContent = "... ...";
            pairingQrImage.classList.add("hidden");
            pairingQrBox.classList.add("qr-loading");
        } else if (!pairingQrImage.getAttribute("src")) {
            pairingQrBox.classList.remove("qr-loading");
        }
        refreshCodeButton.disabled = isLoading;
        refreshCodeButton.textContent = isLoading ? "Generating..." : "Regenerate Code";
    }

    async function renderPairingQr(rawCode) {
        const renderToken = ++pairingQrRenderToken;
        const digits = normalizePairingDigits(rawCode);
        pairingQrImage.classList.add("hidden");
        pairingQrImage.removeAttribute("src");
        if (!digits) {
            pairingQrBox.classList.remove("qr-loading");
            return;
        }

        const qrPayload = `supaphone://pair?code=${digits}`;

        pairingQrBox.classList.add("qr-loading");

        try {
            if (typeof qrcode !== "function") {
                throw new Error("QR generator is unavailable.");
            }
            const qr = qrcode(0, "M");
            qr.addData(qrPayload);
            qr.make();
            const qrSvg = qr.createSvgTag(6, 0);
            const qrDataUrl = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(qrSvg)}`;
            if (renderToken !== pairingQrRenderToken) {
                return;
            }

            pairingQrImage.onload = () => {
                if (renderToken !== pairingQrRenderToken) {
                    return;
                }
                pairingQrBox.classList.remove("qr-loading");
                pairingQrImage.classList.remove("hidden");
            };
            pairingQrImage.onerror = () => {
                if (renderToken !== pairingQrRenderToken) {
                    return;
                }
                pairingQrBox.classList.remove("qr-loading");
                pairingQrImage.classList.add("hidden");
                pairingQrImage.removeAttribute("src");
                setHelperMessage(pairingHelper, "QR preview failed. Use the 6-digit code to pair.", "warning");
            };
            pairingQrImage.src = qrDataUrl;
        } catch (_error) {
            if (renderToken !== pairingQrRenderToken) {
                return;
            }
            pairingQrBox.classList.remove("qr-loading");
            pairingQrImage.classList.add("hidden");
            pairingQrImage.removeAttribute("src");
            setHelperMessage(pairingHelper, "QR preview failed. Use the 6-digit code to pair.", "warning");
        }
    }

    async function loadPairingCode(forceRefresh = false) {
        if (pairingCodeRequestInFlight) {
            return;
        }

        const previousCodeValue = pairingCodeValue.textContent || "--- ---";
        const previousQrSrc = pairingQrImage.getAttribute("src") || "";
        const previousQrHidden = pairingQrImage.classList.contains("hidden");
        pairingCodeRequestInFlight = true;
        setPairingCodeLoading(true);
        if (forceRefresh) {
            setHelperMessage(pairingHelper, "Generating a new pairing code...", "info");
        }
        try {
            const response = await sendRuntimeMessage({
                action: forceRefresh ? "refresh-pairing-code" : "get-pairing-code"
            });
            pairingCodeValue.textContent = response.pairingCode || "--- ---";
            await renderPairingQr(response.pairingCode || "");

            if (response.unavailableReason) {
                setHelperMessage(pairingHelper, response.unavailableReason, "warning");
                return;
            }
            setHelperMessage(
                pairingHelper,
                "Scan QR in Android or use the 6-digit code.",
                "info"
            );
            if (forceRefresh) {
                setHelperMessage(pairingHelper, "Pairing code regenerated.", "success");
                return;
            }
        } catch (error) {
            pairingCodeValue.textContent = previousCodeValue;
            if (previousQrSrc) {
                pairingQrImage.src = previousQrSrc;
                if (!previousQrHidden) {
                    pairingQrImage.classList.remove("hidden");
                }
                pairingQrBox.classList.remove("qr-loading");
            } else {
                pairingQrImage.classList.add("hidden");
                pairingQrImage.removeAttribute("src");
                pairingQrBox.classList.remove("qr-loading");
            }
            throw error;
        } finally {
            pairingCodeRequestInFlight = false;
            setPairingCodeLoading(false);
        }
    }

    function closeDeviceMenu() {
        if (!state.openDeviceMenuId) {
            return;
        }
        state.openDeviceMenuId = null;
        renderDevices();
    }

    function toggleDeviceMenu(deviceId) {
        if (!deviceId) {
            return;
        }
        state.openDeviceMenuId = state.openDeviceMenuId === deviceId ? null : deviceId;
        renderDevices();
    }

    async function renameDevice(deviceId) {
        if (deviceActionInFlight) {
            return;
        }
        const device = state.devices.find(item => item.id === deviceId);
        if (!device) {
            return;
        }
        const proposed = window.prompt("Enter a new device name", device.name);
        if (proposed === null) {
            return;
        }
        const newLabel = proposed.trim();
        if (newLabel.length < 2 || newLabel.length > 48) {
            setHelperMessage(quickSendHelper, "Device name must be 2 to 48 characters.", "error");
            return;
        }
        if (newLabel === device.name) {
            return;
        }

        setDeviceActionLoading(true, "Renaming device...");
        try {
            await sendRuntimeMessage({
                action: "rename-device",
                deviceId,
                newLabel
            });
            await loadDevices();
            setHelperMessage(quickSendHelper, "Device renamed.", "success");
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
        } finally {
            setDeviceActionLoading(false);
        }
    }

    async function removeDevice(deviceId) {
        if (deviceActionInFlight) {
            return;
        }
        setDeviceActionLoading(true, "Removing device...");
        try {
            await sendRuntimeMessage({
                action: "remove-device",
                deviceId
            });
            if (state.selectedDeviceId === deviceId) {
                state.selectedDeviceId = null;
            }
            await loadDevices();
            setHelperMessage(quickSendHelper, "Device removed.", "success");
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
        } finally {
            setDeviceActionLoading(false);
        }
    }

    async function runQuickSend() {
        if (!quickSendButton) {
            return;
        }

        const selectedDevice = state.devices.find(device => device.id === state.selectedDeviceId);
        if (!selectedDevice) {
            setHelperMessage(quickSendHelper, "Select a device before sending.", "error");
            return;
        }
        if (!selectedDevice.hasPushToken) {
            setHelperMessage(
                quickSendHelper,
                "Selected phone is not ready for notifications yet. Open the Android app once, then refresh devices.",
                "warning"
            );
            return;
        }

        const parsed = classifyQuickSendInput(quickSendInput.value);
        if (!parsed) {
            setHelperMessage(quickSendHelper, "Enter a valid phone number or link.", "error");
            quickSendInput.focus();
            return;
        }

        quickSendButton.disabled = true;

        try {
            const response = await sendRuntimeMessage({
                action: "quick-send",
                payloadType: parsed.type,
                payload: parsed.payload,
                deviceId: state.selectedDeviceId
            });

            setHelperMessage(quickSendHelper, response.message || "Sent successfully.", "success");
            quickSendInput.value = "";
            await loadLogs();
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
            await loadLogs();
        } finally {
            quickSendButton.innerHTML = quickSendDefaultIcon;
            quickSendButton.disabled = false;
        }
    }

    function loadThemePreference() {
        const savedTheme = localStorage.getItem("supaphone-popup-theme");
        const useLightTheme = savedTheme === "light";
        document.body.classList.remove("theme-dark", "theme-light");
        document.body.classList.add(useLightTheme ? "theme-light" : "theme-dark");

        document.querySelectorAll(".theme-btn-mini").forEach(button => {
            button.classList.toggle("active", button.getAttribute("data-theme") === (useLightTheme ? "light" : "dark"));
        });
    }

    addDeviceButton.addEventListener("click", async () => {
        if (openPairViewInFlight) {
            return;
        }
        setPairViewOpenLoading(true);
        try {
            switchView(views.pair);
            await loadBackendStatus();
            await loadPairingCode(true);
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
        } finally {
            setPairViewOpenLoading(false);
        }
    });

    refreshDevicesButton.addEventListener("click", () => {
        void refreshDevicesFromBackend();
    });

    byId("settings-btn").addEventListener("click", () => switchView(views.settings));
    byId("view-logs-btn").addEventListener("click", async () => {
        switchView(views.logs);
        await loadLogs();
    });
    byId("how-it-works-link").addEventListener("click", event => {
        event.preventDefault();
        switchView(views.howItWorks);
    });

    document.querySelectorAll(".back-to-devices").forEach(button => {
        button.addEventListener("click", () => switchView(views.devices));
    });
    byId("back-to-settings").addEventListener("click", () => switchView(views.settings));

    quickSendButton?.addEventListener("click", () => {
        void runQuickSend();
    });
    quickSendInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            void runQuickSend();
        }
    });

    document.querySelectorAll(".theme-btn-mini").forEach(button => {
        button.addEventListener("click", () => {
            const selectedTheme = button.getAttribute("data-theme") === "light" ? "light" : "dark";
            localStorage.setItem("supaphone-popup-theme", selectedTheme);
            loadThemePreference();
        });
    });

    deviceListElement.addEventListener("click", event => {
        const menuAction = event.target.closest(".device-menu-item");
        if (menuAction) {
            event.preventDefault();
            event.stopPropagation();
            const action = menuAction.getAttribute("data-device-action");
            const deviceId = menuAction.getAttribute("data-device-id");
            closeDeviceMenu();
            if (action === "rename") {
                void renameDevice(deviceId);
            } else if (action === "remove") {
                void removeDevice(deviceId);
            }
            return;
        }

        const menuButton = event.target.closest(".device-menu-btn");
        if (menuButton) {
            event.preventDefault();
            event.stopPropagation();
            const deviceId = menuButton.getAttribute("data-device-id");
            toggleDeviceMenu(deviceId);
            return;
        }

        const deviceItem = event.target.closest(".device-item");
        if (deviceItem) {
            closeDeviceMenu();
            state.selectedDeviceId = deviceItem.dataset.deviceId;
            renderDevices();
            setHelperMessage(quickSendHelper, "Device selected for quick send.", "info");
        }
    });

    document.addEventListener("click", event => {
        if (!event.target.closest(".device-actions")) {
            closeDeviceMenu();
        }
    });

    byId("refresh-code-btn").addEventListener("click", async () => {
        try {
            await loadPairingCode(true);
        } catch (error) {
            setHelperMessage(pairingHelper, toUserErrorMessage(error), "error");
        }
    });

    pairDeviceButton.addEventListener("click", async () => {
        if (devicesRefreshInFlight) {
            return;
        }
        setPairDeviceRefreshLoading(true);
        try {
            await sendRuntimeMessage({ action: "sync-devices" });
            await loadDevices();
            switchView(views.devices);
            setHelperMessage(quickSendHelper, "Paired device list refreshed.", "success");
        } catch (error) {
            setHelperMessage(pairingHelper, toUserErrorMessage(error), "error");
        } finally {
            setPairDeviceRefreshLoading(false);
        }
    });

    byId("clear-logs-btn").addEventListener("click", async () => {
        try {
            await sendRuntimeMessage({ action: "clear-logs" });
            await loadLogs();
        } catch (error) {
            alert(`Unable to clear logs: ${toUserErrorMessage(error)}`);
        }
    });

    resetIdentityButton.addEventListener("click", async () => {
        const confirmed = window.confirm(
            "Reset this browser identity and remove all local pairing data? You will need to pair again."
        );
        if (!confirmed) {
            return;
        }

        resetIdentityButton.disabled = true;
        try {
            await sendRuntimeMessage({ action: "reset-browser-identity" });
            state.devices = [];
            state.selectedDeviceId = null;
            state.openDeviceMenuId = null;
            renderDevices();
            pairingCodeValue.textContent = "--- ---";
            pairingQrImage.classList.add("hidden");
            pairingQrImage.removeAttribute("src");
            pairingQrBox.classList.remove("qr-loading");
            switchView(views.devices);
            setHelperMessage(quickSendHelper, "Browser identity reset. Pair devices again.", "success");
            await loadLogs();
        } catch (error) {
            setHelperMessage(quickSendHelper, toUserErrorMessage(error), "error");
        } finally {
            resetIdentityButton.disabled = false;
        }
    });

    (async () => {
        try {
            loadThemePreference();
            await loadBackendStatus();
            await loadDevices();
        } catch (error) {
            setHelperMessage(quickSendHelper, `Initialization failed: ${toUserErrorMessage(error)}`, "error");
        }
    })();
});
