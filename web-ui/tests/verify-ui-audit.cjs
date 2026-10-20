// Run from the repository root with Playwright available in NODE_PATH.
// Uses intercepted fixture requests only; no live backend or customer records.
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 1225, height: 900 } });
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.hostname !== 'ui-audit.test') return route.abort();
      const file = path.join(process.cwd(), 'web-ui', url.pathname);
      try {
        let body = await fs.readFile(file);
        if (url.pathname.endsWith('portal.html')) body = body.toString().replace(/<script\b[^>]*>[\s\S]*?<\/script>/g, '');
        await route.fulfill({ body, contentType: file.endsWith('.css') ? 'text/css' : file.endsWith('.js') ? 'text/javascript' : 'text/html' });
      } catch { await route.fulfill({ status: 404, body: '' }); }
    });
    await page.goto('http://ui-audit.test/portal.html');
    const loginHeaderResults = [];
    for (const width of [1340, 768, 375, 320]) {
      await page.setViewportSize({ width, height: 1168 });
      const header = await page.evaluate(() => ({
        brandLeft: document.querySelector('.portal-brand').getBoundingClientRect().left,
        brandWidth: document.querySelector('.portal-brand').getBoundingClientRect().width,
        bellWidth: document.getElementById('btn-notification-bell').getBoundingClientRect().width,
        logoutWidth: document.getElementById('topbar-logout-button').getBoundingClientRect().width,
        themeWidth: document.querySelector('[data-theme-toggle]').getBoundingClientRect().width,
        overflow: document.documentElement.scrollWidth > innerWidth
      }));
      assert.equal(header.brandLeft, width <= 480 ? 12 : 16);
      assert.equal(header.bellWidth, 0);
      assert.equal(header.logoutWidth, 0);
      assert.ok(header.themeWidth > 0);
      assert.equal(header.overflow, false);
      loginHeaderResults.push({ width, ...header });
      if (width === 1340) await page.screenshot({ path: 'docs/verification/web-ui/login-header.png', fullPage: true });
    }
    await fs.writeFile('docs/verification/web-ui/login-header-results.json', JSON.stringify(loginHeaderResults, null, 2));
    await page.setViewportSize({ width: 1225, height: 900 });
    await page.evaluate(() => {
      window.lucide = { createIcons() {} };
      document.getElementById('auth-view').hidden = true;
      document.getElementById('app-view').hidden = false;
      document.getElementById('portal-nav').innerHTML = '<button class="active"><span>Thành viên</span></button>';
      document.getElementById('portal-heading').textContent = 'Thành viên';
      document.getElementById('notification-dropdown').hidden = true;
      document.getElementById('btn-notification-bell').hidden = true;
      document.getElementById('notification-dropdown').classList.add('hidden');
      document.getElementById('btn-notification-bell').classList.add('hidden');
    });
    const results = [];
    for (const role of ['staff', 'customer']) {
      await page.evaluate(async role => {
        document.body.className = `portal-body ${role}-portal`;
        const { api } = await import('/public/api.js');
        api.get = async () => [{ type: 'earn', pointsChange: 1200 }, { type: 'redeem', pointsChange: -500 }];
        const { renderLoyalty } = await import(`/public/js/${role}/loyalty.js`);
        await renderLoyalty(document.getElementById('view-container'), { customerId: 123 });
      }, role);
      if (role === 'customer') await page.waitForSelector('.tier-badge');
      for (const theme of ['light', 'dark']) {
        await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
        for (const width of [1225, 768, 375, 320]) {
          await page.setViewportSize({ width, height: 900 });
          const metrics = await page.evaluate(() => ({
            overflow: document.documentElement.scrollWidth > innerWidth,
            content: getComputedStyle(document.querySelector('.portal-content')).backgroundColor,
            text: getComputedStyle(document.querySelector('.page-header h1')).color,
            card: getComputedStyle(document.querySelector('.portal-view .card')).backgroundColor,
            tier: document.body.dataset.customerTier,
            badge: document.querySelector('.tier-badge')?.textContent
          }));
          assert.equal(metrics.overflow, false, `${role}/${theme}/${width} horizontal overflow`);
          if (role === 'staff' && theme === 'light') assert.equal(metrics.content, 'rgb(246, 243, 237)');
          if (role === 'customer') { assert.equal(metrics.tier, 'gold'); assert.ok(metrics.badge.includes('Gold')); }
          results.push({ role, theme, width, ...metrics });
          if (width === 1225 || width === 375) await page.screenshot({ path: `docs/verification/web-ui/${role}-loyalty-${theme}-${width}.png`, fullPage: true });
        }
      }
    }
    // Compare the two annotated shell controls with the actual admin rules.
    const shellResults = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      for (const width of [1225, 768, 375]) {
        await page.setViewportSize({ width, height: 900 });
        const controls = {};
        for (const role of ['admin', 'staff']) {
          await page.evaluate(role => document.body.className = `portal-body ${role}-portal`, role);
          controls[role] = await page.evaluate(() => {
            const toggle = getComputedStyle(document.getElementById('admin-sidebar-toggle'));
            return {
              toggleWidth: toggle.width,
              toggleHeight: toggle.height,
              toggleBorder: toggle.borderRadius,
              toggleBackground: toggle.backgroundColor,
              account: getComputedStyle(document.querySelector('.portal-topbar-account')).display,
              logoutWidth: getComputedStyle(document.querySelector('.portal-topbar-logout')).width,
              user: getComputedStyle(document.querySelector('.portal-topbar-user')).display
            };
          });
        }
        assert.deepEqual(controls.staff, controls.admin, `Shell controls differ at ${theme}/${width}`);
        shellResults.push({ theme, width, ...controls });
      }
    }
    await page.evaluate(async () => {
      const { api } = await import('/public/api.js');
      api.get = async () => Array.from({ length: 13 }, (_, i) => ({ id: i + 1, fullName: `Khách thử ${i + 1}`, phone: `09000000${String(i + 1).padStart(2, '0')}` }));
      await import('/public/js/shared/pagination.js');
      const { renderCustomers } = await import('/public/js/staff/customers.js');
      document.body.className = 'portal-body staff-portal';
      document.getElementById('portal-heading').textContent = 'Khách hàng';
      await renderCustomers(document.getElementById('view-container'), {});
    });
    await page.waitForSelector('#staff-customers-body .user-avatar');
    const customerResults = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      for (const width of [1225, 768, 375, 320]) {
        await page.setViewportSize({ width, height: 1200 });
        const metrics = await page.evaluate(() => {
          const avatar = getComputedStyle(document.querySelector('#staff-customers-body .user-avatar'));
          const pagination = getComputedStyle(document.querySelector('#pagination-staff-customers .portal-pagination'));
          const action = getComputedStyle(document.querySelector('.btn-view-sc'));
          return {
            overflow: document.documentElement.scrollWidth > innerWidth,
            values: [...document.querySelectorAll('#staff-cust-summary .stat-value')].map(e => e.textContent),
            columns: getComputedStyle(document.querySelector('#staff-cust-summary')).gridTemplateColumns.split(' ').length,
            avatar: { width: avatar.width, height: avatar.height, radius: avatar.borderRadius },
            pagination: { display: pagination.display, direction: pagination.flexDirection },
            action: { width: action.width, height: action.height },
            deleteButtons: document.querySelectorAll('.btn-delete-cust').length
          };
        });
        assert.equal(metrics.overflow, false, `Customer table overflow ${theme}/${width}`);
        assert.deepEqual(metrics.values, ['13', '0', '13']);
        assert.equal(metrics.columns, width > 1000 ? 3 : width > 620 ? 2 : 1);
        assert.deepEqual(metrics.avatar, { width: '36px', height: '36px', radius: '50%' });
        assert.equal(metrics.pagination.display, 'flex');
        assert.equal(metrics.pagination.direction, width > 620 ? 'row' : 'column');
        assert.equal(metrics.action.width, '34px');
        assert.equal(metrics.deleteButtons, 0);
        customerResults.push({ theme, width, ...metrics });
        if (width === 1225 || width === 375) await page.screenshot({ path: `docs/verification/web-ui/staff-customers-${theme}-${width}.png`, fullPage: true });
      }
    }
    await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
    assert.equal(await page.locator('#staff-customers-body tr').count(), 3);
    await page.locator('#staff-cust-search').fill('Khách thử 2');
    assert.equal(await page.locator('#staff-customers-body tr').count(), 1);
    await page.locator('#btn-refresh-cust').click();
    await page.waitForFunction(() => document.querySelectorAll('#staff-customers-body .user-avatar').length === 10);
    await page.setViewportSize({ width: 1225, height: 1200 });
    await page.locator('#pagination-staff-customers').screenshot({ path: 'docs/verification/web-ui/staff-customers-pagination.png' });
    const tabResults = [];
    for (const tab of ['bookings', 'invoices', 'profile']) {
      await page.evaluate(async tab => {
        const { api } = await import('/public/api.js');
        const rows = Array.from({ length: 13 }, (_, i) => ({ id: i + 1, bookingId: i + 1, roomId: 101, roomNumber: '101', customerId: 1, customerName: 'Khách thử', checkInDate: '2026-10-20T14:00:00+07:00', checkOutDate: '2026-10-22T12:00:00+07:00', totalPrice: 12500000, finalTotal: 12500000, status: tab === 'bookings' ? 'checked_out' : 'paid', paymentMethod: 'cash', issueDate: '2026-10-21T10:00:00+07:00' }));
        api.get = async endpoint => ['/bookings', '/invoices'].includes(endpoint) ? rows : [];
        localStorage.setItem('hotel_auth_session', JSON.stringify({ username: 'staff_fixture', fullName: 'Nhân viên thử', role: 'STAFF', token: 'fixture-only-token' }));
        document.getElementById('portal-heading').textContent = { bookings: 'Đặt phòng', invoices: 'Hóa đơn', profile: 'Hồ sơ & bảo mật' }[tab];
        if (tab === 'profile') {
          const { loadProfileView } = await import('/public/js/view-shared.js');
          loadProfileView();
        } else {
          const mod = await import(`/public/js/staff/${tab}.js`);
          await mod[tab === 'bookings' ? 'renderBookings' : 'renderInvoices'](document.getElementById('view-container'), {});
        }
      }, tab);
      if (tab !== 'profile') await page.waitForSelector(`#staff-${tab}-summary .stat-card`);
      for (const theme of ['light', 'dark']) {
        await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
        for (const width of [1440, 1225, 768, 375, 320]) {
          await page.setViewportSize({ width, height: 1200 });
          await page.evaluate(() => document.querySelector('.portal-content').scrollTop = 0);
          const metrics = await page.evaluate(tab => {
            const form = document.querySelector('.profile-security-form');
            const inputs = [...document.querySelectorAll('#form-update-profile input')].map(e => e.getBoundingClientRect().left);
            return {
              overflow: document.documentElement.scrollWidth > innerWidth,
              formDisplay: form && getComputedStyle(form).display,
              inputsAligned: inputs.length ? Math.max(...inputs) - Math.min(...inputs) < 1 : null,
              metrics: document.querySelectorAll(`#staff-${tab}-summary .stat-card`).length,
              brokenActionCell: document.querySelector('td.staff-bookings-actions') !== null,
              dateText: document.querySelector('.booking-datetime')?.textContent,
              tableScrolls: [...document.querySelectorAll('.table-container')].every(e => e.scrollWidth <= e.clientWidth || getComputedStyle(e).overflowX === 'auto')
            };
          }, tab);
          assert.equal(metrics.overflow, false, `${tab}/${theme}/${width} page overflow`);
          if (tab === 'profile') { assert.equal(metrics.formDisplay, 'grid'); assert.equal(metrics.inputsAligned, true); }
          else { assert.equal(metrics.metrics, 4); assert.equal(metrics.tableScrolls, true); }
          if (tab === 'bookings') { assert.equal(metrics.brokenActionCell, false); assert.ok(metrics.dateText.includes('20/10/2026')); }
          tabResults.push({ tab, theme, width, ...metrics });
          if ([1225, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/staff-${tab}-${theme}-${width}.png`, fullPage: true });
        }
      }
    }
    await page.evaluate(async () => {
      const { api } = await import('/public/api.js');
      window.checkoutFixture = Array.from({ length: 14 }, (_, i) => ({ id: i + 1, roomId: i + 205, customerId: i + 1, checkInDate: '2026-10-20T14:00:00+07:00', checkOutDate: '2026-10-22T12:00:00+07:00', totalPrice: 5000000, status: i === 13 ? 'checked_out' : 'checked_in' }));
      window.checkoutFixtureApi = async endpoint => {
        if (endpoint === '/bookings') return window.checkoutFixture;
        if (endpoint === '/rooms') return window.checkoutFixture.map(b => ({ id: b.roomId, roomNumber: b.roomId }));
        if (endpoint === '/customers') return window.checkoutFixture.map(b => ({ id: b.customerId, fullName: `Khách thử ${b.customerId}` }));
        return [];
      };
      api.get = window.checkoutFixtureApi;
      document.getElementById('portal-heading').textContent = 'Nhận & trả phòng';
      const { renderCheckout } = await import('/public/js/staff/checkout.js');
      await renderCheckout(document.getElementById('view-container'), {});
    });
    await page.waitForSelector('.checkout-card');
    const checkoutResults = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      for (const width of [1600, 1225, 768, 375, 320]) {
        await page.setViewportSize({ width, height: 1200 });
        await page.evaluate(() => document.querySelector('.portal-content').scrollTop = 0);
        const metrics = await page.evaluate(() => ({
          overflow: document.documentElement.scrollWidth > innerWidth,
          columns: getComputedStyle(document.querySelector('.checkout-grid')).gridTemplateColumns.split(' ').length,
          cardHeight: document.querySelector('.checkout-card').getBoundingClientRect().height,
          cards: document.querySelectorAll('.checkout-card').length,
          buttonHeight: document.querySelector('.checkout-card .btn-pay').getBoundingClientRect().height,
          count: document.querySelector('#checkout-count').textContent,
          busy: document.querySelector('#checkout-grid').getAttribute('aria-busy')
        }));
        assert.equal(metrics.overflow, false, `Checkout overflow ${theme}/${width}`);
        assert.equal(metrics.columns, width >= 1600 ? 3 : width > 700 ? 2 : 1);
        assert.ok(metrics.cardHeight < 370, `Checkout card too tall ${metrics.cardHeight}`);
        assert.ok(metrics.buttonHeight >= 44);
        assert.equal(metrics.cards, 10);
        assert.equal(metrics.busy, 'false');
        assert.ok(metrics.count.startsWith('13'));
        checkoutResults.push({ theme, width, ...metrics });
        if ([1225, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/staff-checkout-${theme}-${width}.png`, fullPage: true });
      }
    }
    await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
    assert.equal(await page.locator('.checkout-card').count(), 3);
    await page.locator('#checkout-search').fill('205');
    assert.equal(await page.locator('.checkout-card').count(), 1);
    // Click the child span, exercising the payment button's currentTarget binding.
    await page.locator('.btn-pay span').click();
    assert.equal(await page.locator('#global-modal.active').count(), 1);
    assert.equal(await page.locator('#pay-amount').inputValue(), '5000000');
    await page.evaluate(() => document.getElementById('global-modal').classList.remove('active'));
    await page.locator('#checkout-search').fill('no matching guest');
    await page.waitForSelector('#checkout-clear-search');
    await page.locator('#checkout-clear-search').click();
    assert.equal(await page.locator('.checkout-card').count(), 10);
    await page.evaluate(async () => { const { api } = await import('/public/api.js'); api.get = async () => { throw Error('Fixture outage'); }; });
    await page.locator('#checkout-refresh').click();
    await page.waitForSelector('#checkout-retry');
    assert.equal(await page.locator('#checkout-search').isDisabled(), true);
    await page.evaluate(async () => { const { api } = await import('/public/api.js'); api.get = window.checkoutFixtureApi; });
    await page.locator('#checkout-retry').click();
    await page.waitForSelector('.checkout-card');
    await page.evaluate(() => window.checkoutFixture = []);
    await page.locator('#checkout-refresh').click();
    await page.waitForSelector('.checkout-empty');
    assert.ok((await page.locator('.checkout-empty').textContent()).includes('Chưa có phòng đang lưu trú'));
    await page.evaluate(async () => {
      const { api } = await import('/public/api.js');
      const states = ['vacant_dirty', 'vacant_clean', 'reserved', 'occupied_clean', 'out_of_order'];
      api.get = async () => Array.from({ length: 15 }, (_, i) => ({ id: i + 1, roomNumber: 101 + i, typeName: 'Standard', status: states[i % states.length] }));
      const { renderRoomMap } = await import('/public/js/staff/room-map.js');
      document.getElementById('portal-heading').textContent = 'Sơ đồ phòng';
      await renderRoomMap(document.getElementById('view-container'), {});
    });
    await page.waitForSelector('#staff-rooms-grid .room-card');
    const roomMapSpacing = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      for (const width of [1225, 768, 375, 320]) {
        await page.setViewportSize({ width, height: 1200 });
        await page.evaluate(() => document.querySelector('.portal-content').scrollTop = 0);
        roomMapSpacing.push(await page.evaluate(({ width, theme }) => {
          const legend = document.querySelector('.room-status-legend').getBoundingClientRect();
          const grid = document.querySelector('#staff-rooms-grid').getBoundingClientRect();
          return { width, theme, legendToGrid: grid.top - legend.bottom, overflow: document.documentElement.scrollWidth > innerWidth, cards: document.querySelectorAll('#staff-rooms-grid .room-card').length };
        }, { width, theme }));
        if ([1225, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/staff-room-map-${theme}-${width}.png`, fullPage: true });
      }
    }
    await page.evaluate(async () => {
      const { api } = await import('/public/api.js');
      window.supportCalls = 0;
      window.supportPosts = [];
      window.supportHistories = {
        3: [{ senderId: 3, content: 'Tôi muốn đặt phòng và cần hỗ trợ thêm.', createdAt: '2026-10-21T09:00:00+07:00' }, { senderId: 77, content: 'Chào anh/chị, em có thể hỗ trợ thông tin lưu trú.', createdAt: '2026-10-21T09:01:00+07:00' }],
        4: [{ senderId: 4, content: 'Khách thứ hai cần hỗ trợ.', createdAt: '2026-10-21T09:05:00+07:00' }]
      };
      api.get = async endpoint => {
        window.supportCalls++;
        if (endpoint.startsWith('/chat/inbox')) {
          if (window.supportFailInbox) throw Error('Fixture inbox failure');
          if (!endpoint.includes('staffId=77')) throw Error('Missing staff scope');
          return [{ senderId: 77, receiverId: 3, senderName: 'Nguyễn Minh Anh', content: 'Chào anh/chị, em có thể hỗ trợ thông tin lưu trú.', createdAt: '2026-10-21T09:01:00+07:00' }, { senderId: 4, receiverId: null, senderName: 'Trần Ngọc Lan', content: '<img src=x onerror=alert(1)>', createdAt: '2026-10-21T09:05:00+07:00' }];
        }
        const id = new URL(endpoint, location.origin).searchParams.get('u2');
        if (id === '77') throw Error('Staff ID incorrectly used as the conversation peer');
        if (window.supportFailHistory) throw Error('Fixture history failure');
        if (window.supportHoldHistory && id === '3') return new Promise(resolve => window.supportReleaseHistory = () => resolve([{ senderId: 3, content: 'STALE CUSTOMER THREE' }]));
        return window.supportHistories[id] || [];
      };
      api.post = async (endpoint, payload) => {
        window.supportPosts.push({ endpoint, payload });
        if (window.supportFailSend) throw Error('Fixture send failure');
        if (window.supportHoldSend) await new Promise(resolve => window.supportReleaseSend = resolve);
        window.supportHistories[payload.receiverId].push({ senderId: 77, content: payload.content, createdAt: '2026-10-21T09:10:00+07:00' });
        return null;
      };
      const { renderChat } = await import('/public/js/staff/chat.js');
      document.getElementById('portal-heading').textContent = 'Tin nhắn';
      await renderChat(document.getElementById('view-container'), { userId: 77 });
    });
    await page.waitForSelector('.support-inbox-item');
    assert.equal(await page.locator('.support-inbox-item').count(), 2);
    assert.equal(await page.locator('[data-customer-id="77"]').count(), 0);
    assert.equal(await page.locator('.support-inbox-item img').count(), 0);
    await page.setViewportSize({ width: 1225, height: 1100 });
    await page.locator('[data-customer-id="3"]').click();
    await page.waitForFunction(() => !document.getElementById('staff-chat-input').disabled);
    const supportResults = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      for (const width of [1440, 1225, 768, 375, 320]) {
        await page.setViewportSize({ width, height: 1100 });
        await page.evaluate(() => document.querySelector('.portal-content').scrollTop = 0);
        const metrics = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > innerWidth, inboxDisplay: getComputedStyle(document.querySelector('.support-inbox')).display, threadDisplay: getComputedStyle(document.querySelector('.support-conversation')).display, sendHeight: document.querySelector('#support-send').getBoundingClientRect().height, bubbles: document.querySelectorAll('.support-messages .chat-bubble').length, duplicateHeadingHidden: getComputedStyle(document.querySelector('.portal-content-bar')).display === 'none', refreshButtons: document.querySelectorAll('#support-refresh').length, composerBottom: document.querySelector('.support-composer').getBoundingClientRect().bottom, viewportHeight: innerHeight }));
        assert.equal(metrics.overflow, false, `Support overflow ${theme}/${width}`);
        assert.equal(metrics.inboxDisplay, width <= 820 ? 'none' : 'flex');
        assert.equal(metrics.threadDisplay, 'flex');
        assert.ok(metrics.sendHeight >= 44);
        assert.equal(metrics.bubbles, 2);
        const composeBounds = await page.evaluate(() => {
          const textarea = document.getElementById('staff-chat-input').getBoundingClientRect();
          const button = document.getElementById('support-send').getBoundingClientRect();
          return { sameRow: Math.abs((textarea.top + textarea.bottom) / 2 - (button.top + button.bottom) / 2) <= 1, adjacent: button.left >= textarea.right };
        });
        assert.equal(composeBounds.sameRow, true);
        assert.equal(composeBounds.adjacent, true);
        assert.equal(metrics.duplicateHeadingHidden, true);
        assert.equal(metrics.refreshButtons, 0);
        assert.ok(metrics.composerBottom <= metrics.viewportHeight, `Composer below viewport at ${width}`);
        supportResults.push({ theme, width, ...metrics });
        if ([1225, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/staff-support-${theme}-${width}.png`, fullPage: true });
        if (width <= 820) {
          await page.locator('#support-back').click();
          await page.locator('[data-customer-id="3"]').click();
          await page.waitForFunction(() => !document.getElementById('staff-chat-input').disabled);
        }
      }
    }
    const sizingResults = [];
    for (const [width, height] of [[1225, 1350], [1225, 700], [768, 700], [375, 700], [375, 480], [320, 568]]) {
      await page.setViewportSize({ width, height });
      const bounds = await page.evaluate(() => {
        const panel = document.querySelector('.support-workspace').getBoundingClientRect();
        const composer = document.querySelector('.support-composer').getBoundingClientRect();
        const content = document.querySelector('.portal-content');
        const style = getComputedStyle(content);
        return { panelBottom: panel.bottom, panelHeight: panel.height, panelWidth: panel.width, availableWidth: content.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight), composerBottom: composer.bottom, viewportHeight: innerHeight, outerScroll: content.scrollHeight - content.clientHeight, paddingBottom: parseFloat(style.paddingBottom) };
      });
      assert.ok(bounds.composerBottom <= height + 1, `Composer overflows at ${width}x${height}`);
      assert.ok(Math.abs(height - bounds.panelBottom - bounds.paddingBottom) <= 2, `Panel does not fill available height at ${width}x${height}`);
      assert.ok(bounds.outerScroll <= 1);
      assert.ok(Math.abs(bounds.panelWidth - bounds.availableWidth) <= 2, `Panel does not fill width at ${width}x${height}`);
      sizingResults.push({ width, height, ...bounds });
    }
    await fs.writeFile('docs/verification/web-ui/staff-support-sizing.json', JSON.stringify(sizingResults, null, 2));
    await page.setViewportSize({ width: 1225, height: 1100 });
    await page.locator('#support-search').fill('Ngọc Lan');
    assert.equal(await page.locator('.support-inbox-item').count(), 1);
    await page.locator('#support-search').fill('');
    await page.locator('#staff-chat-input').fill('Bản nháp cho khách 3');
    await page.locator('[data-customer-id="4"]').click();
    await page.waitForFunction(() => !document.getElementById('staff-chat-input').disabled);
    await page.locator('#staff-chat-input').fill('Bản nháp cho khách 4');
    await page.locator('[data-customer-id="3"]').click();
    await page.waitForFunction(() => !document.getElementById('staff-chat-input').disabled);
    assert.equal(await page.locator('#staff-chat-input').inputValue(), 'Bản nháp cho khách 3');
    await page.evaluate(() => window.supportFailSend = true);
    await page.locator('#support-send').click();
    await page.waitForFunction(() => document.getElementById('support-send-feedback').textContent.includes('Chưa gửi được'));
    assert.equal(await page.locator('#staff-chat-input').inputValue(), 'Bản nháp cho khách 3');
    await page.evaluate(() => { window.supportFailSend = false; window.supportHoldSend = true; });
    await page.locator('#support-send').click();
    await page.evaluate(() => document.getElementById('staff-chat-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    assert.equal(await page.evaluate(() => window.supportPosts.length), 2);
    await page.evaluate(() => { window.supportHoldSend = false; window.supportReleaseSend(); });
    await page.waitForFunction(() => document.getElementById('support-send-feedback').textContent.includes('Đã gửi'));
    assert.equal(await page.locator('#staff-chat-input').inputValue(), '');
    assert.equal((await page.evaluate(() => window.supportPosts[1].payload)).receiverId, 3);
    await page.evaluate(() => window.supportHoldHistory = true);
    await page.locator('[data-customer-id="3"]').click();
    await page.waitForFunction(() => typeof window.supportReleaseHistory === 'function');
    await page.locator('[data-customer-id="4"]').click();
    await page.waitForFunction(() => document.getElementById('staff-chat-bubbles').textContent.includes('Khách thứ hai'));
    await page.evaluate(() => { window.supportHoldHistory = false; window.supportReleaseHistory(); });
    assert.ok(!(await page.locator('#staff-chat-bubbles').textContent()).includes('STALE'));
    assert.equal(await page.locator('#staff-chat-input').inputValue(), 'Bản nháp cho khách 4');
    await page.evaluate(() => { window.supportFailHistory = true; window.supportFailInbox = true; });
    await page.waitForFunction(() => !document.getElementById('support-history-notice').hidden && !document.getElementById('support-inbox-notice').hidden);
    assert.equal(await page.locator('#staff-chat-input').inputValue(), 'Bản nháp cho khách 4');
    await page.evaluate(() => { window.supportFailHistory = false; window.supportFailInbox = false; });
    await page.locator('#support-history-retry').click();
    await page.waitForFunction(() => document.getElementById('support-history-notice').hidden);
    await page.evaluate(() => window.dispatchEvent(new Event('hotel:session-expired')));
    assert.equal(await page.locator('#staff-chat-input').isDisabled(), true);
    const stoppedCalls = await page.evaluate(() => window.supportCalls);
    await page.waitForTimeout(5200);
    assert.equal(await page.evaluate(() => window.supportCalls), stoppedCalls);
    await fs.writeFile('docs/verification/web-ui/staff-support-results.json', JSON.stringify(supportResults, null, 2));
    await fs.writeFile('docs/verification/web-ui/staff-room-map-spacing.json', JSON.stringify(roomMapSpacing, null, 2));
    await fs.writeFile('docs/verification/web-ui/staff-checkout-results.json', JSON.stringify(checkoutResults, null, 2));
    await fs.writeFile('docs/verification/web-ui/staff-tabs-results.json', JSON.stringify(tabResults, null, 2));
    await fs.writeFile('docs/verification/web-ui/staff-customers-results.json', JSON.stringify(customerResults, null, 2));
    await fs.writeFile('docs/verification/web-ui/shell-control-results.json', JSON.stringify(shellResults, null, 2));
    await fs.writeFile('docs/verification/web-ui/audit-fix-results.json', JSON.stringify(results, null, 2));
    console.log(`Passed ${results.length} role/theme/viewport checks, ${shellResults.length} shell comparisons, ${customerResults.length} customer table checks, ${tabResults.length} staff tab checks, ${checkoutResults.length} checkout checks, ${supportResults.length} support checks, and interaction/error/lifecycle checks.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
