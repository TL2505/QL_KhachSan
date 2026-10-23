/**
 * ==========================================================================
 * AURELIA GRAND LUXURY HOTEL - 5★ API CLIENT & VIETQR ENGINE
 * Connects to Java HTTPServer Backend (Port 8081)
 * Adheres strictly to "NO-BUILD LUXURY" Native ES Module Architecture
 * ==========================================================================
 */

export class ApiError extends Error {
  constructor(message, status = 500, retryAfter = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

function storage() {
  try { return typeof localStorage !== 'undefined' ? localStorage : null; }
  catch { return null; }
}

function read(key) {
  try { return storage()?.getItem(key); }
  catch { return null; }
}

function write(key, value) {
  try {
    if (value === null) storage()?.removeItem(key);
    else storage()?.setItem(key, value);
  } catch {}
}

export function normalizeApiBase(url) {
  if (typeof url !== 'string' || !url) throw new ApiError('Địa chỉ kết nối không hợp lệ.');
  const origin = typeof window !== 'undefined' && window.location && window.location.origin && window.location.origin !== 'null'
    ? window.location.origin
    : 'http://localhost';
  let normalized;
  try {
    normalized = new URL(url, origin);
  } catch {
    throw new ApiError('Địa chỉ kết nối không hợp lệ.');
  }
  if (!['http:', 'https:'].includes(normalized.protocol) || normalized.username || normalized.password || normalized.search || normalized.hash) {
    throw new ApiError('Địa chỉ kết nối không hợp lệ.');
  }
  let path = normalized.pathname.replace(/\/+$/, '');
  if (!path || path === '/api') path = '/api/v1';
  if (!path.endsWith('/api/v1')) throw new ApiError('Địa chỉ kết nối phải sử dụng /api/v1.');
  return normalized.origin + path;
}

// Dynamic Host Normalization: Retrieve the actual hostname & protocol being accessed in the browser
export const getDynamicApiBaseUrl = () => {
  // Safe for Node.js testing environments (where 'window' is undefined)
  if (typeof window === 'undefined' || !window.location || !window.location.hostname) {
    return 'http://localhost:8081/api/v1'; 
  }
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
  const hostname = window.location.hostname; 
  return `${protocol}//${hostname}:8081/api/v1`; 
}; 

export function getAlternativeApiBaseUrl() {
  // Keep requests on the selected host so local development cannot fall back to an unavailable VM.
  return null;
}

export const API_BASE_URL = getDynamicApiBaseUrl();

export function getApiBaseUrl() {
  const saved = read('hotel_api_url') || read('aurelia_api_base') || read('hotel_api_base');
  if (saved) {
    try {
      const normalized = normalizeApiBase(saved);
      const pageHost = typeof window !== 'undefined' && window.location ? window.location.hostname : '';
      const localPage = pageHost === 'localhost' || pageHost === '127.0.0.1';
      if (localPage && new URL(normalized).hostname === '192.168.56.102') {
        const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
        const localBase = `${protocol}//${pageHost}:8081/api/v1`;
        setApiBaseUrl(localBase);
        return localBase;
      }
      return normalized;
    } catch {}
  }
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    const host = window.location.hostname || 'localhost';
    if (window.location.port === '3000') {
      return `http://${host}:8081/api/v1`;
    }
    return window.location.origin + '/api/v1';
  }
  return getDynamicApiBaseUrl();
}

export function setApiBaseUrl(url) {
  write('hotel_api_url', url ? normalizeApiBase(url) : null);
  write('aurelia_api_base', url ? normalizeApiBase(url) : null);
  write('hotel_api_base', url ? normalizeApiBase(url) : null);
}

export function getStoredCustomerToken() {
  try {
    return read('customer_token')
        || read('auth_token')
        || read('token')
        || null;
  } catch {
    return null;
  }
}

export function getAuthSession() {
  try {
    const raw = read('hotel_auth_session');
    const session = raw ? JSON.parse(raw) : null;
    return session && typeof session === 'object' && !Array.isArray(session) ? session : null;
  } catch {
    return null;
  }
}

export function setAuthSession(session) {
  const token = session?.token || null;
  write('hotel_auth_session', session ? JSON.stringify(session) : null);
  write('customer_token', token);
  write('auth_token', token);
  write('token', token);
}

export function clearAuthSession() {
  write('hotel_auth_session', null);
  write('customer_token', null);
  write('auth_token', null);
  write('token', null);
}

function messageFor(status) {
  return ({
    401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    403: 'Bạn không có quyền thực hiện thao tác này.',
    409: 'Dữ liệu vừa thay đổi. Vui lòng tải lại và kiểm tra trước khi thử lại.',
    503: 'Hệ thống đang tạm bận. Vui lòng thử lại sau.'
  })[status] || (status >= 500 ? 'Chưa thể hoàn tất yêu cầu. Vui lòng thử lại hoặc liên hệ lễ tân.' : 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.');
}

export async function apiFetch(path, init = {}) {
  if (!path || /^(?:[a-z]+:|\/\/)/i.test(path)) throw new ApiError('Đường dẫn yêu cầu không hợp lệ.');
  const cleanPath = path.startsWith('/') ? path : '/' + path;
  const isPublic = cleanPath.startsWith('/rooms/availability') || cleanPath.startsWith('/auth/')
    || cleanPath.startsWith('/public/room-types') || cleanPath.startsWith('/public/landing-content');
  const session = getAuthSession();
  const token = (typeof session?.token === 'string' && session.token) ? session.token : getStoredCustomerToken();
  const headers = new Headers(init.headers || {});
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json; charset=utf-8');
  }
  if (!isPublic && typeof token === 'string' && token) {
    headers.set('Authorization', 'Bearer ' + token);
  }
  headers.set('X-User-Role', 'guest');
  headers.set('ngrok-skip-browser-warning', '69420');

  let response;
  const baseUrl = getApiBaseUrl();
  const url = baseUrl + cleanPath;

  const attemptFetch = async (targetUrl) => {
    let timeoutId = null;
    let signal = init.signal;
    if (!signal && typeof AbortController !== 'undefined') {
      const controller = new AbortController();
      signal = controller.signal;
      timeoutId = setTimeout(() => {
        controller.abort(new DOMException('Request timed out after 2.5s', 'TimeoutError'));
      }, 2500);
    }
    try {
      return await fetch(targetUrl, { ...init, headers, signal });
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }
  };

  try {
    response = await attemptFetch(url);
  } catch (error) {
    if (init.signal && (error.name === 'AbortError' || error.name === 'TimeoutError')) {
      throw error;
    }
    // Do not retry another host: local UI traffic must stay on its configured localhost backend.
    const altBase = getAlternativeApiBaseUrl(baseUrl);
    if (altBase && altBase !== baseUrl) {
      try {
        response = await attemptFetch(altBase + cleanPath);
      } catch (altError) {
        if (error.name === 'AbortError' || error.name === 'TimeoutError') throw error;
        throw new ApiError('Không thể kết nối. Vui lòng thử lại hoặc liên hệ lễ tân.');
      }
    } else {
      if (error.name === 'AbortError' || error.name === 'TimeoutError') throw error;
      throw new ApiError('Không thể kết nối. Vui lòng thử lại hoặc liên hệ lễ tân.');
    }
  }

  if (!isPublic && response.status === 401 && session?.token && getAuthSession()?.token === session.token) {
    setAuthSession(null);
    if (typeof window !== 'undefined' && window.dispatchEvent) {
      window.dispatchEvent(new Event('hotel:session-expired'));
    }
  }

  if (response.status === 204) return null;

  const text = await response.text();
  let result;
  try {
    result = text ? JSON.parse(text) : null;
  } catch {
    throw new ApiError('Chưa thể đọc phản hồi. Vui lòng thử lại hoặc liên hệ lễ tân.', response.status);
  }

  if (!response.ok) {
    const serverMessage = (response.status < 500 && result && typeof result.message === 'string') ? result.message.trim() : '';
    throw new ApiError(serverMessage || messageFor(response.status), response.status, response.headers.get('Retry-After'));
  }

  if (result && typeof result === 'object' && 'status' in result) {
    const status = String(result.status).toLowerCase();
    if (status === 'success') return result.data ?? null;
    if (['error', 'failed', 'failure'].includes(status)) {
      throw new ApiError('Yêu cầu chưa được hoàn tất. Vui lòng kiểm tra lại.', response.status);
    }
  }

  return result;
}

export async function fetchAuthenticatedFile(path, init = {}) {
  if (!path || /^(?:[a-z]+:|\/\/)/i.test(path)) throw new ApiError('Đường dẫn tải xuống không hợp lệ.');
  const session = getAuthSession();
  const token = (typeof session?.token === 'string' && session.token) ? session.token : getStoredCustomerToken();
  const headers = new Headers(init.headers || {});
  headers.set('Accept', 'application/pdf');
  if (typeof token === 'string' && token) {
    headers.set('Authorization', 'Bearer ' + token);
  }
  headers.set('X-User-Role', 'guest');
  let response;
  try {
    response = await fetch(getApiBaseUrl() + (path.startsWith('/') ? path : '/' + path), { ...init, headers });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError('Không thể tải tệp. Vui lòng thử lại.');
  }
  if (response.status === 401 && session?.token && getAuthSession()?.token === session.token) {
    setAuthSession(null);
    if (typeof window !== 'undefined' && window.dispatchEvent) {
      window.dispatchEvent(new Event('hotel:session-expired'));
    }
  }
  if (!response.ok) throw new ApiError(messageFor(response.status), response.status, response.headers.get('Retry-After'));
  if (!response.headers.get('Content-Type')?.toLowerCase().includes('application/pdf')) {
    throw new ApiError('Máy chủ không trả về tệp PDF hợp lệ.', response.status);
  }
  return response.blob();
}

export const api = {
  get: (path, init = {}) => apiFetch(path, { ...init, method: 'GET', cache: 'no-store' }),
  post: (path, body, init = {}) => apiFetch(path, { ...init, method: 'POST', body: JSON.stringify(body) }),
  put: (path, body, init = {}) => apiFetch(path, { ...init, method: 'PUT', body: JSON.stringify(body) }),
  delete: (path, init = {}) => apiFetch(path, { ...init, method: 'DELETE' })
};

// Curated 5★ International Luxury Room Catalog (Used as reference & offline resilient fallback)
export const LUXURY_ROOMS_CATALOG = [
  {
    id: 1,
    roomNumber: "101",
    name: "Deluxe King Ocean View",
    category: "Deluxe",
    price: 1850000,
    area: 45,
    capacity: 2,
    childrenCapacity: 1,
    bedType: "1 Giường King Đôi Cỡ Lớn",
    view: "Trực diện biển Vịnh Ngọc",
    rating: 4.95,
    reviewsCount: 142,
    badge: "Bán Chạy Nhất",
    image: "https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["Wi-Fi 6 Tốc Độ Cao", "Bồn Tắm Cẩm Thạch", "Ban Công View Biển", "Máy Pha Cà Phê Nespresso", "Bữa Sáng 5★ Tự Chọn", "Dịch Vụ Phòng 24/7"],
    description: "Không gian tĩnh dưỡng thanh lịch đón trọn bình minh trên biển. Nội thất gỗ sồi cao cấp, đệm lông vũ tiêu chuẩn hoàng gia và phòng tắm cẩm thạch Ý sang trọng."
  },
  {
    id: 2,
    roomNumber: "201",
    name: "Executive Heritage Suite",
    category: "Suite",
    price: 3200000,
    area: 75,
    capacity: 3,
    childrenCapacity: 1,
    bedType: "1 Giường Super King + Sofa Bed",
    view: "Toàn cảnh vịnh biển & vườn nhiệt đới",
    rating: 4.98,
    reviewsCount: 98,
    badge: "Đặc Quyền VIP",
    image: "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["Đặc Quyền Executive Lounge", "Bồn Sục Jacuzzi Massage", "Phòng Khách Riêng Biệt", "Trà Chiều Hoàng Gia Miễn Phí", "Check-in Riêng Tại Suite", "Quản Gia Hỗ Trợ 24/7"],
    description: "Đỉnh cao phong cách sống Indochine giao hòa tân cổ điển. Sở hữu phòng khách đại sảnh, quầy minibar không giới hạn cùng lối vào sảnh thương gia Executive Lounge danh giá."
  },
  {
    id: 3,
    roomNumber: "301",
    name: "Presidential Royal Villa",
    category: "Presidential",
    price: 8900000,
    area: 180,
    capacity: 6,
    childrenCapacity: 2,
    bedType: "2 Giường King + 2 Giường Đơn",
    view: "360° Panorama Biển & Hoàng Hôn",
    rating: 5.0,
    reviewsCount: 46,
    badge: "Kiệt Tác Nghỉ Dưỡng",
    image: "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1600565193348-f74bd3c7ccdf?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["Hồ Bơi Tràn Vô Cực Riêng", "Quản Gia Hoàng Gia 24/7", "Đưa Đón Sân Bay Bằng Rolls-Royce", "Bếp Trưởng Phục Vụ Tại Biệt Thự", "Hầm Rượu Vang Độc Quyền", "Khu Spa & Sauna Riêng"],
    description: "Dinh thự nghỉ dưỡng biệt lập dành riêng cho nguyên thủ và giới siêu giàu. Hồ bơi tràn chân mây riêng tư, sân tắm nắng hướng biển cùng đội ngũ quản gia và bếp trưởng phục vụ theo yêu cầu."
  },
  {
    id: 4,
    roomNumber: "202",
    name: "Grand Family Ocean Residence",
    category: "Family",
    price: 4500000,
    area: 95,
    capacity: 4,
    childrenCapacity: 2,
    bedType: "1 Giường King + 2 Giường Queen",
    view: "Biển xanh & Công viên nước",
    rating: 4.92,
    reviewsCount: 84,
    badge: "Gia Đình Yêu Thích",
    image: "https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["2 Phòng Ngủ Khép Kín", "Bếp Ăn Tiêu Chuẩn Châu Âu", "Khu Vui Chơi An Toàn Cho Trẻ", "Vé Miễn Phí Kids Club", "Máy Rửa Bát & Giặt Sấy", "Xe Điện Nội Khu Đón Tận Cửa"],
    description: "Thiết kế mở ấm cúng kết nối các thành viên gia đình trong kỳ nghỉ trong mơ. Đầy đủ tiện nghi bếp, phòng khách sinh hoạt chung rộng rãi và các dịch vụ thân thiện cho bé."
  },
  {
    id: 5,
    roomNumber: "102",
    name: "Premier Garden Sanctuary",
    category: "Deluxe",
    price: 2100000,
    area: 52,
    capacity: 2,
    childrenCapacity: 1,
    bedType: "1 Giường Super King",
    view: "Vườn nhiệt đới ngàn hoa tĩnh lặng",
    rating: 4.90,
    reviewsCount: 110,
    badge: "Tĩnh Lặng Tự Nhiên",
    image: "https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["Bồn Tắm Nằm Lộ Thiên", "Sân Vườn Riêng Tư", "Liệu Pháp Tinh Dầu Miễn Phí", "Hệ Thống Âm Thanh Harman Kardon", "Bữa Sáng Phục Vụ Tại Giường"],
    description: "Nốt trầm thư thái giữa thiên nhiên nguyên sơ. Thiết kế vách kính chạm trần mở rộng tầm mắt ra khu vườn nhiệt đới xanh mướt với tiếng chim hót và suối chảy êm đềm."
  },
  {
    id: 6,
    roomNumber: "302",
    name: "Sky Penthouse Horizon",
    category: "Presidential",
    price: 6800000,
    area: 130,
    capacity: 4,
    childrenCapacity: 1,
    bedType: "2 Giường King Hoàng Gia",
    view: "Tầng 35 Đỉnh Tháp - Panorama Biển & Thành Phố",
    rating: 4.99,
    reviewsCount: 37,
    badge: "Tầm Nhìn Đỉnh Cao",
    image: "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80",
    gallery: [
      "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80",
      "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80"
    ],
    amenities: ["Kính Thiên Văn Ngắm Sao", "Quầy Bar Rượu Thượng Hạng", "Ban Công Bay 180 Độ", "Thang Máy Riêng Lên Tận Tầng", "Dịch Vụ Sommelier Riêng"],
    description: "Đặc quyền ngắm sao trời và toàn cảnh thành phố biển lung linh về đêm từ đỉnh tháp tầng 35. Penthouse hội tụ những tiện nghi xa xỉ nhất cùng trải nghiệm thị giác ngoạn mục."
  }
];

export function mapRoomCategory(roomTypeId, rawCategory, rawName) {
  if (rawCategory && typeof rawCategory === 'string') {
    const c = rawCategory.trim().toLowerCase();
    if (c.includes('deluxe')) return 'Deluxe';
    if (c.includes('suite')) return 'Suite';
    if (c.includes('presidential') || c.includes('villa') || c.includes('penthouse')) return 'Presidential';
    if (c.includes('family')) return 'Family';
  }
  if (rawName && typeof rawName === 'string') {
    const n = rawName.toLowerCase();
    if (n.includes('deluxe')) return 'Deluxe';
    if (n.includes('suite')) return 'Suite';
    if (n.includes('presidential') || n.includes('villa') || n.includes('penthouse')) return 'Presidential';
    if (n.includes('family')) return 'Family';
    if (rawName.trim()) return rawName.trim();
  }
  const idNum = Number(roomTypeId);
  if (idNum === 2) return 'Suite';
  if (idNum === 3 || idNum === 6) return 'Presidential';
  if (idNum === 4) return 'Family';
  return 'Deluxe';
}

export function formatCurrency(amount, currency = "VND") {
  if (currency === "USD") {
    const usd = Math.round(amount / 25000);
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(usd);
  }
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

export function generateVietQRUrl(payment) {
  return payment?.qr_image_base64 ? 'data:image/png;base64,' + payment.qr_image_base64 : '';
}

export function generateUUID() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

export async function getRooms(filterParams = {}) {
  const baseUrl = getApiBaseUrl();
  const base = baseUrl.replace(/\/+$/, '');
  const root = base.endsWith('/api/v1') ? base : `${base}/api/v1`;
  const searching = Boolean(filterParams.checkIn && filterParams.checkOut);
  const query = searching
    ? `?check_in=${encodeURIComponent(filterParams.checkIn)}&check_out=${encodeURIComponent(filterParams.checkOut)}`
    : '';
  const url = searching
    ? `${root}/rooms/availability${query}`
    : `${root}/rooms`;

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 4000);

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        'X-User-Role': 'guest'
      },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (response.ok) {
      const json = await response.json();
      let rawRooms = [];
      if (json) {
        if (Array.isArray(json.data)) {
          rawRooms = json.data;
        } else if (Array.isArray(json)) {
          rawRooms = json;
        } else if (json.data && typeof json.data === 'object' && Array.isArray(json.data.rooms)) {
          rawRooms = json.data.rooms;
        }
      }

      if (rawRooms.length === 0) return { rooms: [], source: 'api' };
      if (rawRooms.length > 0) {
        const catalogueResponse = await fetch(`${root}/public/room-types`, {
          method: 'GET', headers: { 'Accept': 'application/json', 'X-User-Role': 'guest' }, signal: AbortSignal.timeout(4000)
        });
        if (!catalogueResponse.ok) throw new ApiError('Không thể tải danh mục hạng phòng.', catalogueResponse.status);
        const cataloguePayload = await catalogueResponse.json();
        const publicTypes = Array.isArray(cataloguePayload?.data) ? cataloguePayload.data : [];
        const typesById = new Map(publicTypes.map(type => [Number(type.id), type]));
        const visibleRooms = rawRooms.filter(room => typesById.has(Number(room.room_type_id || room.roomTypeId || room.roomType)));
        const rooms = visibleRooms.map((r, index) => {
          if (!Number.isInteger(r.id) || r.id < 1 || r.price == null || !Number.isFinite(Number(r.price)) || Number(r.price) < 0) throw new Error('Invalid room inventory');
          const typeId = r.roomTypeId || r.room_type_id || r.roomType || (index % 5 + 1);
          const managedType = typesById.get(Number(typeId));
          const typeName = managedType?.name || r.room_type_name || r.roomTypeName || r.name || '';
          const category = mapRoomCategory(typeId, r.category, typeName);
          const fallback = LUXURY_ROOMS_CATALOG.find(room => 
            room.category.toLowerCase() === category.toLowerCase() ||
            room.name.toLowerCase().includes(category.toLowerCase())
          ) || LUXURY_ROOMS_CATALOG[index % LUXURY_ROOMS_CATALOG.length];

          return {
            ...fallback,
            id: r.id,
            roomId: r.id,
            roomNumber: r.roomNumber || r.room_number || null,
            name: typeName || `${category} Luxury Suite`,
            category: category,
            price: Number(r.price),
            area: r.area || fallback.area,
            capacity: managedType?.capacity || r.capacity || fallback.capacity,
            childrenCapacity: fallback.childrenCapacity,
            bedType: r.bedType || fallback.bedType,
            view: r.view || fallback.view,
            rating: fallback.rating,
            reviewsCount: fallback.reviewsCount,
            badge: fallback.badge,
            image: managedType?.image_url || r.image_url || r.imageUrl || '',
            imageAlt: managedType?.image_alt || typeName,
            gallery: fallback.gallery,
            amenities: managedType ? String(managedType.amenities || '').split(',').map(v => v.trim()).filter(Boolean)
              : r.amenities ? (Array.isArray(r.amenities) ? r.amenities : String(r.amenities).split(',').map(v => v.trim()).filter(Boolean)) : fallback.amenities,
            description: managedType ? (managedType.description || '') : (r.description || fallback.description),
            displayOrder: managedType?.display_order ?? 0,
            isDemoCatalog: false,
            status: r.status || 'unknown',
            version: r.version ?? 0
          };
        });
        rooms.sort((a, b) => Number(a.displayOrder) - Number(b.displayOrder) || Number(a.id) - Number(b.id));
        return { rooms, source: 'api' };
      }
    }
    throw new ApiError('Không thể xác minh danh sách phòng.', response.status);
  } catch (error) {
    throw new ApiError('Không thể tải phòng từ máy chủ. Chưa thể xác nhận giá hoặc tình trạng phòng.', error.status || 503);
  }
}

