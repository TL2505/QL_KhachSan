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


/**
 * Generate a random UUIDv4 for Idempotency-Key
 */
export function generateUUID() {
  if (crypto && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/**
 * Fetch available rooms from Backend with graceful fallback
 * Sends GET /api/v1/rooms?check_in=...&check_out=... or GET /api/v1/rooms
 */
export async function getRooms(filterParams = {}) {
  const baseUrl = getApiBaseUrl();
  const queryParts = [];

  if (filterParams.checkIn) queryParts.push(`check_in=${encodeURIComponent(filterParams.checkIn)}`);
  if (filterParams.checkOut) queryParts.push(`check_out=${encodeURIComponent(filterParams.checkOut)}`);
  if (filterParams.guests) queryParts.push(`guests=${encodeURIComponent(filterParams.guests)}`);

  const queryString = queryParts.length > 0 ? `?${queryParts.join('&')}` : '';
  const url = `${baseUrl}/api/v1/rooms${queryString}`;

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3500);

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        'X-User-Role': '3'
      },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (response.ok) {
      const json = await response.json();
      let rawRooms = [];
      if (json && json.status === 'success' && Array.isArray(json.data)) {
        rawRooms = json.data;
      } else if (Array.isArray(json)) {
        rawRooms = json;
      }

      // Merge backend rooms with luxury frontend catalog attributes
      if (rawRooms.length > 0) {
        return rawRooms.map((r, index) => {
          const fallback = LUXURY_ROOMS_CATALOG[index % LUXURY_ROOMS_CATALOG.length];
          return {
            id: r.id || fallback.id,
            roomNumber: r.roomNumber || r.room_number || fallback.roomNumber,
            name: r.roomTypeName || r.name || fallback.name,
            category: r.category || fallback.category,
            price: r.price ? Number(r.price) : fallback.price,
            area: r.area || fallback.area,
            capacity: r.capacity || fallback.capacity,
            childrenCapacity: fallback.childrenCapacity,
            bedType: r.bedType || fallback.bedType,
            view: r.view || fallback.view,
            rating: fallback.rating,
            reviewsCount: fallback.reviewsCount,
            badge: fallback.badge,
            image: r.imageUrl || fallback.image,
            gallery: fallback.gallery,
            amenities: r.amenities ? (Array.isArray(r.amenities) ? r.amenities : r.amenities.split(',')) : fallback.amenities,
            description: r.description || fallback.description,
            status: r.status || 'vacant_clean',
            version: r.version || 0
          };
        });
      }
    }
  } catch (err) {
    console.info(`[Aurelia PMS] Backend at ${baseUrl} is offline or unreachable. Engaging 5★ Luxury Resilient Catalog Mode.`);

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
