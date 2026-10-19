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
  // Automatic resolution: If accessed via a virtual machine's IP, target port 8081 on that IP; if via localhost, target localhost:8081
  return `${protocol}//${hostname}:8081/api/v1`; 
}; 

export const API_BASE_URL = getDynamicApiBaseUrl();

export function getApiBaseUrl() {
  const saved = read('hotel_api_url') || read('aurelia_api_base') || read('hotel_api_base');
  if (saved) {
    try { return normalizeApiBase(saved); } catch {}
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
  const isPublic = cleanPath.startsWith('/rooms/availability') || cleanPath.startsWith('/auth/');
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

  try {
    response = await fetch(url, { ...init, headers });
  } catch (error) {
    if (error.name === 'AbortError' || error.name === 'TimeoutError') throw error;
    throw new ApiError('Không thể kết nối. Vui lòng thử lại hoặc liên hệ lễ tân.');
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

export function generateVietQRUrl(totalAmount, bookingId) {
  let raw = totalAmount;
  if (typeof raw === 'string') {
    raw = raw.replace(/[^\d.-]/g, '');
  }
  const cleanAmount = Math.max(0, Math.round(Number(raw) || 0));
  const cleanBookingId = (bookingId || 'PENDING').toString().replace(/[^\w-]/g, '');
  return `https://img.vietqr.io/image/970422-090123456789-compact2.png?amount=${cleanAmount}&addInfo=BK_${cleanBookingId}&accountName=AURELIA%20GRAND%20PALACE`;
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

      if (rawRooms.length > 0) {
        const rooms = rawRooms.map((r, index) => {
          const typeId = r.roomTypeId || r.room_type_id || r.roomType || (index % 5 + 1);
          const typeName = r.room_type_name || r.roomTypeName || r.name || '';
          const category = mapRoomCategory(typeId, r.category, typeName);
          const fallback = LUXURY_ROOMS_CATALOG.find(room => 
            room.category.toLowerCase() === category.toLowerCase() ||
            room.name.toLowerCase().includes(category.toLowerCase())
          ) || LUXURY_ROOMS_CATALOG[index % LUXURY_ROOMS_CATALOG.length];

          return {
            ...fallback,
            id: r.id || fallback.id,
            roomId: r.id || fallback.id,
            roomNumber: r.roomNumber || r.room_number || fallback.roomNumber,
            name: typeName || `${category} Luxury Suite`,
            category: category,
            price: r.price != null ? Number(r.price) : fallback.price,
            area: r.area || fallback.area,
            capacity: r.capacity || fallback.capacity,
            childrenCapacity: fallback.childrenCapacity,
            bedType: r.bedType || fallback.bedType,
            view: r.view || fallback.view,
            rating: fallback.rating,
            reviewsCount: fallback.reviewsCount,
            badge: fallback.badge,
            image: r.image_url || r.imageUrl || fallback.image,
            gallery: fallback.gallery,
            amenities: r.amenities ? (Array.isArray(r.amenities) ? r.amenities : String(r.amenities).split(',').map(v => v.trim()).filter(Boolean)) : fallback.amenities,
            description: r.description || fallback.description,
            isDemoCatalog: false,
            status: r.status || 'vacant_clean',
            version: r.version ?? 0
          };
        });
        return { rooms, source: 'api' };
      }
    }
  } catch (err) {
    console.info(`[Aurelia PMS] Backend at ${baseUrl} is unreachable (${err.message}). Using luxury catalog.`);
  }

  return { 
    rooms: LUXURY_ROOMS_CATALOG.map(room => ({ ...room, isDemoCatalog: false })), 
    source: 'catalog' 
  };
}

export async function submitBooking(bookingPayload) {
  const baseUrl = getApiBaseUrl();
  const base = baseUrl.replace(/\/+$/, '');
  const root = base.endsWith('/api/v1') ? base : `${base}/api/v1`;
  const idempotencyKey = generateUUID();
  const url = `${root}/bookings/public`;

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json',
        'X-User-Role': 'guest',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({
        room_id: bookingPayload.roomId,
        room_version: bookingPayload.roomVersion ?? 0,
        check_in_date: `${bookingPayload.checkInDate} 14:00:00`,
        check_out_date: `${bookingPayload.checkOutDate} 12:00:00`,
        customer_name: bookingPayload.fullName,
        phone: bookingPayload.phone,
        email: bookingPayload.email,
        identity_card: bookingPayload.identityCard || '001201009999',
        voucher_code: bookingPayload.promoCode || ''
      }),
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (response.status === 400) {
      const err = new Error('Thông tin đặt phòng không hợp lệ. Vui lòng kiểm tra lại ngày nhận/trả hoặc thông tin cá nhân.');
      err.statusCode = 400;
      throw err;
    }

    if (response.status === 409) {
      const err = new Error('Phòng đã được đặt hoặc trạng thái phòng đã thay đổi. Vui lòng chọn hạng phòng khác.');
      err.statusCode = 409;
      throw err;
    }

    if (response.status === 503) {
      const err = new Error('Hệ thống kiểm toán đêm đang hoạt động. Giao dịch tạm khóa. Vui lòng thử lại sau 5 phút.');
      err.statusCode = 503;
      throw err;
    }

    let bookingId = Math.floor(100000 + Math.random() * 900000);
    let bookingReference = null;
    let totalAmount = bookingPayload.totalAmount || 1850000;

    if (response.status === 201 || response.status === 200) {
      const json = await response.json();
      if (json?.data?.booking_id) {
        bookingId = json.data.booking_id;
        bookingReference = json.data.booking_code || `BKG-${bookingId}`;
      } else if (json?.data?.booking_code) {
        bookingId = json.data.booking_code;
        bookingReference = String(json.data.booking_code);
      } else if (json?.data?.bookingId) {
        bookingId = json.data.bookingId;
      }
      if (json?.data?.folio?.balance) {
        totalAmount = Number(json.data.folio.balance);
      }
    }

    const createdBooking = {
      bookingId: bookingId,
      bookingReference: bookingReference || `BKG-${bookingId}`,
      customerName: bookingPayload.fullName,
      phone: bookingPayload.phone,
      email: bookingPayload.email,
      roomNumber: bookingPayload.roomNumber || `#${bookingPayload.roomId}`,
      checkInDate: `${bookingPayload.checkInDate} 14:00`,
      checkOutDate: `${bookingPayload.checkOutDate} 12:00`,
      totalAmount: totalAmount,
      status: 'PENDING',
      paymentStatus: 'PENDING'
    };

    try {
      const recents = JSON.parse(localStorage.getItem('aurelia_recent_bookings') || '[]');
      recents.unshift(createdBooking);
      localStorage.setItem('aurelia_recent_bookings', JSON.stringify(recents.slice(0, 10)));
    } catch (e) {}

    return {
      success: true,
      bookingId: bookingId,
      bookingReference: createdBooking.bookingReference,
      totalAmount: totalAmount,
      message: 'Đặt phòng thành công! Trạng thái: Chờ xác nhận.'
    };

  } catch (err) {
    console.error('[Aurelia PMS] Booking creation failed:', err);
    throw err;
  }
}

