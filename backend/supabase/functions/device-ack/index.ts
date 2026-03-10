import { createServiceClient } from "../_shared/supabase-admin.ts";
import {
    hashClientSecret,
    internalServerError,
    isValidClientId,
    isValidClientSecret,
    isValidUuid,
    jsonResponse,
    optionsResponse,
    parseJsonBody,
    readClientSecretHash,
    requireAnonApiKey,
    trimText
} from "../_shared/runtime.ts";

const STATUS_RANK: Record<string, number> = {
    queued: 0,
    pending_provider: 1,
    sent: 2,
    delivered: 3,
    opened: 4,
    failed: 5
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
            eventId?: string;
            targetClientId?: string;
            targetClientSecret?: string;
            status?: string;
            ackMessage?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const eventId = trimText(parsedBody.data.eventId, 64);
        const targetClientId = trimText(parsedBody.data.targetClientId, 128);
        const targetClientSecret = trimText(parsedBody.data.targetClientSecret, 256);
        const status = trimText(parsedBody.data.status, 24).toLowerCase();
        const ackMessage = trimText(parsedBody.data.ackMessage, 240);

        if (!eventId || !targetClientId || !targetClientSecret || !status) {
            return jsonResponse(
                { ok: false, error: "eventId, targetClientId, targetClientSecret and status are required." },
                400
            );
        }
        if (!isValidUuid(eventId)) {
            return jsonResponse({ ok: false, error: "eventId format is invalid." }, 400);
        }
        if (!isValidClientId(targetClientId)) {
            return jsonResponse({ ok: false, error: "targetClientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(targetClientSecret)) {
            return jsonResponse({ ok: false, error: "targetClientSecret format is invalid." }, 400);
        }
        if (!["sent", "delivered", "opened", "failed"].includes(status)) {
            return jsonResponse({ ok: false, error: "status must be sent, delivered, opened, or failed." }, 400);
        }

        const supabase = createServiceClient();
        const targetSecretHash = await hashClientSecret(targetClientSecret);
        const targetClientQuery = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", targetClientId)
            .maybeSingle();

        if (targetClientQuery.error) {
            return internalServerError("device-ack:select-target-client", targetClientQuery.error);
        }
        if (!targetClientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "targetClientId is not registered." }, 403);
        }
        if (targetClientQuery.data.client_type !== "android") {
            return jsonResponse({ ok: false, error: "targetClientId must be an android client." }, 403);
        }
        const targetMetadata = targetClientQuery.data.metadata as Record<string, unknown> | null;
        const storedSecretHash = readClientSecretHash(targetMetadata);
        if (storedSecretHash && storedSecretHash !== targetSecretHash) {
            return jsonResponse({ ok: false, error: "targetClient credentials do not match this installation." }, 401);
        }
        if (!storedSecretHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }

        const existing = await supabase
            .from("push_events")
            .select("id, status, target_client_id")
            .eq("id", eventId)
            .maybeSingle();

        if (existing.error) {
            return internalServerError("device-ack:select-existing-event", existing.error);
        }
        if (!existing.data?.id) {
            return jsonResponse({ ok: false, error: "Push event not found." }, 404);
        }
        if (trimText(existing.data.target_client_id, 128) !== targetClientId) {
            return jsonResponse({ ok: false, error: "Push event target does not match requester." }, 403);
        }

        const currentStatus = String(existing.data.status ?? "queued");
        const currentRank = STATUS_RANK[currentStatus] ?? 0;
        const incomingRank = STATUS_RANK[status] ?? 0;

        if (currentStatus === "opened" && status !== "opened") {
            return jsonResponse({ ok: false, error: "Cannot downgrade an already opened event." }, 409);
        }
        if (status !== "failed" && incomingRank < currentRank) {
            return jsonResponse({ ok: false, error: "Invalid status transition." }, 409);
        }

        const now = new Date().toISOString();
        const updatePayload: Record<string, unknown> = {
            status,
            backend_message: ackMessage || null
        };
        if (status === "sent") {
            updatePayload.sent_at = now;
        }
        if (status === "delivered" || status === "opened") {
            updatePayload.delivered_at = now;
        }
        if (status === "failed") {
            updatePayload.failed_at = now;
        }

        const updateResult = await supabase
            .from("push_events")
            .update(updatePayload)
            .eq("id", eventId)
            .select("id")
            .maybeSingle();

        if (updateResult.error) {
            return internalServerError("device-ack:update-event", updateResult.error);
        }
        if (!updateResult.data) {
            return jsonResponse({ ok: false, error: "Push event not found." }, 404);
        }

        if (status === "failed") {
            await supabase.from("activity_logs").insert({
                level: "error-server",
                message: `Push event ${eventId} acknowledged as failed.`,
                related_event_id: eventId
            });
        }

        return jsonResponse({
            ok: true,
            eventId,
            status
        });
    } catch (error) {
        return internalServerError("device-ack:unhandled", error);
    }
});
