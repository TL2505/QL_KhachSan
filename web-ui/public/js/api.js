/**
 * ==========================================================================
 * AURELIA GRAND LUXURY HOTEL - 5★ API CLIENT & VIETQR ENGINE
 * Connects to Java HTTPServer Backend (Port 8081)
 * Adheres strictly to "NO-BUILD LUXURY" Native ES Module Architecture
 * ==========================================================================
 */

// Default PMS Backend URL
const DEFAULT_API_BASE = typeof window !== 'undefined' && window.location && window.location.hostname === "localhost"
  ? "http://localhost:8081"
  : (typeof window !== 'undefined' && window.location ? `http://${window.location.hostname}:8081` : "http://localhost:8081");

export function getApiBaseUrl() {
  if (typeof localStorage !== 'undefined') {
    return localStorage.getItem("aurelia_api_base") || DEFAULT_API_BASE;
  }
  return DEFAULT_API_BASE;
}

export function setApiBaseUrl(url) {
  if (typeof localStorage !== 'undefined') {
    if (url) {
      localStorage.setItem("aurelia_api_base", url.replace(/\/$/, ""));
    } else {
      localStorage.removeItem("aurelia_api_base");
    }
  }
}

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
    amenities: ["Wi-Fi 6 Tốc Độ Cao", "Bồn Tắm Đá Cẩm Thạch", "Ban Công View Biển", "Máy Pha Cà Phê Nespresso", "Bữa Sáng 5★ Tự Chọn", "Dịch Vụ Phòng 24/7"],
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
    category: "Villa",
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
    category: "Penthouse",
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

/**
 * Currency Formatter (VND / USD)
 */
export function formatCurrency(amount, currency = "VND") {
  if (currency === "USD") {
    // 1 USD approx 25,000 VND
    const usd = Math.round(amount / 25000);
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(usd);
  }
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

/**
 * Dynamic VietQR Image URL Generator
 * Bank: MB Bank (Quân Đội) | Account: 0987654321 | Template: compact
 * Strictly casts totalAmount to pure positive integer without decimals, commas, or dots.
 */
export function generateVietQRUrl(totalAmount, bookingId) {
  let raw = totalAmount;
  if (typeof raw === 'string') {
    raw = raw.replace(/[^\d.-]/g, '');
  }
  const cleanAmount = Math.max(0, Math.round(Number(raw) || 0));
  const cleanBookingId = (bookingId || 'PENDING').toString().replace(/[^\w-]/g, '');
  return `https://img.vietqr.io/image/MB-0987654321-compact.png?amount=${cleanAmount}&addInfo=BOOKING_${cleanBookingId}`;
}

/**
 * Generate a random UUIDv4 for Idempotency-Key
 */
export function generateUUID() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
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
  const searching = Boolean(filterParams.checkIn && filterParams.checkOut);
  const query = searching
    ? `?check_in=${encodeURIComponent(filterParams.checkIn)}&check_out=${encodeURIComponent(filterParams.checkOut)}`
    : '';
  const url = searching
    ? `${baseUrl}/api/v1/rooms/availability${query}`
    : `${baseUrl}/api/v1/rooms`;

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

      if (!searching) {
        rawRooms = rawRooms.filter(room => !room.status || String(room.status).toLowerCase() === 'vacant_clean');
      }

      // Merge backend rooms with luxury frontend catalog attributes
      if (rawRooms.length > 0) {
        const rooms = rawRooms.map((r, index) => {
          const typeName = r.room_type_name || r.roomTypeName || r.name || '';
          const normalizedType = typeName.toLowerCase();
          const fallback = LUXURY_ROOMS_CATALOG.find(room => normalizedType.includes(room.name.toLowerCase()) || normalizedType.includes(room.category.toLowerCase()))
            || LUXURY_ROOMS_CATALOG[index % LUXURY_ROOMS_CATALOG.length];
          return {
            ...fallback,
            id: r.id || fallback.id,
            roomId: r.id || fallback.id,
            roomNumber: r.roomNumber || r.room_number || fallback.roomNumber,
            name: typeName || fallback.name,
            category: r.category || fallback.category,
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
            amenities: r.amenities ? (Array.isArray(r.amenities) ? r.amenities : String(r.amenities).split(',').map(value => value.trim()).filter(Boolean)) : fallback.amenities,
            description: r.description || fallback.description,
            isDemoCatalog: false,
            status: r.status || 'vacant_clean',
            version: r.version ?? 0
          };
        });
        return { rooms, source: 'api' };
      }
      return { rooms: [], source: 'api' };
    }
    const apiError = new Error(`Room request failed (HTTP ${response.status})`);
    apiError.statusCode = response.status;
    throw apiError;
  } catch (err) {
    if (err.statusCode) throw err;
    console.info(`[Aurelia PMS] Backend at ${baseUrl} is offline or unreachable. Engaging 5★ Luxury Resilient Catalog Mode.`);
  }

  // Resilient Luxury Catalog fallback
  return { rooms: LUXURY_ROOMS_CATALOG.map(room => ({ ...room, isDemoCatalog: true })), source: 'demo' };
}

