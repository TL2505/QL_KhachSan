// API Client for Java HTTPServer Backend
// Handles dynamic API URL configuration, custom headers, and request envelopes

const LOCAL_FALLBACK_URL = "http://localhost:8081/api";

// Auto-detect API URL: same host on port 8081, fallback to localhost for local dev
let defaultApiBase = window.location.hostname === "localhost"
    ? LOCAL_FALLBACK_URL
    : `http://${window.location.hostname}:8081/api`;

// Helper to check if API server is reachable
async function isServerReachable(url) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2000); // 2 seconds timeout
    try {
        const response = await fetch(`${url.replace(/\/$/, "")}/health`, {
            signal: controller.signal,
            headers: { "ngrok-skip-browser-warning": "true" }
        });
        clearTimeout(timeoutId);
        return response.ok;
    } catch (e) {
        clearTimeout(timeoutId);
        return false;
    }
}

try {
    const response = await fetch("/api-config");
    if (response.ok) {
        const configData = await response.json();
        if (configData && configData.serverUrl) {
            const configuredUrl = configData.serverUrl.trim();
            if (configuredUrl !== LOCAL_FALLBACK_URL) {
                console.log("[api.js] Đang kiểm tra server: " + configuredUrl + " ...");
                if (await isServerReachable(configuredUrl)) {
                    defaultApiBase = configuredUrl;
                    console.log("[api.js] ✔ Server phản hồi → dùng: " + defaultApiBase);
                } else {
                    defaultApiBase = LOCAL_FALLBACK_URL;
                    console.log("[api.js] ✘ Không kết nối được server → chuyển sang localhost: " + defaultApiBase);
                }
            } else {
                defaultApiBase = configuredUrl;
                console.log("[api.js] Dùng server local: " + defaultApiBase);
            }
        }
    }
} catch (err) {
    console.warn("[api.js] Could not load API URL configuration from server, using default detection:", err);
}

const DEFAULT_API_BASE = defaultApiBase;

// Fetch the saved API URL from localStorage or fall back to localhost
export function getApiBaseUrl() {
    return localStorage.getItem("hotel_api_url") || DEFAULT_API_BASE;
}

export function setApiBaseUrl(url) {
    if (url) {
        localStorage.setItem("hotel_api_url", url.replace(/\/$/, "")); // Strip trailing slash
    } else {
        localStorage.removeItem("hotel_api_url");
    }
}

// Get stored authentication details
export function getAuthSession() {
    try {
        const raw = localStorage.getItem("hotel_auth_session");
        return raw ? JSON.parse(raw) : null;
    } catch (e) {
        return null;
    }
}

export function setAuthSession(session) {
    if (session) {
        localStorage.setItem("hotel_auth_session", JSON.stringify(session));
    } else {
        localStorage.removeItem("hotel_auth_session");
    }
}

// Main fetch wrapper
export async function apiFetch(path, init = {}) {
    const apiBase = getApiBaseUrl();
    const session = getAuthSession();
    
    const headers = new Headers(init.headers || {});
    
    // Add default JSON Content-Type if body exists and is not form-data
    if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) {
        headers.set("Content-Type", "application/json; charset=utf-8");
    }

    // Role ID mapping helper (ADMIN=1, STAFF=2, CUSTOMER=3)
    if (session && session.role) {
        const roleIdMap = { "ADMIN": "1", "STAFF": "2", "CUSTOMER": "3" };
        const roleHeaderVal = roleIdMap[session.role] || String(session.roleId || "");
        if (roleHeaderVal) {
            headers.set("X-User-Role", roleHeaderVal);
        }
    }
    
    // Bypass Ngrok security warning screen automatically
    headers.set("ngrok-skip-browser-warning", "69420");

    const url = `${apiBase}${path.startsWith('/') ? path : '/' + path}`;
    
    let response;
    try {
        response = await fetch(url, { ...init, headers });
    } catch (err) {
        throw new Error(`Không thể kết nối đến máy chủ API (${apiBase}). Vui lòng kiểm tra Server Java hoặc ngrok đã hoạt động chưa.`);
    }

    let result;
    const text = await response.text();
    if (text) {
        try {
            result = JSON.parse(text);
        } catch (e) {
            throw new Error(`Phản hồi từ Server không đúng định dạng JSON: ${text.substring(0, 100)}`);
        }
    }

    // Handle standard server envelopes { status, message, data }
    if (result && typeof result === 'object' && 'status' in result) {
        if (result.status === 'success') {
            return result.data;
        } else {
            throw new Error(result.message || 'Yêu cầu thất bại từ Server');
        }
    }

    // Fallback for direct responses
    if (!response.ok) {
        throw new Error(result?.message || `Yêu cầu thất bại với mã lỗi HTTP ${response.status}`);
    }
    return result;
}

export const api = {
    get: (path, init = {}) => apiFetch(path, { ...init, method: 'GET' }),
    post: (path, body, init = {}) => apiFetch(path, { ...init, method: 'POST', body: JSON.stringify(body) }),
    put: (path, body, init = {}) => apiFetch(path, { ...init, method: 'PUT', body: JSON.stringify(body) }),
    delete: (path, init = {}) => apiFetch(path, { ...init, method: 'DELETE' })
};
