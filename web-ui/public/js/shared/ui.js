export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
}
export const ROOM_STATUSES = Object.freeze({
 vacant_clean: ['Phòng sạch, sẵn sàng', 'VC'], vacant_dirty: ['Phòng trống, cần dọn', 'VD'],
 occupied_clean: ['Đang ở, đã dọn', 'OC'], occupied_dirty: ['Đang ở, cần dọn', 'OD'],
 out_of_order: ['Ngừng sử dụng: bảo trì', 'OOO'], out_of_service: ['Tạm ngừng phục vụ', 'OOS'], reserved: ['Đã giữ phòng', 'RESERVED']
});
export function roomStatus(value) {
 const code = Object.hasOwn(ROOM_STATUSES, value) ? value : 'unknown';
 const [label, abbreviation] = ROOM_STATUSES[code] ?? ['Trạng thái chưa xác định', '?'];
 return {code,label,abbreviation};
}
export function iconRefresh() { globalThis.lucide?.createIcons(); }
export function renderNotice(container, title, message, tone = 'info') {
 const section = document.createElement('section'); section.className = `card shared-notice notice-${tone}`; section.setAttribute('role','status');
 const heading = document.createElement('h2'); heading.textContent = title;
 const detail = document.createElement('p'); detail.textContent = message;
 section.append(heading,detail); container.replaceChildren(section); return section;
}
