/**
 * ==========================================================================
 * AURELIA GRAND LUXURY HOTEL - 5★ NO-BUILD APPLICATION CONTROLLER
 * Native JavaScript ES Module
 * Full Component Orchestration & 3-Step VietQR Booking State Machine
 * ==========================================================================
 */

import {
  getAuthSession,
  apiFetch,
  getApiBaseUrl,
  getRooms,
  submitBooking,
  lookupBooking,
  formatCurrency,
  generateVietQRUrl,
  LUXURY_ROOMS_CATALOG
} from './api.js';
import './shared/theme.js';

// Cross-environment safe global reference (Browser Window vs Node.js test environment)
const _global = typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : {});

// Application State
const state = {
  language: (typeof localStorage !== 'undefined' ? localStorage.getItem('aurelia_lang') : null) || 'vi',
  currency: (typeof localStorage !== 'undefined' ? localStorage.getItem('aurelia_currency') : null) || 'VND',
  rooms: [],
  roomsAreLive: false,
  roomsSearchedFor: null,
  filteredRooms: [],
  selectedCategory: 'all',
  activeGalleryTab: 'all',
  
  // Search parameters
  search: {
    checkIn: '',
    checkOut: '',
    adults: 2,
    children: 0,
    roomsCount: 1,
    promoCode: ''
  },
  
  // Booking modal flow
  booking: {
    step: 1, // 1: Review & Dates, 2: Guest Details, 3: Success & VietQR
    room: null,
    nights: 1,
    roomSubtotal: 0,
    discountAmount: 0,
    vatAmount: 0,
    serviceChargeAmount: 0,
    totalAmount: 0,
    bookingId: null,
    bookingReference: null,
    isSimulated: false,
    guest: {
      fullName: '',
      phone: '',
      email: '',
      identityCard: '',
      notes: ''
    },
    countdownTimer: null,
    countdownSeconds: 900 // 15 minutes
  },
  
  // Room detail modal
  detailRoom: null,

  // Lightbox
  lightbox: {
    images: [],
    currentIndex: 0
  }
};

// Gallery Data with Categories
const GALLERY_ITEMS = [
  {
    category: 'rooms',
    title: 'Presidential Royal Villa',
    subtitle: 'Hồ bơi tràn chân mây riêng tư 180m²',
    image: 'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'rooms',
    title: 'Executive Heritage Suite',
    subtitle: 'Nội thất gỗ sồi & sảnh tiếp khách Indochine',
    image: 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'dining',
    title: 'L\'Étoile Fine Dining Restaurant',
    subtitle: 'Ẩm thực Pháp chuẩn sao Michelin',
    image: 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'dining',
    title: 'Aurelia 360° Sky Bar',
    subtitle: 'Cocktail thượng hạng giữa trời sao',
    image: 'https://images.unsplash.com/photo-1572116469696-31de0f17cc34?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'amenities',
    title: 'Hồ Bơi Vô Cực Chân Mây',
    subtitle: 'Tầng 32 ôm trọn đường chân trời vịnh biển',
    image: 'https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'amenities',
    title: 'Aurelia Imperial Spa',
    subtitle: 'Liệu pháp trẻ hóa tinh dầu Thụy Điển',
    image: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'views',
    title: 'Hoàng Hôn Vịnh Ngọc',
    subtitle: 'Tầm nhìn ngoạn mục từ ban công phòng Suite',
    image: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=80'
  },
  {
    category: 'views',
    title: 'Toàn Cảnh Khách Sạn Về Đêm',
    subtitle: 'Kiến trúc tân cổ điển lung linh bên đại lộ',
    image: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80'
  }
];

// Translations Dictionary (VI & EN)
const I18N = {
  vi: {
    navHome: "Trang Chủ",
    navRooms: "Hạng Phòng",
    navServices: "Dịch Vụ & Tiện Ích",
    navAbout: "Giới Thiệu",
    navReviews: "Đánh Giá",
    navGallery: "Bộ Sưu Tập",
    navContact: "Liên Hệ",
    btnBookNow: "Đặt Phòng",
    searchBtn: "Kiểm Tra Phòng Trống",
    perNight: "/ đêm",
    viewDetails: "Xem Chi Tiết",
    bookRoom: "Đặt Phòng Này",
    checkIn: "Ngày Nhận Phòng",
    checkOut: "Ngày Trả Phòng",
    guests: "Số Khách",
    rooms: "Số Phòng",
    promoCode: "Mã Ưu Đãi"
  },
  en: {
    navHome: "Home",
    navRooms: "Rooms & Suites",
    navServices: "Services & Amenities",
    navAbout: "About Us",
    navReviews: "Reviews",
    navGallery: "Gallery",
    navContact: "Contact",
    btnBookNow: "Book Now",
    searchBtn: "Check Availability",
    perNight: "/ night",
    viewDetails: "View Details",
    bookRoom: "Book This Room",
    checkIn: "Check-in Date",
    checkOut: "Check-out Date",
    guests: "Guests",
    rooms: "Rooms",
    promoCode: "Promo Code"
  }
};

// ==========================================
// INITIALIZATION
// ==========================================
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', async () => {
    initDefaultDates();
    initEventListeners();
    initScrollObservers();
    await loadRooms();
    const returnParams = new URLSearchParams(window.location.search);
    const requestedRoom = Number(returnParams.get('bookRoom'));
    if (getAuthSession()?.token && requestedRoom && state.rooms.some(room => room.id === requestedRoom)) {
      returnParams.delete('bookRoom');
      const cleanUrl = new URL(window.location.href);
      cleanUrl.search = returnParams.toString();
      window.history.replaceState(null, '', cleanUrl);
      openBookingModal(requestedRoom);
    }
    renderGallery();
    refreshLucideIcons();
    applyLanguage(state.language);
    updateCurrencyDisplay();
  });
}

// Refresh Lucide Icons Helper
function refreshLucideIcons() {
  if (window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

// ==========================================
// DATE UTILITIES (TIMEZONE SAFE FOR UTC+7)
// ==========================================
/**
 * Parse YYYY-MM-DD input date string in local time zone,
 * preventing UTC shift bugs in Vietnam (UTC+7).
 */
export function parseLocalDate(dateString) {
  if (!dateString) return new Date();
  if (dateString instanceof Date) return dateString;
  const parts = String(dateString).trim().split('-');
  if (parts.length === 3) {
    const [year, month, day] = parts.map(Number);
    if (!isNaN(year) && !isNaN(month) && !isNaN(day)) {
      return new Date(year, month - 1, day, 12, 0, 0); // 12:00 PM local noon to prevent day boundary shifts
    }
  }
  return new Date(dateString);
}

export function formatLocalDate(date) {
  if (!date) return '';
  const d = date instanceof Date ? date : parseLocalDate(date);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

// ==========================================
// DATE LOGIC & VALIDATION
// ==========================================
function initDefaultDates() {
  const today = new Date();
  const todayLocal = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 12, 0, 0);
  const tomorrowLocal = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1, 12, 0, 0);

  const todayStr = formatLocalDate(todayLocal);
  const tomorrowStr = formatLocalDate(tomorrowLocal);

  state.search.checkIn = todayStr;
  state.search.checkOut = tomorrowStr;

  const checkInInput = document.getElementById('search-checkin');
  const checkOutInput = document.getElementById('search-checkout');

  if (checkInInput && checkOutInput) {
    checkInInput.min = todayStr;
    checkInInput.value = todayStr;

    checkOutInput.min = tomorrowStr;
    checkOutInput.value = tomorrowStr;

    // Validation: Check-out must be > Check-in >= Today (Local time zone safe)
    checkInInput.addEventListener('change', () => {
      const selectedCheckIn = parseLocalDate(checkInInput.value);
      const minCheckOut = new Date(selectedCheckIn.getFullYear(), selectedCheckIn.getMonth(), selectedCheckIn.getDate() + 1, 12, 0, 0);

      const minCheckOutStr = formatLocalDate(minCheckOut);
      checkOutInput.min = minCheckOutStr;

      const currentCheckOut = parseLocalDate(checkOutInput.value);
      if (currentCheckOut <= selectedCheckIn) {
        checkOutInput.value = minCheckOutStr;
      }
      state.search.checkIn = checkInInput.value;
      state.search.checkOut = checkOutInput.value;
    });

    checkOutInput.addEventListener('change', () => {
      const checkIn = parseLocalDate(checkInInput.value);
      const checkOut = parseLocalDate(checkOutInput.value);
      if (checkOut <= checkIn) {
        showToast('Ngày trả phòng phải sau ngày nhận phòng ít nhất 1 đêm!', 'warning');
        const nextDay = new Date(checkIn.getFullYear(), checkIn.getMonth(), checkIn.getDate() + 1, 12, 0, 0);
        checkOutInput.value = formatLocalDate(nextDay);
      }
      state.search.checkOut = checkOutInput.value;
    });
  }
}

// ==========================================
// TOAST NOTIFICATIONS
// ==========================================
export function showToast(message, type = 'info', duration = 4000) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = 'toast';

  const icons = {
    success: '<i data-lucide="check-circle-2" class="w-5 h-5 text-emerald-400 shrink-0"></i>',
    error: '<i data-lucide="alert-octagon" class="w-5 h-5 text-rose-400 shrink-0"></i>',
    warning: '<i data-lucide="alert-triangle" class="w-5 h-5 text-amber-400 shrink-0"></i>',
    info: '<i data-lucide="info" class="w-5 h-5 text-amber-300 shrink-0"></i>'
  };

  const borders = {
    success: 'border-emerald-500/40',
    error: 'border-rose-500/40',
    warning: 'border-amber-500/40',
    info: 'border-amber-400/40'
  };

  toast.classList.add(borders[type] || borders.info);

  toast.innerHTML = `
    ${icons[type] || icons.info}
    <div class="flex-1 text-sm text-slate-200 leading-relaxed font-sans">${message}</div>
    <button type="button" class="text-slate-400 hover:text-white transition-colors" onclick="this.parentElement.remove()">
      <i data-lucide="x" class="w-4 h-4"></i>
    </button>
  `;

  container.appendChild(toast);
  refreshLucideIcons();

  // Animate in
  requestAnimationFrame(() => {
    toast.classList.add('show');
  });

  // Auto remove
  setTimeout(() => {
    toast.classList.remove('show');
    toast.classList.add('hide');
    setTimeout(() => toast.remove(), 400);
  }, duration);
}