export const TRANSACTION_UNCONFIRMED = 'Hệ thống máy chủ đang bận xử lý hoặc gián đoạn kết nối. Giao dịch chưa được ghi nhận vào sổ cái PMS, quý khách vui lòng thử lại hoặc liên hệ Lễ tân.';

export async function submitBooking(payload) {
  // Retain this key on the caller's draft after an ambiguous network failure.
  payload.idempotencyKey ||= generateUUID();
  try {
    const data = await apiFetch('/bookings/public', {
      method: 'POST', headers: { 'Idempotency-Key': payload.idempotencyKey },
      signal: AbortSignal.timeout(10000),
      body: JSON.stringify({ room_id: payload.roomId, room_version: payload.roomVersion ?? 0,
        check_in_date: payload.checkInDate + ' 14:00:00', check_out_date: payload.checkOutDate + ' 12:00:00',
        customer_name: payload.fullName, phone: payload.phone, email: payload.email,
        identity_card: payload.identityCard || null, voucher_code: payload.promoCode || '' })
    });
    if (!Number.isSafeInteger(data?.booking_id) || data.booking_id <= 0 || !data.status ||
        !data.folio || !Number.isFinite(Number(data.folio.balance)) || Number(data.folio.balance) < 0) {
      throw new Error('Invalid booking receipt');
    }
    return { success: true, bookingId: data.booking_id, bookingReference: data.booking_code,
      totalAmount: Number(data.folio.balance), status: data.status, folio: data.folio,
      payment: data.payment || null };
  } catch (error) {
    throw new ApiError(TRANSACTION_UNCONFIRMED, error.status || 503);
  }
}

