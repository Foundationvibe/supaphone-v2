import { createServiceClient } from "../_shared/supabase-admin.ts";
import {
    hashClientSecret,
    internalServerError,
    isValidClientId,
    isValidClientSecret,
    jsonResponse,
    optionsResponse,
    parseJsonBody,
    readClientSecretHash,
    requireAnonApiKey,
    trimLabel,
    trimText
} from "../_shared/runtime.ts";

type ClientType = "browser" | "android";

type PairLinkRow = {
    id: string;
    browser_client_id: string;
    phone_client_id: string;
    created_at: string;
    updated_at: string;
};

type ClientRow = {
    id: string;
    label: string;
    platform: string | null;
    last_seen_at: string | null;
    push_token: string | null;
    updated_at: string;
};

function normalizeClientType(value: unknown): ClientType | null {
    const text = String(value ?? "").trim().toLowerCase();
    if (text === "browser" || text === "android") {
        return text;
    }
    return null;
}

function isOnline(lastSeenAt: string | null): boolean {
    if (!lastSeenAt) {
        return false;
    }
    const timestamp = new Date(lastSeenAt).getTime();
    if (!Number.isFinite(timestamp)) {
        return false;
    }
    const tenMinutesMs = 10 * 60 * 1000;
    return Date.now() - timestamp <= tenMinutesMs;
}

function isPlaceholderClient(clientId: string, label: string | null): boolean {
    const id = clientId.trim().toLowerCase();
    const safeLabel = String(label ?? "").trim().toLowerCase();
    if (id.startsWith("android-test-") || id.startsWith("test-")) {
        return true;
    }
    return safeLabel.includes("android test device");
}

Deno.serve(async request => {
    if (request.method === "OPTIONS") {
        return optionsResponse(request);
    }
    if (request.method !== "POST") {
        return jsonResponse({ ok: false, error: "Method not allowed" }, 405);
    }

    const authError = requireAnonApiKey(request);
    if (authError) {
        return authError;
    }

    try {
        const parsedBody = await parseJsonBody<{
            clientType?: string;
            clientId?: string;
            clientSecret?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const clientType = normalizeClientType(parsedBody.data.clientType);
        const clientId = trimText(parsedBody.data.clientId, 128);
        const clientSecret = trimText(parsedBody.data.clientSecret, 256);

        if (!clientType || !clientId || !clientSecret) {
            return jsonResponse({ ok: false, error: "clientType, clientId and clientSecret are required." }, 400);
        }
        if (!isValidClientId(clientId)) {
            return jsonResponse({ ok: false, error: "clientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(clientSecret)) {
            return jsonResponse({ ok: false, error: "clientSecret format is invalid." }, 400);
        }

        const supabase = createServiceClient();
        const clientSecretHash = await hashClientSecret(clientSecret);
        const isBrowser = clientType === "browser";

        const requesterClientQuery = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", clientId)
            .maybeSingle();

        if (requesterClientQuery.error) {
            return internalServerError("paired-devices:select-requester-client", requesterClientQuery.error);
        }
        if (!requesterClientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "clientId is not registered." }, 403);
        }
        if (requesterClientQuery.data.client_type !== clientType) {
            return jsonResponse({ ok: false, error: "clientType does not match registered client identity." }, 403);
        }

        const requesterMetadata = requesterClientQuery.data.metadata as Record<string, unknown> | null;
        const storedSecretHash = readClientSecretHash(requesterMetadata);
        if (storedSecretHash && storedSecretHash !== clientSecretHash) {
            return jsonResponse({ ok: false, error: "client credentials do not match this installation." }, 401);
        }
        if (!storedSecretHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }

        const pairLinksQuery = await supabase
            .from("pair_links")
            .select("id, browser_client_id, phone_client_id, created_at, updated_at")
            .eq(isBrowser ? "browser_client_id" : "phone_client_id", clientId)
            .eq("status", "active")
            .order("updated_at", { ascending: false })
            .limit(20);

        if (pairLinksQuery.error) {
            return internalServerError("paired-devices:select-pair-links", pairLinksQuery.error);
        }

        const pairLinks = (pairLinksQuery.data ?? []) as PairLinkRow[];
        const peerIds = Array.from(
            new Set(
                pairLinks.map(link => (isBrowser ? link.phone_client_id : link.browser_client_id)).filter(Boolean)
            )
        );

        if (peerIds.length === 0) {
            return jsonResponse({ ok: true, devices: [] });
        }

        const clientsQuery = await supabase
            .from("clients")
            .select("id, label, platform, last_seen_at, push_token, updated_at")
            .in("id", peerIds);

        if (clientsQuery.error) {
            return internalServerError("paired-devices:select-clients", clientsQuery.error);
        }

        const clients = (clientsQuery.data ?? []) as ClientRow[];
        const byId = new Map(clients.map(client => [client.id, client]));

        const visiblePeerIds = peerIds.filter(peerId => {
            const client = byId.get(peerId);
            return !isPlaceholderClient(peerId, client?.label ?? null);
        });

        const pairLinkByPeerId = new Map<string, PairLinkRow>();
        for (const link of pairLinks) {
            const peerId = isBrowser ? link.phone_client_id : link.browser_client_id;
            if (!peerId || pairLinkByPeerId.has(peerId)) {
                continue;
            }
            pairLinkByPeerId.set(peerId, link);
        }

        const deviceCandidates = visiblePeerIds.map(peerId => {
            const client = byId.get(peerId);
            const pushToken = String(client?.push_token ?? "").trim();
            const hasPushToken = pushToken.length > 20;
            const lastSeenAt = client?.last_seen_at || null;
            const pairLink = pairLinkByPeerId.get(peerId);
            const updatedAt = client?.updated_at || pairLink?.updated_at || pairLink?.created_at || "";

            return {
                id: peerId,
                label: client?.label || (isBrowser ? "Paired Android Device" : "Paired Browser"),
                platform: client?.platform || null,
                lastSeenAt,
                online: isOnline(lastSeenAt),
                hasPushToken,
                _updatedAt: updatedAt
            };
        });

        deviceCandidates.sort((a, b) => {
            if (a.hasPushToken !== b.hasPushToken) {
                return a.hasPushToken ? -1 : 1;
            }
            const aSeen = a.lastSeenAt ? new Date(a.lastSeenAt).getTime() : 0;
            const bSeen = b.lastSeenAt ? new Date(b.lastSeenAt).getTime() : 0;
            if (aSeen !== bSeen) {
                return bSeen - aSeen;
            }
            const aUpdated = a._updatedAt ? new Date(a._updatedAt).getTime() : 0;
            const bUpdated = b._updatedAt ? new Date(b._updatedAt).getTime() : 0;
            return bUpdated - aUpdated;
        });

        const devices = deviceCandidates.map(item => ({
            id: item.id,
            label: trimLabel(item.label, isBrowser ? "Paired Android Device" : "Paired Browser", 48),
            platform: item.platform,
            lastSeenAt: item.lastSeenAt,
            online: item.online,
            hasPushToken: item.hasPushToken
        }));

        return jsonResponse({ ok: true, devices });
    } catch (error) {
        return internalServerError("paired-devices:unhandled", error);
    }
});
