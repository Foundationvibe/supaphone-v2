import { createServiceClient } from "../_shared/supabase-admin.ts";
import {
    buildRequestFingerprint,
    hashClientSecret,
    internalServerError,
    isValidClientId,
    isValidClientSecret,
    jsonResponse,
    optionsResponse,
    parseJsonBody,
    randomPairingCode,
    readClientSecretHash,
    requireAnonApiKey,
    trimLabel,
    trimText
} from "../_shared/runtime.ts";

const REQUEST_WINDOW_MS = 15 * 1000;
const MAX_REQUESTS_PER_BROWSER_CLIENT = 4;
const MAX_REQUESTS_PER_REQUEST_FINGERPRINT = 8;

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
            browserClientId?: string;
            browserClientSecret?: string;
            browserLabel?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const browserClientId = trimText(parsedBody.data.browserClientId, 128);
        const browserClientSecret = trimText(parsedBody.data.browserClientSecret, 256);
        const browserLabel = trimLabel(parsedBody.data.browserLabel, "Browser Extension", 48);

        if (!browserClientId || !browserClientSecret) {
            return jsonResponse(
                { ok: false, error: "browserClientId and browserClientSecret are required." },
                400
            );
        }
        if (!isValidClientId(browserClientId)) {
            return jsonResponse(
                { ok: false, error: "browserClientId format is invalid." },
                400
            );
        }
        if (!isValidClientSecret(browserClientSecret)) {
            return jsonResponse(
                { ok: false, error: "browserClientSecret format is invalid." },
                400
            );
        }

        const browserClientSecretHash = await hashClientSecret(browserClientSecret);
        const requestFingerprint = await buildRequestFingerprint(request);

        const supabase = createServiceClient();

        const existingClient = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", browserClientId)
            .maybeSingle();

        if (existingClient.error) {
            return internalServerError("pairing-code:select-client", existingClient.error);
        }
        if (existingClient.data && existingClient.data.client_type !== "browser") {
            return jsonResponse(
                { ok: false, error: "browserClientId is already assigned to another client type." },
                409
            );
        }
        const existingSecretHash = readClientSecretHash(existingClient.data?.metadata);
        if (existingSecretHash && existingSecretHash !== browserClientSecretHash) {
            return jsonResponse(
                { ok: false, error: "browserClient credentials do not match this installation." },
                401
            );
        }

        const nowIso = new Date().toISOString();
        const rateWindowIso = new Date(Date.now() - REQUEST_WINDOW_MS).toISOString();
        const fingerprintCheck = await supabase
            .from("request_events")
            .select("id", { count: "exact", head: true })
            .eq("action", "pairing-code")
            .eq("request_fingerprint", requestFingerprint)
            .gte("created_at", rateWindowIso);

        if (fingerprintCheck.error) {
            return internalServerError("pairing-code:fingerprint-check", fingerprintCheck.error);
        }
        if ((fingerprintCheck.count ?? 0) >= MAX_REQUESTS_PER_REQUEST_FINGERPRINT) {
            return jsonResponse(
                { ok: false, error: "Too many pairing code requests. Please wait a few seconds and retry." },
                429
            );
        }

        const requestBurstCheck = await supabase
            .from("request_events")
            .select("id", { count: "exact", head: true })
            .eq("action", "pairing-code")
            .eq("client_id", browserClientId)
            .gte("created_at", rateWindowIso);

        if (requestBurstCheck.error) {
            return internalServerError("pairing-code:client-check", requestBurstCheck.error);
        }
        if ((requestBurstCheck.count ?? 0) >= MAX_REQUESTS_PER_BROWSER_CLIENT) {
            return jsonResponse(
                { ok: false, error: "Too many pairing code requests. Please wait a few seconds and retry." },
                429
            );
        }

        const requestEventInsert = await supabase
            .from("request_events")
            .insert({
                action: "pairing-code",
                client_id: browserClientId,
                request_fingerprint: requestFingerprint
            });

        if (requestEventInsert.error) {
            return internalServerError("pairing-code:request-event-insert", requestEventInsert.error);
        }

        const clientUpsert = await supabase.from("clients").upsert(
            {
                id: browserClientId,
                client_type: "browser",
                label: browserLabel,
                platform: "extension",
                metadata: {
                    auth_secret_hash: browserClientSecretHash
                },
                last_seen_at: nowIso
            },
            { onConflict: "id" }
        );
        if (clientUpsert.error) {
            return internalServerError("pairing-code:upsert-client", clientUpsert.error);
        }

        const invalidateExistingCodes = await supabase
            .from("pair_codes")
            .update({ consumed_at: nowIso })
            .eq("browser_client_id", browserClientId)
            .is("consumed_at", null);

        if (invalidateExistingCodes.error) {
            return internalServerError("pairing-code:invalidate-existing-codes", invalidateExistingCodes.error);
        }

        const expiresAt = new Date(Date.now() + 3 * 60 * 1000).toISOString();
        let code = "";
        let insertError: unknown = null;

        for (let index = 0; index < 5; index += 1) {
            code = randomPairingCode();
            const attempt = await supabase
                .from("pair_codes")
                .insert({
                    code,
                    browser_client_id: browserClientId,
                    expires_at: expiresAt
                })
                .select("id")
                .single();

            if (!attempt.error) {
                insertError = null;
                break;
            }
            insertError = attempt.error;
        }

        if (insertError) {
            return internalServerError("pairing-code:insert-pair-code", insertError);
        }

        return jsonResponse({
            ok: true,
            code,
            expiresAt
        });
    } catch (error) {
        return internalServerError("pairing-code:unhandled", error);
    }
});
