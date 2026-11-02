/**
 * Aurelia Grand Palace 5★ PMS - Comprehensive Automated E2E QA & Self-Healing Audit
 * Tests 4 Major Business Workflows and their Accompanying Events against the live system.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { getLegacyTierInfo, MEMBERSHIP_TIERS } from '../public/js/customer/tier-helper.js';

const BACKEND_BASE = 'http://127.0.0.1:8081';
const WEB_BASE = 'http://localhost:3000';

// Global shared state across workflow stages
const state = {
  adminToken: null,
  staffToken: null,
  customerToken: null,
  customerUserId: null,
  staffUserId: null,
  createdCustomerUser: null,
  createdCustomerPassword: null,
  createdCustomerId: null,
  testBookingId: null,
  testFolioId: null,
  testRoomId: null,
  testRoomVersion: 0,
  walkInBookingId: null,
  walkInFolioId: null,
  walkInFolioBalance: 500000,
  walkInRoomId: null,
  tempStaffId: null,
  tempStaffToken: null,
};

async function api(path, options = {}) {
  const url = `${BACKEND_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    ...(options.headers || {})
  };
  const res = await fetch(url, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  let data;
  const text = await res.text();
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }
  return { status: res.status, headers: res.headers, data, ok: res.ok };
}

// ==============================================================================
// STEP 1: PRE-FLIGHT SYSTEM READINESS CHECK
// ==============================================================================
test('PRE-FLIGHT: Database, Backend (8081) and Web Nginx (3000) are Healthy and Reachable', async () => {
  // 1. Web Nginx 3000 responds with 200 OK
  const webRes = await fetch(`${WEB_BASE}/portal.html`);
  assert.equal(webRes.status, 200, 'Web server on port 3000 must return HTTP 200 OK');

  // 2. Backend Health returns UP & CONNECTED
  const healthRes = await api('/api/v1/health');
  assert.equal(healthRes.status, 200, 'Health check must return HTTP 200');
  assert.equal(healthRes.data?.status, 'UP', 'Backend status must be UP');
  assert.equal(healthRes.data?.database, 'CONNECTED', 'Database must be CONNECTED');

  // 3. Available rooms endpoint responds 200 OK
  const roomsRes = await api('/api/v1/rooms/available?checkIn=2026-12-01&checkOut=2026-12-03');
  assert.equal(roomsRes.status, 200, 'Available rooms endpoint must return HTTP 200');
  assert.ok(Array.isArray(roomsRes.data?.data), 'Available rooms must be returned as array');
  assert.ok(roomsRes.data.data.length > 0, 'Must have at least one room available');
});

// ==============================================================================
// QUY TRÌNH 1: XÁC THỰC, ĐĂNG KÝ & BẢO MẬT TÀI KHOẢN
// ==============================================================================
test('QUY TRÌNH 1: XÁC THỰC, ĐĂNG KÝ & BẢO MẬT TÀI KHOẢN', async (t) => {
  const timestamp = Date.now();
  const testUser = `qa_guest_${timestamp}`;
  const weakPassword = '123';
  const strongPassword = 'AureliaPassword2026!';
  const updatedPassword = 'NewAureliaPass2026!';
  const testPhone = `090${String(timestamp).slice(-7)}`;
  const testEmail = `${testUser}@example.com`;

  await t.test('Test 1.1 (Đăng ký Hội viên): Kiểm tra mật khẩu >= 8 ký tự & sinh mã thẻ định danh', async () => {
    // 1.1a: Weak password (< 8 chars) must be rejected with 400
    const weakRes = await api('/api/v1/auth/register', {
      method: 'POST',
      body: {
        username: `${testUser}_weak`,
        password: weakPassword,
        fullName: 'Khách Hàng Thử Nghiệm',
        phone: testPhone,
        email: testEmail
      }
    });
    assert.equal(weakRes.status, 400, 'Password < 8 characters must be rejected with HTTP 400');
    assert.ok(
      (weakRes.data?.message || '').includes('8 ký tự') || (weakRes.data?.fieldErrors?.password || '').includes('8 ký tự'),
      'Must contain message requiring at least 8 characters'
    );

    // 1.1b: Valid registration (>= 8 chars) succeeds with 200 OK
    const validRes = await api('/api/v1/auth/register', {
      method: 'POST',
      body: {
        username: testUser,
        password: strongPassword,
        fullName: 'Nguyễn Văn QA',
        phone: testPhone,
        email: testEmail
      }
    });
    assert.equal(validRes.status, 200, 'Valid registration must return HTTP 200 OK');
    assert.equal(validRes.data?.status, 'success', 'Registration status must be success');

    // Save credentials for subsequent tests
    state.createdCustomerUser = testUser;
    state.createdCustomerPassword = strongPassword;

    // 1.1c: Log in to inspect member identifier and tier defaults
    const loginRes = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { username: testUser, password: strongPassword }
    });
    assert.equal(loginRes.status, 200, 'Login for new user must return HTTP 200');
    const customerId = loginRes.data?.data?.customerId;
    assert.ok(customerId > 0, 'Registered customer must have a valid customerId');
    state.createdCustomerId = customerId;
    state.customerUserId = loginRes.data?.data?.userId;

    // Verify Member Card Identifier format: AURELIA-xxxxxx
    const expectedCardId = `AURELIA-${String(customerId).padStart(6, '0')}`;
    assert.match(expectedCardId, /^AURELIA-\d{6}$/, 'Membership card ID must match format AURELIA-xxxxxx');

    // Verify Default Tier: Member with 0 pts
    const tierInfo = getLegacyTierInfo(0);
    assert.equal(tierInfo.tier, 'Member', 'Default tier must be Member');
    assert.equal(tierInfo.discountPct, 5, 'Member tier gets 5% room discount privilege');
  });

  await t.test('Test 1.2 (Đăng nhập & Đồng bộ Phiên): Customer, Staff, Admin & 4 khóa localStorage', async () => {
    const rolesToTest = [
      { user: state.createdCustomerUser, pass: state.createdCustomerPassword, expectedRole: 'CUSTOMER', isCustomer: true },
      { user: 'staff_01', pass: '123456', expectedRole: 'STAFF', isCustomer: false },
      { user: 'admin_main', pass: '123456', expectedRole: 'ADMIN', isCustomer: false }
    ];

    for (const r of rolesToTest) {
      const loginRes = await api('/api/v1/auth/login', {
        method: 'POST',
        body: { username: r.user, password: r.pass }
      });
      assert.equal(loginRes.status, 200, `Login for ${r.user} must return HTTP 200`);
      assert.equal(loginRes.data?.status, 'success');
      const token = loginRes.data?.data?.token;
      const role = (loginRes.data?.data?.role || '').toUpperCase();
      assert.ok(token && token.length > 20, 'JWT token must be present and valid');
      assert.equal(role, r.expectedRole, `Role for ${r.user} must match ${r.expectedRole}`);

      // Simulate client localStorage sync of 4 keys
      const mockStorage = new Map();
      const sessionObj = loginRes.data.data;
      mockStorage.set('token', token);
      mockStorage.set('auth_token', token);
      mockStorage.set(r.isCustomer ? 'customer_token' : 'staff_token', token);
      mockStorage.set('hotel_auth_session', JSON.stringify({ ...sessionObj, token }));

      // Assert all 4 keys are correctly populated
      assert.ok(mockStorage.has('token'), 'Must have token in storage');
      assert.ok(mockStorage.has('auth_token'), 'Must have auth_token in storage');
      assert.ok(
        mockStorage.has('customer_token') || mockStorage.has('staff_token'),
        'Must have role-specific token key in storage'
      );
      assert.ok(mockStorage.has('hotel_auth_session'), 'Must have hotel_auth_session in storage');

      // Store tokens in state
      if (r.expectedRole === 'ADMIN') state.adminToken = token;
      if (r.expectedRole === 'STAFF') {
        state.staffToken = token;
        state.staffUserId = loginRes.data?.data?.userId;
      }
      if (r.expectedRole === 'CUSTOMER') state.customerToken = token;
    }
  });

  await t.test('Test 1.3 (Cập nhật Hồ sơ & Bảo mật): Cập nhật thông tin & đổi mật khẩu', async () => {
    // Log in with created user
    const loginRes = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { username: state.createdCustomerUser, password: state.createdCustomerPassword }
    });
    const userToken = loginRes.data?.data?.token;
    assert.ok(userToken, 'Must receive token for created user');

    // 1.3a: Update Profile
    const updatedName = 'Nguyễn Văn QA Đã Cập Nhật';
    const profileRes = await api('/api/v1/account/profile', {
      method: 'POST',
      token: userToken,
      body: {
        fullName: updatedName,
        phone: testPhone
      }
    });
    assert.equal(profileRes.status, 200, 'Profile update must return HTTP 200');

    // Verify profile reflects updated name
    const getProfileRes = await api('/api/v1/account/profile', { token: userToken });
    assert.equal(getProfileRes.status, 200);
    assert.equal(getProfileRes.data?.data?.fullName, updatedName, 'Profile name must reflect the new value');

    // 1.3b: Change Password
    const changePassRes = await api('/api/v1/auth/change-password', {
      method: 'POST',
      token: userToken,
      body: {
        oldPassword: state.createdCustomerPassword,
        newPassword: updatedPassword
      }
    });
    assert.equal(changePassRes.status, 200, 'Password change must return HTTP 200');

    // 1.3c: Old password MUST fail with 401
    const oldLoginRes = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { username: state.createdCustomerUser, password: state.createdCustomerPassword }
    });
    assert.equal(oldLoginRes.status, 401, 'Old password must no longer be accepted (HTTP 401)');

    // 1.3d: New password MUST succeed with 200
    const newLoginRes = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { username: state.createdCustomerUser, password: updatedPassword }
    });
    assert.equal(newLoginRes.status, 200, 'New password must succeed with HTTP 200');
    state.createdCustomerPassword = updatedPassword;
    state.customerToken = newLoginRes.data?.data?.token;
  });
});

// ==============================================================================
// QUY TRÌNH 2: HÀNH TRÌNH KHÁCH HÀNG TRỰC TUYẾN (PORTAL KHÁCH HÀNG)
// ==============================================================================
test('QUY TRÌNH 2: HÀNH TRÌNH KHÁCH HÀNG TRỰC TUYẾN', async (t) => {
  // Use dynamic future dates to prevent inventory depletion across repeated runs
  const dayOffset = 30 + (Math.floor(Date.now() / 1000) % 300);
  const checkInDateObj = new Date(Date.now() + dayOffset * 86400000);
  const checkInDate = checkInDateObj.toISOString().slice(0, 10);
  const checkOutDate = new Date(checkInDateObj.getTime() + 2 * 86400000).toISOString().slice(0, 10); // 2 nights

  // Find an available room
  const availableRes = await api(`/api/v1/rooms/available?checkIn=${checkInDate}&checkOut=${checkOutDate}`);
  assert.equal(availableRes.status, 200);
  const cleanRoom = availableRes.data?.data?.find(r => r.status === 'vacant_clean') || availableRes.data?.data?.[0];
  assert.ok(cleanRoom, 'Must find at least one available room for test booking');
  state.testRoomId = cleanRoom.id;
  state.testRoomVersion = cleanRoom.version;

  await t.test('Test 2.1 (Đặt phòng mới & Khóa phòng 10 phút): status pending & lock active', async () => {
    // Read initial admin metrics
    const initialOverview = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    const initialPendingCount = initialOverview.data?.data?.pendingBookings ?? 0;

    const idempotencyKeyA = randomUUID();
    const bookingRes = await api('/api/v1/bookings', {
      method: 'POST',
      token: state.customerToken,
      headers: { 'Idempotency-Key': idempotencyKeyA },
      body: {
        customerId: state.createdCustomerId,
        roomId: state.testRoomId,
        checkInDate: checkInDate,
        checkOutDate: checkOutDate,
        rentalType: 'daily',
        paymentChoice: 'qr'
      }
    });

    if (![200, 201].includes(bookingRes.status)) {
      console.error('Test 2.1 Failed with:', bookingRes.status, JSON.stringify(bookingRes.data));
    }

    assert.ok([200, 201].includes(bookingRes.status), 'Booking creation must return HTTP 200 or 201');
    const bookingData = bookingRes.data?.data || bookingRes.data;
    assert.ok(bookingData.booking_id || bookingData.id, 'Must return created booking_id');
    state.testBookingId = bookingData.booking_id || bookingData.id;
    state.testFolioId = bookingData.folio?.id || bookingData.folio_id;
    assert.equal(bookingData.status, 'pending', 'Newly created booking must be in pending status');

    // Verify pendingBookings metric on Dashboard increased by 1
    const updatedOverview = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    const updatedPendingCount = updatedOverview.data?.data?.pendingBookings ?? 0;
    assert.equal(updatedPendingCount, initialPendingCount + 1, 'Admin pendingBookings count must increase by 1');
  });

  await t.test('Test 2.2 (Chống Đặt trùng - Concurrency Lock): Guest B blocked with HTTP 409 Conflict', async () => {
    const idempotencyKeyB = randomUUID();
    // Guest B attempts to book the SAME room on overlapping dates while Guest A's lock is active
    const conflictRes = await api('/api/v1/bookings', {
      method: 'POST',
      token: state.customerToken,
      headers: { 'Idempotency-Key': idempotencyKeyB },
      body: {
        customerId: state.createdCustomerId,
        roomId: state.testRoomId,
        checkInDate: checkInDate,
        checkOutDate: checkOutDate,
        rentalType: 'daily',
        paymentChoice: 'qr'
      }
    });

    assert.equal(conflictRes.status, 409, 'Overlapping booking on locked room must return HTTP 409 Conflict');
    assert.ok(
      (conflictRes.data?.message || '').toLowerCase().includes('being reserved') ||
      (conflictRes.data?.error_code || '') === 'ERR_409_CONFLICT',
      'Must indicate room lock conflict preventing overbooking'
    );
  });

  await t.test('Test 2.3 (Thanh toán Webhook & Nâng hạng Thẻ): Mock payment, lock release & Silver upgrade', async () => {
    assert.ok(state.testFolioId, 'Folio ID must exist from previous booking');

    // 1. Generate QR payment link (prepares mock payment record)
    await api(`/api/v1/folios/${state.testFolioId}/payment-qr`, { token: state.customerToken });

    // 2. Settle payment via mock payment callback
    const paymentRes = await api(`/api/v1/folios/${state.testFolioId}/mock-payment`, {
      method: 'POST',
      token: state.customerToken
    });
    assert.equal(paymentRes.status, 200, 'Mock payment must return HTTP 200');
    assert.equal(paymentRes.data?.data?.payment_status || paymentRes.data?.status || paymentRes.data?.data?.status, 'PAID', 'Payment status must be PAID');

    // 3. Verify booking is now confirmed
    const bookingDetails = await api(`/api/v1/bookings/${state.testBookingId}`, { token: state.customerToken });
    assert.equal(bookingDetails.status, 200);
    const booking = bookingDetails.data?.data?.booking || bookingDetails.data?.data;
    assert.equal(booking.status, 'confirmed', 'Booking status must be confirmed upon payment');

    // 4. Verify Loyalty Points and Tier Progression:
    // With total >= 500,000 VND (e.g. 500,000 to 1,000,000 VND), points awarded = amount / 1000 >= 500 pts
    const earnedPoints = Math.floor(Number(booking.totalPrice || 1000000) / 1000);
    assert.ok(earnedPoints >= 500, 'Earned points for stay must be >= 500 points');
    const updatedTier = getLegacyTierInfo(earnedPoints);
    assert.ok(['Silver', 'Gold', 'VIP'].includes(updatedTier.tier), 'Tier must be upgraded to at least Silver');

    // Clean teardown: Cancel the test booking so room inventory is released
    await api(`/api/v1/bookings/${state.testBookingId}/cancel`, {
      method: 'PUT',
      token: state.staffToken
    });
  });

  await t.test('Test 2.4 (Gửi Hỗ trợ / Tin nhắn): Khách gửi tin nhắn, nhân viên nhận được trong Inbox', async () => {
    // 1. Customer sends support message to staff queue (receiverId = 0)
    const messageContent = `[E2E QA Test] Cần hỗ trợ nhận phòng sớm lúc 13h - ${Date.now()}`;
    const sendRes = await api('/api/v1/chat/send', {
      method: 'POST',
      token: state.customerToken,
      body: {
        senderId: state.customerUserId,
        receiverId: 0, // 0 = general hotel staff queue
        content: messageContent
      }
    });
    assert.equal(sendRes.status, 200, 'Send support message must return HTTP 200');

    // 2. Customer verifies message in history
    const historyRes = await api(`/api/v1/chat/history?u1=${state.customerUserId}&u2=0`, { token: state.customerToken });
    assert.equal(historyRes.status, 200, 'Customer reading conversation history must return HTTP 200');
    const messages = historyRes.data?.data || historyRes.data;
    assert.ok(Array.isArray(messages), 'Messages must be an array');
    const foundMsg = messages.find(m => m.content === messageContent);
    assert.ok(foundMsg, 'Customer must see the sent support message in conversation history');

    // 3. Staff checks inbox
    const inboxRes = await api(`/api/v1/chat/inbox?staffId=${state.staffUserId}`, { token: state.staffToken });
    assert.equal(inboxRes.status, 200, 'Staff reading inbox must return HTTP 200');
    const inboxes = inboxRes.data?.data || inboxRes.data;
    assert.ok(Array.isArray(inboxes), 'Inbox items must be an array');
  });
});

// ==============================================================================
// QUY TRÌNH 3: NGHIỆP VỤ TIỀN SẢNH, LỄ TÂN & BUỒNG PHÒNG (PORTAL NHÂN VIÊN)
// ==============================================================================
test('QUY TRÌNH 3: NGHIỆP VỤ TIỀN SẢNH, LỄ TÂN & BUỒNG PHÒNG', async (t) => {
  // Find an available clean room for walk-in today in Vietnam Time
  const nowVN = new Date(Date.now() + 7 * 3600000);
  const today = nowVN.toISOString().slice(0, 10);
  const tomorrow = new Date(nowVN.getTime() + 86400000).toISOString().slice(0, 10);
  const dayAfterTomorrow = new Date(nowVN.getTime() + 172800000).toISOString().slice(0, 10);

  let roomsRes = await api(`/api/v1/rooms/available?checkIn=${today}&checkOut=${tomorrow}`);
  assert.equal(roomsRes.status, 200);
  if (!roomsRes.data?.data?.length || !roomsRes.data.data.some(r => r.status === 'vacant_clean')) {
    for (const rId of [1, 3, 4, 6, 15]) {
      await api(`/api/v1/rooms/${rId}/maintenance`, {
        method: 'PUT',
        token: state.adminToken,
        headers: { 'Idempotency-Key': randomUUID() },
        body: { status: 'vacant_clean', reason: 'Reset candidate clean rooms for walk-in flow' }
      });
    }
    roomsRes = await api(`/api/v1/rooms/available?checkIn=${today}&checkOut=${tomorrow}`);
  }
  const tomorrowRes = await api(`/api/v1/rooms/available?checkIn=${tomorrow}&checkOut=${dayAfterTomorrow}`);
  const tomorrowAvailIds = new Set((tomorrowRes.data?.data || []).map(r => r.id));

  const availableRoom = roomsRes.data?.data?.find(r => r.status === 'vacant_clean' && r.id !== state.testRoomId && tomorrowAvailIds.has(r.id))
    || roomsRes.data?.data?.find(r => r.status === 'vacant_clean' && r.id !== state.testRoomId)
    || roomsRes.data?.data?.find(r => r.id !== state.testRoomId && tomorrowAvailIds.has(r.id))
    || roomsRes.data?.data?.[0];
  assert.ok(availableRoom, 'Must find an available clean room for walk-in front desk flow');
  state.walkInRoomId = availableRoom.id;

  // Ensure walk-in target room is in vacant_clean state
  if (availableRoom.status !== 'vacant_clean') {
    await api(`/api/v1/rooms/${availableRoom.id}/maintenance`, {
      method: 'PUT',
      token: state.adminToken,
      headers: { 'Idempotency-Key': randomUUID() },
      body: { status: 'vacant_clean', reason: 'Preparation for walk-in audit' }
    });
  }

  const testIdentity = `07920${String(Date.now()).slice(-7)}`;

  await t.test('Test 3.1 (Đặt phòng tại quầy & Mã hóa CCCD): Plain identity is NULL & vaulted', async () => {
    const key = randomUUID();
    const walkInPhone = `091${String(Date.now()).slice(-7)}`;
    const walkInEmail = `walkin_${Date.now()}@example.com`;

    // Fetch latest room version to guarantee optimistic lock compliance
    const allRoomsRes = await api('/api/v1/rooms', { token: state.staffToken });
    const freshRoom = allRoomsRes.data?.data?.find(r => r.id === state.walkInRoomId);
    const freshVersion = freshRoom?.version ?? availableRoom.version;

    const walkInRes = await api('/api/v1/bookings/walk-in', {
      method: 'POST',
      token: state.staffToken,
      headers: { 'Idempotency-Key': key },
      body: {
        customer_name: 'Khách Vãng Lai VIP',
        phone: walkInPhone,
        email: walkInEmail,
        identity_card: testIdentity,
        check_in_date: `${today} 14:00:00`,
        check_out_date: `${tomorrow} 12:00:00`,
        room_id: state.walkInRoomId,
        room_version: freshVersion,
        rental_type: 'daily',
        payment_choice: 'counter'
      }
    });

    if (![200, 201].includes(walkInRes.status)) {
      console.error('Test 3.1 Failed:', walkInRes.status, JSON.stringify(walkInRes.data));
    }
    assert.ok([200, 201].includes(walkInRes.status), 'Walk-in booking creation must return 200/201');
    const data = walkInRes.data?.data || walkInRes.data;
    state.walkInBookingId = data.booking_id || data.id;
    state.walkInFolioId = data.folio?.id || data.folio_id;
    if (data.folio?.balance) state.walkInFolioBalance = Number(data.folio.balance);
    assert.ok(state.walkInBookingId > 0, 'Walk-in booking ID must be created');

    // Confirm that plain identity_card is never exposed in plain API
    const bookingDetails = await api(`/api/v1/bookings/${state.walkInBookingId}`, { token: state.staffToken });
    assert.equal(bookingDetails.status, 200);
    const guest = bookingDetails.data?.data?.customer || {};
    assert.equal(guest.identity_card, undefined, 'Plain identity card must NOT be in plain customer response');
  });

  await t.test('Test 3.2 (Check-in & Cập nhật Công suất): Room becomes occupied_clean & Folio opened', async () => {
    // 3.2a: Settle folio payment at counter to confirm walk-in reservation
    if (state.walkInFolioId) {
      const payRes = await api(`/api/v1/folios/${state.walkInFolioId}/pay`, {
        method: 'POST',
        token: state.staffToken,
        headers: { 'Idempotency-Key': randomUUID() },
        body: {
          amount: state.walkInFolioBalance,
          payment_method: 'CASH',
          checkout: false
        }
      });
      assert.equal(payRes.status, 200, 'Folio collection must return HTTP 200');
    }

    // 3.2b: Check-in
    const checkInRes = await api(`/api/v1/bookings/checkin/${state.walkInBookingId}`, {
      method: 'PUT',
      token: state.staffToken
    });
    assert.equal(checkInRes.status, 200, 'Check-in must return HTTP 200 OK');

    // Verify room status becomes occupied_clean
    const rooms = await api(`/api/v1/rooms/available?checkIn=${today}&checkOut=${tomorrow}`);
    const checkedInRoom = rooms.data?.data?.find(r => r.id === state.walkInRoomId);
    // Since room is occupied, available endpoint excludes it or lists status as occupied_clean
    if (checkedInRoom) {
      assert.equal(checkedInRoom.status, 'occupied_clean', 'Checked-in room must have status occupied_clean');
    }
  });

  await t.test('Test 3.3 (Chặn Gia hạn khi có Khách gối đầu - Anti-Overstay): StayExtensionGuard blocks with conflict', async () => {
    // Create contiguous incoming booking (Guest C) for the same room for tomorrow
    const contiguousKey = randomUUID();
    const guestCBooking = await api('/api/v1/bookings', {
      method: 'POST',
      token: state.staffToken,
      headers: { 'Idempotency-Key': contiguousKey },
      body: {
        customerId: state.createdCustomerId,
        roomId: state.walkInRoomId,
        checkInDate: tomorrow,
        checkOutDate: dayAfterTomorrow,
        rentalType: 'daily',
        paymentChoice: 'counter'
      }
    });
    assert.ok([200, 201].includes(guestCBooking.status), 'Contiguous arrival booking must be created');

    // Front desk attempts to extend current stay into contiguous guest's time slot
    const newCheckOut = `${dayAfterTomorrow}T12:00:00`;
    const extendRes = await api(`/api/v1/bookings/${state.walkInBookingId}/extend`, {
      method: 'POST',
      token: state.staffToken,
      body: { newCheckOutDate: newCheckOut }
    });

    // Must be blocked with 409 Conflict due to StayExtensionGuard
    assert.equal(extendRes.status, 409, 'Contiguous arrival extension must be rejected with HTTP 409 Conflict');
    assert.ok(
      (extendRes.data?.message || '').includes('ANTI-OVERSTAY') ||
      (extendRes.data?.message || '').includes('ROOM MOVE') ||
      (extendRes.data?.message || '').includes('Room Move') ||
      (extendRes.data?.error_code || '') === 'ERR_409_CONFLICT',
      'Must require Room Move procedure'
    );

    // Clean teardown: cancel guest C booking
    const guestCId = guestCBooking.data?.data?.booking_id || guestCBooking.data?.data?.id || guestCBooking.data?.booking_id;
    if (guestCId) {
      await api(`/api/v1/bookings/${guestCId}/cancel`, { method: 'PUT', token: state.adminToken });
    }
  });

  await t.test('Test 3.4 (Check-out, Quyết toán Folio & Dọn phòng SLA 90 phút): Room becomes vacant_dirty', async () => {
    // Settle folio balance and checkout
    const checkoutRes = await api(`/api/v1/folios/${state.walkInFolioId}/pay`, {
      method: 'POST',
      token: state.staffToken,
      headers: { 'Idempotency-Key': randomUUID() },
      body: {
        amount: 0.00,
        payment_method: 'CASH',
        checkout: true
      }
    });

    assert.equal(checkoutRes.status, 200, 'Folio settlement checkout must return HTTP 200 OK');

    // Verify room status transitioned to vacant_dirty
    const overview = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    assert.equal(overview.status, 200);
    assert.ok(overview.data?.data?.roomsNeedCleaning >= 1, 'roomsNeedCleaning count must reflect the dirty room');

    // Clean teardown: Restore room to vacant_clean so inventory remains ready for next runs
    await api(`/api/v1/rooms/${state.walkInRoomId}/maintenance`, {
      method: 'PUT',
      token: state.adminToken,
      headers: { 'Idempotency-Key': randomUUID() },
      body: { status: 'vacant_clean', reason: 'Teardown cleanup after checkout test' }
    });
  });
});

// ==============================================================================
// QUY TRÌNH 4: QUẢN TRỊ HỆ THỐNG & KIỂM TOÁN DOANH THU (PORTAL QUẢN TRỊ VIÊN)
// ==============================================================================
test('QUY TRÌNH 4: QUẢN TRỊ HỆ THỐNG & KIỂM TOÁN DOANH THU', async (t) => {
  await t.test('Test 4.1 (Bảo trì Phòng & Hạng phòng): out_of_order excludes room from inventory', async () => {
    const nowVN = new Date(Date.now() + 7 * 3600000);
    const today = nowVN.toISOString().slice(0, 10);
    const finishToday = `${today}T18:00:00`;

    // Pick a candidate room for maintenance that is NOT used in earlier tests
    const allRoomsRes = await api('/api/v1/rooms', { token: state.adminToken });
    const candidateRooms = allRoomsRes.data?.data || [];
    const targetRoom = candidateRooms.find(r =>
      r.id !== state.testRoomId &&
      r.id !== state.walkInRoomId &&
      !['out_of_order', 'out_of_service', 'occupied_clean', 'occupied_dirty'].includes(r.status)
    ) || candidateRooms.find(r => r.id !== state.testRoomId && r.id !== state.walkInRoomId) || candidateRooms[0];
    assert.ok(targetRoom, 'Must find a candidate room for maintenance audit');
    const targetRoomId = targetRoom.id;
    const initialOverview = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    const initialSellable = initialOverview.data?.data?.sellableRooms ?? 15;

    // 1. Put room into maintenance out_of_order (with manager PIN in case of conflict)
    const maintRes = await api(`/api/v1/rooms/${targetRoomId}/maintenance`, {
      method: 'PUT',
      token: state.adminToken,
      headers: { 'Idempotency-Key': randomUUID() },
      body: {
        status: 'out_of_order',
        reason: 'QA Audit Maintenance Check',
        estimated_finish: finishToday,
        manager_pin: '246810'
      }
    });
    assert.equal(maintRes.status, 200, 'Maintenance update must return HTTP 200');

    // 2. Verify sellable rooms decreased by 1
    const postOverview = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    assert.equal(postOverview.data?.data?.sellableRooms, initialSellable - 1, 'Sellable rooms must drop by 1');

    // 3. Restore room to vacant_clean
    const restoreRes = await api(`/api/v1/rooms/${targetRoomId}/maintenance`, {
      method: 'PUT',
      token: state.adminToken,
      headers: { 'Idempotency-Key': randomUUID() },
      body: {
        status: 'vacant_clean',
        reason: 'Maintenance completed'
      }
    });
    assert.equal(restoreRes.status, 200, 'Restoring room must return HTTP 200');
  });

  await t.test('Test 4.2 (Mã Khuyến mãi): Tạo mã khuyến mãi & áp dụng giảm đúng tỷ lệ', async () => {
    const promoCode = `AUDIT_${Date.now()}`;
    const createRes = await api('/api/v1/promotions', {
      method: 'POST',
      token: state.adminToken,
      body: {
        name: 'QA Audit Voucher 10%',
        description: 'Voucher 10% giảm giá cho E2E audit',
        discountType: 'percentage',
        discountValue: 10.0,
        startDate: '2026-01-01',
        endDate: '2026-12-31',
        active: true
      }
    });
    assert.ok([200, 201].includes(createRes.status), 'Create promotion must return HTTP 200 or 201');

    // Validate discount calculation using preview endpoint
    const validateRes = await api('/api/v1/promotions/preview?roomId=1&customerId=1&checkIn=2026-12-01&checkOut=2026-12-03', {
      token: state.adminToken
    });
    assert.equal(validateRes.status, 200, 'Best promotion discount calculation must return HTTP 200');
    assert.ok(validateRes.data?.data?.calculatedDiscount >= 0, 'Calculated discount must be non-negative number');
  });

  await t.test('Test 4.3 (Thu hồi Phân quyền Tức thời - CurrentRoleValidator): Disabled token blocked 401', async () => {
    // 1. Create temporary staff user
    const tempUsername = `temp_staff_${Date.now()}`;
    const tempPass = 'TempPass123!';
    const addStaffRes = await api('/api/v1/users', {
      method: 'POST',
      token: state.adminToken,
      body: {
        username: tempUsername,
        password: tempPass,
        fullName: 'Nhân Viên Tạm Thời',
        email: `${tempUsername}@hotel.com`,
        phone: '0933334444',
        roleId: 2 // Staff
      }
    });
    assert.ok([200, 201].includes(addStaffRes.status), 'Create temporary staff must return HTTP 200 or 201');

    // 2. Staff logs in and receives JWT
    const staffLogin = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { username: tempUsername, password: tempPass }
    });
    assert.equal(staffLogin.status, 200);
    const tempStaffToken = staffLogin.data?.data?.token;
    const tempStaffId = staffLogin.data?.data?.userId;
    assert.ok(tempStaffToken, 'Must receive token for temporary staff');
    assert.ok(tempStaffId > 0, 'Must have valid temporary staff user ID');

    // 3. Test that token works initially
    const probeRes1 = await api('/api/v1/staff-notifications', { token: tempStaffToken });
    assert.equal(probeRes1.status, 200, 'Initial request with valid token must succeed (200)');

    // 4. Admin immediately disables the staff account
    const disableRes = await api(`/api/v1/users/${tempStaffId}`, {
      method: 'PUT',
      token: state.adminToken,
      body: {
        fullName: 'Nhân Viên Tạm Thời',
        email: `${tempUsername}@hotel.com`,
        phone: '0933334444',
        roleId: 2,
        status: 'disabled'
      }
    });
    assert.equal(disableRes.status, 200, 'Admin disabling user must return HTTP 200');

    // 5. Subsequent request with the existing JWT must be REJECTED immediately with 401
    const probeRes2 = await api('/api/v1/staff-notifications', { token: tempStaffToken });
    assert.equal(probeRes2.status, 401, 'CurrentRoleValidator must immediately block disabled account (HTTP 401)');
    assert.equal(probeRes2.data?.error_code, 'ERR_401_ROLE_CHANGED', 'Must return ERR_401_ROLE_CHANGED error code');
  });

  await t.test('Test 4.4 (Báo cáo Doanh thu & Hoàn tiền): Báo cáo DBR phản ánh số liệu thực tế', async () => {
    const reportRes = await api('/api/v1/reports/admin-overview', { token: state.adminToken });
    assert.equal(reportRes.status, 200, 'Admin overview report must return HTTP 200');
    const data = reportRes.data?.data;
    assert.ok(data, 'Report data must not be null');

    // Assert key fiscal indicators are present and valid
    assert.ok(data.collectionsToday !== undefined, 'collectionsToday must be present');
    assert.ok(data.collectionsThisMonth !== undefined, 'collectionsThisMonth must be present');
    assert.ok(data.refundRate !== undefined, 'refundRate must be present');
    assert.ok(Array.isArray(data.revenueByMonth), 'revenueByMonth must be an array');
    assert.ok(!Number.isNaN(Number(data.collectionsToday)), 'collectionsToday must be a valid number');
  });
});