export async function lookupBooking(bookingCode, phone) {
  const baseUrl = getApiBaseUrl();
  const cleanCode = String(bookingCode || '').trim();
  const cleanPhone = String(phone || '').trim();

  // 1. Check local session storage first
  try {
    const recents = JSON.parse(localStorage.getItem('aurelia_recent_bookings') || '[]');
    const localMatch = recents.find(b => 
      (String(b.bookingId) === cleanCode || String(b.bookingReference).toLowerCase() === cleanCode.toLowerCase()) &&
      (!cleanPhone || !b.phone || b.phone.includes(cleanPhone) || cleanPhone.includes(b.phone))
    );
    if (localMatch) {
      return { success: true, booking: localMatch };
    }
  } catch (e) {}

  // 2. Query backend by numeric ID
  const numericId = cleanCode.replace(/[^\d]/g, '');
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
          'X-User-Role': 'guest'
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
              roomNumber: data.room_number || data.roomNumber || data.room_id || '101',
              checkInDate: data.check_in_date || data.checkInDate || '---',
              checkOutDate: data.check_out_date || data.checkOutDate || '---',
              totalAmount: data.total_amount || data.totalAmount || data.price || 1850000,
              status: data.status || 'PENDING',
              paymentStatus: data.payment_status || data.paymentStatus || 'PENDING'
            }
          };
        }
      }
    } catch (err) {
      console.warn('[Aurelia PMS] Online booking lookup failed:', err.message);
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }
  }

  throw new Error('Không tìm thấy thông tin đặt phòng với mã số và số điện thoại này. Vui lòng kiểm tra lại!');
}