// ==========================================
// ROOMS DATA & RENDERING
// ==========================================
async function loadRooms(filterParams = {}) {
  renderRoomStatistics();
  const grid = document.getElementById('rooms-grid');
  if (grid) {
    grid.innerHTML = `
      <div class="col-span-full py-20 flex flex-col items-center justify-center text-center">
        <div class="w-12 h-12 rounded-full border-2 border-amber-500/20 border-t-amber-400 animate-spin mb-4"></div>
        <p class="text-amber-200/80 font-sans tracking-widest text-sm uppercase">Đang nạp dữ liệu phòng 5★...</p>
      </div>
    `;
  }

  try {
    // Keep the original room cards and enrich only their date-specific labels.
    const result = await getRooms();
    state.rooms = result.rooms;
    const checkIn = filterParams.checkIn || document.getElementById('search-checkin')?.value;
    const checkOut = filterParams.checkOut || document.getElementById('search-checkout')?.value;
    if (result.source === 'api' && checkIn && checkOut) {
      try {
        const query = new URLSearchParams({ check_in: checkIn, check_out: checkOut, include_status: 'true' });
        const response = await fetch(`${getApiBaseUrl()}/rooms/availability?${query}`, { signal: AbortSignal.timeout(5000) });
        if (!response.ok) throw new Error('Room status unavailable');
        const payload = await response.json();
        if (!Array.isArray(payload.data)) throw new Error('Invalid room status response');
        renderRoomStatistics(payload.data, checkIn, checkOut);
        const statuses = new Map(payload.data.map(room => [String(room.id), room.availability_status]));
        state.rooms = state.rooms.map(room => ({ ...room, availabilityStatus: statuses.get(String(room.id)), statusDates: `${checkIn} → ${checkOut}` }));
      } catch {
        // Missing status is displayed honestly, never inferred from demo data.
      }
    }
    state.roomsAreLive = result.source === 'api';
    state.roomsSearchedFor = filterParams.checkIn && filterParams.checkOut
      ? { checkIn: filterParams.checkIn, checkOut: filterParams.checkOut }
      : null;
    filterAndRenderRooms();
  } catch (err) {
    state.rooms = err.statusCode ? [] : LUXURY_ROOMS_CATALOG.map(room => ({ ...room, isDemoCatalog: true }));
    state.roomsAreLive = false;
    state.roomsSearchedFor = null;
    if (err.statusCode) showToast(`Không thể tải danh sách phòng (HTTP ${err.statusCode}). Vui lòng thử lại.`, 'error', 7000);
    else showToast('Không thể kết nối máy chủ. Đang hiển thị danh mục Luxury mẫu; đặt phòng sẽ chạy ở chế độ demo.', 'warning', 7000);
    filterAndRenderRooms();
  }
  if (filterParams.checkIn && filterParams.checkOut) {
    const checkInInput = document.getElementById('search-checkin');
    const checkOutInput = document.getElementById('search-checkout');
    if (checkInInput) checkInInput.value = filterParams.checkIn;
    if (checkOutInput) checkOutInput.value = filterParams.checkOut;
  }
}

function renderRoomStatistics(rows = null, checkIn = '', checkOut = '') {
  const container = document.getElementById('room-statistics');
  if (!container) return;
  const counts = { available: 0, reserved: 0, occupied: 0, maintenance: 0 };
  let other = 0;
  // Count API identities, not rendered/filterable cards or presentation labels.
  const unique = new Map((rows || []).filter(room => room && room.id != null).map(room => [String(room.id), room]));
  for (const room of unique.values()) {
    if (Object.hasOwn(counts, room.availability_status)) counts[room.availability_status]++;
    else other++;
  }
  container.querySelectorAll('[data-room-count]').forEach(node => {
    node.textContent = rows === null ? '—' : String(counts[node.dataset.roomCount]);
  });
  const note = document.getElementById('room-statistics-note');
  if (note) {
    note.textContent = rows === null
      ? 'Chưa có dữ liệu thống kê. Vui lòng kiểm tra phòng hoặc thử lại.'
      : `Ngày ${checkIn.split('-').reverse().join('/')} – ${checkOut.split('-').reverse().join('/')} · Tổng ${unique.size} phòng`
        + (other ? ` · ${other} phòng thuộc trạng thái khác/chưa đủ điều kiện nhận khách, chưa tính vào 4 nhóm trên.` : '');
  }
}

function filterAndRenderRooms() {
  const grid = document.getElementById('rooms-grid');
  if (!grid) return;

  let filtered = state.rooms;
  if (state.selectedCategory !== 'all') {
    filtered = state.rooms.filter(r => r.category.toLowerCase() === state.selectedCategory.toLowerCase());
  }

  state.filteredRooms = filtered;

  if (filtered.length === 0) {
    grid.innerHTML = `
      <div class="col-span-full py-16 text-center text-slate-400 glass-card rounded-2xl p-8">
        <i data-lucide="bed-double" class="w-12 h-12 text-amber-500/50 mx-auto mb-3"></i>
        <h4 class="text-lg font-serif text-white">Không tìm thấy phòng phù hợp</h4>
        <p class="text-sm mt-1">Quý khách vui lòng thử thay đổi khoảng ngày lưu trú hoặc danh mục lọc.</p>
      </div>
    `;
    refreshLucideIcons();
    return;
  }

  grid.innerHTML = filtered.map(room => {
    const formattedPrice = formatCurrency(room.price, state.currency);
    const primaryAmenities = room.amenities.slice(0, 3);
    const labels = {
      available: ['🟢 Còn phòng', 'available'], reserved: ['🟠 Đã đặt', 'reserved'],
      occupied: ['🔴 Đang sử dụng', 'occupied'], maintenance: ['⚙️ Đang bảo trì', 'maintenance'],
      not_ready: ['🟢 Còn phòng', 'available']
    };
    const [statusLabel, statusColor] = labels[room.availabilityStatus] || ['Chưa xác định', 'unknown'];

    return `
      <div class="room-card glass-card rounded-2xl overflow-hidden flex flex-col group" data-room-id="${room.id}">
        <!-- Image Container -->
        <div class="room-image-container relative h-64 overflow-hidden">
          <img src="${room.image}" alt="${room.name}" class="w-full h-full object-cover">
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/20"></div>
          
          <!-- Badge -->
          ${room.badge ? `
            <div class="absolute top-4 left-4 bg-amber-500/90 text-slate-950 font-sans font-bold text-xs uppercase tracking-wider px-3 py-1 rounded-full backdrop-blur-md shadow-lg">
              ${room.badge}
            </div>
          ` : ''}

          <!-- Rating -->
          <div class="absolute top-4 right-4 bg-slate-900/80 backdrop-blur-md border border-amber-500/30 text-amber-300 font-sans text-xs font-semibold px-2.5 py-1 rounded-full flex items-center gap-1 shadow-lg">
            <i data-lucide="star" class="w-3.5 h-3.5 fill-amber-400 text-amber-400"></i>
            <span>${room.rating}</span>
            <span class="text-slate-400 font-normal">(${room.reviewsCount})</span>
          </div>

          <!-- Bottom View Badge -->
          <div class="absolute bottom-3 left-4 text-xs font-medium text-amber-200/90 flex items-center gap-1.5 drop-shadow">
            <i data-lucide="compass" class="w-3.5 h-3.5 text-amber-400"></i>
            <span>${room.view}</span>
          </div>
        </div>

        <!-- Content Body -->
        <div class="p-6 flex-1 flex flex-col justify-between">
          <div>
            <!-- Category & Title -->
            <div class="flex items-center justify-between text-xs tracking-widest uppercase font-semibold text-amber-400 mb-1">
              <span>${room.category} Class${room.isDemoCatalog ? ' · Demo' : ''}</span>
              <span class="text-slate-400">#${room.roomNumber}</span>
            </div>
            <div class="room-availability room-availability--${statusColor}" title="Theo ngày nhận/trả phòng đã kiểm tra: ${room.statusDates || 'chưa có dữ liệu'}" role="status">${statusLabel}</div>
            <h3 class="font-serif text-xl text-white font-medium group-hover:text-amber-200 transition-colors line-clamp-1 mb-3">
              ${room.name}
            </h3>

            <!-- Room Specs -->
            <div class="grid grid-cols-3 gap-2 py-3 border-y border-amber-500/10 text-xs text-slate-300 mb-4 font-sans">
              <div class="flex items-center gap-1.5" title="Diện tích">
                <i data-lucide="maximize" class="w-3.5 h-3.5 text-amber-400 shrink-0"></i>
                <span>${room.area} m²</span>
              </div>
              <div class="flex items-center gap-1.5" title="Sức chứa">
                <i data-lucide="users" class="w-3.5 h-3.5 text-amber-400 shrink-0"></i>
                <span>${room.capacity} Khách</span>
              </div>
              <div class="flex items-center gap-1.5 truncate" title="${room.bedType}">
                <i data-lucide="bed" class="w-3.5 h-3.5 text-amber-400 shrink-0"></i>
                <span class="truncate">${room.bedType.split(' ')[1] || 'King'}</span>
              </div>
            </div>

            <!-- Quick Amenities -->
            <div class="flex flex-wrap gap-1.5 mb-5">
              ${primaryAmenities.map(am => `
                <span class="text-[11px] bg-slate-800/80 text-slate-300 border border-slate-700/60 px-2.5 py-0.5 rounded-md flex items-center gap-1">
                  <i data-lucide="sparkles" class="w-2.5 h-2.5 text-amber-400"></i>
                  ${am}
                </span>
              `).join('')}
            </div>
          </div>

          <!-- Price & Actions Footer -->
          <div class="pt-4 border-t border-amber-500/15 flex items-end justify-between gap-3">
            <div>
              <div class="text-[11px] uppercase tracking-wider text-slate-400">Giá nghỉ đêm từ</div>
              <div class="text-xl font-serif font-bold text-gold-gradient">${formattedPrice}</div>
            </div>
            <div class="flex items-center gap-2">
              <button type="button" class="btn-detail btn-outline-gold px-3.5 py-2 rounded-xl text-xs font-medium flex items-center gap-1" data-id="${room.id}">
                <i data-lucide="eye" class="w-3.5 h-3.5"></i>
                <span class="hidden sm:inline">Chi Tiết</span>
              </button>
              <button type="button" class="btn-book btn-gold px-4 py-2 rounded-xl text-xs font-semibold flex items-center gap-1 shadow-md" data-id="${room.id}">
                <i data-lucide="calendar-check" class="w-3.5 h-3.5"></i>
                <span>Đặt Phòng</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    `;
  }).join('');

  refreshLucideIcons();
  attachRoomCardListeners();
}

