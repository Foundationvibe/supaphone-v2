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

type ClientType = "browser" | "android";

function normalizeClientType(value: unknown): ClientType | null {
    const text = String(value ?? "").trim().toLowerCase();
    if (text === "browser" || text === "android") {
        return text;
    }
    return null;
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
            currentClientSecret?: string;
            newClientSecret?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const clientType = normalizeClientType(parsedBody.data.clientType);
        const clientId = trimText(parsedBody.data.clientId, 128);
        const currentClientSecret = trimText(parsedBody.data.currentClientSecret, 256);
        const newClientSecret = trimText(parsedBody.data.newClientSecret, 256);

        if (!clientType || !clientId || !currentClientSecret || !newClientSecret) {
            return jsonResponse(
                { ok: false, error: "clientType, clientId, currentClientSecret and newClientSecret are required." },
                400
            );
        }
        if (!isValidClientId(clientId)) {
            return jsonResponse({ ok: false, error: "clientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(currentClientSecret) || !isValidClientSecret(newClientSecret)) {
            return jsonResponse({ ok: false, error: "Client secret format is invalid." }, 400);
        }
        if (currentClientSecret === newClientSecret) {
            return jsonResponse({ ok: false, error: "New client secret must differ from the current secret." }, 400);
        }

        const supabase = createServiceClient();
        const currentHash = await hashClientSecret(currentClientSecret);
        const newHash = await hashClientSecret(newClientSecret);

        const clientQuery = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", clientId)
            .maybeSingle();

        if (clientQuery.error) {
            return internalServerError("rotate-client-secret:select-client", clientQuery.error);
        }
        if (!clientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "clientId is not registered." }, 403);
        }
        if (clientQuery.data.client_type !== clientType) {
            return jsonResponse({ ok: false, error: "clientType does not match registered client identity." }, 403);
        }

        const existingHash = readClientSecretHash(clientQuery.data.metadata);
        if (!existingHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }
        if (existingHash !== currentHash) {
            return jsonResponse({ ok: false, error: "client credentials do not match this installation." }, 401);
        }

        const metadata = typeof clientQuery.data.metadata === "object" && clientQuery.data.metadata
            ? clientQuery.data.metadata as Record<string, unknown>
            : {};
        const updateResult = await supabase
            .from("clients")
            .update({
                metadata: {
                    ...metadata,
                    auth_secret_hash: newHash
                }
            })
            .eq("id", clientId)
            .select("id")
            .single();

        if (updateResult.error) {
            return internalServerError("rotate-client-secret:update-client", updateResult.error);
        }

        return jsonResponse({
            ok: true,
            clientId,
            rotatedAt: new Date().toISOString()
        });
    } catch (error) {
        return internalServerError("rotate-client-secret:unhandled", error);
    }
});

