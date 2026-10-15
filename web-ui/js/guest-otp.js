import {api} from './booking-api.js';

export function createOtpFlow() {
  const dialog = document.querySelector('#otp-dialog');
  const digitArea = document.querySelector('#otp-digits');
  const resend = document.querySelector('#resend-otp');
  const error = document.querySelector('#otp-error');
  const verify = document.querySelector('#verify-otp');
  const digits = Array.from({length: 6}, (_, i) => {
    const input = document.createElement('input');
    input.type = 'text'; input.inputMode = 'numeric'; input.maxLength = 1;
    input.pattern = '[0-9]'; input.required = true; input.setAttribute('aria-label', `Chữ số ${i + 1}`);
    if (i === 0) input.autocomplete = 'one-time-code';
    digitArea.append(input);
    input.addEventListener('input', () => {
      input.value = input.value.replace(/[^0-9]/g, '').slice(-1);
      if (input.value && i < 5) digits[i + 1].focus();
    });
    input.addEventListener('keydown', event => {
      if (event.key === 'Backspace' && !input.value && i > 0) digits[i - 1].focus();
    });
    input.addEventListener('paste', event => {
      const text = event.clipboardData.getData('text').replace(/[^0-9]/g, '').slice(0, 6);
      if (!text) return;
      event.preventDefault(); [...text].forEach((digit, index) => { digits[index].value = digit; });
      digits[Math.min(text.length, 5)].focus();
    });
    return input;
  });
  let timer, until = 0, contact, resolvePending, rejectPending, requesting = false, generation=0, abort;
  function updateTimer() {
    const remaining = Math.max(0, Math.ceil((until - Date.now()) / 1000));
    resend.disabled = requesting || remaining > 0;
    resend.textContent = remaining ? `Gửi lại mã sau ${remaining}s` : 'Gửi lại mã';
  }
  async function request() {
    const current=generation;abort?.abort();abort=new AbortController();
    requesting = true; updateTimer(); error.textContent = '';
    try {
      await api('/auth/guest-otp/request', {method: 'POST', body: contact,signal:abort.signal});
      if(current!==generation)return;
      until = Date.now() + 60000; clearInterval(timer); timer = setInterval(updateTimer, 1000);
      digits.forEach(input => { input.value = ''; }); digits[0].focus();
    } finally { if(current===generation){requesting = false; updateTimer();} }
  }
  function finish() { generation++;abort?.abort();clearInterval(timer);requesting=false;resolvePending = undefined; rejectPending = undefined; dialog.close(); }
  function cancel() { rejectPending?.(new Error('Đã hủy xác thực.')); finish(); }
  dialog.addEventListener('cancel', event => { event.preventDefault(); cancel(); });
  document.querySelector('#close-otp').addEventListener('click', cancel);
  resend.addEventListener('click', async () => {
    try { await request(); } catch { error.textContent = 'Chưa thể gửi mã. Vui lòng thử lại hoặc liên hệ lễ tân.'; }
  });
  document.querySelector('#otp-form').addEventListener('submit', async event => {
    event.preventDefault(); verify.disabled = true; error.textContent = '';
    const current=generation;
    try {
      const result = await api('/auth/guest-otp/verify', {method: 'POST', body: {phone: contact.phone, otp_code: digits.map(input => input.value).join('')}});
      if(current!==generation)return;
      const token = result.access_token ?? result.token;
      if (typeof token !== 'string' || !token) throw new Error('Missing token');
      sessionStorage.setItem('aurelia-guest-token', token);
      resolvePending(token); finish();
    } catch { if(current===generation)error.textContent = 'Mã chưa hợp lệ hoặc đã hết hạn. Vui lòng kiểm tra hoặc gửi lại mã.'; }
    finally { verify.disabled = false; }
  });
  return async details => {
    contact = details; dialog.showModal();
    // Install cancellation before awaiting delivery, so Escape never leaves a pending flow.
    let resolve, reject;
    const pending = new Promise((ok, fail) => { resolve = ok; reject = fail; });
    pending.catch(()=>{}); // Cancellation can occur while delivery is still awaiting its response.
    resolvePending = resolve; rejectPending = reject;
    try { await request(); } catch { reject(new Error('Chưa thể gửi mã. Vui lòng liên hệ lễ tân.')); finish(); }
    return pending;
  };
}
