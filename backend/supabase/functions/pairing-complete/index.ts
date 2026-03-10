import { createServiceClient } from "../_shared/supabase-admin.ts";
import {
    buildRequestFingerprint,
    hashClientSecret,
    internalServerError,
    isValidClientId,
    isValidClientSecret,
    isValidPairCode,
    isValidPushToken,
    jsonResponse,
    optionsResponse,
    parseJsonBody,
    readClientSecretHash,
    requireAnonApiKey,
    trimLabel,
    trimText
} from "../_shared/runtime.ts";

const ATTEMPT_WINDOW_MS = 60 * 1000;
const MAX_ATTEMPTS_PER_PHONE_CLIENT = 12;
const MAX_ATTEMPTS_PER_REQUEST_FINGERPRINT = 20;
const MAX_ATTEMPTS_PER_PAIR_CODE = 6;
const MAX_ATTEMPTS_PER_BROWSER_CLIENT = 10;

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
            code?: string;
            phoneClientId?: string;
            phoneClientSecret?: string;
            phoneLabel?: string;
            pushToken?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const code = trimText(parsedBody.data.code, 6);
        const phoneClientId = trimText(parsedBody.data.phoneClientId, 128);
        const phoneClientSecret = trimText(parsedBody.data.phoneClientSecret, 256);
        const phoneLabel = trimLabel(parsedBody.data.phoneLabel, "Android Device", 48);
        const pushToken = trimText(parsedBody.data.pushToken, 4096);

        if (!code || !phoneClientId || !phoneClientSecret) {
            return jsonResponse(
                { ok: false, error: "code, phoneClientId, and phoneClientSecret are required." },
                400
            );
        }
        if (!isValidPairCode(code)) {
            return jsonResponse({ ok: false, error: "Invalid pairing code format." }, 400);
        }
        if (!isValidClientId(phoneClientId)) {
            return jsonResponse({ ok: false, error: "phoneClientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(phoneClientSecret)) {
            return jsonResponse({ ok: false, error: "phoneClientSecret format is invalid." }, 400);
        }
        if (pushToken && !isValidPushToken(pushToken)) {
            return jsonResponse({ ok: false, error: "pushToken format is invalid." }, 400);
        }

        const supabase = createServiceClient();
        const requestFingerprint = await buildRequestFingerprint(request);
        const nowIso = new Date().toISOString();
        const attemptWindowIso = new Date(Date.now() - ATTEMPT_WINDOW_MS).toISOString();
        const pairCodeCandidate = await supabase
            .from("pair_codes")
            .select("browser_client_id, expires_at, consumed_at")
            .eq("code", code)
            .maybeSingle();

        if (pairCodeCandidate.error) {
            return internalServerError("pairing-complete:pair-code-candidate", pairCodeCandidate.error);
        }
        const candidateBrowserClientId = trimText(pairCodeCandidate.data?.browser_client_id, 128);

        const phoneAttemptCheck = await supabase
            .from("pairing_attempts")
            .select("id", { count: "exact", head: true })
            .eq("phone_client_id", phoneClientId)
            .gte("created_at", attemptWindowIso);

        if (phoneAttemptCheck.error) {
            return internalServerError("pairing-complete:attempt-check-phone", phoneAttemptCheck.error);
        }
        if ((phoneAttemptCheck.count ?? 0) >= MAX_ATTEMPTS_PER_PHONE_CLIENT) {
            return jsonResponse(
                { ok: false, error: "Too many pairing attempts. Please wait a minute and retry." },
                429
            );
        }

        const fingerprintAttemptCheck = await supabase
            .from("pairing_attempts")
            .select("id", { count: "exact", head: true })
            .eq("request_fingerprint", requestFingerprint)
            .gte("created_at", attemptWindowIso);

        if (fingerprintAttemptCheck.error) {
            return internalServerError("pairing-complete:attempt-check-fingerprint", fingerprintAttemptCheck.error);
        }
        if ((fingerprintAttemptCheck.count ?? 0) >= MAX_ATTEMPTS_PER_REQUEST_FINGERPRINT) {
            return jsonResponse(
                { ok: false, error: "Too many pairing attempts. Please wait a minute and retry." },
                429
            );
        }

        const pairCodeAttemptCheck = await supabase
            .from("pairing_attempts")
            .select("id", { count: "exact", head: true })
            .eq("pair_code", code)
            .gte("created_at", attemptWindowIso);

        if (pairCodeAttemptCheck.error) {
            return internalServerError("pairing-complete:attempt-check-pair-code", pairCodeAttemptCheck.error);
        }
        if ((pairCodeAttemptCheck.count ?? 0) >= MAX_ATTEMPTS_PER_PAIR_CODE) {
            return jsonResponse(
                { ok: false, error: "Too many attempts for this pairing code. Generate a new code and retry." },
                429
            );
        }

        if (candidateBrowserClientId) {
            const browserAttemptCheck = await supabase
                .from("pairing_attempts")
                .select("id", { count: "exact", head: true })
                .eq("browser_client_id", candidateBrowserClientId)
                .gte("created_at", attemptWindowIso);

            if (browserAttemptCheck.error) {
                return internalServerError("pairing-complete:attempt-check-browser", browserAttemptCheck.error);
            }
            if ((browserAttemptCheck.count ?? 0) >= MAX_ATTEMPTS_PER_BROWSER_CLIENT) {
                return jsonResponse(
                    { ok: false, error: "Too many attempts for this browser pairing request. Generate a new code and retry." },
                    429
                );
            }
        }

        const attemptInsert = await supabase
            .from("pairing_attempts")
            .insert({
                phone_client_id: phoneClientId,
                request_fingerprint: requestFingerprint,
                pair_code: code,
                browser_client_id: candidateBrowserClientId || null
            });

        if (attemptInsert.error) {
            return internalServerError("pairing-complete:attempt-insert", attemptInsert.error);
        }

        const phoneClientSecretHash = await hashClientSecret(phoneClientSecret);

        const existingClient = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", phoneClientId)
            .maybeSingle();

        if (existingClient.error) {
            return internalServerError("pairing-complete:select-client", existingClient.error);
        }
        if (existingClient.data && existingClient.data.client_type !== "android") {
            return jsonResponse(
                { ok: false, error: "phoneClientId is already assigned to another client type." },
                409
            );
        }
        const existingSecretHash = readClientSecretHash(existingClient.data?.metadata);
        if (existingSecretHash && existingSecretHash !== phoneClientSecretHash) {
            return jsonResponse(
                { ok: false, error: "phoneClient credentials do not match this installation." },
                401
            );
        }

        const pairCodeConsume = await supabase
            .from("pair_codes")
            .update({ consumed_at: nowIso })
            .eq("code", code)
            .is("consumed_at", null)
            .gte("expires_at", nowIso)
            .select("id, code, browser_client_id, expires_at")
            .maybeSingle();

        if (pairCodeConsume.error) {
            return internalServerError("pairing-complete:consume-pair-code", pairCodeConsume.error);
        }
        if (!pairCodeConsume.data) {
            const pairCodeStatus = await supabase
                .from("pair_codes")
                .select("expires_at, consumed_at")
                .eq("code", code)
                .maybeSingle();

            if (pairCodeStatus.error) {
                return internalServerError("pairing-complete:pair-code-status", pairCodeStatus.error);
            }

            if (pairCodeStatus.data && !pairCodeStatus.data.consumed_at) {
                const expiresAtMs = new Date(pairCodeStatus.data.expires_at).getTime();
                if (Number.isFinite(expiresAtMs) && expiresAtMs < Date.now()) {
                    return jsonResponse({ ok: false, error: "Pairing code expired." }, 410);
                }
            }

            return jsonResponse({ ok: false, error: "Invalid pairing code." }, 404);
        }
        const pairCode = pairCodeConsume.data;

        const phoneClientPayload: Record<string, unknown> = {
            id: phoneClientId,
            client_type: "android",
            label: phoneLabel,
            platform: "android",
            metadata: {
                auth_secret_hash: phoneClientSecretHash
            },
            last_seen_at: nowIso
        };
        if (pushToken) {
            // Preserve existing token if current request does not include one.
            phoneClientPayload.push_token = pushToken;
        }

        const phoneUpsert = await supabase.from("clients").upsert(
            phoneClientPayload,
            { onConflict: "id" }
        );
        if (phoneUpsert.error) {
            return internalServerError("pairing-complete:upsert-phone-client", phoneUpsert.error);
        }

        const linkResult = await supabase
            .from("pair_links")
            .upsert(
                {
                    browser_client_id: pairCode.browser_client_id,
                    phone_client_id: phoneClientId,
                    status: "active"
                },
                { onConflict: "browser_client_id,phone_client_id" }
            )
            .select("id")
            .single();

        if (linkResult.error) {
            return internalServerError("pairing-complete:upsert-pair-link", linkResult.error);
        }

        return jsonResponse({
            ok: true,
            pairLinkId: linkResult.data.id,
            browserClientId: pairCode.browser_client_id,
            phoneClientId
        });
    } catch (error) {
        return internalServerError("pairing-complete:unhandled", error);
    }
});
