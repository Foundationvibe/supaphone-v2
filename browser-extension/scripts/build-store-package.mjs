import { cp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const extensionDir = path.resolve(__dirname, "..");
const distDir = path.join(extensionDir, "dist-store");

const requiredEnv = ["SUPABASE_URL", "SUPABASE_ANON_KEY", "SUPAPHONE_EDGE_BASE_URL"];

function requireEnv(name) {
    const value = (process.env[name] || "").trim();
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

function escapeJsString(value) {
    return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function normalizeOrigin(url, fieldName) {
    let parsed;
    try {
        parsed = new URL(url);
    } catch (_error) {
        throw new Error(`${fieldName} is not a valid URL.`);
    }
    if (parsed.protocol !== "https:") {
        throw new Error(`${fieldName} must use https.`);
    }
    return `${parsed.origin}/*`;
}

async function copyReleaseAssets() {
    const copyTargets = [
        "assets",
        "icons",
        "vendor",
        "background.js",
        "manifest.json",
        "popup.css",
        "popup.html",
        "popup.js",
        "styles.css"
    ];

    for (const target of copyTargets) {
        await cp(path.join(extensionDir, target), path.join(distDir, target), {
            recursive: true
        });
    }
}

async function main() {
    for (const envName of requiredEnv) {
        requireEnv(envName);
    }

    const supabaseUrl = requireEnv("SUPABASE_URL");
    const supabaseAnonKey = requireEnv("SUPABASE_ANON_KEY");
    const edgeBaseUrl = requireEnv("SUPAPHONE_EDGE_BASE_URL");

    await rm(distDir, { recursive: true, force: true });
    await mkdir(distDir, { recursive: true });

    await copyReleaseAssets();

    const manifestPath = path.join(distDir, "manifest.json");
    const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
    manifest.host_permissions = Array.from(
        new Set([
            normalizeOrigin(supabaseUrl, "SUPABASE_URL"),
            normalizeOrigin(edgeBaseUrl, "SUPAPHONE_EDGE_BASE_URL")
        ])
    );
    await writeFile(manifestPath, `${JSON.stringify(manifest, null, 4)}\n`, "utf8");

    const runtimeConfig = `self.SUPAPHONE_BACKEND_CONFIG = {
    supabaseUrl: "${escapeJsString(supabaseUrl)}",
    supabaseAnonKey: "${escapeJsString(supabaseAnonKey)}",
    edgeBaseUrl: "${escapeJsString(edgeBaseUrl)}"
};
`;
    await writeFile(path.join(distDir, "config.runtime.js"), runtimeConfig, "utf8");

    const releaseNote = `SupaPhone extension store package prepared.\n\n` +
        `Upload the contents of this folder to the Chrome Web Store.\n` +
        `Generated from browser-extension/scripts/build-store-package.mjs.\n`;
    await writeFile(path.join(distDir, "BUILD_INFO.txt"), releaseNote, "utf8");

    console.log(`Extension store package ready at ${distDir}`);
}

main().catch(error => {
    console.error(error.message || error);
    process.exitCode = 1;
});
