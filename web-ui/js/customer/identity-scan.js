const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function birthDate(value) {
  const raw = String(value || '').trim();
  const iso = /^\d{8}$/.test(raw) ? `${raw.slice(4)}-${raw.slice(2,4)}-${raw.slice(0,2)}` : raw;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) throw new Error('Không đọc được ngày sinh hợp lệ.');
  const date = new Date(`${iso}T00:00:00Z`);
  const today = new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0,10) !== iso || iso < '1900-01-01' || iso > today)
    throw new Error('Ngày sinh trên giấy tờ không hợp lệ.');
  return iso;
}

export function normalizeIdentity(data) {
  const identityCard = String(data.identityCard ?? data.id_card ?? data.id_number ?? '').trim();
  const fullName = String(data.fullName ?? data.full_name ?? '').trim();
  const address = String(data.address ?? data.residence_address ?? '').trim();
  if (!/^\d{12}$/.test(identityCard) || !fullName || fullName.length > 100 || !address || address.length > 255 || /[\x00-\x1f]/.test(fullName + address))
    throw new Error('Không đọc đủ số CCCD, họ tên và địa chỉ. Hãy thử ảnh rõ hơn hoặc nhập thủ công.');
  return {identityCard,fullName,address,dateOfBirth:birthDate(data.dateOfBirth ?? data.dob)};
}

export function parseCccdQr(text) {
  const parts = String(text || '').trim().replace(/^\uFEFF/,'').split('|');
  if (parts.length < 6 || parts.length > 7) throw new Error('Mã QR không có định dạng CCCD được hỗ trợ.');
  return normalizeIdentity({identityCard:parts[0],fullName:parts[2],dateOfBirth:parts[3],address:parts[5]});
}

export const identityScannerMarkup = `
  <section id="profile-identity-scan" class="identity-scan-panel" aria-labelledby="identity-scan-title">
    <div class="identity-scan-heading"><i data-lucide="scan-line" aria-hidden="true"></i><div>
      <h2 id="identity-scan-title">Quét CCCD để điền nhanh</h2>
      <p>Chụp hoặc chọn ảnh rõ mã QR trên CCCD. Bạn sẽ kiểm tra thông tin trước khi điền vào hồ sơ.</p>
    </div></div>
    <label for="identity-image" class="identity-file-label">Ảnh CCCD hoặc ảnh mã QR · JPG, PNG, tối đa 5 MB</label>
    <input id="identity-image" class="file-input file-input-sm w-full" type="file" accept="image/jpeg,image/png" capture="environment">
    <div class="identity-scan-actions">
      <button type="button" id="identity-ocr" class="btn btn-sm btn-outline" disabled><i data-lucide="scan-text" aria-hidden="true"></i>Đọc chữ trên thẻ</button>
      <button type="button" id="identity-clear" class="btn btn-sm btn-ghost">Xóa kết quả</button>
    </div>
    <p class="identity-scan-note">Mã QR được đọc ngay trên thiết bị. “Đọc chữ trên thẻ” gửi ảnh tới hệ thống OCR của khách sạn; ảnh không được lưu vào hồ sơ.</p>
    <details class="identity-qr-entry"><summary>Dùng đầu đọc QR hoặc nhập nội dung mã QR</summary>
      <label for="identity-qr-text">Nội dung QR từ đầu đọc CCCD</label>
      <textarea id="identity-qr-text" class="textarea w-full" rows="2" maxlength="1024" placeholder="Dán nội dung mã QR từ đầu đọc"></textarea>
      <button type="button" id="identity-read-qr" class="btn btn-sm btn-outline">Đọc nội dung QR</button>
    </details>
    <p id="identity-scan-status" class="identity-scan-status" role="status" aria-live="polite">Chưa chọn ảnh. Bạn vẫn có thể nhập hồ sơ thủ công.</p>
    <div id="identity-scan-result" class="identity-scan-result" hidden>
      <strong>Thông tin đọc được · chưa lưu</strong>
      <dl><div><dt>Số CCCD</dt><dd id="identity-preview-id"></dd></div><div><dt>Họ và tên</dt><dd id="identity-preview-name"></dd></div>
      <div><dt>Ngày sinh</dt><dd id="identity-preview-dob"></dd></div><div><dt>Địa chỉ</dt><dd id="identity-preview-address"></dd></div></dl>
      <button type="button" id="identity-apply" class="btn btn-sm">Điền vào hồ sơ</button>
      <p>Kiểm tra lại và bấm “Lưu Thay Đổi” bên dưới. Quét giấy tờ không thay thế việc đối chiếu tại quầy.</p>
    </div>
  </section>`;

