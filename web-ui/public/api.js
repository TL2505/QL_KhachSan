const SESSION_KEY = 'hotel_auth_session';
const API_KEY = 'hotel_api_base';

export function getApiBaseUrl() {
  const configured = typeof localStorage !== 'undefined' ? (localStorage.getItem(API_KEY) || localStorage.getItem('aurelia_api_base')) : null;
  if (configured) return configured.replace(/\/+$/, '');
  const host = typeof window !== 'undefined' && window.location.hostname ? window.location.hostname : 'localhost';
  return `http://${host}:8081/api/v1`;
}

export function setApiBaseUrl(value) {
  if (typeof localStorage === 'undefined') return;
  if (!value) localStorage.removeItem(API_KEY);
  else localStorage.setItem(API_KEY, value.replace(/\/+$/, ''));
}

export function getAuthSession() {
  try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); }
  catch { return null; }
}

export function setAuthSession(session) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearAuthSession() {
  localStorage.removeItem(SESSION_KEY);
}

export async function apiFetch(path, options = {}) {
  const session = getAuthSession();
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json; charset=utf-8');
  if (session?.token) headers.set('Authorization', `Bearer ${session.token}`);
  const response = await fetch(`${getApiBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`, {
    ...options,
    headers,
    body: options.body && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok || json?.status === 'error') {
    const error = new Error(json?.message || json?.detail || `Yêu cầu thất bại (HTTP ${response.status})`);
    error.status = response.status;
    throw error;
  }
  return json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json;
}

export const api = {
  get: path => apiFetch(path),
  post: (path, body) => apiFetch(path, { method: 'POST', body }),
  put: (path, body) => apiFetch(path, { method: 'PUT', body }),
  patch: (path, body) => apiFetch(path, { method: 'PATCH', body }),
  delete: path => apiFetch(path, { method: 'DELETE' })
};