export async function lookupBooking(bookingCode, phone) {
  const baseUrl = getApiBaseUrl();
  const cleanCode = String(bookingCode || '').trim();
  const cleanPhone = String(phone || '').trim();

  // 2. Query backend by numeric ID
  const numericId = cleanCode.match(/(?:^|[-#])(\d+)$/)?.[1] || '';
  if (numericId) {
    let timeoutId = null;
    try {
      const controller = new AbortController();
      timeoutId = setTimeout(() => controller.abort(), 10000);
      const base = baseUrl.replace(/\/+$/, '');
      const root = base.endsWith('/api/v1') ? base : `${base}/api/v1`;
      const url = `${root}/bookings/${numericId}`;

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          'Authorization': 'Bearer ' + (getAuthSession()?.token || getStoredCustomerToken() || '')
        },
        signal: controller.signal
      });

      if (response.ok) {
        const json = await response.json();
        const data = json?.data || json;
        if (data && (data.id || data.booking_id || data.bookingId)) {
          return {
            success: true,
            booking: {
              bookingId: data.id || data.booking_id || data.bookingId || numericId,
              bookingReference: data.booking_code || `BKG-${numericId}`,
              customerName: data.customer_name || data.customerName || 'Quý khách Aurelia',
              phone: data.phone || cleanPhone || '---',
              roomNumber: data.room_number || data.roomNumber || data.room_id || null,
              checkInDate: data.check_in_date || data.checkInDate || '---',
              checkOutDate: data.check_out_date || data.checkOutDate || '---',
              totalAmount: data.total_amount ?? data.totalAmount ?? data.total_price ?? data.totalPrice ?? data.price ?? null,
              status: data.status || 'PENDING',
              paymentStatus: data.payment_status || data.paymentStatus || 'PENDING'
            }
          };
        }
      }
    } catch (err) {
      throw new ApiError(TRANSACTION_UNCONFIRMED, 503);
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }
  }

  throw new Error('Không tìm thấy thông tin đặt phòng với mã số và số điện thoại này. Vui lòng kiểm tra lại!');
}

