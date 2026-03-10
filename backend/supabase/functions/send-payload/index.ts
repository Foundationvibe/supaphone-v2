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
import { loadFirebaseConfig, sendFirebasePush } from "../_shared/firebase.ts";

function summarizePayload(payloadType: string, payload: string): { title: string; body: string } {
    if (payloadType === "call") {
        return {
            title: "Call from SupaPhone",
            body: payload
        };
    }

    const maxLength = 120;
    const safePayload = payload.length > maxLength ? `${payload.slice(0, maxLength - 3)}...` : payload;
    return {
        title: "Link from SupaPhone",
        body: safePayload
    };
}

function looksLikeLinkPayload(payload: string): boolean {
    const value = trimText(payload).toLowerCase();
    if (!value) {
        return false;
    }
    if (value.startsWith("http://") || value.startsWith("https://")) {
        return true;
    }
    const hasDomain = value.includes(".") && /[a-z]/.test(value);
    const hasUrlMarkers = value.includes("/") || value.includes("?") || value.includes("#");
    return hasDomain || hasUrlMarkers;
}

function hasPushToken(token: string | null | undefined): boolean {
    return trimText(token).length > 20;
}

function normalizeCallPayload(payload: string): string {
    const trimmed = trimText(payload);
    if (!trimmed) {
        return "";
    }
    let normalized = "";
    for (let index = 0; index < trimmed.length; index += 1) {
        const char = trimmed[index];
        if (/\d/.test(char)) {
            normalized += char;
            continue;
        }
        if (char === "+" && index === 0) {
            normalized += char;
        }
    }
    return normalized || trimmed;
}

