import { createServiceClient } from "../_shared/supabase-admin.ts";
import {
    buildRequestFingerprint,
    hashClientSecret,
    internalServerError,
    isValidClientId,
    isValidClientSecret,
    isValidPushToken,
    jsonResponse,
    optionsResponse,
    parseJsonBody,
    readClientSecretHash,
    requireAnonApiKey,
    trimLabel,
    trimText
} from "../_shared/runtime.ts";

const REQUEST_WINDOW_MS = 5 * 60 * 1000;
const MAX_REQUESTS_PER_PHONE_CLIENT = 12;
const MAX_REQUESTS_PER_REQUEST_FINGERPRINT = 30;

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
            phoneClientId?: string;
            phoneClientSecret?: string;
            phoneLabel?: string;
            pushToken?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const phoneClientId = trimText(parsedBody.data.phoneClientId, 128);
        const phoneClientSecret = trimText(parsedBody.data.phoneClientSecret, 256);
        const phoneLabel = trimLabel(parsedBody.data.phoneLabel, "Android Device", 48);
        const pushToken = trimText(parsedBody.data.pushToken, 4096);

        if (!phoneClientId || !phoneClientSecret || !pushToken) {
            return jsonResponse(
                { ok: false, error: "phoneClientId, phoneClientSecret, and pushToken are required." },
                400
            );
        }
        if (!isValidClientId(phoneClientId)) {
            return jsonResponse({ ok: false, error: "phoneClientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(phoneClientSecret)) {
            return jsonResponse({ ok: false, error: "phoneClientSecret format is invalid." }, 400);
        }
        if (!isValidPushToken(pushToken)) {
            return jsonResponse({ ok: false, error: "pushToken format is invalid." }, 400);
        }

        const supabase = createServiceClient();
        const now = new Date().toISOString();
        const phoneClientSecretHash = await hashClientSecret(phoneClientSecret);
        const requestFingerprint = await buildRequestFingerprint(request);
        const requestWindowIso = new Date(Date.now() - REQUEST_WINDOW_MS).toISOString();

        const phoneBurstCheck = await supabase
            .from("request_events")
            .select("id", { count: "exact", head: true })
            .eq("action", "register-push-token")
            .eq("client_id", phoneClientId)
            .gte("created_at", requestWindowIso);

        if (phoneBurstCheck.error) {
            return internalServerError("register-push-token:phone-burst-check", phoneBurstCheck.error);
        }
        if ((phoneBurstCheck.count ?? 0) >= MAX_REQUESTS_PER_PHONE_CLIENT) {
            return jsonResponse(
                { ok: false, error: "Too many push registration attempts. Please wait a minute and retry." },
                429
            );
        }

        const fingerprintBurstCheck = await supabase
            .from("request_events")
            .select("id", { count: "exact", head: true })
            .eq("action", "register-push-token")
            .eq("request_fingerprint", requestFingerprint)
            .gte("created_at", requestWindowIso);

        if (fingerprintBurstCheck.error) {
            return internalServerError("register-push-token:fingerprint-burst-check", fingerprintBurstCheck.error);
        }
        if ((fingerprintBurstCheck.count ?? 0) >= MAX_REQUESTS_PER_REQUEST_FINGERPRINT) {
            return jsonResponse(
                { ok: false, error: "Too many push registration attempts. Please wait a minute and retry." },
                429
            );
        }

        const requestEventInsert = await supabase
            .from("request_events")
            .insert({
                action: "register-push-token",
                client_id: phoneClientId,
                request_fingerprint: requestFingerprint
            });

        if (requestEventInsert.error) {
            return internalServerError("register-push-token:request-event-insert", requestEventInsert.error);
        }

        const existingClient = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", phoneClientId)
            .maybeSingle();

        if (existingClient.error) {
            return internalServerError("register-push-token:select-client", existingClient.error);
        }
        if (existingClient.data && existingClient.data.client_type !== "android") {
            return jsonResponse(
                { ok: false, error: "phoneClientId is already assigned to another client type." },
                409
            );
        }
        if (!existingClient.data?.id) {
            return jsonResponse(
                { ok: false, error: "Phone must be paired before push token registration." },
                403
            );
        }
        const existingSecretHash = readClientSecretHash(existingClient.data?.metadata);
        if (existingSecretHash && existingSecretHash !== phoneClientSecretHash) {
            return jsonResponse(
                { ok: false, error: "phoneClient credentials do not match this installation." },
                401
            );
        }
        if (!existingSecretHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }

        const pairLinkCheck = await supabase
            .from("pair_links")
            .select("id", { count: "exact", head: true })
            .eq("phone_client_id", phoneClientId)
            .eq("status", "active");

        if (pairLinkCheck.error) {
            return internalServerError("register-push-token:pair-link-check", pairLinkCheck.error);
        }
        if ((pairLinkCheck.count ?? 0) === 0) {
            return jsonResponse(
                { ok: false, error: "Phone must be paired before push token registration." },
                403
            );
        }

        const updateResult = await supabase
            .from("clients")
            .update({
                label: phoneLabel,
                push_token: pushToken,
                last_seen_at: now
            })
            .eq("id", phoneClientId)
            .select("id")
            .single();

        if (updateResult.error) {
            return internalServerError("register-push-token:update-client", updateResult.error);
        }

        return jsonResponse({
            ok: true,
            phoneClientId,
            updatedAt: now
        });
    } catch (error) {
        return internalServerError("register-push-token:unhandled", error);
    }
});
