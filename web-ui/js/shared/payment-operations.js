import {api} from '../../api.js';
import {paymentLabel, paymentBadge} from './payment-state.js';

const labels={PENDING:'Đang chờ gửi',RETRY:'Chờ thử lại',SENDING:'Đang gửi',SENT:'Máy chủ email đã nhận',FAILED:'Gửi thất bại',UNKNOWN:'Cần kiểm tra kết quả gửi',SKIPPED:'Đã bỏ qua'};
function el(tag,text,cls){const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;}
export async function renderPaymentOperations(container) {
  container.replaceChildren();container.append(el('h1','Giao dịch & thông báo'),el('p','Lịch sử các phương thức thanh toán và kết quả gửi email.'));
  const tools=el('div',null,'card card-body'),select=el('select',null,'select'),search=el('input',null,'input'),reload=el('button','Làm mới','btn');
  select.setAttribute('aria-label','Loại lịch sử');
  for(const [v,t] of [['transactions','Giao dịch thanh toán'],['notifications','Gửi thông báo']]){const o=el('option',t);o.value=v;select.append(o);}
  search.placeholder='Mã folio (để trống để xem tất cả)';search.setAttribute('aria-label','Lọc theo mã folio');search.inputMode='numeric';tools.append(select,search,reload);container.append(tools);
  const message=el('p',null,'alert'),region=el('div',null,'overflow-x-auto'),previous=el('button','Trang trước','btn'),next=el('button','Trang sau','btn');message.setAttribute('role','status');container.append(message,region,previous,next);
  let offset=0,version=0;const limit=25;
  async function load(){
    const current=++version;message.textContent='Đang tải lịch sử…';reload.disabled=true;previous.disabled=true;next.disabled=true;search.disabled=select.value!=='transactions';
    try{
      const folio=search.value.trim();if(folio&&!/^\d+$/.test(folio))throw new Error('Mã folio phải là số.');
      const params=new URLSearchParams({limit:String(limit),offset:String(offset)});if(folio&&select.value==='transactions')params.set('folio_id',folio);
      const response=await api.get(`/payment-operations/${select.value}?${params}`);if(current!==version||!container.isConnected)return;
      const rows=Array.isArray(response)?response:response?.data||[];region.replaceChildren();const table=el('table',null,'table table-zebra'),head=el('thead'),tr=el('tr'),body=el('tbody'),transaction=select.value==='transactions';
      const headers=transaction?['Thời gian','Kênh / phương thức','Mã giao dịch','Folio','Số tiền','Đã áp dụng / dư','Kết quả','Chi tiết']:['Sự kiện / đặt phòng','Trạng thái email','Số lần gửi','Lần gửi tiếp','Lỗi','Lịch sử gửi'];
      for(const h of headers)tr.append(el('th',h));head.append(tr);table.append(head,body);region.append(table);
      for(const r of rows){const row=el('tr');body.append(row);
        if(transaction){
          for(const v of [r.transaction_at,`${r.provider} / ${r.payment_method}`,r.transaction_id,r.folio_id??'Chưa xác định',Number(r.amount).toLocaleString('vi-VN')+' đ',`${r.applied_amount} / ${r.excess_amount}`])row.append(el('td',v));
          const status=el('td');status.append(el('span',paymentLabel(r),paymentBadge(r)));row.append(status,el('td',`${r.reason} · webhook: ${r.webhook_deliveries}`));
        }else{
          for(const v of [`${r.event_type} / #${r.booking_id??'—'}`,labels[r.state]??r.state,r.attempts,r.next_attempt_at,r.last_error??'—'])row.append(el('td',v));
          const td=el('td'),button=el('button','Xem lần gửi','btn btn-sm');td.append(button);row.append(td);
          button.onclick=async()=>{button.disabled=true;try{const result=await api.get(`/payment-operations/attempts?id=${r.id}`),attempts=Array.isArray(result)?result:result?.data||[];
            const detail=el('tr'),cell=el('td');cell.colSpan=headers.length;cell.textContent=attempts.length?attempts.map(a=>`#${a.attempt_number}: ${labels[a.state]??a.state} ${a.error_code??''} (${a.started_at})`).join(' · '):'Chưa có lần gửi nào.';detail.append(cell);row.after(detail);button.remove();
          }catch{message.textContent='Chưa tải được lịch sử gửi.';button.disabled=false;}};
        }
      }
      message.textContent=rows.length?`Đang xem ${offset+1}–${offset+rows.length}.`:'Chưa có dữ liệu phù hợp.';previous.disabled=offset===0;next.disabled=rows.length<limit;
    }catch(error){if(current===version){region.replaceChildren();message.textContent=error.message||'Chưa tải được lịch sử.';}}
    finally{if(current===version)reload.disabled=false;}
  }
  reload.onclick=()=>{offset=0;load();};select.onchange=()=>{offset=0;load();};search.onchange=()=>{offset=0;load();};previous.onclick=()=>{offset=Math.max(0,offset-limit);load();};next.onclick=()=>{offset+=limit;load();};await load();
}
