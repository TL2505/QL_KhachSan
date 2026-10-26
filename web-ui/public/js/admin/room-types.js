import { api } from '../../api.js';
import { enableInlineValidation } from '../shared/inline-validation.js?v=1.0.0';
import { uploadMedia, validateImageFile } from '../shared/media-upload.js';
import { renderRentalPolicy } from './rental-policy.js?v=3.6.5';

const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const money = value => Number(value || 0).toLocaleString('vi-VN') + ' đ';

export async function renderRoomTypes(container) {
  container.innerHTML = `
    <section class="admin-room-types-view">
      <div class="page-header flex-row justify-between">
        <div><h1>Hạng phòng</h1><p>Tạo hạng phòng, khai báo sức chứa, tiện nghi và cấu hình giá cho các gói lưu trú.</p></div>
        <button class="btn btn-primary" id="room-type-add"><i data-lucide="plus" aria-hidden="true"></i> Thêm hạng phòng</button>
      </div>
      <div id="room-types-feedback" class="rooms-feedback" role="status" aria-live="polite" hidden></div>
      <section class="card room-types-list-card" aria-labelledby="room-types-list-title">
        <div class="room-types-toolbar">
          <div><h2 id="room-types-list-title">Danh sách hạng phòng</h2><p id="room-types-count" aria-live="polite">Đang tải…</p></div>
          <div class="room-types-filters"><label><span class="sr-only">Tìm hạng phòng</span><input class="input" id="room-types-search" type="search" placeholder="Tìm theo tên hoặc tiện nghi"></label><button class="btn btn-secondary btn-sm" id="room-types-reload" type="button">Làm mới</button></div>
        </div>
        <div class="table-container"><table class="table room-types-table"><thead><tr><th>Hạng phòng</th><th>Sức chứa</th><th>Giá ngày tham chiếu</th><th>Số phòng</th><th>Hiển thị</th><th>Thao tác</th></tr></thead><tbody id="room-types-body"><tr><td colspan="6" class="rooms-empty">Đang tải danh sách hạng phòng…</td></tr></tbody></table></div>
        <div id="pagination-admin-room-types"></div>
      </section>
      <section class="card room-types-rates-card" aria-labelledby="room-types-rates-title">
        <div class="room-types-rates-heading"><div><h2 id="room-types-rates-title">Giá theo gói lưu trú</h2><p>Thiết lập một lần cho từng hạng phòng; mọi phòng cùng hạng sử dụng chung bảng giá này.</p></div></div>
        <div id="room-types-rate-editor"></div>
      </section>
    </section>`;
  window.lucide?.createIcons?.();

  const $ = selector => container.querySelector(selector);
  const feedback = (text, kind = 'error') => { const node=$('#room-types-feedback'); node.textContent=text;node.className=`rooms-feedback ${kind}`;node.hidden=!text; };
  let types=[],page=1,pageSize=10,requestId=0;
  const filtered = () => { const query=$('#room-types-search').value.trim().toLocaleLowerCase('vi');return types.filter(type=>!query||`${type.name} ${type.description||''} ${type.amenities||''}`.toLocaleLowerCase('vi').includes(query)); };

  const renderTable = () => {
    const tbody=$('#room-types-body'),pagination=$('#pagination-admin-room-types'),rows=filtered();
    $('#room-types-count').textContent=`Hiển thị ${rows.length} / ${types.length} hạng phòng`;
    tbody.replaceChildren();
    if(!rows.length){const tr=tbody.insertRow(),td=tr.insertCell();td.colSpan=6;td.className='rooms-empty';td.textContent=types.length?'Không tìm thấy hạng phòng phù hợp.':'Chưa có hạng phòng.';pagination.replaceChildren();return;}
    const pages=Math.max(1,Math.ceil(rows.length/pageSize));page=Math.min(page,pages);
    for(const type of rows.slice((page-1)*pageSize,page*pageSize)){
      const tr=tbody.insertRow(),name=tr.insertCell(),identity=document.createElement('div');identity.className='room-type-table-identity';
      if(type.imageUrl){const img=document.createElement('img');img.src=type.imageUrl;img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';img.onerror=()=>img.remove();identity.append(img);}
      const copy=document.createElement('div'),strong=document.createElement('strong'),description=document.createElement('small');strong.textContent=type.name;description.textContent=[type.description,type.amenities].filter(Boolean).join(' · ')||'Chưa có mô tả';copy.append(strong,description);identity.append(copy);name.append(identity);
      tr.insertCell().textContent=`${type.capacity} khách`;
      tr.insertCell().textContent=money(type.basePrice);
      tr.insertCell().textContent=String(type.roomCount||0);
      const visible=tr.insertCell(),badge=document.createElement('span');badge.className=`room-badge ${type.visible?'badge-available':'badge-maintenance'}`;badge.textContent=type.visible?'Đang bán':'Đang ẩn';visible.append(badge);
      const actions=tr.insertCell();actions.className='rooms-actions';
      const edit=document.createElement('button');edit.type='button';edit.className='btn btn-secondary btn-sm';edit.textContent='Sửa';edit.addEventListener('click',()=>openForm(type));
      const remove=document.createElement('button');remove.type='button';remove.className='btn btn-danger btn-sm';remove.textContent='Xóa';remove.disabled=Number(type.roomCount)>0;remove.title=remove.disabled?'Hãy chuyển hoặc xóa các phòng vật lý đang dùng hạng này trước.':'Xóa hạng phòng';remove.addEventListener('click',()=>deleteType(type));actions.append(edit,remove);
    }
    if(window.renderPaginationComponent)window.renderPaginationComponent('pagination-admin-room-types',rows.length,pageSize,page,p=>{page=p;renderTable();},size=>{pageSize=size;page=1;renderTable();});
    else pagination.replaceChildren();
  };

  const load = async () => {
    const id=++requestId;$('#room-types-reload').disabled=true;$('#room-types-body').innerHTML='<tr><td colspan="6" class="rooms-empty">Đang tải danh sách hạng phòng…</td></tr>';
    try{const result=await api.get('/rooms/types');if(id!==requestId||!container.isConnected)return;if(!Array.isArray(result))throw new Error('Danh sách hạng phòng không đúng định dạng.');types=result;page=1;feedback('');renderTable();await renderRentalPolicy($('#room-types-rate-editor'));}
    catch(error){if(id===requestId){$('#room-types-body').innerHTML=`<tr><td colspan="6" class="rooms-empty">${esc(error.message||'Không thể tải danh sách hạng phòng.')}</td></tr>`;feedback(error.message||'Không thể tải dữ liệu hạng phòng.');}}
    finally{if(id===requestId&&container.isConnected)$('#room-types-reload').disabled=false;}
  };

  const openForm = type => {
    const modal=document.getElementById('global-modal'),content=document.getElementById('global-modal-content');
    if(!modal||!content){feedback('Không thể mở biểu mẫu hạng phòng. Hãy tải lại trang.');return;}
    const editing=Boolean(type),close=()=>{if(previewUrl)URL.revokeObjectURL(previewUrl);window.closeModal?.('global-modal');};
    modal.classList.add('active');
    content.innerHTML=`<div class="modal-header"><h2>${editing?'Cập nhật hạng phòng':'Thêm hạng phòng'}</h2><button type="button" class="modal-close" data-room-type-close aria-label="Đóng">&times;</button></div>
      <form id="room-type-form" class="room-type-form">
        <div class="form-group"><label class="form-label" for="rt-name">Tên hạng phòng *</label><input class="form-input" id="rt-name" maxlength="100" required value="${esc(type?.name||'')}" placeholder="Ví dụ: Deluxe hướng biển"></div>
        <div class="room-type-form-grid"><div class="form-group"><label class="form-label" for="rt-capacity">Sức chứa tối đa *</label><input class="form-input" id="rt-capacity" type="number" min="1" max="100" step="1" required value="${esc(type?.capacity??2)}"></div><div class="form-group"><label class="form-label" for="rt-base-price">Giá ngày tham chiếu (VND) *</label><input class="form-input" id="rt-base-price" type="number" min="1" max="99999999.99" step="0.01" required value="${esc(type?.basePrice??'')}" placeholder="Ví dụ: 1500000"></div></div>
        <div class="form-group"><label class="form-label" for="rt-description">Mô tả</label><textarea class="form-input" id="rt-description" maxlength="2000" rows="3" placeholder="Mô tả ngắn về hạng phòng">${esc(type?.description||'')}</textarea></div>
        <div class="form-group"><label class="form-label" for="rt-amenities">Tiện nghi</label><input class="form-input" id="rt-amenities" maxlength="500" value="${esc(type?.amenities||'')}" placeholder="Wi-Fi, TV, điều hòa"></div>
        <div class="form-group"><label class="form-label" for="rt-image-file">Ảnh đại diện hạng phòng (không bắt buộc)</label><div class="rooms-image-picker"><label class="btn btn-secondary rooms-image-picker-button" for="rt-image-file">Chọn ảnh</label><input class="rooms-image-file-input" type="file" id="rt-image-file" accept="image/jpeg,image/png"><span id="rt-image-file-name">Chưa chọn ảnh</span></div><small class="rooms-form-note" id="rt-image-help">JPEG hoặc PNG, tối đa 5 MB.</small><div class="rooms-image-preview room-type-image-preview"><div class="rooms-image-preview-placeholder" id="rt-image-empty"><span aria-hidden="true">Ảnh</span><small>Ảnh hạng phòng xem trước</small></div><img id="rt-image-preview" alt="Ảnh xem trước hạng phòng" referrerpolicy="no-referrer" hidden></div><small class="rooms-form-note" id="rt-image-status" role="status" aria-live="polite">${type?.imageUrl?'Đang hiển thị ảnh hiện tại.':'Chọn ảnh để xem trước.'}</small></div>
        <div class="form-group"><label class="form-label" for="rt-image-alt">Mô tả ảnh</label><input class="form-input" id="rt-image-alt" maxlength="255" value="${esc(type?.imageAlt||'')}" placeholder="Ví dụ: Phòng Deluxe hướng biển"></div>
        <div class="form-group"><label class="form-label" for="rt-visible">Trạng thái hiển thị</label><select class="form-input" id="rt-visible"><option value="true" ${type?.visible!==false?'selected':''}>Đang bán</option><option value="false" ${type?.visible===false?'selected':''}>Đang ẩn</option></select><small class="rooms-form-note">Ẩn hạng phòng sẽ không xuất hiện trong tìm kiếm đặt phòng mới.</small></div>
        <div class="rooms-form-error" id="room-type-error" role="alert" hidden></div>
        <div class="flex-row mt-4" style="justify-content:flex-end;"><button type="button" class="btn btn-secondary" data-room-type-close>Hủy</button><button type="submit" class="btn btn-primary">${editing?'Lưu hạng phòng':'Thêm hạng phòng'}</button></div>
      </form>`;
    content.querySelectorAll('[data-room-type-close]').forEach(button=>button.addEventListener('click',close));
    const roomTypeForm=content.querySelector('#room-type-form');
    enableInlineValidation(roomTypeForm,{
      'rt-name':{valueMissing:'Vui lòng nhập tên hạng phòng.',tooLong:'Tên hạng phòng không được vượt quá 100 ký tự.'},
      'rt-capacity':{valueMissing:'Vui lòng nhập sức chứa.',badInput:'Vui lòng nhập sức chứa hợp lệ.',rangeUnderflow:'Sức chứa phải ít nhất là 1 khách.',rangeOverflow:'Sức chứa tối đa là 100 khách.',stepMismatch:'Sức chứa phải là số nguyên.'},
      'rt-base-price':{valueMissing:'Vui lòng nhập giá ngày tham chiếu.',badInput:'Vui lòng nhập giá hợp lệ.',rangeUnderflow:'Giá ngày tham chiếu phải lớn hơn 0.',rangeOverflow:'Giá ngày tham chiếu không được vượt quá 99.999.999,99 đ.',stepMismatch:'Giá chỉ hỗ trợ tối đa 2 chữ số thập phân.'}
    });
    const imageInput=content.querySelector('#rt-image-file'),image=content.querySelector('#rt-image-preview'),empty=content.querySelector('#rt-image-empty'),imageStatus=content.querySelector('#rt-image-status'),fileName=content.querySelector('#rt-image-file-name');
    let previewUrl='',storedImage=type?.imageUrl||null,uploadedImage='';
    const setPreview=(src,note)=>{image.onload=()=>{image.hidden=false;empty.hidden=true;imageStatus.textContent=note;};image.onerror=()=>{image.hidden=true;empty.hidden=false;imageStatus.textContent='Không tải được ảnh xem trước.';};image.src=src;};
    if(storedImage)setPreview(storedImage,'Ảnh hiện tại.');
    imageInput.addEventListener('change',()=>{
      const file=imageInput.files?.[0];uploadedImage='';if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl='';}
      if(!file){fileName.textContent='Chưa chọn ảnh';if(storedImage)setPreview(storedImage,'Ảnh hiện tại.');else{image.removeAttribute('src');image.hidden=true;empty.hidden=false;imageStatus.textContent='Chọn ảnh để xem trước.';}return;}
      try{validateImageFile(file);}catch(error){imageInput.value='';fileName.textContent='Chưa chọn ảnh';imageStatus.textContent=error.message;return;}
      fileName.textContent=file.name;previewUrl=URL.createObjectURL(file);setPreview(previewUrl,`Đang xem trước ${file.name}. Ảnh tải lên khi lưu.`);
    });
    roomTypeForm.addEventListener('submit',async event=>{
      event.preventDefault();const form=event.currentTarget,submit=form.querySelector('button[type="submit"]'),error=form.querySelector('#room-type-error');submit.disabled=true;submit.textContent='Đang lưu…';error.hidden=true;
      const payload={name:form.querySelector('#rt-name').value.trim(),capacity:Number(form.querySelector('#rt-capacity').value),basePrice:Number(form.querySelector('#rt-base-price').value),description:form.querySelector('#rt-description').value.trim(),amenities:form.querySelector('#rt-amenities').value.trim(),imageUrl:storedImage,imageAlt:form.querySelector('#rt-image-alt').value.trim(),visible:form.querySelector('#rt-visible').value==='true'};
      try{
        const file=imageInput.files?.[0];if(file){if(!uploadedImage)uploadedImage=(await uploadMedia(file)).url;payload.imageUrl=uploadedImage;}
        if(editing)await api.put(`/rooms/types/${encodeURIComponent(type.id)}`,payload);else await api.post('/rooms/types',payload);
        if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl='';}close();feedback(editing?'Đã cập nhật hạng phòng.':'Đã thêm hạng phòng.','success');await load();
      }catch(error2){error.textContent=error2.message||'Không thể lưu hạng phòng.';error.hidden=false;submit.disabled=false;submit.textContent=editing?'Lưu hạng phòng':'Thêm hạng phòng';}
    });
  };

  const deleteType=async type=>{
    if(Number(type.roomCount)>0)return;
    const confirmed=await window.showCustomConfirm(`Xóa hạng phòng “${type.name}”? Hạng này chưa được gán cho phòng nào.`,{title:'Xác nhận xóa hạng phòng',confirmLabel:'Xóa hạng phòng'});
    if(!confirmed)return;
    try{await api.delete(`/rooms/types/${encodeURIComponent(type.id)}`);feedback(`Đã xóa hạng phòng ${type.name}.`,'success');await load();}
    catch(error){feedback(error.message||'Không thể xóa hạng phòng.');}
  };

  $('#room-type-add').addEventListener('click',()=>openForm(null));$('#room-types-reload').addEventListener('click',load);
  $('#room-types-search').addEventListener('input',()=>{page=1;renderTable();});
  await load();
}