/**
 * Submit Booking Request to Backend
 * Sends POST /api/v1/bookings/public using the public booking contract.
 */
export async function submitBooking(bookingPayload) {
  const baseUrl = getApiBaseUrl();
  const idempotencyKey = generateUUID();

  const url = `${baseUrl}/api/v1/bookings/public`;

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json',
        'X-User-Role': '3', // Customer Role
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
        identity_card: bookingPayload.identityCard,
        voucher_code: bookingPayload.promoCode || ''
      }),
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    // Status code specific inspection
    if (response.status === 400) {
      const errJson = await response.json().catch(() => ({}));
      const err = new Error('Thông tin đặt phòng không hợp lệ. Vui lòng kiểm tra lại ngày lưu trú hoặc thông tin cá nhân.');
      err.statusCode = 400;
      throw err;
    }

    if (response.status === 409) {
      const err = new Error('Phòng bạn vừa chọn đã có khách đặt trong khoảng thời gian này hoặc trạng thái phòng đã thay đổi. Vui lòng chọn hạng phòng khác!');
      err.statusCode = 409;
      throw err;
    }

    if (response.status === 503) {
      const err = new Error('Hệ thống khách sạn đang trong chu kỳ kiểm toán đêm (00:00 - 00:05). Giao dịch tạm thời bị khóa nhằm đảm bảo an toàn sổ cái. Vui lòng quay lại sau ít phút!');
      err.statusCode = 503;
      throw err;
    }

    if (response.status === 201 || response.status === 200) {
      const json = await response.json();
      let bookingId = Math.floor(100000 + Math.random() * 900000);
      let bookingReference = null;
      if (json?.data?.booking_id) {
        bookingId = json.data.booking_id;
        bookingReference = json.data.booking_code || `BKG-${bookingId}`;
      } else if (json?.data?.booking_code) {
        bookingId = json.data.booking_code;
      } else if (json && json.data && json.data.bookingId) {
        bookingId = json.data.bookingId;
      } else if (json && json.bookingId) {
        bookingId = json.bookingId;
      }
      return {
        success: true,
        bookingId: bookingId,
        bookingReference,
        totalAmount: Number(json?.data?.folio?.balance) || null,
        message: 'Đặt phòng thành công!'
      };
    }

    const errJson = await response.json().catch(() => ({}));
    const requestError = new Error(errJson.message || `Lỗi máy chủ HTTP ${response.status}`);
    requestError.statusCode = response.status;
    throw requestError;

  } catch (err) {
    if (err.statusCode) {
      throw err;
    }

    // Backend Offline Simulation for Seamless Verification & Demonstration
    console.warn(`[Aurelia demo] Backend unreachable; simulating an offline booking. ${err.message}`);
    
    // Simulate slight luxury processing delay (600ms)
    await new Promise(res => setTimeout(res, 600));

    const simulatedBookingId = Math.floor(202600 + Math.random() * 9000);
    return {
      success: true,
      bookingId: simulatedBookingId,
      bookingReference: `DEMO-${simulatedBookingId}`,
      message: 'Mô phỏng ngoại tuyến: chưa tạo đặt phòng thật.',
      isSimulated: true
    };
  }
}
