import {api} from './booking-api.js';

export function paymentFlow() {
  let controller, timer, countdown, active = 0;
  const panel = document.getElementById('payment-step');
  function stop() { active++; controller?.abort(); clearTimeout(timer); clearInterval(countdown); panel.hidden = true; }
  async function start(folio, token, settled) {
    stop(); const current = active; controller = new AbortController(); panel.replaceChildren(); panel.hidden = false;
    const details = document.createElement('div'), title = document.createElement('h2'), status = document.createElement('p');
    title.textContent = 'Thanh toán VietQR'; status.textContent = 'Đang lấy mã thanh toán…'; details.append(title, status); panel.append(details);
    try {
      const qr = await api(`/folios/${folio.id}/vietqr`, {token, signal: controller.signal});
      if (current !== active) return;
      const bytes = qr.qr_image_base64.replace(/^data:image\/png;base64,/, '');
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(bytes)) throw new Error('Invalid QR image');
      const image = document.createElement('img'); image.src = `data:image/png;base64,${bytes}`; image.alt = 'Mã VietQR thanh toán'; panel.prepend(image);
      const amount = document.createElement('p'); amount.textContent = new Intl.NumberFormat('vi-VN', {style:'currency',currency:'VND'}).format(qr.amount);
      const account = document.createElement('p'); account.textContent = `${qr.beneficiary_bank} · ${qr.account_number} · ${qr.account_holder}`;
      const reference = document.createElement('p'); reference.textContent = `Nội dung chuyển khoản: ${qr.reference_code}`;
      const actions = document.createElement('div'); actions.className = 'payment-actions';
      const download = document.createElement('a'); download.href = image.src; download.download = 'aurelia-vietqr.png'; download.textContent = 'Tải ảnh QR';
      const copy = document.createElement('button'); copy.type = 'button'; copy.textContent = 'Sao chép số tài khoản';
      copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(qr.account_number); copy.textContent='Đã sao chép'; } catch { status.textContent='Vui lòng sao chép số tài khoản được hiển thị bên trên.'; } });
      actions.append(download,copy); details.append(amount,account,reference,actions);
      const until = Date.now() + Math.min(300, qr.expires_in_seconds ?? 300)*1000;
      countdown = setInterval(() => {
        if (current !== active) return;
        const remaining = Math.max(0,Math.ceil((until-Date.now())/1000)); status.textContent=`Đang chờ xác nhận thanh toán · ${remaining}s`;
        if (!remaining) {clearTimeout(timer);clearInterval(countdown);status.textContent='Mã đã hết thời gian hiển thị. Vui lòng liên hệ lễ tân để xác minh thanh toán.';}
      },1000);
      async function poll() {
        if (current !== active || Date.now() >= until) return;
        try {
          const payment = await api(`/folios/${folio.id}/payment-status`,{token,signal:controller.signal});
          if (current !== active) return;
          if(payment.status === 'settled') {stop();settled(payment);return;}
        } catch(error) {
          if(current !== active) return;
          if(error.status === 401 || error.status === 403) {clearInterval(countdown);status.textContent='Vui lòng liên hệ lễ tân để xác minh thanh toán.';return;}
        }
        if(current === active) timer=setTimeout(poll,2500);
      }
      timer=setTimeout(poll,2500);
    } catch(error) { if(current === active){status.textContent='Đặt phòng đã được ghi nhận. Chưa thể hiển thị mã thanh toán; vui lòng liên hệ lễ tân.';} }
  }
  return {start,stop};
}
