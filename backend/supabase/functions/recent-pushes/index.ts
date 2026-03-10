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
    trimText
} from "../_shared/runtime.ts";

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
            clientId?: string;
            clientSecret?: string;
            limit?: number;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const clientId = trimText(parsedBody.data.clientId, 128);
        const clientSecret = trimText(parsedBody.data.clientSecret, 256);
        const limit = Math.max(1, Math.min(100, Number(parsedBody.data.limit ?? 50)));
        const fromDateIso = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

        if (!clientId || !clientSecret) {
            return jsonResponse({ ok: false, error: "clientId and clientSecret are required." }, 400);
        }
        if (!isValidClientId(clientId)) {
            return jsonResponse({ ok: false, error: "clientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(clientSecret)) {
            return jsonResponse({ ok: false, error: "clientSecret format is invalid." }, 400);
        }

        const supabase = createServiceClient();
        const clientSecretHash = await hashClientSecret(clientSecret);
        const requesterClientQuery = await supabase
            .from("clients")
            .select("id, metadata")
            .eq("id", clientId)
            .maybeSingle();

        if (requesterClientQuery.error) {
            return internalServerError("recent-pushes:select-client", requesterClientQuery.error);
        }
        if (!requesterClientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "clientId is not registered." }, 403);
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

        const result = await supabase
            .from("push_events")
            .select("id, payload_type, payload, status, created_at")
            .eq("target_client_id", clientId)
            .gte("created_at", fromDateIso)
            .order("created_at", { ascending: false })
            .limit(limit);

        if (result.error) {
            return internalServerError("recent-pushes:select-push-events", result.error);
        }

        const items = (result.data ?? []).map(item => ({
            id: item.id,
            type: item.payload_type,
            content: item.payload,
            status: item.status,
            createdAt: item.created_at
        }));

        return jsonResponse({ ok: true, items });
    } catch (error) {
        return internalServerError("recent-pushes:unhandled", error);
    }
});