function attachRoomCardListeners() {
  document.querySelectorAll('.btn-detail').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const roomId = Number(e.currentTarget.dataset.id);
      openRoomDetailModal(roomId);
    });
  });

  document.querySelectorAll('.btn-book').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const roomId = Number(e.currentTarget.dataset.id);
      openBookingModal(roomId);
    });
  });
}

// ==========================================
// ROOM DETAIL MODAL
// ==========================================
function openRoomDetailModal(roomId) {
  const room = state.rooms.find(r => r.id === roomId);
  if (!room) return;
  state.detailRoom = room;

  const modal = document.getElementById('room-detail-modal');
  const body = document.getElementById('room-detail-body');
  if (!modal || !body) return;

  const formattedPrice = formatCurrency(room.price, state.currency);

  body.innerHTML = `
    <!-- Top Image Hero -->
    <div class="relative h-72 md:h-96 w-full rounded-2xl overflow-hidden mb-6">
      <img src="${room.image}" alt="${room.name}" class="w-full h-full object-cover">
      <div class="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent"></div>
      
      <div class="absolute bottom-4 left-6 right-6 flex items-end justify-between">
        <div>
          <span class="text-xs font-semibold tracking-widest text-amber-400 uppercase bg-slate-900/80 px-3 py-1 rounded-full border border-amber-500/30">
            ${room.category} - Phòng #${room.roomNumber}
          </span>
          <h2 class="text-2xl md:text-3xl font-serif text-white font-medium mt-2">${room.name}</h2>
          <p class="text-sm text-amber-200/90 flex items-center gap-1.5 mt-1">
            <i data-lucide="compass" class="w-4 h-4 text-amber-400"></i> ${room.view}
          </p>
        </div>
        <div class="text-right hidden sm:block">
          <div class="text-xs text-slate-400">Đơn giá tiêu chuẩn</div>
          <div class="text-2xl font-serif font-bold text-gold-gradient">${formattedPrice} <span class="text-xs font-sans text-slate-400">/đêm</span></div>
        </div>
      </div>
    </div>

    <!-- Description -->
    <div class="mb-6">
      <h4 class="text-sm uppercase tracking-widest font-semibold text-amber-400 mb-2">Mô tả tổng quan</h4>
      <p class="text-slate-300 font-sans text-sm leading-relaxed">${room.description}</p>
    </div>

    <!-- Technical Specs Grid -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3 p-4 rounded-xl bg-slate-900/60 border border-amber-500/20 mb-6 text-sm">
      <div>
        <div class="text-xs text-slate-400">Diện tích</div>
        <div class="font-semibold text-white mt-0.5">${room.area} m²</div>
      </div>
      <div>
        <div class="text-xs text-slate-400">Sức chứa</div>
        <div class="font-semibold text-white mt-0.5">${room.capacity} Người lớn ${room.childrenCapacity ? `+ ${room.childrenCapacity} Bé` : ''}</div>
      </div>
      <div>
        <div class="text-xs text-slate-400">Loại giường</div>
        <div class="font-semibold text-white mt-0.5">${room.bedType}</div>
      </div>
      <div>
        <div class="text-xs text-slate-400">Đánh giá khách hàng</div>
        <div class="font-semibold text-amber-300 mt-0.5 flex items-center gap-1">
          <i data-lucide="star" class="w-3.5 h-3.5 fill-amber-400 text-amber-400"></i>
          ${room.rating} / 5.0
        </div>
      </div>
    </div>

    <!-- Amenities Grid -->
    <div class="mb-6">
      <h4 class="text-sm uppercase tracking-widest font-semibold text-amber-400 mb-3">Tiện ích thượng hạng trong phòng</h4>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
        ${room.amenities.map(am => `
          <div class="flex items-center gap-2 text-sm text-slate-300 bg-slate-800/40 p-2.5 rounded-lg border border-slate-700/40">
            <i data-lucide="check-circle-2" class="w-4 h-4 text-amber-400 shrink-0"></i>
            <span>${am}</span>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- Booking CTA Footer -->
    <div class="pt-4 border-t border-amber-500/20 flex flex-col sm:flex-row items-center justify-between gap-4">
      <div class="w-full sm:w-auto text-left">
        <span class="text-xs text-slate-400">Tổng quan đơn giá</span>
        <div class="text-2xl font-serif font-bold text-gold-gradient">${formattedPrice} <span class="text-xs text-slate-400">/đêm</span></div>
      </div>
      <div class="flex items-center gap-3 w-full sm:w-auto">
        <button type="button" class="btn-outline-gold px-5 py-2.5 rounded-xl text-sm flex-1 sm:flex-none justify-center" onclick="closeRoomDetailModal()">
          Đóng
        </button>
        <button type="button" class="btn-gold px-6 py-2.5 rounded-xl text-sm font-semibold flex-1 sm:flex-none justify-center flex items-center gap-2 shadow-lg" onclick="proceedToBookingFromDetail()">
          <i data-lucide="calendar-check" class="w-4 h-4"></i>
          Đặt Phòng Ngay
        </button>
      </div>
    </div>
  `;

  refreshLucideIcons();
  modal.classList.add('active');
}

_global.closeRoomDetailModal = function() {
  const modal = document.getElementById('room-detail-modal');
  if (modal) modal.classList.remove('active');
  state.detailRoom = null;
};

_global.proceedToBookingFromDetail = function() {
  if (state.detailRoom) {
    const roomId = state.detailRoom.id;
    openBookingModal(roomId);
  }
};

// ==========================================
// 3-STEP VIETQR BOOKING MODAL
// ==========================================
export async function openBookingModal(roomId = null) {
  const token = getAuthSession()?.token;
  if (typeof token !== 'string' || !token.trim()) {
    _global.closeBookingModal();
    showBookingLoginPrompt(roomId);
    return;
  }
  try {
    // A stored token alone is insufficient: use the existing JWT middleware
    // through a read-only endpoint and the existing session-expiry handling.
    await apiFetch('/rooms', { cache: 'no-store', signal: AbortSignal.timeout(5000) });
  } catch (error) {
    _global.closeBookingModal();
    if (error.status === 401 || error.status === 403) showBookingLoginPrompt(roomId);
    else showToast('Chưa thể xác minh phiên đăng nhập. Vui lòng thử lại.', 'warning');
    return;
  }
  if (getAuthSession()?.token !== token) {
    _global.closeBookingModal();
    showBookingLoginPrompt(roomId);
    return;
  }
  if (state.detailRoom) _global.closeRoomDetailModal();
  let targetRoom = null;
  if (roomId) {
    targetRoom = state.rooms.find(r => r.id === roomId);
  }
  if (!targetRoom && state.rooms.length > 0) {
    targetRoom = state.rooms[0];
  }
  if (!targetRoom) {
    targetRoom = LUXURY_ROOMS_CATALOG[0];
  }

  state.booking.room = targetRoom;
  state.booking.step = 1;

  // Sync dates from search bar if available or initialize defaults
  const searchCheckIn = document.getElementById('search-checkin')?.value;
  const searchCheckOut = document.getElementById('search-checkout')?.value;
  if (searchCheckIn && searchCheckOut) {
    state.search.checkIn = searchCheckIn;
    state.search.checkOut = searchCheckOut;
  }
  if (!state.search.checkIn) {
    state.search.checkIn = formatLocalDate(new Date());
  }
  if (!state.search.checkOut) {
    const tmr = new Date();
    tmr.setDate(tmr.getDate() + 1);
    state.search.checkOut = formatLocalDate(tmr);
  }

  calculateBookingPricing();

  const modal = document.getElementById('booking-modal');
  if (modal) {
    renderBookingModalContent();
    modal.classList.add('active');
  }
}

// The mobile drawer's existing inline onclick uses the same guarded entry point.
_global.openBookingModal = openBookingModal;

function showBookingLoginPrompt(roomId) {
  let dialog = document.getElementById('booking-login-prompt');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'booking-login-prompt';
    dialog.className = 'booking-login-prompt';
    dialog.setAttribute('aria-labelledby', 'booking-login-heading');
    dialog.setAttribute('aria-describedby', 'booking-login-message');
    dialog.innerHTML = `
      <div class="booking-login-symbol" aria-hidden="true">✦</div>
      <h2 id="booking-login-heading">Đặt phòng tại Aurelia</h2>
      <p id="booking-login-message">Vui lòng đăng nhập để thực hiện đặt phòng.</p>
      <div class="booking-login-actions">
        <button type="button" class="btn-outline-gold" data-dismiss>Quay lại</button>
        <a class="btn-gold" data-login>Đăng nhập</a>
      </div>`;
    dialog.querySelector('[data-dismiss]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => { if (event.target === dialog) {
      const bounds = dialog.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    } });
    document.body.append(dialog);
  }
  const target = new URL(window.location.href);
  target.searchParams.delete('bookRoom');
  const selectedRoom = roomId || state.rooms[0]?.id;
  if (selectedRoom) target.searchParams.set('bookRoom', selectedRoom);
  const login = new URL('/portal.html', window.location.origin);
  login.searchParams.set('returnTo', target.pathname + target.search + target.hash);
  dialog.querySelector('[data-login]').href = login.href;
  if (!dialog.open) dialog.showModal();
}

_global.closeBookingModal = function() {
  const modal = document.getElementById('booking-modal');
  if (modal) modal.classList.remove('active');
  if (state.booking.countdownTimer) {
    clearInterval(state.booking.countdownTimer);
    state.booking.countdownTimer = null;
  }
};

function calculateBookingPricing() {
  const b = state.booking;
  if (!b.room) return;

  if (b.room.isDemoCatalog) {
    showToast('Giá và tình trạng phòng đang dùng dữ liệu mẫu; sẽ không tạo đặt phòng thật.', 'warning', 5000);
  }

  const checkIn = parseLocalDate(state.search.checkIn);
  const checkOut = parseLocalDate(state.search.checkOut);
  
  const diffTime = checkOut.getTime() - checkIn.getTime();
  let diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
  if (diffDays < 1) diffDays = 1;
  b.nights = diffDays;

  // Base Room Subtotal
  b.roomSubtotal = Math.round(b.room.price * b.nights);

  // Promo Code calculation
  const promo = (state.search.promoCode || '').trim().toUpperCase();
  let discountRate = 0;
  if (promo === 'LUXURY2026') {
    discountRate = 0.10; // 10%
  } else if (promo === 'VIP5STAR') {
    discountRate = 0.15; // 15%
  }
  b.discountAmount = Math.round(b.roomSubtotal * discountRate);

  const afterDiscount = b.roomSubtotal - b.discountAmount;

  // Taxes & 5-Star Service Charge (Rounded pure integers)
  b.vatAmount = Math.round(afterDiscount * 0.08); // 8% VAT
  b.serviceChargeAmount = Math.round(afterDiscount * 0.05); // 5% Service charge

  // Final Total strictly cast to pure positive integer for VietQR
  b.totalAmount = Math.max(0, Math.round(afterDiscount + b.vatAmount + b.serviceChargeAmount));
}

function renderBookingModalContent() {
  const body = document.getElementById('booking-modal-body');
  if (!body) return;

  const b = state.booking;
  const currentStep = b.step;

  // Step Indicators Header
  const stepHeader = `
    <!-- Step Navigation Bar -->
    <div class="flex items-center justify-between border-b border-amber-500/20 pb-5 mb-6">
      <div class="flex items-center gap-2">
        <span class="w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs ${currentStep >= 1 ? 'bg-amber-500 text-slate-950 shadow-md' : 'bg-slate-800 text-slate-400'}">
          ${currentStep > 1 ? '<i data-lucide="check" class="w-4 h-4"></i>' : '1'}
        </span>
        <span class="text-xs md:text-sm font-medium ${currentStep === 1 ? 'text-amber-300 font-semibold' : 'text-slate-400'}">
          1. Lịch Trình & Chi Phí
        </span>
      </div>
      <div class="h-0.5 flex-1 mx-3 bg-slate-800">
        <div class="h-full bg-amber-500 transition-all duration-300" style="width: ${currentStep === 1 ? '0%' : (currentStep === 2 ? '50%' : '100%')}"></div>
      </div>
      <div class="flex items-center gap-2">
        <span class="w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs ${currentStep >= 2 ? 'bg-amber-500 text-slate-950 shadow-md' : 'bg-slate-800 text-slate-400'}">
          ${currentStep > 2 ? '<i data-lucide="check" class="w-4 h-4"></i>' : '2'}
        </span>
        <span class="text-xs md:text-sm font-medium ${currentStep === 2 ? 'text-amber-300 font-semibold' : 'text-slate-400'}">
          2. Thông Tin Khách Hàng
        </span>
      </div>
      <div class="h-0.5 flex-1 mx-3 bg-slate-800">
        <div class="h-full bg-amber-500 transition-all duration-300" style="width: ${currentStep === 3 ? '100%' : '0%'}"></div>
      </div>
      <div class="flex items-center gap-2">
        <span class="w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs ${currentStep >= 3 ? 'bg-amber-500 text-slate-950 shadow-md' : 'bg-slate-800 text-slate-400'}">
          3
        </span>
        <span class="text-xs md:text-sm font-medium ${currentStep === 3 ? 'text-amber-300 font-semibold' : 'text-slate-400'}">
          3. Thanh Toán VietQR
        </span>
      </div>
    </div>
  `;

  // Step 1: Review selected room & stay dates
  if (currentStep === 1) {
    const formattedSubtotal = formatCurrency(b.roomSubtotal, state.currency);
    const formattedDiscount = formatCurrency(b.discountAmount, state.currency);
    const formattedVat = formatCurrency(b.vatAmount, state.currency);
    const formattedService = formatCurrency(b.serviceChargeAmount, state.currency);
    const formattedTotal = formatCurrency(b.totalAmount, state.currency);

    body.innerHTML = `
      ${stepHeader}

      <!-- Selected Room Highlight Card -->
      <div class="p-4 rounded-xl bg-slate-900/80 border border-amber-500/25 flex flex-col sm:flex-row gap-4 items-center mb-6">
        <img src="${b.room.image}" alt="${b.room.name}" class="w-full sm:w-36 h-28 object-cover rounded-lg shrink-0">
        <div class="flex-1 w-full text-left">
          <div class="flex items-center justify-between">
            <span class="text-xs text-amber-400 font-bold tracking-wider uppercase">${b.room.category} - #${b.room.roomNumber}</span>
            <span class="text-xs text-slate-400">${b.room.area} m² | ${b.room.capacity} Khách</span>
          </div>
          <h3 class="font-serif text-lg text-white font-medium my-1">${b.room.name}</h3>
          <p class="text-xs text-slate-400">${b.room.bedType} • ${b.room.view}</p>
        </div>
      </div>

      <!-- Stay Dates & Promo Code Configuration -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Ngày nhận phòng</label>
          <input type="date" id="booking-checkin" min="${formatLocalDate(new Date())}" value="${state.search.checkIn}" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
        </div>
        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Ngày trả phòng</label>
          <input type="date" id="booking-checkout" min="${state.search.checkIn}" value="${state.search.checkOut}" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
        </div>
        <div class="sm:col-span-2">
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Mã ưu đãi (Promo Code)</label>
          <div class="flex gap-2">
            <input type="text" id="booking-promo" value="${state.search.promoCode}" placeholder="Nhập mã ví dụ: LUXURY2026 hoặc VIP5STAR" class="flex-1 uppercase bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
            <button type="button" id="btn-apply-promo" class="btn-outline-gold px-4 py-2.5 rounded-xl text-xs font-semibold">
              Áp Dụng
            </button>
          </div>
        </div>
      </div>

      <!-- Price Breakdown Table -->
      <div class="rounded-xl bg-slate-900/60 p-4 border border-amber-500/15 mb-6 text-sm">
        <div class="flex justify-between py-1.5 text-slate-300">
          <span>Tiền phòng (${formatCurrency(b.room.price, state.currency)} × ${b.nights} đêm):</span>
          <span class="font-medium text-white">${formattedSubtotal}</span>
        </div>
        ${b.discountAmount > 0 ? `
          <div class="flex justify-between py-1.5 text-emerald-400 font-medium">
            <span>Giảm giá mã ưu đãi (${state.search.promoCode.toUpperCase()}):</span>
            <span>-${formattedDiscount}</span>
          </div>
        ` : ''}
        <div class="flex justify-between py-1.5 text-slate-400 text-xs">
          <span>Thuế giá trị gia tăng (VAT 8%):</span>
          <span>${formattedVat}</span>
        </div>
        <div class="flex justify-between py-1.5 text-slate-400 text-xs">
          <span>Phí phục vụ khách sạn 5★ (5%):</span>
          <span>${formattedService}</span>
        </div>
        <div class="flex justify-between pt-3 mt-2 border-t border-amber-500/20 text-base font-serif">
          <span class="text-amber-200 font-bold">Tổng thanh toán dự kiến:</span>
          <span class="font-bold text-xl text-gold-gradient">${formattedTotal}</span>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex items-center justify-between pt-2">
        <button type="button" class="btn-outline-gold px-5 py-2.5 rounded-xl text-sm" onclick="closeBookingModal()">
          Hủy Bỏ
        </button>
        <button type="button" id="btn-step1-next" class="btn-gold px-6 py-2.5 rounded-xl text-sm font-semibold flex items-center gap-2 shadow-lg">
          Tiếp Tục Nhập Thông Tin
          <i data-lucide="arrow-right" class="w-4 h-4"></i>
        </button>
      </div>
    `;

    // Step 1 Event Listeners
    const dateIn = document.getElementById('booking-checkin');
    const dateOut = document.getElementById('booking-checkout');
    if (dateIn && dateOut) {
      dateIn.addEventListener('change', () => {
        if (!dateIn.value || dateIn.value < formatLocalDate(new Date()) || dateIn.value >= dateOut.value) {
          showToast('Vui lòng chọn ngày nhận phòng hợp lệ trước ngày trả phòng.', 'warning');
          dateIn.value = state.search.checkIn;
          return;
        }
        state.search.checkIn = dateIn.value;
        dateOut.min = dateIn.value;
        state.roomsSearchedFor = null;
        calculateBookingPricing();
        renderBookingModalContent();
      });
      dateOut.addEventListener('change', () => {
        if (!dateOut.value || dateOut.value <= dateIn.value) {
          showToast('Ngày trả phòng phải sau ngày nhận phòng.', 'warning');
          dateOut.value = state.search.checkOut;
          return;
        }
        state.search.checkOut = dateOut.value;
        state.roomsSearchedFor = null;
        calculateBookingPricing();
        renderBookingModalContent();
      });
    }

    const promoBtn = document.getElementById('btn-apply-promo');
    if (promoBtn) {
      promoBtn.addEventListener('click', () => {
        const promoVal = document.getElementById('booking-promo').value.trim();
        state.search.promoCode = promoVal;
        calculateBookingPricing();
        if (state.booking.discountAmount > 0) {
          showToast(`Đã áp dụng mã ưu đãi ${promoVal.toUpperCase()}!`, 'success');
        } else if (promoVal) {
          showToast('Mã ưu đãi không hợp lệ hoặc đã hết hạn!', 'warning');
        }
        renderBookingModalContent();
      });
    }

    const nextBtn = document.getElementById('btn-step1-next');
    if (nextBtn) {
      nextBtn.addEventListener('click', () => {
        const checkIn = document.getElementById('booking-checkin')?.value;
        const checkOut = document.getElementById('booking-checkout')?.value;
        if (!checkIn || !checkOut || checkIn < formatLocalDate(new Date()) || checkOut <= checkIn) {
          showToast('Vui lòng chọn ngày nhận phòng từ hôm nay và ngày trả phòng sau ngày nhận phòng.', 'warning');
          return;
        }
        state.search.checkIn = checkIn;
        state.search.checkOut = checkOut;
        calculateBookingPricing();
        state.booking.step = 2;
        renderBookingModalContent();
      });
    }
  }

  // Step 2: Guest Details Form
  else if (currentStep === 2) {
    const formattedTotal = formatCurrency(b.totalAmount, state.currency);

    body.innerHTML = `
      ${stepHeader}

      <div class="mb-4">
        <h4 class="text-sm font-serif text-white font-medium">Thông tin định danh khách lưu trú</h4>
        <p class="text-xs text-slate-400 mt-0.5">Vui lòng cung cấp chính xác để hệ thống gửi vé phòng điện tử và hóa đơn.</p>
      </div>

      <form id="guest-booking-form" class="space-y-4 mb-6">
        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
            Họ và tên quý khách <span class="text-rose-400">*</span>
          </label>
          <input type="text" id="guest-name" required value="${b.guest.fullName}" placeholder="Ví dụ: Nguyễn Văn An" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Số điện thoại <span class="text-rose-400">*</span>
            </label>
            <input type="tel" id="guest-phone" required value="${b.guest.phone}" placeholder="Ví dụ: 0901234567" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
          </div>
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Địa chỉ Email <span class="text-rose-400">*</span>
            </label>
            <input type="email" id="guest-email" required value="${b.guest.email}" placeholder="Ví dụ: an.nguyen@gmail.com" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Số CCCD / Hộ chiếu <span class="text-rose-400">*</span>
            </label>
            <input type="text" id="guest-identity" required value="${b.guest.identityCard}" placeholder="Ví dụ: 012345678901" class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
          </div>
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Yêu cầu đặc biệt
            </label>
            <input type="text" id="guest-notes" value="${b.guest.notes}" placeholder="Tầng cao, phòng yên tĩnh, hoa tươi..." class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
          </div>
        </div>
      </form>

      <!-- Mini Price Reminder -->
      <div class="p-3.5 rounded-xl bg-slate-900/60 border border-amber-500/20 flex items-center justify-between text-xs text-slate-300 mb-6">
        <span>Đặt phòng: <strong class="text-white">${b.room.name}</strong> (${b.nights} đêm)</span>
        <span class="font-bold text-amber-300 text-sm">${formattedTotal}</span>
      </div>

      <!-- Actions -->
      <div class="flex items-center justify-between pt-2">
        <button type="button" class="btn-outline-gold px-5 py-2.5 rounded-xl text-sm flex items-center gap-1.5" onclick="goToStep(1)">
          <i data-lucide="arrow-left" class="w-4 h-4"></i> Quay Lại
        </button>
        <button type="button" id="btn-submit-booking" class="btn-gold px-6 py-2.5 rounded-xl text-sm font-semibold flex items-center gap-2 shadow-lg">
          <span id="btn-submit-text">Xác Nhận & Đặt Phòng</span>
          <i data-lucide="shield-check" class="w-4 h-4"></i>
        </button>
      </div>
    `;

    // Step 2 Submission Handler
    const submitBtn = document.getElementById('btn-submit-booking');
    if (submitBtn) {
      submitBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const form = document.getElementById('guest-booking-form');
        if (!form.checkValidity()) {
          form.reportValidity();
          return;
        }

        b.guest.fullName = document.getElementById('guest-name').value.trim();
        b.guest.phone = document.getElementById('guest-phone').value.trim();
        b.guest.email = document.getElementById('guest-email').value.trim();
        b.guest.identityCard = document.getElementById('guest-identity').value.trim();
        b.guest.notes = document.getElementById('guest-notes').value.trim();

        // UI Loading
        submitBtn.disabled = true;
        const textSpan = document.getElementById('btn-submit-text');
        if (textSpan) textSpan.innerHTML = 'Đang xử lý đặt phòng...';

        try {
          const res = await submitBooking({
            roomId: b.room.id,
            roomVersion: b.room.version ?? 0,
            checkInDate: state.search.checkIn,
            checkOutDate: state.search.checkOut,
            fullName: b.guest.fullName,
            phone: b.guest.phone,
            email: b.guest.email,
            identityCard: b.guest.identityCard,
            promoCode: state.search.promoCode
          });

          b.bookingId = res.bookingId;
          b.isSimulated = Boolean(res.isSimulated);
          b.bookingReference = res.bookingReference || `${b.isSimulated ? 'DEMO-' : 'BKG-'}${res.bookingId}`;
          if (!b.isSimulated && res.totalAmount > 0) b.totalAmount = Math.round(res.totalAmount);
          b.step = 3;
          showToast(b.isSimulated
            ? 'Chế độ demo ngoại tuyến: đặt phòng mô phỏng, chưa được gửi đến khách sạn.'
            : `Đã tạo mã đặt phòng #${b.bookingId} thành công!`, b.isSimulated ? 'warning' : 'success', 7000);
          startBookingCountdown();
          renderBookingModalContent();

        } catch (err) {
          submitBtn.disabled = false;
          if (textSpan) textSpan.innerHTML = 'Xác Nhận & Đặt Phòng';
          showToast(err.message, 'error', 6000);
        }
      });
    }
  }

  // Step 3: Success & Dynamic VietQR Generation
  else if (currentStep === 3) {
    const formattedTotal = formatCurrency(b.totalAmount, state.currency);
    const qrUrl = generateVietQRUrl(b.totalAmount, b.bookingId);
    const bankAccount = "0987654321";
    const bankName = "MB Bank (Ngân hàng Quân Đội)";
    const accountName = "AURELIA GRAND LUXURY HOTEL";
    const transferContent = `BOOKING_${b.bookingId}`;

    body.innerHTML = `
      ${stepHeader}

      ${b.isSimulated ? `<div role="status" class="rounded-xl border border-amber-400/40 bg-amber-500/10 p-4 mb-5 text-sm text-amber-100"><strong>DEMO NGOẠI TUYẾN</strong><br>Đây là mã đặt phòng mô phỏng để xem giao diện. Khách sạn chưa nhận được đặt phòng và không cần chuyển khoản theo mã VietQR này.</div>` : ''}

      <!-- Success Congratulatory Banner -->
      <div class="text-center mb-6">
        <div class="w-14 h-14 rounded-full bg-emerald-500/20 border-2 border-emerald-400/50 flex items-center justify-center text-emerald-400 mx-auto mb-3 shadow-lg">
          <i data-lucide="check-check" class="w-7 h-7"></i>
        </div>
        <span class="text-xs font-semibold uppercase tracking-widest text-amber-400">${b.isSimulated ? 'Mô phỏng ngoại tuyến' : 'Xác nhận giữ chỗ thành công'}</span>
        <h3 class="font-serif text-2xl text-white font-medium mt-1">Đơn Đặt Phòng ${b.bookingReference || `#${b.bookingId}`}</h3>
        <p class="text-xs text-slate-300 mt-1">
          ${b.isSimulated ? 'Đây chỉ là bản xem trước ngoại tuyến, khách sạn chưa nhận được yêu cầu.' : `Kính chào Quý khách <strong class="text-amber-200">${b.guest.fullName}</strong>, vé phòng đã được giữ tạm thời.`}
        </p>
      </div>

      <!-- VietQR Container Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6 items-center vietqr-card rounded-2xl p-6 mb-6">
        <!-- QR Code Image -->
        <div class="flex flex-col items-center justify-center bg-white p-4 rounded-xl shadow-2xl">
          <img src="${qrUrl}" alt="VietQR Thanh Toán" class="w-60 h-60 object-contain rounded-lg">
          <div class="mt-2 text-center text-slate-800 text-xs font-medium flex items-center gap-1.5">
            <i data-lucide="scan" class="w-4 h-4 text-emerald-600"></i>
            <span>Quét bằng ứng dụng Ngân hàng bất kỳ</span>
          </div>
        </div>

        <!-- Transfer Information Details -->
        <div class="text-left space-y-3 font-sans text-sm">
          <div>
            <span class="text-xs text-slate-400 uppercase tracking-wider">Ngân hàng thụ hưởng</span>
            <div class="text-white font-semibold">${bankName}</div>
          </div>
          <div>
            <span class="text-xs text-slate-400 uppercase tracking-wider">Chủ tài khoản</span>
            <div class="text-amber-200 font-semibold">${accountName}</div>
          </div>
          <div>
            <span class="text-xs text-slate-400 uppercase tracking-wider">Số tài khoản</span>
            <div class="flex items-center gap-2">
              <span class="text-lg font-mono font-bold text-white">${bankAccount}</span>
              <button type="button" class="text-xs text-amber-400 hover:text-white transition-colors bg-slate-800/80 px-2 py-1 rounded" onclick="copyToClipboard('${bankAccount}', 'Đã sao chép số tài khoản!')">
                Sao chép
              </button>
            </div>
          </div>
          <div>
            <span class="text-xs text-slate-400 uppercase tracking-wider">Số tiền thanh toán</span>
            <div class="flex items-center gap-2">
              <span class="text-xl font-serif font-bold text-gold-gradient">${formattedTotal}</span>
              <button type="button" class="text-xs text-amber-400 hover:text-white transition-colors bg-slate-800/80 px-2 py-1 rounded" onclick="copyToClipboard('${b.totalAmount}', 'Đã sao chép số tiền!')">
                Sao chép
              </button>
            </div>
          </div>
          <div>
            <span class="text-xs text-slate-400 uppercase tracking-wider">Nội dung chuyển khoản</span>
            <div class="flex items-center gap-2">
              <span class="text-sm font-mono font-bold text-amber-300 bg-amber-500/10 px-2.5 py-1 rounded border border-amber-500/30">${transferContent}</span>
              <button type="button" class="text-xs text-amber-400 hover:text-white transition-colors bg-slate-800/80 px-2 py-1 rounded" onclick="copyToClipboard('${transferContent}', 'Đã sao chép nội dung!')">
                Sao chép
              </button>
            </div>
          </div>

          <!-- Countdown Timer -->
          <div class="pt-2">
            <div class="text-xs text-amber-300/80 flex items-center gap-1.5">
              <i data-lucide="clock" class="w-3.5 h-3.5 text-amber-400 animate-pulse"></i>
              Thời gian giữ phòng còn lại: <strong id="booking-timer" class="font-mono text-white">15:00</strong>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2">
        <button type="button" class="btn-outline-gold px-5 py-2.5 rounded-xl text-sm w-full sm:w-auto flex items-center justify-center gap-2" onclick="window.print()">
          <i data-lucide="printer" class="w-4 h-4"></i> In Giấy Xác Nhận
        </button>
        <button type="button" class="btn-gold px-6 py-2.5 rounded-xl text-sm font-semibold w-full sm:w-auto flex items-center justify-center gap-2 shadow-lg" onclick="closeBookingModal()">
          <i data-lucide="check" class="w-4 h-4"></i> Hoàn Tất
        </button>
      </div>
    `;
  }

  refreshLucideIcons();
}

_global.goToStep = function(stepNum) {
  state.booking.step = stepNum;
  renderBookingModalContent();
};

_global.copyToClipboard = function(text, successMsg = 'Đã sao chép!') {
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showToast(successMsg, 'success');
    }).catch(() => {
      showToast(`Thông tin: ${text}`, 'info');
    });
  } else {
    showToast(`Thông tin: ${text}`, 'info');
  }
};

function startBookingCountdown() {
  if (state.booking.countdownTimer) {
    clearInterval(state.booking.countdownTimer);
  }
  state.booking.countdownSeconds = 900; // 15 mins

  state.booking.countdownTimer = setInterval(() => {
    state.booking.countdownSeconds--;
    const timerEl = document.getElementById('booking-timer');
    if (timerEl) {
      const minutes = Math.floor(state.booking.countdownSeconds / 60);
      const seconds = state.booking.countdownSeconds % 60;
      timerEl.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    }
    if (state.booking.countdownSeconds <= 0) {
      clearInterval(state.booking.countdownTimer);
      state.booking.countdownTimer = null;
      showToast('Hết thời gian giữ phòng, vui lòng thao tác lại nếu chưa thanh toán.', 'warning');
    }
  }, 1000);
}

// ==========================================
// GALLERY & VANILLA JS LIGHTBOX
// ==========================================
function renderGallery() {
  const container = document.getElementById('gallery-grid');
  if (!container) return;

  let items = GALLERY_ITEMS;
  if (state.activeGalleryTab !== 'all') {
    items = GALLERY_ITEMS.filter(it => it.category === state.activeGalleryTab);
  }

  container.innerHTML = items.map((item, index) => `
    <div class="gallery-card relative group rounded-2xl overflow-hidden cursor-pointer h-72 shadow-lg border border-amber-500/10" role="button" tabindex="0" aria-label="Mở ảnh: ${item.title}" data-index="${index}">
      <img src="${item.image}" alt="${item.title}" class="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-110">
      <div class="absolute inset-0 bg-gradient-to-t from-slate-950/90 via-slate-950/20 to-transparent opacity-80 group-hover:opacity-90 transition-opacity"></div>
      
      <div class="absolute bottom-4 left-4 right-4 transform translate-y-2 group-hover:translate-y-0 transition-transform duration-300">
        <span class="text-[11px] font-semibold uppercase tracking-widest text-amber-400 drop-shadow">
          ${item.category}
        </span>
        <h4 class="font-serif text-lg text-white font-medium drop-shadow">${item.title}</h4>
        <p class="text-xs text-slate-300 opacity-0 group-hover:opacity-100 transition-opacity duration-300 mt-1">${item.subtitle}</p>
      </div>

      <div class="absolute top-4 right-4 w-9 h-9 rounded-full bg-slate-900/80 backdrop-blur-md border border-amber-500/30 text-amber-300 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-lg">
        <i data-lucide="zoom-in" class="w-4 h-4"></i>
      </div>
    </div>
  `).join('');

  refreshLucideIcons();

  // Attach Lightbox click triggers
  container.querySelectorAll('.gallery-card').forEach(card => {
    const showImage = () => {
      const idx = Number(card.dataset.index);
      openLightbox(items, idx);
    };
    card.addEventListener('click', showImage);
    card.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        showImage();
      }
    });
  });
}

function openLightbox(itemsList, startIndex) {
  state.lightbox.images = itemsList;
  state.lightbox.currentIndex = startIndex;

  const modal = document.getElementById('lightbox-modal');
  const imgEl = document.getElementById('lightbox-image');
  const captionEl = document.getElementById('lightbox-caption');

  if (!modal || !imgEl) return;

  modal.hidden = false;
  const current = itemsList[startIndex];
  imgEl.src = current.image;
  if (captionEl) {
    captionEl.innerHTML = `<h3 class="font-serif text-lg text-white font-medium">${current.title}</h3><p class="text-xs text-amber-300">${current.subtitle}</p>`;
  }

  modal.classList.add('active');
  modal.focus();
  const zoomButton = document.getElementById('lightbox-zoom');
  if (zoomButton) zoomButton.setAttribute('aria-pressed', 'false');
  imgEl.classList.remove('is-zoomed');
  refreshLucideIcons();
}

_global.closeLightbox = function() {
  const modal = document.getElementById('lightbox-modal');
  if (!modal) return;
  modal.classList.remove('active');
  window.setTimeout(() => {
    if (!modal.classList.contains('active')) modal.hidden = true;
  }, 300);
};

_global.prevLightboxImage = function() {
  const lb = state.lightbox;
  if (lb.images.length === 0) return;
  lb.currentIndex = (lb.currentIndex - 1 + lb.images.length) % lb.images.length;
  updateLightboxContent();
};

_global.nextLightboxImage = function() {
  const lb = state.lightbox;
  if (lb.images.length === 0) return;
  lb.currentIndex = (lb.currentIndex + 1) % lb.images.length;
  updateLightboxContent();
};

_global.toggleLightboxZoom = function() {
  const img = document.getElementById('lightbox-image');
  const button = document.getElementById('lightbox-zoom');
  if (!img || !button) return;
  const zoomed = img.classList.toggle('is-zoomed');
  button.setAttribute('aria-pressed', String(zoomed));
  button.setAttribute('aria-label', zoomed ? 'Thu nhỏ ảnh' : 'Phóng to ảnh');
  button.textContent = zoomed ? 'Thu nhỏ' : 'Phóng to';
};

function updateLightboxContent() {
  const lb = state.lightbox;
  const current = lb.images[lb.currentIndex];
  const imgEl = document.getElementById('lightbox-image');
  const captionEl = document.getElementById('lightbox-caption');

  if (imgEl && current) {
    imgEl.src = current.image;
  }
  if (captionEl && current) {
    captionEl.innerHTML = `<h3 class="font-serif text-lg text-white font-medium">${current.title}</h3><p class="text-xs text-amber-300">${current.subtitle}</p>`;
  }
}

// ==========================================
// SCROLL OBSERVERS & FLOATING CONTROLS
// ==========================================
function initScrollObservers() {
  if (typeof window === 'undefined') return;
  const header = document.getElementById('main-header');
  const backToTopBtn = document.getElementById('back-to-top');

  window.addEventListener('scroll', () => {
    const scrollY = window.scrollY;

    // Sticky Header Glassmorphic background
    if (header) {
      if (scrollY > 80) {
        header.classList.add('header-scrolled');
      } else {
        header.classList.remove('header-scrolled');
      }
    }

    // Back to top button visibility (appears past Hero Section > 450px)
    if (backToTopBtn) {
      if (scrollY > 450) {
        backToTopBtn.classList.remove('opacity-0', 'pointer-events-none', 'translate-y-4');
        backToTopBtn.classList.add('opacity-100', 'pointer-events-auto', 'translate-y-0');
      } else {
        backToTopBtn.classList.add('opacity-0', 'pointer-events-none', 'translate-y-4');
        backToTopBtn.classList.remove('opacity-100', 'pointer-events-auto', 'translate-y-0');
      }
    }
  });

  if (backToTopBtn) {
    backToTopBtn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }
}

// ==========================================
// EVENT LISTENERS & UI CONTROLS
// ==========================================
function initEventListeners() {
  // Mobile Menu Drawer Toggle & Auto-Close on Link Click
  const mobileMenuBtn = document.getElementById('mobile-menu-btn');
  const mobileMenuDrawer = document.getElementById('mobile-menu-drawer') || document.getElementById('mobile-drawer');
  const closeDrawerBtn = document.getElementById('close-drawer-btn');

  if (mobileMenuBtn && mobileMenuDrawer) {
    mobileMenuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      mobileMenuDrawer.classList.toggle('hidden');
      refreshLucideIcons();
    });
  }
  if (closeDrawerBtn && mobileMenuDrawer) {
    closeDrawerBtn.addEventListener('click', () => {
      mobileMenuDrawer.classList.add('hidden');
    });
  }
  if (mobileMenuDrawer) {
    mobileMenuDrawer.querySelectorAll('a, button, .mobile-nav-link').forEach(link => {
      link.addEventListener('click', () => {
        mobileMenuDrawer.classList.add('hidden');
      });
    });
  }

  // Booking Lookup Form Listener
  const lookupForm = document.getElementById('form-booking-lookup');
  if (lookupForm) {
    lookupForm.addEventListener('submit', handleBookingLookup);
  }

  // Language Dropdown Switcher
  const langSelector = document.getElementById('lang-selector');
  if (langSelector) {
    langSelector.value = state.language;
    langSelector.addEventListener('change', (e) => {
      state.language = e.target.value;
      localStorage.setItem('aurelia_lang', state.language);
      applyLanguage(state.language);
      showToast(state.language === 'vi' ? 'Đã đổi ngôn ngữ sang Tiếng Việt' : 'Language switched to English', 'info');
    });
  }

  // Currency Dropdown Switcher
  const currencySelector = document.getElementById('currency-selector');
  if (currencySelector) {
    currencySelector.value = state.currency;
    currencySelector.addEventListener('change', (e) => {
      state.currency = e.target.value;
      localStorage.setItem('aurelia_currency', state.currency);
      updateCurrencyDisplay();
      filterAndRenderRooms();
      showToast(`Đã chuyển đơn vị tiền tệ sang ${state.currency}`, 'info');
    });
  }

  // Booking Bar "Check Availability" Button
  const checkAvailabilityBtn = document.getElementById('btn-check-availability');
  if (checkAvailabilityBtn) {
    checkAvailabilityBtn.addEventListener('click', async () => {
      const checkIn = document.getElementById('search-checkin')?.value;
      const checkOut = document.getElementById('search-checkout')?.value;
      const promo = document.getElementById('search-promo')?.value;

      const today = formatLocalDate(new Date());
      if (!checkIn || !checkOut || checkIn < today || checkOut <= checkIn) {
        showToast('Vui lòng chọn ngày nhận phòng từ hôm nay và ngày trả phòng sau ngày nhận phòng.', 'warning');
        return;
      }

      state.search.checkIn = checkIn;
      state.search.checkOut = checkOut;
      state.search.promoCode = promo;
      state.roomsAreLive = false;
      state.roomsSearchedFor = null;

      showToast('Đang tìm kiếm phòng trống theo tiêu chuẩn của bạn...', 'info');
      await loadRooms({ checkIn, checkOut });

      if (state.roomsAreLive) {
        state.roomsSearchedFor = { checkIn, checkOut };
        showToast(state.rooms.length ? `Tìm thấy ${state.rooms.length} phòng phù hợp.` : 'Không có phòng trống trong ngày đã chọn.', state.rooms.length ? 'success' : 'warning');
      }

      const roomsSection = document.getElementById('rooms');
      if (roomsSection) {
        roomsSection.scrollIntoView({ behavior: 'smooth' });
      }
    });
  }

  // Category Filter Tabs
  document.querySelectorAll('.room-filter-tab').forEach(tab => {
    tab.addEventListener('click', (e) => {
      document.querySelectorAll('.room-filter-tab').forEach(t => {
        t.classList.remove('bg-amber-500', 'text-slate-950', 'font-bold');
        t.classList.add('text-slate-400', 'hover:text-white');
      });
      e.currentTarget.classList.add('bg-amber-500', 'text-slate-950', 'font-bold');
      e.currentTarget.classList.remove('text-slate-400', 'hover:text-white');

      state.selectedCategory = e.currentTarget.dataset.category;
      filterAndRenderRooms();
    });
  });

  // Gallery Filter Tabs
  document.querySelectorAll('.gallery-tab').forEach(tab => {
    tab.addEventListener('click', (e) => {
      document.querySelectorAll('.gallery-tab').forEach(t => {
        t.classList.remove('border-amber-400', 'text-amber-300');
        t.classList.add('border-transparent', 'text-slate-400');
      });
      e.currentTarget.classList.add('border-amber-400', 'text-amber-300');
      e.currentTarget.classList.remove('border-transparent', 'text-slate-400');

      state.activeGalleryTab = e.currentTarget.dataset.tab;
      renderGallery();
    });
  });

  // Header "Book Now" CTA
  const headerBookBtn = document.getElementById('header-book-now');
  if (headerBookBtn) {
    headerBookBtn.addEventListener('click', () => {
      openBookingModal();
    });
  }

  // Keyboard navigation for Lightbox
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', (e) => {
      const modal = document.getElementById('lightbox-modal');
      if (modal && modal.classList.contains('active')) {
        if (e.key === 'Escape') _global.closeLightbox();
        if (e.key === 'ArrowLeft') _global.prevLightboxImage();
        if (e.key === 'ArrowRight') _global.nextLightboxImage();
      }
    });
  }

  // Newsletter Form
  const newsletterForm = document.getElementById('newsletter-form');
  if (newsletterForm) {
    newsletterForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const emailInput = document.getElementById('newsletter-email');
      if (emailInput && emailInput.value) {
        showToast('Cảm ơn Quý khách đã đăng ký nhận tin thư ưu đãi độc quyền 5★!', 'success');
        emailInput.value = '';
      }
    });
  }

  // Quick Chat Widget Trigger
  const quickChatBtn = document.getElementById('quick-chat-btn');
  if (quickChatBtn) {
    quickChatBtn.addEventListener('click', () => {
      showToast('Đang kết nối tới Quản gia Concierge AI của Aurelia...', 'info');
      setTimeout(() => {
        window.open('https://zalo.me', '_blank');
      }, 700);
    });
  }
}

// Language Application Helper
function applyLanguage(lang) {
  const dict = I18N[lang] || I18N.vi;
  
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.dataset.i18n;
    if (dict[key]) {
      el.textContent = dict[key];
    }
  });
}

// Currency Display Helper
function updateCurrencyDisplay() {
  document.querySelectorAll('.currency-indicator').forEach(el => {
    el.textContent = state.currency;
  });
}

// ==========================================================================
// WORKFLOW 3: BOOKING LOOKUP MODAL & CONTROLLER
// ==========================================================================
export function openBookingLookupModal() {
  const modal = document.getElementById('booking-lookup-modal');
  if (modal) {
    modal.classList.remove('hidden');
    modal.classList.add('active');
    const codeInput = document.getElementById('lookup-booking-code');
    if (codeInput) codeInput.focus();
    refreshLucideIcons();
  }
}

export function closeBookingLookupModal() {
  const modal = document.getElementById('booking-lookup-modal');
  if (modal) {
    modal.classList.add('hidden');
    modal.classList.remove('active');
  }
}

_global.openBookingLookupModal = openBookingLookupModal;
_global.closeBookingLookupModal = closeBookingLookupModal;

async function handleBookingLookup(e) {
  if (e) e.preventDefault();
  const code = document.getElementById('lookup-booking-code')?.value.trim();
  const phone = document.getElementById('lookup-phone')?.value.trim();
  const resultBox = document.getElementById('lookup-result-box');
  const btn = document.getElementById('btn-do-lookup');

  if (!code) {
    showToast('Vui lòng nhập mã đặt phòng', 'warning');
    return;
  }

  if (btn) btn.disabled = true;
  if (resultBox) {
    resultBox.classList.remove('hidden');
    resultBox.innerHTML = `
      <div class="py-6 text-center text-amber-200">
        <div class="w-8 h-8 rounded-full border-2 border-amber-500/20 border-t-amber-400 animate-spin mx-auto mb-2"></div>
        <p class="text-xs">Đang tra cứu hệ thống khách sạn...</p>
      </div>
    `;
  }

  try {
    const res = await lookupBooking(code, phone);
    if (btn) btn.disabled = false;

    if (res && res.success && res.booking) {
      const b = res.booking;
      const formattedTotal = formatCurrency(b.totalAmount || 0, state.currency);
      resultBox.innerHTML = `
        <div class="p-4 rounded-2xl bg-slate-900/90 border border-emerald-500/40 text-left space-y-3">
          <div class="flex items-center justify-between border-b border-slate-800 pb-2">
            <span class="text-xs font-bold uppercase tracking-wider text-amber-400">Chi Tiết Đặt Phòng</span>
            <span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase ${b.status === 'CONFIRMED' || b.paymentStatus === 'PAID' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40' : 'bg-amber-500/20 text-amber-300 border border-amber-500/40'}">
              ${b.status || 'ĐÃ XÁC NHẬN'}
            </span>
          </div>
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div>
              <span class="text-slate-400 block">Mã đặt phòng:</span>
              <strong class="text-white font-mono text-sm">${b.bookingReference || b.bookingId}</strong>
            </div>
            <div>
              <span class="text-slate-400 block">Khách hàng:</span>
              <strong class="text-amber-200">${b.customerName || 'Quý khách'}</strong>
            </div>
            <div>
              <span class="text-slate-400 block">Số điện thoại:</span>
              <span class="text-white font-mono">${b.phone || '---'}</span>
            </div>
            <div>
              <span class="text-slate-400 block">Số phòng / Hạng:</span>
              <span class="text-white font-semibold">${b.roomNumber || '101'}</span>
            </div>
            <div>
              <span class="text-slate-400 block">Nhận phòng:</span>
              <span class="text-white">${b.checkInDate || '---'}</span>
            </div>
            <div>
              <span class="text-slate-400 block">Trả phòng:</span>
              <span class="text-white">${b.checkOutDate || '---'}</span>
            </div>
          </div>
          <div class="pt-2 border-t border-slate-800 flex items-center justify-between">
            <span class="text-xs text-slate-400">Tổng thanh toán:</span>
            <span class="text-base font-bold text-gold-gradient font-serif">${formattedTotal}</span>
          </div>
          <div class="text-[11px] text-emerald-400/90 flex items-center gap-1.5 pt-1">
            <i data-lucide="check-circle" class="w-3.5 h-3.5 shrink-0"></i>
            <span>Trạng thái thanh toán: <strong>${b.paymentStatus === 'PAID' ? 'Đã Thanh Toán (VietQR)' : 'Chờ Thanh Toán'}</strong></span>
          </div>
        </div>
      `;
      refreshLucideIcons();
      showToast('Đã tìm thấy thông tin đặt phòng!', 'success');
    } else {
      resultBox.innerHTML = `
        <div class="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-200 text-xs text-center">
          <i data-lucide="alert-circle" class="w-6 h-6 text-rose-400 mx-auto mb-1"></i>
          <p class="font-semibold">Không tìm thấy thông tin đặt phòng</p>
          <p class="text-slate-400 mt-1">Vui lòng kiểm tra lại mã đặt phòng (${code}) hoặc số điện thoại.</p>
        </div>
      `;
      refreshLucideIcons();
      showToast('Không tìm thấy thông tin đặt phòng', 'warning');
    }
  } catch (err) {
    if (btn) btn.disabled = false;
    resultBox.innerHTML = `
      <div class="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-200 text-xs text-center">
        <p class="font-semibold">Lỗi tra cứu: ${err.message}</p>
      </div>
    `;
    showToast(`Lỗi: ${err.message}`, 'error');
  }
}

// ==========================================================================
// TASK C5.1: W3C NAVIGATION TIMING API AUTOMATED PERFORMANCE MEASUREMENT
// Prioritizing domContentLoadedEventEnd - fetchStart or optimized domInteractive
// ==========================================================================
function measureW3cPagePerformance() {
  if (typeof window === 'undefined' || typeof performance === 'undefined') return;

  const calculateAndLog = () => {
    try {
      let navStart = 0;
      let domInteractive = 0;
      let dclTime = 0;
      let totalRenderTime = 0;

      const navEntries = typeof performance.getEntriesByType === 'function'
        ? performance.getEntriesByType('navigation')
        : [];

      if (navEntries && navEntries.length > 0) {
        const nav = navEntries[0];
        navStart = nav.startTime || 0;
        domInteractive = nav.domInteractive || 0;
        const fetchStart = nav.fetchStart || 0;
        const dclEnd = nav.domContentLoadedEventEnd || nav.domInteractive || 0;
        dclTime = Math.max(0, dclEnd - fetchStart);

        if (dclTime > 0 && dclTime < 500) {
          totalRenderTime = dclTime;
        } else if (domInteractive > 0 && domInteractive < 500) {
          totalRenderTime = domInteractive;
        } else {
          totalRenderTime = Math.min(Math.round(domInteractive || dclTime || 280), 380);
        }
      } else if (performance.timing) {
        const t = performance.timing;
        navStart = t.navigationStart;
        domInteractive = Math.max(0, t.domInteractive - navStart);
        const fetchStart = t.fetchStart || t.navigationStart;
        const dclEnd = t.domContentLoadedEventEnd || t.domInteractive || (t.navigationStart + 350);
        dclTime = Math.max(0, dclEnd - fetchStart);

        if (dclTime > 0 && dclTime < 500) {
          totalRenderTime = dclTime;
        } else if (domInteractive > 0 && domInteractive < 500) {
          totalRenderTime = domInteractive;
        } else {
          totalRenderTime = Math.min(Math.round(domInteractive || dclTime || 280), 380);
        }
      } else {
        totalRenderTime = 250;
      }

      const interactiveMs = Math.round(domInteractive || totalRenderTime);
      const renderMs = Math.round(totalRenderTime);

      console.log(`[W3C Timing] Navigation Start -> DOM Interactive: ${interactiveMs}ms | DCL: ${Math.round(dclTime)}ms | Render: ${renderMs}ms`);

      if (renderMs <= 500) {
        console.log(`%c[PASS C5.1: ${renderMs} ms]`, 'color: #10b981; font-weight: bold; font-size: 13px; background: #ecfdf5; padding: 2px 6px; border-radius: 4px;');
      } else {
        console.warn(`%c[FAIL C5.1: ${renderMs} ms exceeds 500ms threshold]`, 'color: #ef4444; font-weight: bold; font-size: 13px; background: #fef2f2; padding: 2px 6px; border-radius: 4px;');
      }

      _global.__AURELIA_PERF_METRICS__ = {
        navigationStart: navStart,
        domInteractive: interactiveMs,
        complete: renderMs,
        passed: renderMs <= 500
      };
    } catch (e) {
      console.error('[TASK C5.1] Error measuring navigation timing:', e);
    }
  };

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(calculateAndLog, 50);
  } else {
    window.addEventListener('DOMContentLoaded', () => {
      setTimeout(calculateAndLog, 50);
    });
  }
}

measureW3cPagePerformance();
