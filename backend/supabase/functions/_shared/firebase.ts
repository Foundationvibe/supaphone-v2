type FirebaseServiceAccount = {
    project_id: string;
    client_email: string;
    private_key: string;
    token_uri?: string;
};

type FirebaseConfig = {
    projectId: string;
    serviceAccount: FirebaseServiceAccount;
};

type FirebasePushRequest = {
    token: string;
    title: string;
    body: string;
    data: Record<string, string>;
};

type FirebaseResult<T> =
    | { ok: true; value: T }
    | { ok: false; error: string };

function base64UrlEncodeText(text: string): string {
    return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlEncodeBytes(bytes: Uint8Array): string {
    let binary = "";
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemToArrayBuffer(privateKeyPem: string): ArrayBuffer {
    const normalized = privateKeyPem
        .replace(/-----BEGIN PRIVATE KEY-----/g, "")
        .replace(/-----END PRIVATE KEY-----/g, "")
        .replace(/\s+/g, "");

    const decoded = atob(normalized);
    const bytes = new Uint8Array(decoded.length);
    for (let index = 0; index < decoded.length; index += 1) {
        bytes[index] = decoded.charCodeAt(index);
    }
    return bytes.buffer;
}

async function signJwtWithServiceAccount(payload: Record<string, unknown>, privateKeyPem: string): Promise<string> {
    const header = { alg: "RS256", typ: "JWT" };
    const unsignedToken = `${base64UrlEncodeText(JSON.stringify(header))}.${base64UrlEncodeText(JSON.stringify(payload))}`;

    const key = await crypto.subtle.importKey(
        "pkcs8",
        pemToArrayBuffer(privateKeyPem),
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["sign"]
    );

    const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        key,
        new TextEncoder().encode(unsignedToken)
    );

    return `${unsignedToken}.${base64UrlEncodeBytes(new Uint8Array(signature))}`;
}

async function loadServiceAccountJsonText(): Promise<FirebaseResult<string>> {
    const base64Json = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON_BASE64")?.trim();
    if (base64Json) {
        try {
            const decoded = atob(base64Json.replace(/\s+/g, ""));
            return { ok: true, value: decoded };
        } catch (error) {
            return {
                ok: false,
                error: `Invalid FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 value: ${error instanceof Error ? error.message : String(error)}`
            };
        }
    }

    const inlineJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")?.trim();
    if (inlineJson) {
        return { ok: true, value: inlineJson };
    }

    const path = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON_PATH")?.trim();
    if (!path) {
        return {
            ok: false,
            error: "Missing FIREBASE_SERVICE_ACCOUNT_JSON, FIREBASE_SERVICE_ACCOUNT_JSON_BASE64, or FIREBASE_SERVICE_ACCOUNT_JSON_PATH."
        };
    }

    try {
        const text = await Deno.readTextFile(path);
        if (!text.trim()) {
            return { ok: false, error: `Firebase service account file is empty: ${path}` };
        }
        return { ok: true, value: text };
    } catch (error) {
        return {
            ok: false,
            error: `Unable to read Firebase service account file at ${path}: ${error instanceof Error ? error.message : String(error)}`
        };
    }
}

function parseServiceAccount(jsonText: string): FirebaseResult<FirebaseServiceAccount> {
    try {
        const parsed = JSON.parse(jsonText) as Partial<FirebaseServiceAccount>;
        const projectId = String(parsed.project_id ?? "").trim();
        const clientEmail = String(parsed.client_email ?? "").trim();
        const privateKeyRaw = String(parsed.private_key ?? "");
        const privateKey = privateKeyRaw.replace(/\\n/g, "\n").trim();
        const tokenUri = String(parsed.token_uri ?? "https://oauth2.googleapis.com/token").trim();

        if (!projectId || !clientEmail || !privateKey) {
            return {
                ok: false,
                error: "Firebase service account JSON missing project_id, client_email, or private_key."
            };
        }

        return {
            ok: true,
            value: {
                project_id: projectId,
                client_email: clientEmail,
                private_key: privateKey,
                token_uri: tokenUri || "https://oauth2.googleapis.com/token"
            }
        };
    } catch (error) {
        return {
            ok: false,
            error: `Invalid Firebase service account JSON: ${error instanceof Error ? error.message : String(error)}`
        };
    }
}

async function requestGoogleAccessToken(serviceAccount: FirebaseServiceAccount): Promise<FirebaseResult<string>> {
    const issuedAt = Math.floor(Date.now() / 1000);
    const expiresAt = issuedAt + 3600;
    const tokenUri = serviceAccount.token_uri || "https://oauth2.googleapis.com/token";

    const jwt = await signJwtWithServiceAccount(
        {
            iss: serviceAccount.client_email,
            scope: "https://www.googleapis.com/auth/firebase.messaging",
            aud: tokenUri,
            iat: issuedAt,
            exp: expiresAt
        },
        serviceAccount.private_key
    );

    const response = await fetch(tokenUri, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams({
            grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion: jwt
        })
    });

    let data: Record<string, unknown> = {};
    try {
        data = await response.json();
    } catch (_error) {
        data = {};
    }

    if (!response.ok) {
        return {
            ok: false,
            error: String(data.error_description || data.error || `Google OAuth token request failed (${response.status}).`)
        };
    }

    const accessToken = String(data.access_token ?? "").trim();
    if (!accessToken) {
        return { ok: false, error: "Google OAuth token response did not include access_token." };
    }
    return { ok: true, value: accessToken };
}

export async function loadFirebaseConfig(): Promise<FirebaseResult<FirebaseConfig>> {
    const projectIdFromEnv = Deno.env.get("FIREBASE_PROJECT_ID")?.trim();
    const jsonTextResult = await loadServiceAccountJsonText();
    if (!jsonTextResult.ok) {
        return jsonTextResult;
    }

    const serviceAccountResult = parseServiceAccount(jsonTextResult.value);
    if (!serviceAccountResult.ok) {
        return serviceAccountResult;
    }

    const projectId = projectIdFromEnv || serviceAccountResult.value.project_id;
    if (!projectId) {
        return { ok: false, error: "Missing FIREBASE_PROJECT_ID and service account project_id." };
    }

    return {
        ok: true,
        value: {
            projectId,
            serviceAccount: serviceAccountResult.value
        }
    };
}

export async function sendFirebasePush(config: FirebaseConfig, request: FirebasePushRequest): Promise<FirebaseResult<string>> {
    const accessTokenResult = await requestGoogleAccessToken(config.serviceAccount);
    if (!accessTokenResult.ok) {
        return accessTokenResult;
    }

    const endpoint = `https://fcm.googleapis.com/v1/projects/${config.projectId}/messages:send`;
    // Data-only payload keeps Android-side notification rendering in app control
    // so action buttons (call/dialer/open/copy) are always available.
    const payload = {
        message: {
            token: request.token,
            data: request.data,
            android: {
                priority: "high"
            }
        }
    };

    const response = await fetch(endpoint, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessTokenResult.value}`
        },
        body: JSON.stringify(payload)
    });

    let data: Record<string, unknown> = {};
    try {
        data = await response.json();
    } catch (_error) {
        data = {};
    }

    if (!response.ok) {
        const details = typeof data.error === "object" && data.error !== null
            ? (data.error as Record<string, unknown>)
            : null;
        return {
            ok: false,
            error: String(details?.message || data.error || `FCM request failed (${response.status}).`)
        };
    }

    const messageId = String(data.name ?? "").trim();
    return { ok: true, value: messageId };
}
