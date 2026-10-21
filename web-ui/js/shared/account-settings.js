import { api, getAuthSession, setAuthSession, clearAuthSession } from '../../api.js';
import { escapeHtml as esc } from './ui.js';

const otpInput = (id, label) => `<label for="${id}">${label}</label><input class="ais-input account-otp" id="${id}" inputmode="numeric" autocomplete="one-time-code" type="text" maxlength="6" pattern="[0-9]{6}" placeholder="000000" required>`;
const passwordInput = (id, label, autocomplete) => `<div class="account-field"><label for="${id}">${label}</label><div class="account-password-field"><input class="ais-input" id="${id}" type="password" autocomplete="${autocomplete}" maxlength="72" ${autocomplete === 'new-password' ? 'minlength="8"' : ''} required><button class="ais-btn ais-btn-ghost ais-btn-sm" type="button" data-password="${id}" aria-label="Hiện ${label.toLowerCase()}" aria-pressed="false">Hiện</button></div></div>`;
const transport = { retryOnNetworkError: false, timeoutMs: 60000 };
const normalizeEmail = value => String(value || '').trim().toLowerCase();

export async function renderAccountSettings(container, session = getAuthSession(), options = {}) {
    if (!container || !session) return;
    const token = session.token;
    const role = String(session.role || '').toUpperCase();
    const mode = role === 'CUSTOMER' && ['profile', 'security'].includes(options.mode) ? options.mode : 'combined';
    const title = mode === 'profile' ? 'Hồ sơ cá nhân' : mode === 'security' ? 'Bảo mật tài khoản' : 'Hồ sơ & bảo mật';
    const description = mode === 'profile' ? 'Cập nhật thông tin cá nhân và hồ sơ lưu trú của bạn.' : mode === 'security' ? 'Xác minh email và bảo vệ tài khoản bằng mã OTP.' : 'Cập nhật thông tin, xác minh email và bảo vệ tài khoản của bạn.';
    const roleLabel = role === 'ADMIN' ? 'Quản trị viên' : role === 'CUSTOMER' ? 'Khách hàng' : 'Nhân viên';
    container.innerHTML = `<section class="account-settings-view account-mode-${mode}" aria-label="${title}">
      <div class="page-header"><div><h1>${title}</h1><p>${description}</p></div></div>
      <div class="account-summary"><div class="account-settings-avatar" aria-hidden="true">${esc((session.fullName || session.username || 'A').trim().charAt(0).toUpperCase())}</div><div><strong id="account-display-name">${esc(session.fullName || session.username)}</strong><small>${esc(session.username)} · ${roleLabel}</small></div><span class="ais-badge ais-badge-outline" id="account-email-badge">Đang tải hồ sơ…</span></div>
      <p class="ais-alert ais-alert-soft account-notice" id="account-load-error" role="alert"></p><button class="ais-btn ais-btn-outline" id="account-reload" hidden>Tải lại hồ sơ</button>
      <p id="account-operation" role="status" aria-live="polite"></p>
      <div class="account-settings-grid">
        <section class="ais-card ais-card-border account-card"><div class="ais-card-body">
          <div class="account-card-heading"><h2>${mode === 'security' ? 'Xác minh email' : 'Thông tin cá nhân'}</h2><p>${mode === 'security' ? 'Nhận mã OTP tại email đã lưu trong hồ sơ của bạn.' : mode === 'profile' ? 'Thông tin được dùng khi liên hệ và làm thủ tục lưu trú.' : 'Email đã xác minh sẽ nhận mã OTP khi đổi mật khẩu.'}</p></div>
          ${mode === 'security' ? `<div class="account-field"><label for="account-email">Email nhận mã OTP</label><input class="ais-input" id="account-email" type="email" readonly aria-describedby="account-email-help"><small id="account-email-help"></small></div><div class="ais-card-actions"><button class="ais-btn ais-btn-outline" id="account-email-send" type="button">Gửi mã xác minh email</button><a class="ais-btn ais-btn-ghost" href="portal.html?view=profile">Cập nhật email trong Hồ sơ</a></div>` : `
          <form id="account-profile-form"><fieldset class="account-fields" id="account-profile-fields" disabled><div class="account-profile-grid">
            <div class="account-field"><label for="account-username">Tên đăng nhập</label><input class="ais-input" id="account-username" value="${esc(session.username)}" readonly autocomplete="username"></div>
            <div class="account-field"><label for="account-fullname">Họ và tên</label><input class="ais-input" id="account-fullname" value="${esc(session.fullName)}" maxlength="100" autocomplete="name" required></div>
            <div class="account-field"><label for="account-phone">Số điện thoại</label><input class="ais-input" id="account-phone" type="tel" value="${esc(session.phone)}" maxlength="20" autocomplete="tel"></div>
            <div class="account-field"><label for="account-email">Email</label><input class="ais-input" id="account-email" type="email" value="${esc(session.email)}" maxlength="100" autocomplete="email" aria-describedby="account-email-help"><small id="account-email-help">Lưu email trước khi yêu cầu mã xác minh.</small></div>
            <div class="ais-card-actions"><button class="ais-btn ais-btn-outline" type="submit">Lưu hồ sơ</button>${mode === 'profile' ? '<a class="ais-btn ais-btn-ghost" href="portal.html?view=security">Đến Bảo mật & xác minh email</a>' : '<button class="ais-btn ais-btn-outline" id="account-email-send" type="button">Gửi mã xác minh email</button>'}</div>
          </div></fieldset></form>`}
          <p class="ais-alert ais-alert-soft account-notice" id="account-profile-message" role="status" aria-live="polite"></p>
          ${mode !== 'profile' ? `
          <form class="account-otp-panel" id="account-email-verify" hidden><h3>Xác minh email</h3><p id="account-email-target"></p><div class="account-field">${otpInput('account-email-code', 'Mã OTP từ email')}</div><div class="ais-card-actions"><button class="ais-btn ais-btn-outline" type="submit">Xác minh email</button><button class="ais-btn ais-btn-ghost" id="account-email-resend" type="button">Gửi lại mã</button><button class="ais-btn ais-btn-ghost" id="account-email-cancel" type="button">Hủy</button></div></form>
          ` : ''}
        </div></section>
        ${mode !== 'profile' ? `
        <section class="ais-card ais-card-border account-card"><div class="ais-card-body">
          <div class="account-card-heading"><h2>Đổi mật khẩu</h2><p>Xác nhận bằng mật khẩu hiện tại và mã OTP gửi đến email đã xác minh.</p></div>
          <div class="account-security-note" id="account-password-help">Xác minh email ở phần thông tin cá nhân trước khi đổi mật khẩu.</div>
          <form id="account-password-request"><fieldset class="account-fields" id="account-password-fields" disabled>
            ${passwordInput('current-password', 'Mật khẩu hiện tại', 'current-password')}
            ${passwordInput('new-password', 'Mật khẩu mới', 'new-password')}
            ${passwordInput('confirm-password', 'Xác nhận mật khẩu mới', 'new-password')}
            <small>Ít nhất 8 ký tự. Không sử dụng lại mật khẩu hiện tại.</small>
            <button class="ais-btn ais-btn-primary" id="account-password-send" type="submit">Gửi mã OTP đổi mật khẩu</button>
          </fieldset></form>
          <form class="account-otp-panel" id="account-password-confirm" hidden><h3>Xác nhận đổi mật khẩu</h3><p id="account-password-target"></p><div class="account-field">${otpInput('account-password-code', 'Mã OTP đổi mật khẩu')}</div><div class="ais-card-actions"><button class="ais-btn ais-btn-primary" type="submit">Xác nhận đổi mật khẩu</button><button class="ais-btn ais-btn-ghost" id="account-password-resend" type="button">Gửi lại mã</button><button class="ais-btn ais-btn-ghost" id="account-password-cancel" type="button">Hủy</button></div><small>Đổi mật khẩu thành công sẽ kết thúc phiên đăng nhập này.</small></form>
          <p class="ais-alert ais-alert-soft account-notice" id="account-password-message" role="status" aria-live="polite"></p>
        </div></section>
        ` : ''}
      </div>
      ${options.showIdentity ? '<details class="ais-collapse ais-collapse-arrow account-identity"><summary class="ais-collapse-title">Hồ sơ lưu trú & giấy tờ định danh</summary><div class="ais-collapse-content"><div id="account-identity-content"></div></div></details>' : ''}
    </section>`;
    if (!document.getElementById('account-settings-ui')) {
        const link = document.createElement('link'); link.id = 'account-settings-ui'; link.rel = 'stylesheet';
        link.href = new URL('../../css/account-settings-ui.css?v=2', import.meta.url).href; document.head.append(link);
    }
    const root = container.querySelector('.account-settings-view'), $ = selector => root.querySelector(selector);
    const on = (selector, event, handler) => $(selector)?.addEventListener(event, handler);
    const hidden = (selector, value) => { const node = $(selector); if (node) node.hidden = value; };
    const disabled = (selector, value) => { const node = $(selector); if (node) node.disabled = value; };
    const active = () => root.isConnected && getAuthSession()?.token === token;
    let profile = null, busy = false, emailFlow = null, passwordFlow = null, identityLoaded = false;
    const message = (target, text, error = false) => { $(target).textContent = text; $(target).classList.toggle('account-error', error); };
    const post = (path, body = {}) => api.post('/account/' + path, body, transport);
    const remaining = flow => Math.max(0, Math.ceil((Date.parse(flow?.resendAt || '') - Date.now()) / 1000) || 0);
    function sync() {
        if (!active()) return;
        const dirtyEmail = normalizeEmail($('#account-email').value) !== normalizeEmail(profile?.email);
        disabled('#account-profile-fields', busy || !profile);
        disabled('#account-password-fields', busy || !profile?.emailVerified || !!passwordFlow);
        hidden('#account-password-request', !!passwordFlow);
        hidden('#account-password-confirm', !passwordFlow);
        hidden('#account-email-verify', !emailFlow);
        disabled('#account-email-send', busy || !profile?.email || dirtyEmail || !!profile?.emailVerified || !profile?.mailConfigured || !!emailFlow);
        disabled('#account-password-send', busy || !profile?.emailVerified || !profile?.mailConfigured);
        $('#account-email-help').textContent = dirtyEmail ? 'Email đã thay đổi. Lưu hồ sơ trước khi yêu cầu mã mới.' : profile?.emailVerified ? 'Email đã xác minh, có thể dùng để nhận OTP đổi mật khẩu.' : mode === 'profile' ? 'Xác minh email trong tab Bảo mật sau khi lưu hồ sơ.' : mode === 'security' && !profile?.email ? 'Thêm email trong tab Hồ sơ trước khi yêu cầu mã xác minh.' : 'Xác minh bằng mã OTP gửi đến hộp thư của bạn.';
        $('#account-email-badge').textContent = !profile ? 'Đang tải hồ sơ…' : dirtyEmail ? 'Email chưa lưu' : profile.emailVerified ? 'Email đã xác minh' : 'Email chưa xác minh';
        $('#account-email-badge').classList.toggle('ais-badge-success', !!profile?.emailVerified && !dirtyEmail);
        if ($('#account-password-help')) $('#account-password-help').textContent = profile && !profile.mailConfigured ? 'Hiện chưa thể gửi email xác thực. Vui lòng liên hệ quản trị viên.' : profile?.emailVerified ? `Mã OTP sẽ được gửi đến ${profile.email}. Chỉ bạn mới nên sử dụng mã này.` : 'Xác minh email trước khi đổi mật khẩu.';
        for (const [name, flow] of [['email', emailFlow], ['password', passwordFlow]]) {
            const button = $(`#account-${name}-resend`), seconds = remaining(flow);
            if (!button) continue;
            button.disabled = busy || !flow || seconds > 0;
            button.textContent = seconds > 0 ? `Gửi lại sau ${seconds}s` : 'Gửi lại mã';
            if (flow) {
                const ttl = Math.max(0, Math.ceil((Date.parse(flow.expiresAt) - Date.now()) / 1000));
                $(`#account-${name}-target`).textContent = ttl > 0 ? `Đã gửi mã đến ${flow.maskedEmail}. Còn ${Math.floor(ttl / 60)}:${String(ttl % 60).padStart(2, '0')} để xác nhận.` : 'Mã đã hết hạn. Gửi lại mã mới để tiếp tục.';
                $(`#account-${name === 'email' ? 'email-verify' : 'password-confirm'} button[type="submit"]`).disabled = busy || ttl === 0;
            }
        }
    }
    function apply(data, fill = true) {
        if (profile && normalizeEmail(profile.email) !== normalizeEmail(data.email)) { emailFlow = passwordFlow = null; }
        profile = data;
        if (fill) for (const [field, key] of [['username','username'],['fullname','fullName'],['phone','phone'],['email','email']]) { const input = $(`#account-${field}`); if (input) input.value = data[key] || ''; }
        $('#account-display-name').textContent = data.fullName || data.username;
        const stored = getAuthSession();
        if (stored?.token === token) setAuthSession({ ...stored, fullName: data.fullName, phone: data.phone, email: data.email, emailVerified: data.emailVerified });
        const sidebarName = document.getElementById('user-display-name'); if (sidebarName) sidebarName.textContent = data.fullName || data.username;
        const topbarName = document.getElementById('topbar-user-name'); if (topbarName) topbarName.textContent = data.fullName || data.username;
        const avatar = root.querySelector('.account-settings-avatar'); if (avatar) avatar.textContent = (data.fullName || data.username || 'A').trim().charAt(0).toUpperCase();
        sync();
    }
    async function run(target, operation, status) {
        if (busy || !active()) return;
        busy = true; root.setAttribute('aria-busy', 'true'); $('#account-operation').textContent = status;
        const controls = Array.from(root.querySelectorAll('button,input,select,textarea')).map(el => [el, el.disabled]);
        controls.forEach(([el]) => el.disabled = true); sync();
        message(target, '');
        try { await operation(); }
        catch (error) {
            if (active()) {
                message(target, error.name === 'TimeoutError' || error.name === 'AbortError' ? 'Yêu cầu đang mất kết nối. Kiểm tra hộp thư và tải lại hồ sơ trước khi thử lại.' : error.message || 'Chưa thể hoàn tất. Vui lòng thử lại.', true);
                if (error.status === 410) { if (target.includes('password')) passwordFlow = null; else emailFlow = null; }
            }
        } finally {
            busy = false;
            if (active()) { controls.forEach(([el, disabled]) => el.disabled = disabled); root.removeAttribute('aria-busy'); $('#account-operation').textContent = ''; sync(); }
        }
    }
    const load = () => run('#account-load-error', async () => { const data = await api.get('/account/profile'); if (active()) { apply(data); $('#account-reload').hidden = true; } }, 'Đang tải hồ sơ…').then(() => { if (active() && !profile) $('#account-reload').hidden = false; });
    $('#account-reload').onclick = load;
    on('#account-profile-form', 'submit', event => { event.preventDefault(); run('#account-profile-message', async () => {
        const data = await post('profile', { fullName: $('#account-fullname').value.trim(), phone: $('#account-phone').value.trim(), email: $('#account-email').value.trim() });
        if (active()) { apply(data); message('#account-profile-message', 'Đã lưu hồ sơ.'); }
    }, 'Đang lưu hồ sơ…'); });
    $('#account-email').addEventListener('input', sync);
    async function sendEmail() {
        await run('#account-profile-message', async () => {
            const flow = await post('email/request'); if (!active()) return;
            emailFlow = flow; $('#account-email-code').value = '';
            $('#account-email-target').textContent = `Đã gửi mã đến ${flow.maskedEmail}. Mã có hiệu lực 5 phút.`;
        }, 'Đang gửi mã xác minh email…');
        if (active() && emailFlow) $('#account-email-code').focus();
    }
    on('#account-email-send', 'click', sendEmail); on('#account-email-resend', 'click', sendEmail);
    on('#account-email-cancel', 'click', () => { emailFlow = null; $('#account-email-code').value = ''; sync(); });
    on('#account-email-verify', 'submit', event => { event.preventDefault(); if (!emailFlow) return; run('#account-profile-message', async () => {
        const data = await post('email/verify', { challengeId: emailFlow.challengeId, code: $('#account-email-code').value.trim() });
        if (active()) { emailFlow = null; $('#account-email-code').value = ''; apply(data, false); message('#account-profile-message', 'Email đã được xác minh. Bạn có thể đổi mật khẩu bằng OTP.'); }
    }, 'Đang xác minh email…'); });
    async function sendPassword() {
        if (!profile?.emailVerified || busy) return;
        const currentPassword = $('#current-password').value, newPassword = $('#new-password').value;
        if (!currentPassword || newPassword.length < 8 || new TextEncoder().encode(newPassword).length > 72 || newPassword !== $('#confirm-password').value || currentPassword === newPassword) {
            message('#account-password-message', 'Nhập mật khẩu hiện tại, mật khẩu mới ít nhất 8 ký tự và xác nhận trùng khớp. Mật khẩu mới phải khác mật khẩu cũ.', true); return;
        }
        await run('#account-password-message', async () => {
            const flow = await post('password/request', { currentPassword }); if (!active()) return;
            passwordFlow = flow; $('#account-password-code').value = '';
            $('#account-password-target').textContent = `Đã gửi mã đến ${flow.maskedEmail}. Mã có hiệu lực 5 phút.`;
        }, 'Đang gửi mã OTP đổi mật khẩu…');
        if (active() && passwordFlow) $('#account-password-code').focus();
    }
    on('#account-password-request', 'submit', event => { event.preventDefault(); sendPassword(); });
    on('#account-password-resend', 'click', sendPassword);
    on('#account-password-cancel', 'click', () => { passwordFlow = null; $('#account-password-code').value = ''; sync(); });
    on('#account-password-confirm', 'submit', event => { event.preventDefault(); if (!passwordFlow) return; run('#account-password-message', async () => {
        await post('password/confirm', { challengeId: passwordFlow.challengeId, code: $('#account-password-code').value.trim(), newPassword: $('#new-password').value });
        if (!active()) return;
        for (const field of root.querySelectorAll('#current-password, #new-password, #confirm-password, .account-otp')) field.value = '';
        clearAuthSession();
        root.innerHTML = '<section class="ais-card ais-card-border account-card"><div class="ais-card-body"><h1>Đã đổi mật khẩu</h1><p>Phiên đăng nhập này đã kết thúc. Đăng nhập lại bằng mật khẩu mới để tiếp tục.</p><a class="ais-btn ais-btn-primary" href="portal.html">Đăng nhập lại</a></div></section>';
    }, 'Đang xác nhận đổi mật khẩu…'); });
    root.querySelectorAll('[data-password]').forEach(button => button.onclick = () => {
        const input = $('#' + button.dataset.password), visible = input.type === 'password';
        input.type = visible ? 'text' : 'password'; button.textContent = visible ? 'Ẩn' : 'Hiện'; button.setAttribute('aria-pressed', String(visible)); button.setAttribute('aria-label', `${visible ? 'Ẩn' : 'Hiện'} ${input.labels?.[0]?.textContent?.toLowerCase() || 'mật khẩu'}`);
    });
    const refresh = () => { if (active()) load(); };
    window.addEventListener('hotel:profile-updated', refresh);
    const timer = window.setInterval(() => { if (!active()) { window.clearInterval(timer); window.removeEventListener('hotel:profile-updated', refresh); } else sync(); }, 1000);
    if (options.showIdentity) $('#account-identity-content').closest('details').addEventListener('toggle', async event => {
        if (!event.currentTarget.open || identityLoaded) return;
        identityLoaded = true;
        const host = $('#account-identity-content'); host.textContent = 'Đang tải hồ sơ lưu trú…';
        try { const { renderProfile } = await import('../customer/profile.js'); if (active()) await renderProfile(host, getAuthSession()); }
        catch { if (active()) { host.textContent = 'Chưa thể tải hồ sơ lưu trú. Đóng và mở lại để thử lại.'; identityLoaded = false; } }
    });
    await load();
}
