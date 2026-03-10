export const corsHeaders: Record<string, string> = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
};

const DEFAULT_MAX_JSON_BYTES = 16 * 1024;
const CLIENT_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{2,127}$/;
const CLIENT_SECRET_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{31,255}$/;
const PAIR_CODE_PATTERN = /^\d{6}$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PUSH_TOKEN_PATTERN = /^[A-Za-z0-9\-_.:~]+$/;

export function jsonResponse(payload: unknown, status = 200): Response {
    return new Response(JSON.stringify(payload), {
        status,
        headers: {
            "Content-Type": "application/json",
            ...corsHeaders
        }
    });
}

export function requireEnv(name: string): string {
    const value = Deno.env.get(name)?.trim();
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

export function optionsResponse(request: Request): Response {
    const origin = trimText(request.headers.get("origin"), 256);
    if (origin && !isAllowedRequestOrigin(origin)) {
        return new Response("forbidden", { status: 403 });
    }

    return new Response("ok", {
        headers: origin
            ? {
                ...corsHeaders,
                "Access-Control-Allow-Origin": origin,
                Vary: "Origin"
            }
            : corsHeaders
    });
}

export function randomPairingCode(): string {
    const maxExclusive = 1_000_000;
    const uint32Range = 0x1_0000_0000;
    const unbiasedUpperBound = Math.floor(uint32Range / maxExclusive) * maxExclusive;
    const randomBuffer = new Uint32Array(1);

    let randomValue = 0;
    do {
        crypto.getRandomValues(randomBuffer);
        randomValue = randomBuffer[0];
    } while (randomValue >= unbiasedUpperBound);

    return String(randomValue % maxExclusive).padStart(6, "0");
}

export async function parseJsonBody<T = Record<string, unknown>>(
    request: Request,
    maxBytes = DEFAULT_MAX_JSON_BYTES
): Promise<{ ok: true; data: T } | { ok: false; response: Response }> {
    const contentLengthRaw = request.headers.get("content-length")?.trim() ?? "";
    const contentLength = Number(contentLengthRaw);
    if (Number.isFinite(contentLength) && contentLength > maxBytes) {
        return {
            ok: false,
            response: jsonResponse(
                { ok: false, error: `Request body is too large. Max ${maxBytes} bytes.` },
                413
            )
        };
    }

    let text = "";
    try {
        text = await request.text();
    } catch (_error) {
        return {
            ok: false,
            response: jsonResponse({ ok: false, error: "Unable to read request body." }, 400)
        };
    }

    if (!text.trim()) {
        return {
            ok: false,
            response: jsonResponse({ ok: false, error: "Request body is required." }, 400)
        };
    }

    const actualSize = new TextEncoder().encode(text).length;
    if (actualSize > maxBytes) {
        return {
            ok: false,
            response: jsonResponse(
                { ok: false, error: `Request body is too large. Max ${maxBytes} bytes.` },
                413
            )
        };
    }

    try {
        return { ok: true, data: JSON.parse(text) as T };
    } catch (_error) {
        return {
            ok: false,
            response: jsonResponse({ ok: false, error: "Invalid JSON body." }, 400)
        };
    }
}

export function requireAnonApiKey(request: Request): Response | null {
    const origin = trimText(request.headers.get("origin"), 256);
    if (origin && !isAllowedRequestOrigin(origin)) {
        return jsonResponse({ ok: false, error: "Origin is not allowed." }, 403);
    }

    const apiKey = request.headers.get("apikey")?.trim() ?? "";
    const authorization = request.headers.get("authorization")?.trim() ?? "";
    const bearer = authorization.toLowerCase().startsWith("bearer ")
        ? authorization.slice(7).trim()
        : "";

    if (!apiKey || !bearer) {
        return jsonResponse({ ok: false, error: "Unauthorized request." }, 401);
    }
    if (apiKey !== bearer) {
        return jsonResponse({ ok: false, error: "Unauthorized request." }, 401);
    }

    const expectedKeys = Array.from(
        new Set(
            [
                Deno.env.get("APP_PUBLIC_ANON_KEY")?.trim(),
                Deno.env.get("SUPAPHONE_PUBLIC_ANON_KEY")?.trim(),
                Deno.env.get("SUPABASE_ANON_KEY")?.trim(),
                Deno.env.get("SUPABASE_PUBLISHABLE_KEY")?.trim(),
                Deno.env.get("SUPABASE_PUBLIC_API_KEY")?.trim()
            ].filter((value): value is string => Boolean(value))
        )
    );

    if (expectedKeys.length === 0) {
        return jsonResponse({ ok: false, error: "Server auth configuration is missing." }, 500);
    }

    if (expectedKeys.includes(apiKey)) {
        return null;
    }

    return jsonResponse({ ok: false, error: "Unauthorized request." }, 401);
}

function isAllowedRequestOrigin(origin: string): boolean {
    if (origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://")) {
        return true;
    }

    const allowedOrigins = new Set<string>();
    const websiteBaseUrl = Deno.env.get("SUPAPHONE_WEBSITE_BASE_URL")?.trim() ?? "";
    if (websiteBaseUrl) {
        try {
            allowedOrigins.add(new URL(websiteBaseUrl).origin);
        } catch (_error) {
            // Ignore invalid website URL config and continue with explicit allowlist env vars.
        }
    }

    const envLists = [
        Deno.env.get("SUPAPHONE_ALLOWED_ORIGINS"),
        Deno.env.get("APP_ALLOWED_ORIGINS")
    ];
    for (const listValue of envLists) {
        for (const candidate of String(listValue ?? "").split(",")) {
            const trimmed = trimText(candidate, 256);
            if (!trimmed) {
                continue;
            }
            allowedOrigins.add(trimmed);
        }
    }

    return allowedOrigins.has(origin);
}

export function extractRequestIp(request: Request): string {
    const cfConnectingIp = trimText(request.headers.get("cf-connecting-ip"), 64);
    if (cfConnectingIp) {
        return cfConnectingIp;
    }

    const xForwardedFor = trimText(request.headers.get("x-forwarded-for"), 256);
    if (xForwardedFor) {
        const [firstIp = ""] = xForwardedFor.split(",");
        return trimText(firstIp, 64);
    }

    return trimText(request.headers.get("x-real-ip"), 64);
}

export async function buildRequestFingerprint(request: Request): Promise<string> {
    const requestIp = extractRequestIp(request);
    const userAgent = trimText(request.headers.get("user-agent"), 160).toLowerCase();
    const parts: string[] = [];
    if (requestIp) {
        parts.push(`ip:${requestIp}`);
    }
    if (userAgent) {
        parts.push(`ua:${userAgent}`);
    }
    if (parts.length === 0) {
        return hashText("unknown");
    }
    return hashText(trimText(parts.join("|"), 256));
}

export function trimText(value: unknown, maxLength = 0): string {
    const trimmed = String(value ?? "").trim();
    if (maxLength > 0) {
        return trimmed.slice(0, maxLength);
    }
    return trimmed;
}

export function trimLabel(value: unknown, fallback: string, maxLength = 48): string {
    const text = trimText(value, maxLength);
    return text || fallback;
}

export function isValidClientId(value: string): boolean {
    return CLIENT_ID_PATTERN.test(value);
}

export function isValidClientSecret(value: string): boolean {
    return CLIENT_SECRET_PATTERN.test(value);
}

async function hashText(value: string): Promise<string> {
    const digest = await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(value)
    );
    return Array.from(new Uint8Array(digest))
        .map(byte => byte.toString(16).padStart(2, "0"))
        .join("");
}

export async function hashClientSecret(value: string): Promise<string> {
    return hashText(value);
}

export function readClientSecretHash(metadata: unknown): string {
    if (!metadata || typeof metadata !== "object") {
        return "";
    }
    const hash = (metadata as Record<string, unknown>).auth_secret_hash;
    return trimText(hash, 128);
}

export function isValidPairCode(value: string): boolean {
    return PAIR_CODE_PATTERN.test(value);
}

export function isValidUuid(value: string): boolean {
    return UUID_PATTERN.test(value);
}

export function isValidPushToken(value: string): boolean {
    if (value.length < 32 || value.length > 4096) {
        return false;
    }
    return PUSH_TOKEN_PATTERN.test(value);
}

export function internalServerError(context: string, error?: unknown): Response {
    if (error !== undefined) {
        console.error(`[${context}]`, error);
    } else {
        console.error(`[${context}] Internal server error`);
    }
    return jsonResponse({ ok: false, error: "Internal server error." }, 500);
}
