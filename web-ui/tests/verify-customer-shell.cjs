// Run from the repository root with Playwright in NODE_PATH. All APIs are fixtures.
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
    const browser = await chromium.launch({ headless: true, channel: 'chrome' });
    const results = [];
    try {
        for (const [tier, points] of [['member', 0], ['silver', 500], ['gold', 1000], ['vip', 5000]]) {
            const page = await browser.newPage({ viewport: { width: 1340, height: 1168 } });
            await page.route('**/*', async route => {
                const url = new URL(route.request().url());
                if (url.hostname !== 'customer-ui.test') return route.abort();
                const portalAsset = ['/portal.html', '/portal-app.js', '/portal.css', '/portal-customer.css', '/portal-admin.css', '/portal-theme.css'].includes(url.pathname);
                const file = path.join(process.cwd(), 'web-ui', portalAsset ? '' : 'public', url.pathname);
                try {
                    let body = await fs.readFile(file);
                    if (url.pathname === '/portal.html') body = body.toString().replace(/<script\b[^>]*>[\s\S]*?<\/script>/g, '');
                    await route.fulfill({ body, contentType: file.endsWith('.css') ? 'text/css' : file.endsWith('.js') ? 'text/javascript' : 'text/html' });
                } catch { await route.fulfill({ status: 404, body: '' }); }
            });
            await page.goto('http://customer-ui.test/portal.html?view=dashboard');
            await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; animation: none !important; }' });
            // Model the stacking contexts normally supplied by CDN backdrop utilities.
            await page.addStyleTag({ content: '.greeting-banner { position: relative; backdrop-filter: blur(12px); } #member-stats-wrapper { position: relative; z-index: 1; }' });
            await page.evaluate(async points => {
                window.lucide = { createIcons() {} };
                const session = { token: 'fixture-customer-token-123456', role: 'CUSTOMER', userId: 3, customerId: 3, username: 'customer_fixture', fullName: 'Khách hàng kiểm thử' };
                localStorage.setItem('hotel_auth_session', JSON.stringify(session));
                const { api } = await import('/api.js');
                api.get = async endpoint => endpoint.startsWith('/loyalty/history/') ? [{ type: 'earn', pointsChange: points }] : endpoint.startsWith('/notifications') ? (window.__emptyNotifications ? [] : Array.from({ length: 12 }, (_, index) => ({ id: index + 1, title: 'Thông báo kiểm thử', message: 'Nội dung hỗ trợ khách hàng đủ dài để kiểm tra xuống dòng và cuộn trong hộp thông báo.', voucher_code: index === 0 ? 'VOUCHER_TEST_LONG_CODE_1234567890' : '', is_read: false, created_at: '2026-10-21T04:00:00Z' }))) : [];
                api.put = async () => ({});
                api.post = async () => { throw Error('No mutations are expected in shell verification'); };
                await import('/portal-app.js');
            }, points);
            await page.waitForFunction(tier => document.body.dataset.customerTier === tier && document.querySelectorAll('#portal-nav button').length === 5, tier);
            assert.equal(await page.locator('#portal-sidebar #portal-account').count(), 1);
            for (const theme of ['light', 'dark']) {
                await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
                for (const width of [1340, 1000, 768, 375, 320]) {
                    await page.setViewportSize({ width, height: 1168 });
                    await page.mouse.move(width - 5, 1150);
                    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
                    const metrics = await page.evaluate(() => {
                        const sidebar = document.getElementById('portal-sidebar');
                        const nav = [...document.querySelectorAll('#portal-nav button')].map(button => { const bounds = button.getBoundingClientRect(); return { top: bounds.top, width: bounds.width, height: bounds.height }; });
                        return {
                            accountParent: document.getElementById('portal-account').parentElement.id,
                            brandLeft: document.querySelector('.portal-brand').getBoundingClientRect().left,
                            sidebarDisplay: getComputedStyle(sidebar).display,
                            sidebarWidth: sidebar.getBoundingClientRect().width,
                            navDisplay: getComputedStyle(document.getElementById('portal-nav')).display,
                            navFlow: getComputedStyle(document.getElementById('portal-nav')).gridAutoFlow,
                            navDirection: getComputedStyle(document.getElementById('portal-nav')).flexDirection,
                            nav,
                            visibleLogouts: [...document.querySelectorAll('#logout-button, #topbar-logout-button')].filter(button => button.getBoundingClientRect().width > 0).length,
                            headerAccount: getComputedStyle(document.querySelector('.portal-topbar-account')).display,
                            legacyHeader: document.getElementById('customer-header-account').getBoundingClientRect().width,
                            accent: getComputedStyle(document.body).getPropertyValue('--portal-gold').trim(),
                            overflow: document.documentElement.scrollWidth > innerWidth
                        };
                    });
                    assert.equal(metrics.accountParent, 'portal-sidebar');
                    assert.equal(metrics.brandLeft, width <= 480 ? 12 : 16);
                    assert.equal(metrics.visibleLogouts, 1, `${tier}/${theme}/${width} duplicate or missing logout`);
                    assert.equal(metrics.legacyHeader, 0);
                    assert.equal(metrics.headerAccount, width > 820 ? 'none' : 'flex');
                    assert.equal(metrics.overflow, false, `${tier}/${theme}/${width} overflow`);
                    assert.ok(metrics.nav.every(button => button.width > 0 && button.height >= 38));
                    if (width > 820) {
                        assert.equal(metrics.sidebarDisplay, 'flex');
                        assert.equal(metrics.sidebarWidth, width > 1100 ? 248 : 78);
                        assert.ok(metrics.nav.every(button => button.width >= metrics.sidebarWidth - 40), JSON.stringify({ tier, theme, width, ...metrics }));
                        assert.ok(metrics.nav.every((button, i) => i === 0 || button.top >= metrics.nav[i - 1].top + metrics.nav[i - 1].height), JSON.stringify({ tier, theme, width, ...metrics }));
                    }
                    await page.locator('#btn-notification-bell').click();
                    await page.waitForFunction(() => document.querySelectorAll('#notification-list .notif-item').length === 12);
                    const notification = await page.locator('#notification-dropdown').evaluate(panel => {
                        const rect = panel.getBoundingClientRect();
                        const probe = document.createElement('span');
                        probe.style.background = 'var(--customer-surface)';
                        panel.appendChild(probe);
                        const surface = getComputedStyle(probe).backgroundColor;
                        probe.remove();
                        const list = document.getElementById('notification-list');
                        return {
                            onTop: [rect.top + 25, rect.bottom - 20].every(y => panel.contains(document.elementFromPoint(rect.left + rect.width / 2, y))),
                            inViewport: rect.left >= 0 && rect.right <= innerWidth && rect.top >= 0 && rect.bottom <= innerHeight,
                            bounds: { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom },
                            themed: getComputedStyle(panel).backgroundColor === surface && getComputedStyle(panel.querySelector('.notification-title')).color === getComputedStyle(document.body).color,
                            scrolls: list.scrollHeight > list.clientHeight && getComputedStyle(list).overflowY === 'auto'
                        };
                    });
                    assert.ok(notification.onTop && notification.inViewport && notification.themed && notification.scrolls, JSON.stringify({ tier, theme, width, notification }));
                    if (tier === 'member' && [1340, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/customer-notifications-${theme}-${width}.png`, fullPage: true });
                    await page.locator('#btn-notification-bell').click();
                    assert.ok(await page.locator('#notification-dropdown').isHidden());
                    metrics.notification = notification;
                    results.push({ tier, theme, width, ...metrics });
                    if ([1340, 375].includes(width)) await page.screenshot({ path: `docs/verification/web-ui/customer-shell-${tier}-${theme}-${width}.png`, fullPage: true });
                }
            }
            await page.setViewportSize({ width: 1340, height: 1168 });
            await page.evaluate(() => window.__emptyNotifications = true);
            await page.locator('#btn-notification-bell').click();
            await page.waitForSelector('#notification-list .notification-empty');
            await page.locator('.portal-brand').click({ trial: true });
            await page.mouse.click(300, 1150);
            assert.ok(await page.locator('#notification-dropdown').isHidden());
            await page.locator('#admin-sidebar-toggle').click();
            await page.waitForFunction(() => document.body.classList.contains('portal-sidebar-collapsed'));
            assert.equal(await page.locator('#portal-sidebar').evaluate(element => element.getBoundingClientRect().width), 84);
            await page.locator('#admin-sidebar-toggle').click();
            await page.waitForFunction(() => !document.body.classList.contains('portal-sidebar-collapsed'));
            await page.close();
        }
        for (const theme of ['light', 'dark']) {
            assert.equal(new Set(results.filter(row => row.width === 1340 && row.theme === theme).map(row => row.accent)).size, 4);
        }
        await fs.writeFile('docs/verification/web-ui/customer-shell-results.json', JSON.stringify(results, null, 2));
        console.log(`Passed ${results.length} customer role/tier/theme/viewport checks with real shell initialization, plus sidebar collapse/expand.`);
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
