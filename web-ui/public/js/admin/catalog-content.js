import { api } from '../../api.js';

const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const classificationLabel = value => value === 'legal' ? 'Pháp lý · cần duyệt' : value === 'brand' ? 'Thương hiệu · cần duyệt' : 'Nội dung cập nhật thường xuyên';
const statusLabel = value => ({ draft: 'Bản nháp', pending_review: 'Chờ duyệt', published: 'Đã xuất bản', rejected: 'Đã từ chối' }[value] || value);
const editorFields = {
  home_hero: [['headline', 'Tiêu đề chính', 'text'], ['subtitle', 'Mô tả ngắn', 'textarea'], ['cta_label', 'Nhãn nút', 'text'], ['cta_url', 'Liên kết nút', 'text'], ['image_url', 'Ảnh nền URL', 'image']],
  home_about: [['headline', 'Tiêu đề giới thiệu', 'text'], ['description', 'Mô tả', 'textarea']],
  home_featured: [['headline', 'Tiêu đề khối nổi bật', 'text'], ['description', 'Mô tả', 'textarea'], ['cta_label', 'Nhãn nút', 'text'], ['cta_url', 'Liên kết nội bộ', 'text'], ['image_url', 'Ảnh URL', 'image']],
  home_legal_notice: [['notice', 'Thông báo pháp lý', 'textarea']]
};

function makeField(labelText, key, kind, value, required = false) {
  const label = document.createElement('label'); label.className = 'form-control w-full';
  const caption = document.createElement('span'); caption.className = 'label label-text font-medium'; caption.textContent = labelText;
  const input = kind === 'textarea' ? document.createElement('textarea') : document.createElement('input');
  input.className = kind === 'textarea' ? 'textarea textarea-bordered w-full' : 'input input-bordered w-full';
  input.name = key; input.value = value || ''; input.required = required;
  if (kind === 'image') { input.type = 'text'; input.placeholder = 'URL HTTPS hoặc /api/v1/media/…'; }
  if (kind === 'text') input.type = 'text';
  label.append(caption, input);
  if (kind === 'image') {
    const upload = document.createElement('input'); upload.type = 'file'; upload.accept = 'image/jpeg,image/png';
    upload.className = 'file-input file-input-bordered file-input-sm mt-2'; upload.setAttribute('aria-label', 'Tải ảnh lên');
    label.append(upload);
    upload.addEventListener('change', async () => {
      const file = upload.files?.[0]; if (!file) return;
      try { input.value = (await uploadMedia(file)).url; }
      catch (error) { window.showCustomAlert?.(error.message || 'Chưa tải được ảnh.'); }
      finally { upload.value = ''; }
    });
  }
  return { label, input };
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader(); reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(new Error('Không đọc được ảnh đã chọn.')); reader.readAsDataURL(file);
  });
}

async function uploadMedia(file) {
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size < 1 || file.size > 5 * 1024 * 1024) {
    throw new Error('Chọn ảnh JPEG hoặc PNG, dung lượng tối đa 5 MB.');
  }
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 60000);
  try { return await api.post('/admin/catalog/media', { fileName: file.name, dataBase64: await readFile(file) }, { signal: controller.signal }); }
  finally { clearTimeout(timeout); }
}

