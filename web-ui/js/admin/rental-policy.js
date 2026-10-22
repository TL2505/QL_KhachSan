import { api } from '../../api.js';

const money = value => value == null ? 'Chưa cấu hình' : `${Number(value).toLocaleString('vi-VN')} đ`;
const field = (name, value, label) => {
  const wrap = document.createElement('label'); wrap.className = 'form-control';
  const caption = document.createElement('span'); caption.className = 'label-text'; caption.textContent = label;
  const input = document.createElement('input'); input.className = 'input input-bordered input-sm'; input.type = 'number';
  input.min = '1'; input.step = '0.01'; input.required = true; input.value = value ?? '';
  input.dataset.rate = name; input.setAttribute('aria-label', label); wrap.append(caption, input); return wrap;
};

export async function renderRentalPolicy(container) {
  container.replaceChildren();
  const header = document.createElement('div'); header.className = 'page-header';
  const title = document.createElement('div'); title.innerHTML = '<h1>Giá theo loại hình lưu trú</h1><p>Thiết lập giá block 2 giờ đầu, mỗi giờ phụ thu và gói qua đêm cho từng hạng phòng.</p>';
  const reload = document.createElement('button'); reload.className = 'btn btn-outline'; reload.textContent = 'Làm mới'; header.append(title, reload);
  const status = document.createElement('p'); status.className = 'alert'; status.setAttribute('role', 'status');
  const list = document.createElement('div'); list.className = 'grid gap-4'; container.append(header, status, list);

  async function load() {
    reload.disabled = true; status.textContent = 'Đang tải bảng giá…'; list.replaceChildren();
    try {
      const types = await api.get('/admin/rental-policy');
      for (const type of types || []) {
        const card = document.createElement('form'); card.className = 'card card-border bg-base-100 shadow-sm';
        const body = document.createElement('div'); body.className = 'card-body';
        const heading = document.createElement('div'); heading.className = 'flex flex-wrap items-center justify-between gap-2';
        const h = document.createElement('h2'); h.className = 'card-title'; h.textContent = type.name;
        const daily = document.createElement('span'); daily.className = 'badge badge-outline'; daily.textContent = `Giá ngày tham chiếu: ${money(type.daily_base_price)}`; heading.append(h, daily);
        const fields = document.createElement('div'); fields.className = 'grid gap-3 md:grid-cols-3';
        fields.append(field('hourly_base_price', type.hourly_base_price, 'Block giờ đầu (2 giờ)'), field('hourly_extra_hour_price', type.hourly_extra_hour_price, 'Mỗi giờ phụ thu'), field('overnight_price', type.overnight_price, 'Gói qua đêm'));
        const actions = document.createElement('div'); actions.className = 'card-actions justify-end';
        const save = document.createElement('button'); save.className = 'btn btn-primary btn-sm'; save.type = 'submit'; save.textContent = 'Lưu giá'; actions.append(save);
        const note = document.createElement('p'); note.className = 'text-sm opacity-70'; note.textContent = `Giá hiện tại: giờ ${money(type.hourly_base_price)}, phụ thu ${money(type.hourly_extra_hour_price)}, qua đêm ${money(type.overnight_price)}.`;
        body.append(heading, fields, note, actions); card.append(body); list.append(card);
        card.addEventListener('submit', async event => {
          event.preventDefault(); save.disabled = true;
          try {
            const rates = Object.fromEntries([...card.querySelectorAll('[data-rate]')].map(input => [input.dataset.rate, input.value]));
            for (const value of Object.values(rates)) if (!Number.isFinite(Number(value)) || Number(value) <= 0 || !/^\d+(\.\d{1,2})?$/.test(value)) throw new Error('Nhập đủ ba mức giá dương, tối đa 2 chữ số thập phân.');
            await api.put(`/admin/rental-policy/${type.id}`, rates);
            status.textContent = `Đã lưu bảng giá cho ${type.name}.`;
            await load();
          } catch (error) { status.textContent = error.message || 'Chưa lưu được bảng giá.'; save.disabled = false; }
        });
      }
      status.textContent = types?.length ? 'Mỗi hạng phòng cần đủ ba mức giá trước khi mở bán giờ/qua đêm.' : 'Chưa có hạng phòng.';
    } catch (error) { status.textContent = error.message || 'Chưa tải được bảng giá.'; }
    finally { reload.disabled = false; }
  }
  reload.addEventListener('click', load); await load();
}
