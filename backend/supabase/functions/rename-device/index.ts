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

type RequesterType = "browser" | "android";

function normalizeRequesterType(value: unknown): RequesterType | null {
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
            requesterClientId?: string;
            requesterClientSecret?: string;
            requesterClientType?: string;
            targetClientId?: string;
            newLabel?: string;
        }>(request);
        if (!parsedBody.ok) {
            return parsedBody.response;
        }

        const requesterClientId = trimText(parsedBody.data.requesterClientId, 128);
        const requesterClientSecret = trimText(parsedBody.data.requesterClientSecret, 256);
        const requesterClientType = normalizeRequesterType(parsedBody.data.requesterClientType);
        const targetClientId = trimText(parsedBody.data.targetClientId, 128);
        const newLabel = trimText(parsedBody.data.newLabel, 48);

        if (!requesterClientId || !requesterClientSecret || !requesterClientType || !targetClientId || !newLabel) {
            return jsonResponse(
                {
                    ok: false,
                    error: "requesterClientId, requesterClientSecret, requesterClientType, targetClientId and newLabel are required."
                },
                400
            );
        }
        if (!isValidClientId(requesterClientId) || !isValidClientId(targetClientId)) {
            return jsonResponse({ ok: false, error: "requesterClientId/targetClientId format is invalid." }, 400);
        }
        if (!isValidClientSecret(requesterClientSecret)) {
            return jsonResponse({ ok: false, error: "requesterClientSecret format is invalid." }, 400);
        }
        if (requesterClientId === targetClientId) {
            return jsonResponse({ ok: false, error: "Cannot rename the requester identity itself." }, 400);
        }

        if (newLabel.length < 2 || newLabel.length > 48) {
            return jsonResponse(
                {
                    ok: false,
                    error: "newLabel must be between 2 and 48 characters."
                },
                400
            );
        }

        const supabase = createServiceClient();
        const isRequesterBrowser = requesterClientType === "browser";
        const requesterSecretHash = await hashClientSecret(requesterClientSecret);

        const requesterClientQuery = await supabase
            .from("clients")
            .select("id, client_type, metadata")
            .eq("id", requesterClientId)
            .maybeSingle();

        if (requesterClientQuery.error) {
            return internalServerError("rename-device:select-requester-client", requesterClientQuery.error);
        }
        if (!requesterClientQuery.data?.id) {
            return jsonResponse({ ok: false, error: "requesterClientId is not registered." }, 403);
        }
        if (requesterClientQuery.data.client_type !== requesterClientType) {
            return jsonResponse({ ok: false, error: "requesterClientType does not match registered client identity." }, 403);
        }
        const requesterMetadata = requesterClientQuery.data.metadata as Record<string, unknown> | null;
        const storedSecretHash = readClientSecretHash(requesterMetadata);
        if (storedSecretHash && storedSecretHash !== requesterSecretHash) {
            return jsonResponse({ ok: false, error: "requester credentials do not match this installation." }, 401);
        }
        if (!storedSecretHash) {
            return jsonResponse(
                { ok: false, error: "Client credentials are not registered for this installation. Re-pair and try again." },
                401
            );
        }

        const linkCheck = await supabase
            .from("pair_links")
            .select("id")
            .eq(isRequesterBrowser ? "browser_client_id" : "phone_client_id", requesterClientId)
            .eq(isRequesterBrowser ? "phone_client_id" : "browser_client_id", targetClientId)
            .eq("status", "active")
            .maybeSingle();

        if (linkCheck.error) {
            return internalServerError("rename-device:select-pair-link", linkCheck.error);
        }
        if (!linkCheck.data?.id) {
            return jsonResponse({ ok: false, error: "No active pairing found for rename." }, 404);
        }

        const updateResult = await supabase
            .from("clients")
            .update({ label: newLabel })
            .eq("id", targetClientId)
            .select("id")
            .maybeSingle();

        if (updateResult.error) {
            return internalServerError("rename-device:update-client-label", updateResult.error);
        }
        if (!updateResult.data?.id) {
            return jsonResponse({ ok: false, error: "Target device not found." }, 404);
        }

        return jsonResponse({
            ok: true,
            targetClientId,
            newLabel
        });
    } catch (error) {
        return internalServerError("rename-device:unhandled", error);
    }
});