export function mountIdentityScanner(root, {apply, requestOcr}) {
  if (!root?.querySelector) return;
  const find = id => root.querySelector(`#${id}`);
  const input = find('identity-image'), status = find('identity-scan-status'), panel = find('identity-scan-result');
  if (!input || !status || !panel) return;
  const ocr = find('identity-ocr'), clear = find('identity-clear'), read = find('identity-read-qr');
  let imageFile = null, result = null, version = 0;
  const controller = new AbortController();
  const observer = new MutationObserver(() => {
    if (!root.isConnected) { version++; imageFile=null; result=null; controller.abort(); observer.disconnect(); }
  });
  observer.observe(document.body,{childList:true,subtree:true});
  const message = text => { status.textContent = text; };
  const show = data => {
    result = data;
    for (const [id,value] of [['id',data.identityCard],['name',data.fullName],['dob',data.dateOfBirth],['address',data.address]]) find(`identity-preview-${id}`).textContent=value;
    panel.hidden=false; message('Đã đọc thông tin. Hãy kiểm tra trước khi điền vào hồ sơ.');
  };
  const busy = value => { input.disabled=value; ocr.disabled=value || !imageFile; read.disabled=value; clear.disabled=value; root.setAttribute('aria-busy',String(value)); };
  const reset = () => { version++; imageFile=null; result=null; input.value=''; find('identity-qr-text').value=''; panel.hidden=true; panel.querySelectorAll('dd').forEach(e=>{e.textContent='';}); busy(false); };
  clear.addEventListener('click',()=>{reset();message('Đã xóa ảnh và kết quả quét.');});
  find('identity-apply').addEventListener('click',()=>{
    if (!result) return;
    apply(result); reset(); message('Đã điền vào biểu mẫu, chưa lưu. Kiểm tra lại rồi bấm “Lưu Thay Đổi”.');
  });
  read.addEventListener('click',()=>{try{show(parseCccdQr(find('identity-qr-text').value));}catch(error){result=null;panel.hidden=true;message(error.message);}});
  input.addEventListener('change',async()=>{
    const file=input.files?.[0]; const current=++version; result=null; panel.hidden=true; imageFile=null;
    if (!file) {busy(false);return;}
    if (!['image/jpeg','image/png'].includes(file.type) || file.size>MAX_IMAGE_BYTES || file.size===0) {input.value='';busy(false);message('Chọn ảnh JPG hoặc PNG không quá 5 MB.');return;}
    busy(true);message('Đang đọc mã QR trên thiết bị…');
    let bitmap;
    try {
      bitmap=await createImageBitmap(file);
      if(bitmap.width*bitmap.height>24_000_000)throw new Error('Ảnh quá lớn. Hãy chụp gần mã QR hoặc giảm kích thước ảnh.');
      const scale=Math.min(1,1800/Math.max(bitmap.width,bitmap.height));
      const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));
      const context=canvas.getContext('2d',{willReadFrequently:true});context.drawImage(bitmap,0,0,canvas.width,canvas.height);
      const pixels=context.getImageData(0,0,canvas.width,canvas.height);
      const {default:jsQR}=await import('../../vendor/jsQR.js');
      const decoded=jsQR(pixels.data,pixels.width,pixels.height,{inversionAttempts:'attemptBoth'});
      canvas.width=canvas.height=0;
      if (current!==version || !root.isConnected) return;
      imageFile=file;
      if (decoded) show(parseCccdQr(decoded.data));
      else message('Chưa thấy mã QR. Thử ảnh chụp rõ mã QR, nhập nội dung QR hoặc chọn “Đọc chữ trên thẻ”.');
    } catch(error) {if(current===version)message(/^(Không đọc|Ảnh quá|Ngày sinh|Mã QR)/.test(error.message || '') ? error.message : 'Không đọc được ảnh. Hãy chọn ảnh JPG/PNG rõ mã QR và thử lại.');}
    finally {bitmap?.close();if(current===version)busy(false);}
  });
  ocr.addEventListener('click',async()=>{
    if(!imageFile)return;
    const current=++version;result=null;panel.hidden=true;busy(true);message('Đang nhận dạng chữ qua hệ thống của khách sạn…');
    try {
      const bytes=new Uint8Array(await imageFile.arrayBuffer());let binary='';
      for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
      const response=await requestOcr({image_base64:btoa(binary),document_type:'VIETNAMESE_CCCD'},controller.signal);
      if(current===version && root.isConnected)show(normalizeIdentity(response));
    }catch(error){if(current===version && error.name!=='AbortError')message('OCR chưa đọc được ảnh hoặc dịch vụ chưa sẵn sàng. Hãy dùng ảnh mã QR hoặc nhập hồ sơ thủ công.');}
    finally{if(current===version)busy(false);}
  });
}
