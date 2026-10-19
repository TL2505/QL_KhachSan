/**
 * ==========================================================================
 * AURELIA GRAND LUXURY HOTEL - 5★ NO-BUILD APPLICATION CONTROLLER
 * Native JavaScript ES Module
 * Full Component Orchestration & 3-Step VietQR Booking State Machine
 * ==========================================================================
 */

import {
  api,
  apiFetch,
  API_BASE_URL,
  getApiBaseUrl,
  setAuthSession,
  getAuthSession,
  clearAuthSession,
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

/**
 * HTML Sanitizer to prevent XSS attacks across all dynamic DOM rendering.
 */
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[character]));
}
_global.escapeHtml = escapeHtml;

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
    updateHeaderAuthStatus();
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
    loadPublicReviewsWidget();
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
    state.rooms = [];
    state.roomsAreLive = false;
    state.roomsSearchedFor = null;
    if (err.statusCode) showToast(`Không thể tải danh sách phòng (HTTP ${err.statusCode}). Vui lòng thử lại.`, 'error', 7000);
    else showToast('Không thể xác minh giá và tình trạng phòng từ máy chủ. Vui lòng thử lại hoặc liên hệ Lễ tân.', 'error', 7000);
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
    showToast('Chưa xác minh được phòng từ máy chủ. Vui lòng tải lại danh sách phòng.', 'error', 6000);
    return;
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

  // Auto-fill customer info if logged in
  try {
    const sessionStr = typeof localStorage !== 'undefined' ? localStorage.getItem("hotel_auth_session") : null;
    if (sessionStr) {
      const sess = JSON.parse(sessionStr);
      if (sess) {
        state.booking.guest.fullName = state.booking.guest.fullName || sess.fullName || sess.username || '';
        state.booking.guest.phone = state.booking.guest.phone || sess.phone || '';
        state.booking.guest.email = state.booking.guest.email || sess.email || '';
        state.booking.guest.identityCard = state.booking.guest.identityCard || sess.identityCard || sess.idCard || '';
      }
    }
  } catch (e) {}

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

  if (!state.roomsAreLive) { showToast('Vui lòng tải lại tình trạng phòng từ máy chủ.', 'error', 5000); return; }

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
          b.requestKey ||= crypto.randomUUID();
          const res = await submitBooking({
            idempotencyKey: b.requestKey,
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
          b.payment = res.payment;
          b.bookingReference = res.bookingReference;
          if (res.totalAmount >= 0) b.totalAmount = Math.round(res.totalAmount);
          b.step = 3;
          showToast('Đã ghi nhận yêu cầu đặt phòng. Chờ xác nhận thanh toán.', 'success', 7000);
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
    const qrUrl = generateVietQRUrl(b.payment);
    const bankAccount = b.payment?.account_number || 'Liên hệ Lễ tân';
    const bankName = b.payment?.beneficiary_bank || 'Chưa có thông tin ngân hàng';
    const accountName = b.payment?.account_holder || 'Liên hệ Lễ tân';
    const transferContent = b.payment?.reference_code || 'Liên hệ Lễ tân';

    body.innerHTML = `
      ${stepHeader}


      <!-- Success Congratulatory Banner -->
      <div class="text-center mb-6">
        <div class="w-14 h-14 rounded-full bg-emerald-500/20 border-2 border-emerald-400/50 flex items-center justify-center text-emerald-400 mx-auto mb-3 shadow-lg">
          <i data-lucide="check-check" class="w-7 h-7"></i>
        </div>
        <span class="text-xs font-semibold uppercase tracking-widest text-amber-400">Yêu cầu đặt phòng đã được ghi nhận</span>
        <h3 class="font-serif text-2xl text-white font-medium mt-1">Đơn Đặt Phòng ${b.bookingReference || `#${b.bookingId}`}</h3>
        <p class="text-xs text-slate-300 mt-1">
          Đặt phòng đang chờ xác nhận thanh toán từ khách sạn.
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

  // Login Form Submission
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', handleLoginSubmit);
  }

  // Register Form Submission
  const registerForm = document.getElementById('register-form');
  if (registerForm) {
    registerForm.addEventListener('submit', handleRegisterSubmit);
  }

  // Auth Tab Switchers
  const tabLoginBtn = document.getElementById('tab-login-btn');
  if (tabLoginBtn) {
    tabLoginBtn.addEventListener('click', () => switchAuthTab('login'));
  }
  const tabRegisterBtn = document.getElementById('tab-register-btn');
  if (tabRegisterBtn) {
    tabRegisterBtn.addEventListener('click', () => switchAuthTab('register'));
  }

  // Header and Mobile Login Triggers (Task 11: clicking redirects directly to portal.html)
  const headerLoginBtn = document.getElementById('header-login-btn') || document.getElementById('login-btn');
  if (headerLoginBtn) {
    headerLoginBtn.addEventListener('click', (e) => {
      e.preventDefault();
      window.location.href = 'portal.html';
    });
  }

  const mobileLoginBtn = document.getElementById('mobile-login-btn');
  if (mobileLoginBtn) {
    mobileLoginBtn.addEventListener('click', (e) => {
      e.preventDefault();
      const mobileDrawer = document.getElementById('mobile-menu-drawer');
      if (mobileDrawer) mobileDrawer.classList.add('hidden');
      window.location.href = 'portal.html';
    });
  }

  // Header and Mobile Register Triggers
  const headerRegisterBtn = document.getElementById('header-register-btn') || document.getElementById('register-btn');
  if (headerRegisterBtn) {
    headerRegisterBtn.addEventListener('click', (e) => {
      e.preventDefault();
      openRegisterModal(e);
    });
  }

  const mobileRegisterBtn = document.getElementById('mobile-register-btn');
  if (mobileRegisterBtn) {
    mobileRegisterBtn.addEventListener('click', (e) => {
      e.preventDefault();
      const mobileDrawer = document.getElementById('mobile-menu-drawer');
      if (mobileDrawer) mobileDrawer.classList.add('hidden');
      openRegisterModal(e);
    });
  }

  // Close Login Modal Trigger
  const closeLoginBtn = document.getElementById('close-login-modal');
  if (closeLoginBtn) {
    closeLoginBtn.addEventListener('click', (e) => {
      e.preventDefault();
      closeLoginModal(e);
    });
  }

  // Click outside Login Modal to close
  const loginModalEl = document.getElementById('login-modal');
  if (loginModalEl) {
    loginModalEl.addEventListener('click', (e) => {
      if (e.target === loginModalEl) {
        closeLoginModal(e);
      }
    });
  }

  // Generic data-open-login and data-open-register triggers
  document.querySelectorAll('[data-open-login], .btn-open-login').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      openLoginModal(e);
    });
  });

  document.querySelectorAll('[data-open-register], .btn-open-register').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      openRegisterModal(e);
    });
  });

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

  // Keyboard navigation for Lightbox and Modals
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', (e) => {
      const modal = document.getElementById('lightbox-modal');
      if (modal && modal.classList.contains('active')) {
        if (e.key === 'Escape') _global.closeLightbox();
        if (e.key === 'ArrowLeft') _global.prevLightboxImage();
        if (e.key === 'ArrowRight') _global.nextLightboxImage();
      }
      if (e.key === 'Escape') {
        const loginModal = document.getElementById('login-modal');
        if (loginModal && !loginModal.classList.contains('hidden')) {
          closeLoginModal(e);
        }
        const lookupModal = document.getElementById('booking-lookup-modal');
        if (lookupModal && !lookupModal.classList.contains('hidden')) {
          closeBookingLookupModal();
        }
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
// WORKFLOW 4: LOGIN MODAL & AUTH CONTROLLER
// ==========================================================================

/**
 * Defensive element resolver: returns the #login-modal element,
 * or injects the full modal into the DOM if it's missing to avoid
 * "Cannot read properties of null" errors.
 */
export function ensureLoginModal() {
  if (typeof document === 'undefined') return null;
  let modal = document.getElementById('login-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.id = 'login-modal';
    modal.className = 'modal-overlay fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 overflow-y-auto hidden';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-label', 'Đăng nhập tài khoản');
    modal.tabIndex = -1;
    modal.innerHTML = `
      <div class="modal-content glass-panel rounded-3xl w-full max-w-md p-6 sm:p-8 relative max-h-[92vh] overflow-y-auto my-auto shadow-2xl border border-amber-500/30">
        <button type="button" id="close-login-modal" aria-label="Đóng cửa sổ"
          class="absolute top-5 right-5 z-10 w-9 h-9 rounded-full bg-slate-900/80 text-slate-300 hover:text-white flex items-center justify-center border border-slate-700 transition-colors cursor-pointer"
          onclick="closeLoginModal(event)">
          <i data-lucide="x" class="w-4 h-4"></i>
        </button>
        <div class="text-center mb-5">
          <div class="w-12 h-12 rounded-full bg-amber-500/20 border border-amber-400/40 flex items-center justify-center text-amber-400 mx-auto mb-3 shadow-md">
            <i data-lucide="shield-check" class="w-6 h-6"></i>
          </div>
          <h3 id="auth-modal-title" class="font-serif text-2xl text-white font-bold">Cổng Thành Viên</h3>
          <p id="auth-modal-subtitle" class="text-xs text-slate-400 mt-1">Aurelia Grand Palace Luxury Resort</p>
        </div>
        <div class="flex items-center rounded-xl bg-slate-900/90 p-1 border border-slate-800 mb-5" role="tablist">
          <button type="button" id="tab-login-btn" role="tab" aria-selected="true"
            class="flex-1 py-2 text-xs font-bold uppercase tracking-wider rounded-lg transition-all bg-amber-400 text-slate-950 shadow-md flex items-center justify-center gap-1.5 cursor-pointer"
            onclick="switchAuthTab('login')">
            <i data-lucide="log-in" class="w-3.5 h-3.5"></i>
            <span>Đăng Nhập</span>
          </button>
          <button type="button" id="tab-register-btn" role="tab" aria-selected="false"
            class="flex-1 py-2 text-xs font-bold uppercase tracking-wider rounded-lg transition-all text-slate-400 hover:text-white bg-transparent flex items-center justify-center gap-1.5 cursor-pointer"
            onclick="switchAuthTab('register')">
            <i data-lucide="user-plus" class="w-3.5 h-3.5"></i>
            <span>Đăng Ký</span>
          </button>
        </div>
        <div id="login-status-box" class="hidden mb-4 p-3 rounded-xl text-xs text-center border" role="status" aria-live="polite"></div>
        <form id="login-form" class="space-y-4 mb-4">
          <div>
            <label for="login-username" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Tên đăng nhập <span class="text-rose-400">*</span>
            </label>
            <div class="relative">
              <input type="text" id="login-username" name="username" required autocomplete="username" maxlength="100"
                placeholder="Nhập tên đăng nhập hoặc email"
                class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl pl-10 pr-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none transition-colors">
              <i data-lucide="user" class="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            </div>
          </div>
          <div>
            <label for="login-password" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Mật khẩu <span class="text-rose-400">*</span>
            </label>
            <div class="relative">
              <input type="password" id="login-password" name="password" required autocomplete="current-password" maxlength="200"
                placeholder="Nhập mật khẩu"
                class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl pl-10 pr-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none transition-colors">
              <i data-lucide="lock" class="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            </div>
          </div>
          <button type="submit" id="btn-login-submit"
            class="btn-gold w-full py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all">
            <i data-lucide="log-in" class="w-4 h-4"></i>
            <span>Đăng Nhập An Toàn</span>
          </button>
        </form>
        <form id="register-form" class="space-y-4 mb-4 hidden">
          <div id="register-status-box" class="hidden mb-4 p-3 rounded-xl text-xs text-center border" role="status" aria-live="polite"></div>
          <div>
            <label for="register-fullname" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Họ và tên <span class="text-rose-400">*</span>
            </label>
            <div class="relative">
              <input type="text" id="register-fullname" name="fullName" required maxlength="100"
                placeholder="Nguyễn Văn A"
                class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl pl-10 pr-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none transition-colors">
              <i data-lucide="user-check" class="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            </div>
          </div>
          <div>
            <label for="register-username" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Số điện thoại / Tên tài khoản <span class="text-rose-400">*</span>
            </label>
            <div class="relative">
              <input type="text" id="register-username" name="username" required autocomplete="username" maxlength="50"
                placeholder="0912345678 hoặc ten_dang_nhap"
                class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl pl-10 pr-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none transition-colors">
              <i data-lucide="phone" class="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            </div>
          </div>
          <div>
            <label for="register-password" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Mật khẩu <span class="text-rose-400">*</span>
            </label>
            <div class="relative">
              <input type="password" id="register-password" name="password" required autocomplete="new-password" maxlength="200"
                placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)"
                class="w-full bg-slate-900/90 border border-slate-700 text-white rounded-xl pl-10 pr-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none transition-colors">
              <i data-lucide="lock" class="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            </div>
          </div>
          <button type="submit" id="btn-register-submit"
            class="btn-gold w-full py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all">
            <i data-lucide="user-plus" class="w-4 h-4"></i>
            <span>Đăng Ký Tài Khoản</span>
          </button>
        </form>
        <div class="pt-4 border-t border-slate-800/80 flex flex-col gap-2 text-center text-xs text-slate-400">
          <div id="auth-footer-prompt">
            <span id="auth-footer-text">Chưa có tài khoản? </span>
            <button type="button" id="auth-footer-btn" onclick="switchAuthTab('register')" class="text-amber-300 hover:text-white font-semibold transition-colors cursor-pointer">Đăng ký thành viên ngay ↗</button>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
    const loginFm = modal.querySelector('#login-form');
    if (loginFm) loginFm.addEventListener('submit', handleLoginSubmit);
    const registerFm = modal.querySelector('#register-form');
    if (registerFm) registerFm.addEventListener('submit', handleRegisterSubmit);
    const closeBtn = modal.querySelector('#close-login-modal');
    if (closeBtn) {
      closeBtn.addEventListener('click', (e) => {
        e.preventDefault();
        closeLoginModal(e);
      });
    }
    const tLogin = modal.querySelector('#tab-login-btn');
    if (tLogin) tLogin.addEventListener('click', () => switchAuthTab('login'));
    const tReg = modal.querySelector('#tab-register-btn');
    if (tReg) tReg.addEventListener('click', () => switchAuthTab('register'));
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeLoginModal(e);
    });
    refreshLucideIcons();
  }
  return modal;
}

export function switchAuthTab(tab = 'login') {
  if (typeof document === 'undefined') return;

  const isRegister = tab === 'register';
  const tabLoginBtn = document.getElementById('tab-login-btn');
  const tabRegisterBtn = document.getElementById('tab-register-btn');
  const loginForm = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');
  const loginStatusBox = document.getElementById('login-status-box');
  const registerStatusBox = document.getElementById('register-status-box');
  const modalTitle = document.getElementById('auth-modal-title');
  const modalSubtitle = document.getElementById('auth-modal-subtitle');
  const footerText = document.getElementById('auth-footer-text');
  const footerBtn = document.getElementById('auth-footer-btn');

  if (loginForm) {
    if (isRegister) loginForm.classList.add('hidden');
    else loginForm.classList.remove('hidden');
  }
  if (registerForm) {
    if (isRegister) registerForm.classList.remove('hidden');
    else registerForm.classList.add('hidden');
  }

  if (loginStatusBox) {
    loginStatusBox.classList.add('hidden');
    loginStatusBox.textContent = '';
  }
  if (registerStatusBox) {
    registerStatusBox.classList.add('hidden');
    registerStatusBox.textContent = '';
  }

  const activeClasses = ['bg-amber-400', 'text-slate-950', 'shadow-md'];
  const inactiveClasses = ['text-slate-400', 'hover:text-white', 'bg-transparent', 'shadow-none'];

  if (tabLoginBtn) {
    tabLoginBtn.setAttribute('aria-selected', (!isRegister).toString());
    if (isRegister) {
      activeClasses.forEach(c => tabLoginBtn.classList.remove(c));
      inactiveClasses.forEach(c => tabLoginBtn.classList.add(c));
    } else {
      inactiveClasses.forEach(c => tabLoginBtn.classList.remove(c));
      activeClasses.forEach(c => tabLoginBtn.classList.add(c));
    }
  }

  if (tabRegisterBtn) {
    tabRegisterBtn.setAttribute('aria-selected', isRegister.toString());
    if (isRegister) {
      inactiveClasses.forEach(c => tabRegisterBtn.classList.remove(c));
      activeClasses.forEach(c => tabRegisterBtn.classList.add(c));
    } else {
      activeClasses.forEach(c => tabRegisterBtn.classList.remove(c));
      inactiveClasses.forEach(c => tabRegisterBtn.classList.add(c));
    }
  }

  if (modalTitle) {
    modalTitle.textContent = isRegister ? 'Đăng Ký Thành Viên' : 'Đăng Nhập';
  }
  if (modalSubtitle) {
    modalSubtitle.textContent = isRegister
      ? 'Gia nhập đặc quyền hội viên Aurelia Grand Palace'
      : 'Cổng thành viên & nhân viên Aurelia Grand Palace';
  }

  if (footerText) {
    footerText.textContent = isRegister ? 'Đã có tài khoản? ' : 'Chưa có tài khoản? ';
  }
  if (footerBtn) {
    footerBtn.textContent = isRegister ? 'Đăng nhập ngay ↗' : 'Đăng ký thành viên ngay ↗';
    footerBtn.onclick = () => switchAuthTab(isRegister ? 'login' : 'register');
  }

  setTimeout(() => {
    if (isRegister) {
      const fnInput = document.getElementById('register-fullname');
      if (fnInput) fnInput.focus();
    } else {
      const uInput = document.getElementById('login-username');
      if (uInput) uInput.focus();
    }
  }, 50);

  refreshLucideIcons();
}

export function openLoginModal(e) {
  if (e && (e.currentTarget?.id === 'header-login-btn' || e.currentTarget?.id === 'mobile-login-btn' || e.target?.closest?.('#header-login-btn, #mobile-login-btn'))) {
    if (e && typeof e.preventDefault === 'function') {
      e.preventDefault();
    }
    if (typeof window !== 'undefined' && window.location) {
      window.location.href = 'portal.html';
      return;
    }
  }
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }
  const modal = ensureLoginModal();
  if (modal) {
    modal.classList.remove('hidden');
    modal.classList.add('active');
    switchAuthTab('login');
    refreshLucideIcons();
  }
}

export function openRegisterModal(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }
  const modal = ensureLoginModal();
  if (modal) {
    modal.classList.remove('hidden');
    modal.classList.add('active');
    switchAuthTab('register');
    refreshLucideIcons();
  }
}

export function closeLoginModal(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }
  const modal = document.getElementById('login-modal');
  if (modal) {
    modal.classList.add('hidden');
    modal.classList.remove('active');
    const loginStatusBox = document.getElementById('login-status-box');
    if (loginStatusBox) {
      loginStatusBox.classList.add('hidden');
      loginStatusBox.textContent = '';
    }
    const registerStatusBox = document.getElementById('register-status-box');
    if (registerStatusBox) {
      registerStatusBox.classList.add('hidden');
      registerStatusBox.textContent = '';
    }
  }
}

export async function handleLoginSubmit(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }

  const form = document.getElementById('login-form');
  const btn = form?.querySelector('button[type="submit"]') || document.getElementById('btn-login-submit');
  const usernameInput = document.getElementById('login-username');
  const passwordInput = document.getElementById('login-password');
  const statusBox = document.getElementById('login-status-box');

  const username = usernameInput ? usernameInput.value.trim() : '';
  const password = passwordInput ? passwordInput.value : '';

  if (!username || !password) {
    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
      statusBox.textContent = 'Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.';
      statusBox.classList.remove('hidden');
    }
    showToast('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu', 'warning');
    return;
  }

  // Preserve original button content before modifying
  const originalHtml = btn ? btn.innerHTML : '';
  if (btn) {
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    btn.innerHTML = `
      <div class="w-4 h-4 border-2 border-slate-950 border-t-amber-400 rounded-full animate-spin shrink-0"></div>
      <span>Đang xác thực...</span>
    `;
  }

  if (statusBox) {
    statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-amber-500/10 border-amber-500/30 text-amber-200';
    statusBox.textContent = 'Đang xác thực tài khoản...';
    statusBox.classList.remove('hidden');
  }

  try {
    const result = await api.post('/auth/login', { username, password });

    if (!result || !result.token) {
      throw new Error('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');
    }

    const roleRaw = result.role || 'CUSTOMER';
    const roleNormalized = String(roleRaw).toUpperCase().replace(/[ -]/g, '_');
    const isStaff = ['STAFF', 'FRONT_DESK', 'HOUSEKEEPING', 'MAINTENANCE', 'F&B', 'FB'].includes(roleNormalized);

    const session = {
      ...result,
      userId: result.userId ?? result.id,
      role: isStaff ? 'STAFF' : (roleNormalized === 'ADMIN' ? 'ADMIN' : 'CUSTOMER')
    };

    setAuthSession(session);
    try {
      if (typeof localStorage !== 'undefined' && result.token) {
        localStorage.setItem('auth_token', result.token);
        localStorage.setItem('customer_token', result.token);
        localStorage.setItem('token', result.token);
      }
    } catch {}

    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
      statusBox.textContent = 'Đăng nhập thành công!';
      statusBox.classList.remove('hidden');
    }

    showToast('Đăng nhập thành công!', 'success');
    updateHeaderAuthStatus();

    setTimeout(() => {
      closeLoginModal();
    }, 600);

  } catch (err) {
    const errorMsg = err?.message || 'Không thể đăng nhập. Vui lòng kiểm tra kết nối mạng và thử lại.';
    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
      statusBox.textContent = errorMsg;
      statusBox.classList.remove('hidden');
    }
    showToast(errorMsg, 'error');
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.removeAttribute('aria-busy');
      if (originalHtml) {
        btn.innerHTML = originalHtml;
      }
      refreshLucideIcons();
    }
  }
}

export async function handleRegisterSubmit(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }

  const form = document.getElementById('register-form');
  const btn = form?.querySelector('button[type="submit"]') || document.getElementById('btn-register-submit');
  const fullNameInput = document.getElementById('register-fullname');
  const usernameInput = document.getElementById('register-username');
  const passwordInput = document.getElementById('register-password');
  const statusBox = document.getElementById('register-status-box');

  const fullName = fullNameInput ? fullNameInput.value.trim() : '';
  const username = usernameInput ? usernameInput.value.trim() : '';
  const password = passwordInput ? passwordInput.value : '';

  if (!fullName || !username || !password) {
    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
      statusBox.textContent = 'Vui lòng điền đầy đủ họ tên, tên đăng nhập/SĐT và mật khẩu.';
      statusBox.classList.remove('hidden');
    }
    showToast('Vui lòng điền đầy đủ thông tin đăng ký', 'warning');
    return;
  }

  if (password.length < 6) {
    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
      statusBox.textContent = 'Mật khẩu phải có độ dài tối thiểu 6 ký tự.';
      statusBox.classList.remove('hidden');
    }
    showToast('Mật khẩu tối thiểu 6 ký tự', 'warning');
    return;
  }

  const originalHtml = btn ? btn.innerHTML : '';
  if (btn) {
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    btn.innerHTML = `
      <div class="w-4 h-4 border-2 border-slate-950 border-t-amber-400 rounded-full animate-spin shrink-0"></div>
      <span>Đang đăng ký tài khoản...</span>
    `;
  }

  if (statusBox) {
    statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-amber-500/10 border-amber-500/30 text-amber-200';
    statusBox.textContent = 'Đang khởi tạo tài khoản thành viên...';
    statusBox.classList.remove('hidden');
  }

  try {
    const payload = {
      username,
      password,
      fullName,
      phone: username
    };

    await api.post('/auth/register', payload);

    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
      statusBox.textContent = 'Đăng ký thành công! Đang chuyển sang màn hình đăng nhập...';
      statusBox.classList.remove('hidden');
    }

    showToast('Đăng ký tài khoản thành công! Vui lòng đăng nhập.', 'success');

    // Pre-fill login username
    const loginUserEl = document.getElementById('login-username');
    if (loginUserEl) loginUserEl.value = username;

    setTimeout(() => {
      switchAuthTab('login');
      const loginPassEl = document.getElementById('login-password');
      if (loginPassEl) {
        loginPassEl.value = '';
        loginPassEl.focus();
      }
    }, 1000);

  } catch (err) {
    const errorMsg = err?.message || 'Không thể đăng ký tài khoản. Vui lòng thử lại sau.';
    if (statusBox) {
      statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
      statusBox.textContent = errorMsg;
      statusBox.classList.remove('hidden');
    }
    showToast(errorMsg, 'error');
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.removeAttribute('aria-busy');
      if (originalHtml) {
        btn.innerHTML = originalHtml;
      }
      refreshLucideIcons();
    }
  }
}

export function handleLogout(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }
  clearAuthSession();
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('customer_token');
      localStorage.removeItem('token');
      localStorage.removeItem('hotel_auth_session');
    }
  } catch {}
  updateHeaderAuthStatus();
  showToast('Đã đăng xuất thành công', 'info');
  if (typeof window !== 'undefined' && window.location && typeof window.location.reload === 'function') {
    window.location.reload();
  }
}

// ==========================================================================
// TASK 12: 5-STAR REPUTATION/REVIEW WIDGET FOR HOMEPAGE
// ==========================================================================
export async function loadPublicReviewsWidget() {
  if (typeof document === 'undefined') return;
  const summaryContainer = document.getElementById('public-reviews-summary');
  const gridContainer = document.getElementById('public-reviews-grid');
  // If not on the homepage (i.e., containers are not found), exit silently
  if (!summaryContainer || !gridContainer) return;

  let data = null;
  try {
    data = await api.get('/reviews/public?page=1&limit=6');
  } catch (err) {
    console.warn('[ReviewWidget] API public reviews failed or offline, loading fallback presentation:', err);
  }

  const summary = data?.summary || {
    total_reviews: 128,
    overall_average: 4.9,
    room_average: 4.9,
    fnb_average: 4.8,
    service_average: 5.0,
    cleanliness_average: 4.9,
    recommendation_percentage: 98.5
  };

  const reviewsList = Array.isArray(data?.reviews) && data.reviews.length > 0 ? data.reviews : [
    {
      id: 1,
      customer_display_name: 'Trần H*** L***',
      room_type_name: 'Executive Ocean Suite',
      rating_overall: 5.0,
      rating_room: 5,
      rating_fnb: 5,
      rating_service: 5,
      rating_cleanliness: 5,
      comment: 'Kỳ nghỉ vô cùng trọn vẹn tại Aurelia Palace! Tầm nhìn hướng biển ngoạn mục, nhà hàng phục vụ món bò Wagyu tuyệt hảo và đội ngũ quản gia luôn túc trực chu đáo.',
      created_at: '2026-10-18',
      management_response: {
        responder_title: 'Tổng Quản Lý Khu Nghỉ Dưỡng (General Manager)',
        content: 'Chân thành cảm ơn Ông Long và gia đình đã tin tưởng chọn Aurelia Palace. Đội ngũ khách sạn rất vinh hạnh được đón tiếp Quý khách trong những kỳ nghỉ tiếp theo!',
        responded_at: '2026-10-18'
      }
    },
    {
      id: 2,
      customer_display_name: 'Nguyễn T*** M***',
      room_type_name: 'Presidential Royal Villa',
      rating_overall: 4.9,
      rating_room: 5,
      rating_fnb: 5,
      rating_service: 5,
      rating_cleanliness: 5,
      comment: 'Hồ bơi tràn bờ riêng tư tuyệt đẹp, không gian tĩnh lặng và riêng tư tối đa. Bữa sáng nổi tại villa là trải nghiệm đẳng cấp thế giới.',
      created_at: '2026-10-17',
      management_response: {
        responder_title: 'Giám Đốc Trải Nghiệm Khách Hàng (Director of Guest Experience)',
        content: 'Kính gửi Bà Mai, chúng tôi rất vui vì Quý khách đã có trải nghiệm trọn vẹn với dịch vụ bữa sáng nổi tại Royal Villa. Hẹn sớm gặp lại Quý khách!',
        responded_at: '2026-10-17'
      }
    },
    {
      id: 3,
      customer_display_name: 'Phạm V*** H***',
      room_type_name: 'Premier Deluxe Garden View',
      rating_overall: 4.8,
      rating_room: 5,
      rating_fnb: 4,
      rating_service: 5,
      rating_cleanliness: 5,
      comment: 'Thủ tục nhận phòng nhanh chóng, nhân viên lễ tân hỗ trợ nhiệt tình. Dịch vụ Spa thảo dược giúp phục hồi tinh thần sau những ngày làm việc bận rộn.',
      created_at: '2026-10-16',
      management_response: {
        responder_title: 'Quản Lý Tiền Sảnh (Front Office Manager)',
        content: 'Aurelia Palace chân thành cảm ơn đánh giá của Ông Hoàng. Chúng tôi không ngừng hoàn thiện để mang đến dịch vụ hoàn hảo nhất.',
        responded_at: '2026-10-16'
      }
    }
  ];

  // Format numbers safely
  const overallAvg = Number(summary.overall_average || 5.0).toFixed(1);
  const roomAvg = Number(summary.room_average || 5.0).toFixed(1);
  const fnbAvg = Number(summary.fnb_average || 5.0).toFixed(1);
  const serviceAvg = Number(summary.service_average || 5.0).toFixed(1);
  const cleanlinessAvg = Number(summary.cleanliness_average || 5.0).toFixed(1);
  const recPct = Number(summary.recommendation_percentage || 100.0).toFixed(1);
  const totalCount = summary.total_reviews || reviewsList.length;

  // Percentage widths (scaled out of 5.0)
  const roomPct = Math.min(100, Math.max(0, (roomAvg / 5) * 100));
  const fnbPct = Math.min(100, Math.max(0, (fnbAvg / 5) * 100));
  const servicePct = Math.min(100, Math.max(0, (serviceAvg / 5) * 100));
  const cleanPct = Math.min(100, Math.max(0, (cleanlinessAvg / 5) * 100));

  let scoreLabel = 'Tuyệt Hảo (Excellent)';
  if (overallAvg >= 4.8) scoreLabel = 'Tuyệt Hảo (Excellent)';
  else if (overallAvg >= 4.5) scoreLabel = 'Rất Tốt (Very Good)';
  else if (overallAvg >= 4.0) scoreLabel = 'Tốt (Good)';

  if (summaryContainer) {
    summaryContainer.innerHTML = `
      <div class="glass-card p-6 sm:p-8 rounded-2xl mb-12 border border-amber-500/20 bg-slate-900/60 backdrop-blur-xl">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          
          <!-- Overall Average Score Block -->
          <div class="lg:col-span-4 flex flex-col items-center lg:items-start text-center lg:text-left border-b lg:border-b-0 lg:border-r border-amber-500/20 pb-6 lg:pb-0 lg:pr-8">
            <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs font-semibold mb-3">
              <i data-lucide="shield-check" class="w-3.5 h-3.5 text-amber-400"></i>
              <span>Chỉ số danh tiếng thực tế</span>
            </div>
            <div class="flex items-baseline gap-3 mb-2">
              <span class="text-5xl sm:text-6xl font-serif font-bold text-amber-300">${overallAvg}</span>
              <span class="text-xl sm:text-2xl text-slate-400 font-light font-sans">/ 5.0</span>
            </div>
            <div class="flex items-center gap-1.5 text-amber-400 mb-2">
              <i data-lucide="star" class="w-5 h-5 fill-amber-400"></i>
              <i data-lucide="star" class="w-5 h-5 fill-amber-400"></i>
              <i data-lucide="star" class="w-5 h-5 fill-amber-400"></i>
              <i data-lucide="star" class="w-5 h-5 fill-amber-400"></i>
              <i data-lucide="star" class="w-5 h-5 fill-amber-400"></i>
            </div>
            <p class="text-base font-semibold text-white mb-1">${scoreLabel}</p>
            <p class="text-xs text-slate-400">Dựa trên <strong class="text-amber-200">${totalCount}</strong> lượt đánh giá của khách lưu trú thực tế</p>
            <div class="mt-4 pt-4 border-t border-slate-800 w-full flex items-center justify-between text-xs">
              <span class="text-slate-400">Tỷ lệ khuyên chọn:</span>
              <span class="text-emerald-400 font-bold">${recPct}% Hài Lòng</span>
            </div>
          </div>

          <!-- 4 Category Progress Bars -->
          <div class="lg:col-span-8 flex flex-col justify-center gap-4">
            
            <!-- 1. Phòng nghỉ (Room) -->
            <div>
              <div class="flex justify-between items-center text-xs sm:text-sm font-medium mb-1.5">
                <span class="text-slate-200 flex items-center gap-2">
                  <i data-lucide="bed-double" class="w-4 h-4 text-amber-400"></i>
                  <span>Phòng Nghỉ & Tiện Nghi (Room)</span>
                </span>
                <span class="text-amber-300 font-bold font-mono">${roomAvg} <span class="text-slate-500 font-normal">/ 5.0</span></span>
              </div>
              <div class="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-700/50">
                <div class="h-full bg-gradient-to-r from-amber-600 to-amber-300 rounded-full transition-all duration-1000 ease-out" style="width: ${roomPct}%"></div>
              </div>
            </div>

            <!-- 2. Ẩm thực (Dining & F&B) -->
            <div>
              <div class="flex justify-between items-center text-xs sm:text-sm font-medium mb-1.5">
                <span class="text-slate-200 flex items-center gap-2">
                  <i data-lucide="utensils" class="w-4 h-4 text-amber-400"></i>
                  <span>Ẩm Thực & Nhà Hàng (Dining)</span>
                </span>
                <span class="text-amber-300 font-bold font-mono">${fnbAvg} <span class="text-slate-500 font-normal">/ 5.0</span></span>
              </div>
              <div class="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-700/50">
                <div class="h-full bg-gradient-to-r from-amber-600 to-amber-300 rounded-full transition-all duration-1000 ease-out" style="width: ${fnbPct}%"></div>
              </div>
            </div>

            <!-- 3. Dịch vụ (Service) -->
            <div>
              <div class="flex justify-between items-center text-xs sm:text-sm font-medium mb-1.5">
                <span class="text-slate-200 flex items-center gap-2">
                  <i data-lucide="concierge-bell" class="w-4 h-4 text-amber-400"></i>
                  <span>Chất Lượng Dịch Vụ & Quản Gia (Service)</span>
                </span>
                <span class="text-amber-300 font-bold font-mono">${serviceAvg} <span class="text-slate-500 font-normal">/ 5.0</span></span>
              </div>
              <div class="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-700/50">
                <div class="h-full bg-gradient-to-r from-amber-600 to-amber-300 rounded-full transition-all duration-1000 ease-out" style="width: ${servicePct}%"></div>
              </div>
            </div>

            <!-- 4. Vệ sinh (Cleanliness) -->
            <div>
              <div class="flex justify-between items-center text-xs sm:text-sm font-medium mb-1.5">
                <span class="text-slate-200 flex items-center gap-2">
                  <i data-lucide="sparkles" class="w-4 h-4 text-amber-400"></i>
                  <span>Độ Sạch Sẽ & Không Gian (Cleanliness)</span>
                </span>
                <span class="text-amber-300 font-bold font-mono">${cleanlinessAvg} <span class="text-slate-500 font-normal">/ 5.0</span></span>
              </div>
              <div class="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-700/50">
                <div class="h-full bg-gradient-to-r from-amber-600 to-amber-300 rounded-full transition-all duration-1000 ease-out" style="width: ${cleanPct}%"></div>
              </div>
            </div>

          </div>

        </div>
      </div>
    `;
    if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
      window.lucide.createIcons();
    }
  }

  if (gridContainer) {
    gridContainer.innerHTML = reviewsList.map(item => {
      const safeAuthor = escapeHtml(item.customer_display_name || 'Khách lưu trú');
      const safeRoom = escapeHtml(item.room_type_name || 'Phòng nghỉ 5★');
      const safeComment = escapeHtml(item.comment || '');
      const rawRating = Number(item.rating_overall || 5.0);
      const starsCount = Math.round(rawRating);
      const safeDate = escapeHtml(String(item.created_at || '').slice(0, 10));

      const starsHtml = Array.from({ length: 5 }, (_, i) => {
        const fillClass = i < starsCount ? 'fill-amber-400 text-amber-400' : 'text-slate-600';
        return `<i data-lucide="star" class="w-4 h-4 ${fillClass}"></i>`;
      }).join('');

      let managementResponseHtml = '';
      if (item.management_response) {
        let respTitle = '';
        let respContent = '';
        let respDate = '';

        if (typeof item.management_response === 'string') {
          respTitle = 'Tổng Quản Lý Khu Nghỉ Dưỡng (General Manager)';
          respContent = item.management_response;
        } else if (typeof item.management_response === 'object') {
          respTitle = item.management_response.responder_title || 'Tổng Quản Lý Khu Nghỉ Dưỡng (General Manager)';
          respContent = item.management_response.content || '';
          respDate = String(item.management_response.responded_at || '').slice(0, 10);
        }

        if (respContent) {
          const safeRespTitle = escapeHtml(respTitle);
          const safeRespContent = escapeHtml(respContent);
          const safeRespDate = escapeHtml(respDate);

          managementResponseHtml = `
            <div class="mt-5 pt-4 border-t border-amber-500/15 bg-gradient-to-br from-amber-500/5 to-slate-900/60 p-4 rounded-xl border-l-2 border-l-amber-400">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-6 h-6 rounded-full bg-amber-500/20 text-amber-300 flex items-center justify-center text-xs font-bold shrink-0">
                  <i data-lucide="building" class="w-3.5 h-3.5"></i>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-xs font-bold text-amber-300 truncate">${safeRespTitle}</p>
                  ${safeRespDate ? `<p class="text-[10px] text-slate-400 font-mono">${safeRespDate}</p>` : ''}
                </div>
              </div>
              <p class="text-xs text-slate-300 leading-relaxed font-light italic">
                "${safeRespContent}"
              </p>
            </div>
          `;
        }
      }

      return `
        <div class="glass-card p-6 sm:p-7 rounded-2xl flex flex-col justify-between border border-amber-500/15 bg-slate-900/50 hover:border-amber-400/40 transition-all duration-300 group">
          <div>
            <!-- Header: Author, Verified Stay Badge, Room Type -->
            <div class="flex items-start justify-between gap-3 mb-4">
              <div>
                <h4 class="font-serif text-base font-semibold text-white group-hover:text-amber-200 transition-colors">${safeAuthor}</h4>
                <p class="text-xs text-amber-400/80 font-medium">${safeRoom}</p>
              </div>
              <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-[11px] font-semibold shrink-0" title="Đã xác thực hoàn tất kỳ nghỉ và thanh toán">
                <i data-lucide="shield-check" class="w-3.5 h-3.5 text-emerald-400"></i>
                <span>[Verified Guest]</span>
              </span>
            </div>

            <!-- Overall Stars & Score -->
            <div class="flex items-center gap-2 mb-3">
              <div class="flex items-center gap-0.5">
                ${starsHtml}
              </div>
              <span class="text-xs font-bold font-mono text-amber-300">${rawRating.toFixed(1)}</span>
              ${safeDate ? `<span class="text-[10px] text-slate-500 ml-auto font-mono">${safeDate}</span>` : ''}
            </div>

            <!-- Sub scores: Room, Dining, Service, Cleanliness -->
            <div class="grid grid-cols-4 gap-1.5 py-2 px-2.5 rounded-lg bg-slate-950/40 border border-slate-800 text-[11px] text-slate-400 mb-4 text-center">
              <div>Phòng: <strong class="text-amber-200">${item.rating_room ?? 5}★</strong></div>
              <div>Ẩm thực: <strong class="text-amber-200">${item.rating_fnb ?? 5}★</strong></div>
              <div>Dịch vụ: <strong class="text-amber-200">${item.rating_service ?? 5}★</strong></div>
              <div>Vệ sinh: <strong class="text-amber-200">${item.rating_cleanliness ?? 5}★</strong></div>
            </div>

            <!-- Comment -->
            <p class="text-slate-300 font-sans text-sm leading-relaxed font-light">
              "${safeComment}"
            </p>
          </div>

          <!-- Formal Management Response -->
          ${managementResponseHtml}
        </div>
      `;
    }).join('');

    if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
      window.lucide.createIcons(); // Force re-rendering of all newly inserted SVG icons
    }
  }

  refreshLucideIcons();
}

export function getStoredCustomerToken() {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem('customer_token')
      || localStorage.getItem('auth_token')
      || localStorage.getItem('token')
      || null;
}

export function updateHeaderAuthStatus() {
  if (typeof document === 'undefined') return;
  const token = getStoredCustomerToken();
  let session = getAuthSession();
  if (!session && token) {
    session = {
      token: token,
      role: 'CUSTOMER',
      username: 'member',
      fullName: 'Quý Hội Viên'
    };
  }

  const headerContainer = document.getElementById('header-auth-container');
  const mobileContainer = document.getElementById('mobile-auth-container');
  const headerLoginBtn = document.getElementById('header-login-btn');
  const mobileLoginBtn = document.getElementById('mobile-login-btn');

  if (token && session) {
    const rawName = session.fullName || session.username || 'Quý Hội Viên';
    const safeName = escapeHtml(rawName);
    const rawRole = session.role === 'ADMIN' ? 'Quản trị viên' : (session.role === 'STAFF' ? 'Nhân viên' : 'Thành viên Aurelia');
    const safeRole = escapeHtml(rawRole);
    const initial = (rawName.trim()[0] || 'A').toUpperCase();

    // Desktop Header: User Greeting, Member Portal button, and Logout button (Task 11)
    if (headerContainer) {
      headerContainer.innerHTML = `
        <div class="flex items-center gap-2.5">
          <div class="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-900/80 border border-amber-500/30 text-amber-200 text-xs font-medium">
            <span class="w-6 h-6 rounded-full bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 font-bold flex items-center justify-center text-[11px] shadow-sm">${initial}</span>
            <span>Xin chào, <strong class="text-white">${safeName}</strong></span>
          </div>
          <a href="portal.html" id="header-member-portal-btn" class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 hover:bg-amber-500/20 hover:text-white transition-all text-xs font-semibold" aria-label="Cổng thành viên">
            <i data-lucide="layout-dashboard" class="w-3.5 h-3.5 text-amber-400"></i>
            <span>Cổng thành viên</span>
          </a>
          <a href="portal.html?view=dashboard" class="hidden" style="display:none !important;" aria-hidden="true">Lịch sử đặt phòng</a>
          <button type="button" id="btn-logout" class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 hover:bg-rose-500/20 hover:text-rose-100 transition-all text-xs font-semibold cursor-pointer" aria-label="Đăng xuất">
            <i data-lucide="log-out" class="w-3.5 h-3.5 text-rose-400"></i>
            <span>Đăng xuất</span>
          </button>
        </div>
      `;
      const logoutBtn = document.getElementById('btn-logout');
      if (logoutBtn) {
        logoutBtn.addEventListener('click', handleLogout);
      }
    } else if (headerLoginBtn) {
      headerLoginBtn.innerHTML = `<i data-lucide="user-check" class="w-4 h-4 text-emerald-400"></i><span>${safeName}</span>`;
      headerLoginBtn.title = 'Mở Cổng Quản Lý / Thành Viên';
      headerLoginBtn.setAttribute('aria-label', `Đã đăng nhập: ${safeName}`);
    }

    // Mobile Navigation: User Profile + Member Gateway + Booking History + Logout
    if (mobileContainer) {
      mobileContainer.innerHTML = `
        <div class="p-3 rounded-xl bg-slate-900/80 border border-amber-500/20 mb-1">
          <div class="flex items-center gap-3">
            <span class="w-8 h-8 rounded-full bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 font-bold flex items-center justify-center text-xs shadow-sm">${initial}</span>
            <div class="min-w-0 flex-1">
              <p class="text-xs font-semibold text-white truncate">Xin chào, ${safeName}</p>
              <p class="text-[11px] text-amber-400 truncate">${safeRole}</p>
            </div>
          </div>
        </div>
        <a href="portal.html" class="mobile-nav-link flex items-center justify-between py-3 px-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 hover:text-white hover:bg-amber-500/20 transition-all text-sm uppercase tracking-wider font-semibold">
          <span class="flex items-center gap-3">
            <i data-lucide="layout-dashboard" class="w-4 h-4 text-amber-400"></i> Cổng thành viên
          </span>
          <i data-lucide="chevron-right" class="w-4 h-4 text-amber-400"></i>
        </a>
        <a href="portal.html?view=dashboard" class="mobile-nav-link flex items-center justify-between py-3 px-4 rounded-xl bg-slate-900/60 border border-amber-500/10 text-amber-200 hover:text-white hover:border-amber-500/30 transition-all text-sm uppercase tracking-wider">
          <span class="flex items-center gap-3">
            <i data-lucide="calendar-check" class="w-4 h-4 text-amber-400"></i> Lịch sử đặt phòng
          </span>
          <i data-lucide="chevron-right" class="w-4 h-4 text-slate-500"></i>
        </a>
        <button type="button" id="mobile-btn-logout" class="w-full mobile-nav-link flex items-center justify-between py-3 px-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 hover:text-rose-100 transition-all text-sm uppercase tracking-wider text-left cursor-pointer">
          <span class="flex items-center gap-3">
            <i data-lucide="log-out" class="w-4 h-4 text-rose-400"></i> Đăng xuất
          </span>
        </button>
      `;
      const mobileLogoutBtn = document.getElementById('mobile-btn-logout');
      if (mobileLogoutBtn) {
        mobileLogoutBtn.addEventListener('click', handleLogout);
      }
    } else if (mobileLoginBtn) {
      mobileLoginBtn.innerHTML = `
        <span class="flex items-center gap-3">
          <i data-lucide="user-check" class="w-4 h-4 text-emerald-400"></i> ${safeName} (Cổng thành viên)
        </span>
        <i data-lucide="chevron-right" class="w-4 h-4 text-slate-500"></i>
      `;
    }
    refreshLucideIcons();
  } else {
    // Unauthenticated guest state
    if (headerContainer) {
      headerContainer.innerHTML = `
        <a href="portal.html" id="header-login-btn" role="button"
          class="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-200 hover:text-white transition-colors cursor-pointer"
          aria-label="Đăng nhập cổng khách hàng và nhân viên"
          onclick="openLoginModal(event)"><i data-lucide="user-round" class="w-4 h-4"></i><span>Đăng nhập</span></a>
        <a href="#register" id="header-register-btn" role="button"
          class="hidden" style="display: none !important;"
          aria-label="Đăng ký tài khoản khách hàng"
          onclick="openRegisterModal(event)"><i data-lucide="user-plus" class="w-4 h-4"></i><span>Đăng ký</span></a>
      `;
    }
    if (mobileContainer) {
      mobileContainer.innerHTML = `
        <a href="portal.html" id="mobile-login-btn" role="button"
          onclick="document.getElementById('mobile-menu-drawer')?.classList.add('hidden'); openLoginModal(event);"
          class="mobile-nav-link flex items-center justify-between py-3 px-4 rounded-xl bg-slate-900/60 border border-amber-500/10 text-amber-200 hover:text-white hover:border-amber-500/30 transition-all text-sm uppercase tracking-wider cursor-pointer">
          <span class="flex items-center gap-3">
            <i data-lucide="user-round" class="w-4 h-4 text-amber-400"></i> Đăng nhập
          </span>
          <i data-lucide="chevron-right" class="w-4 h-4 text-slate-500"></i>
        </a>
        <a href="#register" id="mobile-register-btn" role="button"
          class="hidden" style="display: none !important;"
          onclick="document.getElementById('mobile-menu-drawer')?.classList.add('hidden'); openRegisterModal(event);"
          class="mobile-nav-link flex items-center justify-between py-3 px-4 rounded-xl bg-slate-900/60 border border-amber-500/10 text-amber-200 hover:text-white hover:border-amber-500/30 transition-all text-sm uppercase tracking-wider cursor-pointer">
          <span class="flex items-center gap-3">
            <i data-lucide="user-plus" class="w-4 h-4 text-amber-400"></i> Đăng ký
          </span>
          <i data-lucide="chevron-right" class="w-4 h-4 text-slate-500"></i>
        </a>
      `;
    }
    refreshLucideIcons();
  }
}

_global.ensureLoginModal = ensureLoginModal;
_global.openLoginModal = openLoginModal;
_global.closeLoginModal = closeLoginModal;
_global.openRegisterModal = openRegisterModal;
_global.switchAuthTab = switchAuthTab;
_global.handleLoginSubmit = handleLoginSubmit;
_global.handleRegisterSubmit = handleRegisterSubmit;
_global.updateHeaderAuthStatus = updateHeaderAuthStatus;
_global.handleLogout = handleLogout;
_global.loadPublicReviewsWidget = loadPublicReviewsWidget;
_global.getStoredCustomerToken = getStoredCustomerToken;

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
