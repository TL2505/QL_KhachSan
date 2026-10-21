// One Google Identity Services button for sign-in and customer registration.
// Credentials go only to the server's existing Google + email OTP flow.
export function createGoogleSignIn({ api, document: doc, window: win, onAuthenticated, onStatus, onCancel,
  waitForSdk = async () => {
    for (let i = 0; i < 100 && !win.google?.accounts?.id; i++) {
      await new Promise(resolve => win.setTimeout(resolve, 50));
    }
    return win.google?.accounts?.id;
  }
}) {
  const byId = id => doc.getElementById(id);
  let challengeId = null;
  let busy = false;
  let loading = false;
  let initialisedClient = null;
  let buttonSdk = null;
  let buttonTheme = null;
  const visible = (id, show) => { const el = byId(id); if (el) el.hidden = !show; };
  const message = text => { const el = byId('google-signin-status'); if (el) el.textContent = text; };
  const requestOptions = { timeoutMs: 45000, retryOnNetworkError: false };
  const failureMessage = (error, fallback) => ['TimeoutError', 'AbortError'].includes(error?.name)
    ? 'Xác thực mất nhiều thời gian hơn dự kiến. Vui lòng chọn lại Google để nhận mã mới.'
    : error?.message || fallback;
  const otpStep = show => {
    visible('google-otp-form', show);
    visible('google-login-area', !show);
    for (const id of ['tab-login', 'tab-register']) { if (byId(id)) byId(id).disabled = show; }
    if (show) {
      visible('login-form', false);
      visible('register-form', false);
    }
  };
  const setBusy = value => {
    busy = value;
    byId('google-login-area')?.setAttribute('aria-busy', String(value));
    for (const button of byId('google-otp-form')?.querySelectorAll('button') || []) button.disabled = value;
    const widget = byId('google-signin-button');
    if (widget) widget.inert = value;
  };
  const handleOtpFailure = (error, fallback) => {
    if ([410, 429].includes(error?.status)) {
      challengeId = null;
      if (byId('google-otp-code')) byId('google-otp-code').value = '';
      otpStep(false);
      onCancel();
    }
    onStatus(failureMessage(error, fallback));
  };
  function renderGoogleButton() {
    if (!buttonSdk) return;
    const theme = doc.documentElement.dataset.theme === 'light' ? 'outline' : 'outline_dark';
    if (theme === buttonTheme) return;
    const widget = byId('google-signin-button');
    widget.replaceChildren();
    // Medium prevents Google from showing the account name/email on the button.
    buttonSdk.renderButton(widget, { theme, size: 'medium', text: 'signin_with', shape: 'rectangular', locale: 'vi',
      width: Math.min(400, Math.max(200, Math.floor((widget.clientWidth || byId('google-login-area').clientWidth || 300) - 18))) });
    buttonTheme = theme;
  }
  const themeObserver = new win.MutationObserver(renderGoogleButton);
  themeObserver.observe(doc.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

  async function credential(response) {
    if (busy || challengeId) return;
    if (!response?.credential) { onStatus('Google không trả về thông tin xác thực. Vui lòng chọn lại tài khoản.'); return; }
    setBusy(true);
    onStatus('Đang xác thực tài khoản Google…', 'success');
    try {
      const data = await api.post('/auth/google/request-otp', { credential: response.credential }, requestOptions);
      if (!data?.challenge_id) throw new Error('Máy chủ chưa tạo được phiên xác thực. Vui lòng thử lại.');
      challengeId = data.challenge_id;
      if (byId('google-otp-email')) byId('google-otp-email').textContent = data.email || 'email Google của bạn';
      otpStep(true);
      if (byId('google-otp-code')) { byId('google-otp-code').value = ''; byId('google-otp-code').focus(); }
      onStatus('Mã xác thực đã được gửi tới email. Mã có hiệu lực trong 5 phút.', 'success');
    } catch (error) { onStatus(failureMessage(error, 'Không thể bắt đầu đăng nhập Google. Vui lòng thử lại.')); }
    finally { setBusy(false); }
  }

  async function initialise() {
    if (loading) return;
    loading = true;
    visible('google-signin-retry', false);
    visible('google-signin-fallback', true);
    visible('google-signin-button', false);
    message('Đang tải tuỳ chọn đăng nhập Google…');
    try {
      const config = await api.get('/auth/google/config');
      if (!config?.enabled || !config.client_id) {
        message('Đăng nhập Google chưa được bật. Bạn có thể đăng nhập hoặc tạo tài khoản bằng email.');
        return;
      }
      const sdk = await waitForSdk();
      if (!sdk) throw new Error('Không tải được nút Google. Vui lòng kiểm tra kết nối rồi thử lại.');
      if (initialisedClient !== config.client_id) {
        sdk.initialize({ client_id: config.client_id, callback: credential, auto_select: false, ux_mode: 'popup' });
        initialisedClient = config.client_id;
      }
      buttonSdk = sdk;
      buttonTheme = null;
      renderGoogleButton();
      visible('google-signin-fallback', false);
      visible('google-signin-button', true);
      message('Dùng tài khoản Google để đăng nhập hoặc tạo tài khoản thành viên mới.');
    } catch (error) {
      message(error.message || 'Không tải được cấu hình Google. Vui lòng thử lại.');
      visible('google-signin-retry', true);
    } finally { loading = false; }
  }

  async function verify(event) {
    event.preventDefault();
    if (busy) return;
    const otp = byId('google-otp-code')?.value.trim();
    if (!challengeId || !/^\d{4}$/.test(otp || '')) { onStatus('Vui lòng nhập mã xác thực gồm 4 chữ số.'); return; }
    setBusy(true);
    onStatus('Đang kiểm tra mã xác thực…', 'success');
    try {
      const result = await api.post('/auth/google/verify-otp', { challenge_id: challengeId, otp }, requestOptions);
      if (!(result?.token || result?.data?.token)) throw new Error('Máy chủ chưa cấp phiên đăng nhập hợp lệ.');
      onAuthenticated(result);
      challengeId = null;
    } catch (error) { handleOtpFailure(error, 'Không thể xác nhận mã. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  async function resend() {
    if (busy || !challengeId) return;
    setBusy(true);
    onStatus('Đang gửi lại mã xác thực…', 'success');
    try {
      const data = await api.post('/auth/google/resend-otp', { challenge_id: challengeId }, requestOptions);
      if (data?.email && byId('google-otp-email')) byId('google-otp-email').textContent = data.email;
      if (byId('google-otp-code')) byId('google-otp-code').value = '';
      byId('google-otp-code')?.focus();
      onStatus('Mã mới đã được gửi. Mã cũ không còn hiệu lực.', 'success');
    } catch (error) { handleOtpFailure(error, 'Không thể gửi lại mã.'); }
    finally { setBusy(false); }
  }

  function cancel() {
    if (busy) return;
    challengeId = null;
    if (byId('google-otp-code')) byId('google-otp-code').value = '';
    otpStep(false);
    onStatus('');
    onCancel();
  }

  byId('google-otp-form')?.addEventListener('submit', verify);
  byId('google-otp-resend')?.addEventListener('click', resend);
  byId('google-otp-cancel')?.addEventListener('click', cancel);
  byId('google-signin-retry')?.addEventListener('click', initialise);
  return { initialise, credential };
}
