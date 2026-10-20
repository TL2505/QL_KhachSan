import { escapeHtml } from '../shared/ui.js';

// Presentation only. The shared view retains all existing submit handlers.
export function staffProfileMarkup(session) {
    const username = String(session.username || 'Nhân viên');
    const initial = username.trim().charAt(0).toUpperCase() || 'N';
    const passwordField = (id, label, autocomplete) => `<div class="sap-field"><label for="${id}">${label}</label><div class="sap-password"><input type="password" id="${id}" autocomplete="${autocomplete}" required><button type="button" data-password-toggle="${id}" data-field-label="${label.toLowerCase()}" aria-label="Hiện ${label.toLowerCase()}" aria-pressed="false">Hiện</button></div></div>`;
    return `<style>
        body.staff-portal:has(.sap-view) .portal-shell { grid-template-columns:248px minmax(0,1fr); }
        body.staff-portal:has(.sap-view) :is(.portal-sidebar,.portal-nav) { min-width:0; }
        .sap-view { color:var(--portal-text); min-width:0; }
        .sap-view * { box-sizing:border-box; }
        .sap-account { display:flex; align-items:center; gap:16px; padding:22px 24px; margin-bottom:22px; border:1px solid var(--portal-border); border-radius:14px; background:var(--portal-panel); }
        .sap-avatar { display:grid; place-items:center; width:56px; height:56px; border:1px solid color-mix(in srgb,var(--portal-gold) 35%,transparent); border-radius:15px; flex:none; background:color-mix(in srgb,var(--portal-gold) 10%,transparent); color:var(--portal-soft); font-size:24px; font-weight:600; }
        .sap-account-copy { min-width:0; }
        .sap-eyebrow { margin:0 0 5px; color:var(--portal-muted); font-size:10px; font-weight:600; letter-spacing:.1em; text-transform:uppercase; }
        .sap-account h2 { margin:0; font:600 19px/1.4 system-ui,sans-serif; color:var(--portal-text); overflow-wrap:anywhere; }
        .sap-role { display:inline-flex; align-items:center; gap:7px; margin-left:auto; padding:7px 10px; border:1px solid var(--portal-border); border-radius:7px; color:var(--portal-soft); font-size:11px; white-space:nowrap; }
        .sap-view svg { width:17px; height:17px; flex:none; }
        #view-container .sap-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); align-items:stretch; gap:20px; }
        #view-container .sap-card { display:flex; flex-direction:column; min-width:0; padding:24px; border:1px solid var(--portal-border); border-radius:14px; background:var(--portal-panel); }
        .sap-card-heading { display:flex; align-items:flex-start; gap:12px; padding-bottom:20px; margin-bottom:22px; border-bottom:1px solid var(--portal-border); }
        .sap-card-icon { display:grid; place-items:center; width:36px; height:36px; flex:none; border-radius:10px; background:var(--portal-surface); color:var(--portal-gold); }
        .sap-card-heading h3 { margin:0 0 5px; font-family:inherit; font-size:15px; line-height:1.5; font-weight:650; }
        .sap-card-heading p { margin:0; color:var(--portal-muted); font-size:12px; line-height:1.6; }
        .sap-view form { display:flex; flex-direction:column; flex:1; gap:17px; min-width:0; }
        .sap-field { min-width:0; }
        .sap-field > label { display:block; margin:0 0 8px; color:var(--portal-text); font-size:12px; line-height:1.5; font-weight:600; }
        #view-container .sap-field input { display:block; width:100%; min-width:0; height:44px; min-height:44px; margin:0; padding:10px 13px; border:1px solid var(--portal-border); border-radius:8px; background:var(--portal-bg); color:var(--portal-text); font:inherit; font-size:13px; }
        #view-container .sap-field input:disabled { color:var(--portal-muted); background:var(--portal-surface); cursor:not-allowed; }
        .sap-hint { margin:7px 0 0; color:var(--portal-muted); font-size:11px; line-height:1.6; }
        .sap-password { position:relative; }
        #view-container .sap-password input { padding-right:63px; }
        #view-container .sap-password button { position:absolute; right:5px; top:5px; min-height:34px; height:34px; padding:5px 10px; border:0; border-radius:5px; background:transparent; color:var(--portal-muted); font:inherit; font-size:11px; cursor:pointer; }
        #view-container .sap-password button:hover { background:var(--portal-surface); color:var(--portal-soft); }
        .sap-note { display:flex; gap:10px; padding:13px; border:1px solid var(--portal-border); border-radius:9px; background:color-mix(in srgb,var(--portal-bg) 45%,transparent); color:var(--portal-muted); font-size:11px; line-height:1.7; }
        .sap-note svg { margin-top:2px; color:var(--portal-gold); }
        .sap-feedback { margin-bottom:16px; padding:11px 13px; border-radius:8px; font-size:12px; line-height:1.7; overflow-wrap:anywhere; }
        .sap-feedback-success { color:#6ee7b7; background:rgba(52,211,153,.08); }
        .sap-feedback-error { color:#fda4af; background:rgba(251,113,133,.08); }
        .sap-form-footer { display:flex; justify-content:flex-end; padding-top:20px; margin-top:auto; border-top:1px solid var(--portal-border); }
        #view-container .sap-submit { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:42px; padding:10px 17px; border:1px solid var(--portal-gold); border-radius:8px; color:#161510; background:var(--portal-gold); font:inherit; font-size:12px; font-weight:650; cursor:pointer; }
        .sap-view :is(button,input):focus-visible { outline:2px solid var(--portal-gold); outline-offset:3px; }
        html[data-theme="light"] body.staff-portal:has(.sap-view) .portal-content { background:var(--portal-bg); }
        html[data-theme="light"] #view-container .sap-submit { color:#fff; }
        html[data-theme="light"] .sap-feedback-success { color:#146c50; }
        html[data-theme="light"] .sap-feedback-error { color:#b3334c; }
        @media(max-width:1000px) { body.staff-portal:has(.sap-view) .portal-shell { grid-template-columns:minmax(0,1fr); } }
        @media(max-width:760px) { #view-container .sap-grid { grid-template-columns:minmax(0,1fr); gap:16px; } #view-container .sap-card { padding:20px; } .sap-account { padding:18px; flex-wrap:wrap; gap:12px; } .sap-role { margin-left:0; } .sap-avatar { width:46px; height:46px; font-size:21px; } }
        @media(max-width:420px) { .sap-role { margin-left:58px; } .sap-account h2 { font-size:17px; } #view-container .sap-submit { width:100%; } }
    </style>
    <section class="sap-view" aria-label="Hồ sơ và bảo mật của nhân viên">
        <div class="sap-account"><span class="sap-avatar" aria-hidden="true">${escapeHtml(initial)}</span><div class="sap-account-copy"><p class="sap-eyebrow">Tài khoản của bạn</p><h2>${escapeHtml(username)}</h2></div><span class="sap-role"><i data-lucide="badge-check" aria-hidden="true"></i>Nhân viên khách sạn</span></div>
        <div class="sap-grid">
            <section class="sap-card" aria-labelledby="sap-personal-title">
                <div class="sap-card-heading"><span class="sap-card-icon"><i data-lucide="user-round" aria-hidden="true"></i></span><div><h3 id="sap-personal-title">Thông tin cá nhân</h3><p>Cập nhật thông tin liên hệ của bạn.</p></div></div>
                <div id="profile-success" class="sap-feedback sap-feedback-success" role="status" style="display:none"></div><div id="profile-error" class="sap-feedback sap-feedback-error" role="alert" style="display:none"></div>
                <form id="form-update-profile">
                    <div class="sap-field"><label for="prof-username">Tên đăng nhập</label><input id="prof-username" type="text" value="${escapeHtml(session.username || '')}" autocomplete="username" disabled><p class="sap-hint">Tên đăng nhập dùng để truy cập tài khoản.</p></div>
                    <div class="sap-field"><label for="prof-fullname">Họ và tên</label><input type="text" id="prof-fullname" value="${escapeHtml(session.fullName || '')}" autocomplete="name" required></div>
                    <div class="sap-field"><label for="prof-phone">Số điện thoại</label><input type="text" id="prof-phone" value="${escapeHtml(session.phone || '')}" autocomplete="tel" inputmode="tel"></div>
                    <div class="sap-field"><label for="prof-email">Email</label><input type="email" id="prof-email" value="${escapeHtml(session.email || '')}" autocomplete="email"></div>
                    <div class="sap-form-footer"><button type="submit" class="sap-submit"><i data-lucide="save" aria-hidden="true"></i>Lưu thay đổi</button></div>
                </form>
            </section>
            <section class="sap-card" aria-labelledby="sap-password-title">
                <div class="sap-card-heading"><span class="sap-card-icon"><i data-lucide="lock-keyhole" aria-hidden="true"></i></span><div><h3 id="sap-password-title">Đổi mật khẩu</h3><p>Chọn mật khẩu riêng cho tài khoản làm việc.</p></div></div>
                <div id="pass-success" class="sap-feedback sap-feedback-success" role="status" style="display:none"></div><div id="pass-error" class="sap-feedback sap-feedback-error" role="alert" style="display:none"></div>
                <form id="form-change-password">
                    ${passwordField('pass-old', 'Mật khẩu hiện tại', 'current-password')}
                    ${passwordField('pass-new', 'Mật khẩu mới', 'new-password')}
                    ${passwordField('pass-confirm', 'Xác nhận mật khẩu mới', 'new-password')}
                    <div class="sap-note"><i data-lucide="shield-check" aria-hidden="true"></i><span>Không chia sẻ mật khẩu với người khác. Kiểm tra mật khẩu mới trước khi lưu thay đổi.</span></div>
                    <div class="sap-form-footer"><button type="submit" class="sap-submit"><i data-lucide="key-round" aria-hidden="true"></i>Đổi mật khẩu</button></div>
                </form>
            </section>
        </div>
    </section>`;
}

export function bindStaffProfileUi(container) {
    container.querySelectorAll('[data-password-toggle]').forEach(button => {
        button.addEventListener('click', () => {
            const input = container.querySelector(`#${button.dataset.passwordToggle}`);
            const visible = input.type === 'password';
            input.type = visible ? 'text' : 'password';
            button.textContent = visible ? 'Ẩn' : 'Hiện';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', `${visible ? 'Ẩn' : 'Hiện'} ${button.dataset.fieldLabel}`);
        });
    });
    globalThis.lucide?.createIcons();
}