export async function renderCatalogContent(container, session = {}) {
  container.replaceChildren();
  const header = document.createElement('div'); header.className = 'page-header flex flex-wrap items-center justify-between gap-3';
  const heading = document.createElement('div'); heading.innerHTML = '<h1>Danh mục & nội dung landing page</h1><p>Cập nhật hạng phòng, ảnh và nội dung trang chủ mà không sửa mã nguồn.</p>';
  const reload = document.createElement('button'); reload.type = 'button'; reload.className = 'btn btn-outline btn-sm'; reload.textContent = 'Làm mới'; header.append(heading, reload);
  const status = document.createElement('p'); status.className = 'alert alert-info'; status.setAttribute('role', 'status');
  const typeSection = document.createElement('section'); typeSection.className = 'card card-border bg-base-100 shadow-sm';
  const typeBody = document.createElement('div'); typeBody.className = 'card-body';
  const typeHeader = document.createElement('div'); typeHeader.className = 'flex flex-wrap items-center justify-between gap-2';
  const typeTitle = document.createElement('h2'); typeTitle.className = 'card-title'; typeTitle.textContent = 'Hạng phòng';
  const addType = document.createElement('button'); addType.type = 'button'; addType.className = 'btn btn-primary btn-sm'; addType.textContent = 'Thêm hạng phòng'; typeHeader.append(typeTitle, addType);
  const typesList = document.createElement('div'); typesList.className = 'grid gap-3'; typeBody.append(typeHeader, typesList); typeSection.append(typeBody);
  const cmsSection = document.createElement('section'); cmsSection.className = 'grid gap-4';
  const cmsTitle = document.createElement('h2'); cmsTitle.className = 'text-xl font-semibold'; cmsTitle.textContent = 'Nội dung landing page';
  const cmsList = document.createElement('div'); cmsList.className = 'grid gap-4'; cmsSection.append(cmsTitle, cmsList);
  container.append(header, status, typeSection, cmsSection);

  function showModal(titleText, contentNode) {
    const modal = document.getElementById('global-modal'), content = document.getElementById('global-modal-content');
    if (!modal || !content) { status.textContent = 'Không mở được cửa sổ chỉnh sửa.'; return; }
    content.replaceChildren();
    const top = document.createElement('div'); top.className = 'modal-header flex items-center justify-between gap-3';
    const title = document.createElement('h2'); title.className = 'text-xl font-semibold'; title.textContent = titleText;
    const close = document.createElement('button'); close.type = 'button'; close.className = 'btn btn-ghost btn-sm'; close.textContent = 'Đóng';
    close.addEventListener('click', () => modal.classList.remove('active')); top.append(title, close);
    content.append(top, contentNode); modal.classList.add('active');
  }

  async function load() {
    status.textContent = 'Đang tải danh mục…'; reload.disabled = true; typesList.replaceChildren(); cmsList.replaceChildren();
    try {
      const [types, contents] = await Promise.all([api.get('/admin/catalog/room-types'), api.get('/admin/catalog/content')]);
      renderTypes(types || []); renderCms(contents || []); status.textContent = 'Danh mục và trạng thái CMS đã được cập nhật.';
    } catch (error) { status.textContent = error.message || 'Không tải được danh mục quản trị.'; }
    finally { reload.disabled = false; }
  }

  function renderTypes(types) {
    if (!types.length) { const empty = document.createElement('p'); empty.className = 'opacity-70'; empty.textContent = 'Chưa có hạng phòng.'; typesList.append(empty); return; }
    for (const type of types) {
      const row = document.createElement('article'); row.className = 'card card-compact bg-base-200/50';
      const body = document.createElement('div'); body.className = 'card-body grid gap-3 md:grid-cols-[auto_1fr_auto] md:items-center';
      if (type.image_url) { const image = document.createElement('img'); image.src = type.image_url; image.alt = type.image_alt || type.name; image.className = 'h-20 w-28 rounded-lg object-cover'; image.onerror = () => { image.replaceWith(imageFallback()); }; body.append(image); }
      else body.append(imageFallback());
      const info = document.createElement('div'); const title = document.createElement('h3'); title.className = 'card-title text-base'; title.textContent = `${type.display_order}. ${type.name}`;
      const detail = document.createElement('p'); detail.className = 'text-sm opacity-75'; detail.textContent = `${type.capacity} khách · ${type.is_visible ? 'Đang hiển thị' : 'Đang ẩn'} · ${(type.amenities || 'Chưa có tiện nghi').slice(0, 140)}`;
      info.append(title, detail); body.append(info);
      const actions = document.createElement('div'); actions.className = 'card-actions justify-end';
      const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'btn btn-outline btn-sm'; edit.textContent = 'Sửa & ảnh'; edit.addEventListener('click', () => editType(type)); actions.append(edit); body.append(actions); row.append(body); typesList.append(row);
    }
  }

  function imageFallback() {
    const div = document.createElement('div'); div.className = 'flex h-20 w-28 flex-col items-center justify-center rounded-lg bg-base-300 text-center text-xs opacity-70';
    const icon = document.createElement('span'); icon.textContent = 'Ảnh chưa có'; div.append(icon); return div;
  }

  function editType(type = null) {
    const id = type?.id || null;
    const wrap = document.createElement('div'); wrap.className = 'space-y-4';
    const form = document.createElement('form'); form.className = 'grid gap-3 md:grid-cols-2';
    const fields = [
      ['Tên hạng phòng', 'name', 'text', type?.name || '', true], ['Giá tham chiếu / đêm', 'base_price', 'number', type?.base_price ?? '', true],
      ['Mô tả', 'description', 'textarea', type?.description || '', false], ['Sức chứa tối đa', 'capacity', 'number', type?.capacity ?? 2, true],
      ['Tiện nghi (phân tách bằng dấu phẩy)', 'amenities', 'textarea', type?.amenities || '', false], ['Thứ tự hiển thị', 'display_order', 'number', type?.display_order ?? 0, true]
    ];
    const inputs = {};
    for (const [label, key, kind, value, required] of fields) { const field = makeField(label, key, kind === 'number' ? 'text' : kind, value, required); if (kind === 'number') { field.input.type = 'number'; field.input.min = key === 'capacity' ? '1' : key === 'display_order' ? '0' : '0.01'; field.input.step = key === 'base_price' ? '0.01' : '1'; } inputs[key] = field.input; field.label.classList.add('md:col-span-1'); form.append(field.label); }
    const visibleLabel = document.createElement('label'); visibleLabel.className = 'label cursor-pointer justify-start gap-3';
    const visible = document.createElement('input'); visible.type = 'checkbox'; visible.className = 'checkbox checkbox-primary'; visible.checked = type?.is_visible ?? true;
    const visibleText = document.createElement('span'); visibleText.className = 'label-text'; visibleText.textContent = 'Hiển thị trên landing page'; visibleLabel.append(visible, visibleText); form.append(visibleLabel);
    const buttons = document.createElement('div'); buttons.className = 'card-actions justify-end md:col-span-2';
    const save = document.createElement('button'); save.type = 'submit'; save.className = 'btn btn-primary'; save.textContent = 'Lưu hạng phòng'; buttons.append(save); form.append(buttons);
    form.addEventListener('submit', async event => {
      event.preventDefault(); save.disabled = true;
      try {
        const payload = Object.fromEntries(Object.entries(inputs).map(([key, input]) => [key, input.value]));
        payload.capacity = Number(payload.capacity); payload.display_order = Number(payload.display_order); payload.base_price = Number(payload.base_price); payload.is_visible = visible.checked;
        const saved = id ? await api.put(`/admin/catalog/room-types/${id}`, payload) : await api.post('/admin/catalog/room-types', payload);
        status.textContent = `Đã lưu hạng phòng ${saved.name}.`; await load();
        if (!id) { const modal = document.getElementById('global-modal'); modal?.classList.remove('active'); }
      } catch (error) { status.textContent = error.message || 'Chưa lưu được hạng phòng.'; }
      finally { save.disabled = false; }
    });
    wrap.append(form);
    if (type) {
      const imageSection = document.createElement('section'); imageSection.className = 'space-y-3 border-t border-base-300 pt-4';
      const title = document.createElement('h3'); title.className = 'font-semibold'; title.textContent = 'Ảnh của hạng phòng'; imageSection.append(title);
      const uploadForm = document.createElement('form'); uploadForm.className = 'grid gap-2 md:grid-cols-[1fr_1fr_auto]';
      const file = document.createElement('input'); file.type = 'file'; file.accept = 'image/jpeg,image/png'; file.required = true; file.className = 'file-input file-input-bordered file-input-sm'; file.setAttribute('aria-label', 'Chọn ảnh JPEG hoặc PNG');
      const alt = document.createElement('input'); alt.className = 'input input-bordered input-sm'; alt.placeholder = 'Mô tả ảnh (alt text)'; alt.setAttribute('aria-label', 'Mô tả ảnh');
      const upload = document.createElement('button'); upload.type = 'submit'; upload.className = 'btn btn-secondary btn-sm'; upload.textContent = 'Tải ảnh'; uploadForm.append(file, alt, upload);
      uploadForm.addEventListener('submit', async event => {
        event.preventDefault(); const chosen = file.files?.[0]; if (!chosen) return;
        upload.disabled = true;
        try { const asset = await uploadMedia(chosen); await api.post(`/admin/catalog/room-types/${id}/images`, { asset_id: asset.id, alt_text: alt.value, display_order: type.images?.length || 0 }); status.textContent = 'Đã tải ảnh lên và lưu metadata.'; await load(); editType((await api.get('/admin/catalog/room-types')).find(item => item.id === id)); }
        catch (error) { status.textContent = error.message || 'Chưa tải được ảnh.'; }
        finally { upload.disabled = false; file.value = ''; }
      }); imageSection.append(uploadForm);
      for (const image of type.images || []) {
        const card = document.createElement('div'); card.className = 'card card-compact bg-base-200';
        const cardBody = document.createElement('div'); cardBody.className = 'card-body grid gap-2 md:grid-cols-[auto_1fr_auto] md:items-center';
        const img = document.createElement('img'); img.src = image.url || ''; img.alt = image.alt_text || ''; img.className = 'h-16 w-24 rounded object-cover';
        img.onerror = () => img.replaceWith(imageFallback());
        const meta = document.createElement('div'); meta.className = 'text-xs opacity-70'; meta.textContent = `${image.original_name || 'Ảnh đã cấu hình'}${image.width ? ` · ${image.width}×${image.height}` : ''}${image.size_bytes ? ` · ${Math.ceil(image.size_bytes / 1024)} KB` : ''}`;
        const fieldsWrap = document.createElement('div'); fieldsWrap.className = 'grid gap-2 sm:grid-cols-2';
        const altEdit = document.createElement('input'); altEdit.className = 'input input-bordered input-xs'; altEdit.value = image.alt_text || ''; altEdit.placeholder = 'Mô tả ảnh';
        const orderEdit = document.createElement('input'); orderEdit.className = 'input input-bordered input-xs'; orderEdit.type = 'number'; orderEdit.min = '0'; orderEdit.value = image.display_order ?? 0; orderEdit.setAttribute('aria-label', 'Thứ tự ảnh'); fieldsWrap.append(altEdit, orderEdit, meta);
        const actions = document.createElement('div'); actions.className = 'flex flex-wrap gap-2';
        const replacement = document.createElement('input'); replacement.type = 'file'; replacement.accept = 'image/jpeg,image/png'; replacement.className = 'file-input file-input-bordered file-input-xs max-w-48'; replacement.setAttribute('aria-label', 'Chọn ảnh thay thế');
        replacement.addEventListener('change', async () => {
          const chosen = replacement.files?.[0]; if (!chosen) return;
          replacement.disabled = true;
          try { const asset = await uploadMedia(chosen); await api.put(`/admin/catalog/room-types/${id}/images/${image.id}`, { asset_id: asset.id, alt_text: altEdit.value, display_order: Number(orderEdit.value), is_visible: image.is_visible, is_primary: image.is_primary }); status.textContent = 'Đã thay ảnh và cập nhật metadata.'; await load(); editType((await api.get('/admin/catalog/room-types')).find(item => item.id === id)); }
          catch (error) { status.textContent = error.message || 'Chưa thay được ảnh.'; }
          finally { replacement.disabled = false; replacement.value = ''; }
        });
        const saveImage = document.createElement('button'); saveImage.type = 'button'; saveImage.className = 'btn btn-outline btn-xs'; saveImage.textContent = 'Lưu ảnh';
        const toggle = document.createElement('button'); toggle.type = 'button'; toggle.className = `btn btn-xs ${image.is_visible ? 'btn-warning' : 'btn-success'}`; toggle.textContent = image.is_visible ? 'Ẩn ảnh' : 'Hiện ảnh';
        const primary = document.createElement('button'); primary.type = 'button'; primary.className = `btn btn-xs ${image.is_primary ? 'btn-primary' : 'btn-ghost'}`; primary.textContent = image.is_primary ? 'Ảnh chính' : 'Chọn làm ảnh chính';
        const saveImageChanges = async changes => { try { await api.put(`/admin/catalog/room-types/${id}/images/${image.id}`, { alt_text: altEdit.value, display_order: Number(orderEdit.value), is_visible: image.is_visible, is_primary: false, ...changes }); await load(); editType((await api.get('/admin/catalog/room-types')).find(item => item.id === id)); } catch (error) { status.textContent = error.message || 'Chưa cập nhật được ảnh.'; } };
        saveImage.addEventListener('click', () => saveImageChanges({})); toggle.addEventListener('click', () => saveImageChanges({ is_visible: !image.is_visible, is_primary: false })); primary.addEventListener('click', () => saveImageChanges({ is_visible: true, is_primary: true }));
        actions.append(replacement, saveImage, primary, toggle); cardBody.append(img, fieldsWrap, actions); card.append(cardBody); imageSection.append(card);
      }
      wrap.append(imageSection);
    }
    showModal(id ? `Sửa hạng phòng · ${type.name}` : 'Thêm hạng phòng', wrap);
  }

  function renderCms(contents) {
    for (const item of contents) {
      const card = document.createElement('article'); card.className = 'card card-border bg-base-100 shadow-sm';
      const body = document.createElement('div'); body.className = 'card-body';
      const head = document.createElement('div'); head.className = 'flex flex-wrap items-center justify-between gap-2';
      const title = document.createElement('h3'); title.className = 'card-title'; title.textContent = ({ home_hero: 'Banner chính', home_about: 'Giới thiệu khách sạn', home_featured: 'Khối nội dung nổi bật', home_legal_notice: 'Thông báo pháp lý' })[item.key] || item.key;
      const badge = document.createElement('span'); badge.className = `badge ${item.status === 'pending_review' ? 'badge-warning' : item.status === 'published' ? 'badge-success' : item.status === 'rejected' ? 'badge-error' : 'badge-outline'}`; badge.textContent = statusLabel(item.status);
      head.append(title, badge);
      const type = document.createElement('p'); type.className = 'text-sm opacity-70'; type.textContent = classificationLabel(item.classification);
      const form = document.createElement('form'); form.className = 'grid gap-3 md:grid-cols-2';
      const payload = item.draft || item.published || {};
      const inputs = {};
      for (const [key, label, kind] of editorFields[item.key] || []) {
        const required = (item.key === 'home_hero' || item.key === 'home_about') && key === 'headline';
        const field = makeField(label, key, kind, payload[key] || '', required); inputs[key] = field.input; form.append(field.label);
      }
      const actions = document.createElement('div'); actions.className = 'card-actions justify-end md:col-span-2';
      const save = document.createElement('button'); save.type = 'submit'; save.className = 'btn btn-primary btn-sm'; save.textContent = item.classification === 'dynamic' ? 'Lưu và xuất bản' : 'Lưu bản nháp'; actions.append(save);
      if (item.classification !== 'dynamic') {
        const submit = document.createElement('button'); submit.type = 'button'; submit.className = 'btn btn-secondary btn-sm'; submit.textContent = 'Gửi duyệt'; submit.disabled = !item.draft || item.status === 'pending_review';
        submit.addEventListener('click', async () => { try { await api.post(`/admin/catalog/content/${item.key}/submit`, {}); await load(); } catch (error) { status.textContent = error.message || 'Chưa gửi được bản duyệt.'; } }); actions.append(submit);
        if (item.status === 'pending_review' && String(session.role || session.user?.role || session.userInfo?.role || '').toUpperCase() === 'ADMIN') {
          const approve = document.createElement('button'); approve.type = 'button'; approve.className = 'btn btn-success btn-sm'; approve.textContent = 'Duyệt & xuất bản';
          const reject = document.createElement('button'); reject.type = 'button'; reject.className = 'btn btn-error btn-sm'; reject.textContent = 'Từ chối';
          const isAuthor = Number(item.submitted_by) === Number(session.userId);
          approve.disabled = isAuthor; reject.disabled = isAuthor;
          if (isAuthor) { approve.title = 'Người gửi không thể tự duyệt nội dung này.'; reject.title = approve.title; }
          approve.addEventListener('click', () => review(item.key, 'approve')); reject.addEventListener('click', () => review(item.key, 'reject'));
          actions.append(approve, reject);
        }
      }
      form.append(actions); form.addEventListener('submit', async event => { event.preventDefault(); save.disabled = true; try { const data = Object.fromEntries(Object.entries(inputs).map(([key, input]) => [key, input.value])); await api.put(`/admin/catalog/content/${item.key}`, data); await load(); } catch (error) { status.textContent = error.message || 'Chưa lưu được nội dung.'; } finally { save.disabled = false; } });
      body.append(head, type, form);
      if (item.review_note) { const note = document.createElement('p'); note.className = 'text-sm text-error'; note.textContent = `Ý kiến duyệt: ${item.review_note}`; body.append(note); }
      if (item.classification !== 'dynamic') { const note = document.createElement('p'); note.className = 'text-xs opacity-65'; note.textContent = 'Nội dung công khai chỉ đổi sau khi quản trị viên khác người gửi duyệt. Bản đang xuất bản tiếp tục hiển thị trong lúc chờ duyệt.'; body.append(note); }
      card.append(body); cmsList.append(card);
    }
  }

  async function review(key, action) {
    const note = action === 'reject' ? await window.showCustomPrompt('Ghi lý do từ chối để người biên tập chỉnh sửa:', { title: 'Lý do từ chối nội dung', confirmLabel: 'Gửi từ chối' }) : '';
    if (action === 'reject' && !note?.trim()) return;
    try { await api.post(`/admin/catalog/content/${key}/${action}`, { review_note: note || '' }); await load(); }
    catch (error) { status.textContent = error.message || 'Chưa hoàn tất được bước duyệt.'; }
  }

  addType.addEventListener('click', () => editType()); reload.addEventListener('click', load); await load();
}