type MetadataRow = {
    [key: string]: unknown;
};

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
            sourceClientId?: string;
            sourceClientSecret?: string;
            targetClientId?: string;
            payloadType?: string;
            payload?: string;
        }>(request, 32 * 1024);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const sourceClientId = trimText(parsedBody.data.sourceClientId, 128);
        const sourceClientSecret = trimText(parsedBody.data.sourceClientSecret, 256);
        const targetClientId = trimText(parsedBody.data.targetClientId, 128);
        const payloadType = trimText(parsedBody.data.payloadType, 16).toLowerCase();
        const rawPayload = trimText(parsedBody.data.payload);

        if (!sourceClientId || !sourceClientSecret || !targetClientId || !rawPayload) {
            return jsonResponse(
                { ok: false, error: "sourceClientId, sourceClientSecret, targetClientId and payload are required." },
                400
            );
        }
        if (rawPayload.length > 2048) {
            return jsonResponse({ ok: false, error: "payload exceeds 2048 characters." }, 413);
        }
        const payload = rawPayload;
        if (!isValidClientId(sourceClientId) || !isValidClientId(targetClientId)) {
            return jsonResponse({ ok: false, error: "sourceClientId/targetClientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(sourceClientSecret)) {
            return jsonResponse({ ok: false, error: "sourceClientSecret format is invalid." }, 400);
        }
        if (sourceClientId === targetClientId) {
            return jsonResponse({ ok: false, error: "source and target clients must be different." }, 400);
        }
        if (!["link", "call"].includes(payloadType)) {
            return jsonResponse({ ok: false, error: "payloadType must be 'link' or 'call'." }, 400);
        }
        const resolvedPayloadType = payloadType === "call" && looksLikeLinkPayload(payload)
            ? "link"
            : payloadType;
        const resolvedPayload = resolvedPayloadType === "call" ? normalizeCallPayload(payload) : payload;
        if (!resolvedPayload) {
            return jsonResponse({ ok: false, error: "Payload is empty after normalization." }, 400);
        }
        if (resolvedPayloadType === "call") {
            const digitCount = Array.from(resolvedPayload).filter(char => /\d/.test(char)).length;
            if (digitCount < 6) {
                return jsonResponse({ ok: false, error: "Call payload must include at least 6 digits." }, 400);
            }
        }
        if (resolvedPayloadType === "link" && resolvedPayload.length < 4) {
            return jsonResponse({ ok: false, error: "Link payload is too short." }, 400);
        }

        const supabase = createServiceClient();
        const sourceSecretHash = await hashClientSecret(sourceClientSecret);

        const sourceClientQuery = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", sourceClientId)
            .maybeSingle();

        if (sourceClientQuery.error) {
            return internalServerError("send-payload:select-source-client", sourceClientQuery.error);
        }
        if (!sourceClientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "sourceClientId is not registered." }, 403);
        }
        if (sourceClientQuery.data.client_type !== "browser") {
            return jsonResponse({ ok: false, error: "sourceClientId must be a browser client." }, 403);
        }

        const sourceMetadata = sourceClientQuery.data.metadata as MetadataRow | null;
        const existingSourceHash = readClientSecretHash(sourceMetadata);
        if (existingSourceHash && existingSourceHash !== sourceSecretHash) {
            return jsonResponse({ ok: false, error: "sourceClient credentials do not match this installation." }, 401);
        }
        if (!existingSourceHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }

        const burstWindowIso = new Date(Date.now() - 10_000).toISOString();
        const burstCheck = await supabase
            .from("push_events")
            .select("id", { count: "exact", head: true })
            .eq("source_client_id", sourceClientId)
            .gte("created_at", burstWindowIso);

        if (burstCheck.error) {
            return internalServerError("send-payload:burst-check", burstCheck.error);
        }
        if ((burstCheck.count ?? 0) >= 20) {
            return jsonResponse(
                { ok: false, error: "Too many send requests. Please wait a moment and retry." },
                429
            );
        }

        const pairLinkQuery = await supabase
            .from("pair_links")
            .select("id, browser_client_id, phone_client_id")
            .eq("browser_client_id", sourceClientId)
            .eq("phone_client_id", targetClientId)
            .eq("status", "active")
            .maybeSingle();

        if (pairLinkQuery.error) {
            return internalServerError("send-payload:select-pair-link", pairLinkQuery.error);
        }
        if (!pairLinkQuery.data?.id) {
            return jsonResponse({ ok: false, error: "No active pairing found for the selected source and target." }, 403);
        }

        const targetClientQuery = await supabase
            .from("clients")
            .select("id, push_token")
            .eq("id", targetClientId)
            .maybeSingle();

        if (targetClientQuery.error) {
            return internalServerError("send-payload:select-target-client", targetClientQuery.error);
        }

        const resolvedPushToken = trimText(targetClientQuery.data?.push_token);
        let pendingMessage: string | null = null;

        if (!hasPushToken(resolvedPushToken)) {
            pendingMessage = "Target phone has no push token yet. Open Android app once and allow notifications.";
        }

        const initialStatus = pendingMessage ? "pending_provider" : "queued";
        const initialMessage = pendingMessage || "Payload accepted and queued.";

        const initialEvent = await supabase
            .from("push_events")
            .insert({
                pair_link_id: pairLinkQuery.data.id,
                source_client_id: sourceClientId,
                target_client_id: targetClientId,
                payload_type: resolvedPayloadType,
                payload: resolvedPayload,
                status: initialStatus,
                backend_message: initialMessage
            })
            .select("id")
            .single();

        if (initialEvent.error || !initialEvent.data?.id) {
            return internalServerError("send-payload:create-push-event", initialEvent.error ?? "missing event id");
        }

        const eventId = initialEvent.data.id;
        const now = new Date().toISOString();

        if (pendingMessage) {
            await supabase.from("activity_logs").insert({
                level: "error-client",
                message: `Push event ${eventId} pending provider: ${pendingMessage}`,
                related_event_id: eventId
            });

            return jsonResponse({
                ok: true,
                eventId,
                status: "pending_provider",
                message: pendingMessage
            });
        }

        const firebaseConfigResult = await loadFirebaseConfig();
        if (!firebaseConfigResult.ok) {
            const providerUnavailableMessage = "Push provider not configured yet.";
            await supabase.from("push_events").update({
                status: "pending_provider",
                backend_message: providerUnavailableMessage
            }).eq("id", eventId);

            await supabase.from("activity_logs").insert({
                level: "error-server",
                message: `Push event ${eventId} pending provider: ${firebaseConfigResult.error}`,
                related_event_id: eventId
            });

            return jsonResponse({
                ok: true,
                eventId,
                status: "pending_provider",
                message: providerUnavailableMessage
            });
        }

        const summary = summarizePayload(resolvedPayloadType, resolvedPayload);
        const firebaseSendResult = await sendFirebasePush(firebaseConfigResult.value, {
            token: resolvedPushToken,
            title: summary.title,
            body: summary.body,
            data: {
                type: resolvedPayloadType,
                payload: resolvedPayload,
                eventId,
                sourceClientId,
                targetClientId,
                requestedTargetClientId: targetClientId
            }
        });

        if (!firebaseSendResult.ok) {
            const providerDeliveryError = "Push provider delivery failed.";
            await supabase.from("push_events").update({
                status: "failed",
                failed_at: now,
                backend_message: providerDeliveryError
            }).eq("id", eventId);

            await supabase.from("activity_logs").insert({
                level: "error-server",
                message: `Push event ${eventId} failed: ${firebaseSendResult.error}`,
                related_event_id: eventId
            });

            return jsonResponse({
                ok: false,
                eventId,
                status: "failed",
                error: providerDeliveryError
            }, 502);
        }

        const successMessage = `Payload delivered to FCM (${firebaseSendResult.value || "message accepted"}).`;
        await supabase.from("push_events").update({
            status: "sent",
            sent_at: now,
            backend_message: successMessage
        }).eq("id", eventId);

        return jsonResponse({
            ok: true,
            eventId,
            status: "sent",
            message: successMessage,
            targetClientId
        });
    } catch (error) {
        return internalServerError("send-payload:unhandled", error);
    }
});
