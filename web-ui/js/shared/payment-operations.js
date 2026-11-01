import {api} from '../../api.js';
import {paymentLabel, paymentBadge} from './payment-state.js';
import {renderPaginationComponent} from './pagination.js';

const labels={PENDING:'Đang chờ gửi',RETRY:'Chờ thử lại',SENDING:'Đang gửi',SENT:'Máy chủ email đã nhận',FAILED:'Gửi thất bại',UNKNOWN:'Cần kiểm tra kết quả gửi',SKIPPED:'Đã bỏ qua'};
function el(tag,text,cls){const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;}
export async function renderPaymentOperations(container) {
  container.replaceChildren();container.append(el('h2','Lịch sử thanh toán & thông báo'),el('p','Tra cứu giao dịch, đơn đặt liên quan và trạng thái gửi email.'));
  const tools=el('div',null,'card card-body payment-operations-tools'),select=el('select',null,'select'),search=el('input',null,'input'),reload=el('button','Làm mới','btn');
  select.setAttribute('aria-label','Loại lịch sử');
  for(const [v,t] of [['transactions','Giao dịch thanh toán'],['notifications','Gửi thông báo']]){const o=el('option',t);o.value=v;select.append(o);}
  search.placeholder='Mã folio (để trống để xem tất cả)';search.setAttribute('aria-label','Lọc theo mã folio');search.inputMode='numeric';tools.append(select,search,reload);container.append(tools);
  const tableCard=el('section',null,'card card-border payment-operations-table-card'),cardBody=el('div',null,'card-body'),message=el('p',null,'payment-operations-message'),region=el('div',null,'overflow-x-auto payment-operations-table-region'),pagination=el('div');pagination.id='pagination-payment-operations';message.setAttribute('role','status');tableCard.append(cardBody);cardBody.append(message,region,pagination);container.append(tableCard);
  let page=1,pageSize=10,total=0,version=0;
  async function load(){
    const current=++version;message.textContent='Đang tải lịch sử…';reload.disabled=true;pagination.replaceChildren();search.disabled=select.value!=='transactions';
    try{
      const folio=search.value.trim();if(folio&&!/^\d+$/.test(folio))throw new Error('Mã folio phải là số.');
      const offset=(page-1)*pageSize,params=new URLSearchParams({limit:String(pageSize),offset:String(offset)});if(folio&&select.value==='transactions')params.set('folio_id',folio);
      const response=await api.get(`/payment-operations/${select.value}?${params}`);if(current!==version||!container.isConnected)return;
      const rows=Array.isArray(response)?response:(response?.items??response?.data??[]);total=Number(response?.total??rows.length)||0;
      const lastPage=Math.max(1,Math.ceil(total/pageSize));if(page>lastPage){page=lastPage;return load();}
      region.dataset.kind=select.value;region.replaceChildren();const table=el('table',null,'table table-zebra'),head=el('thead'),tr=el('tr'),body=el('tbody'),transaction=select.value==='transactions';
      const headers=transaction?['Thời gian','Kênh / phương thức','Mã giao dịch','Đơn đặt / khách hàng','Folio','Số tiền','Đã áp dụng / dư','Kết quả','Chi tiết']:['Sự kiện / đặt phòng','Trạng thái email','Số lần gửi','Lần gửi tiếp','Lỗi','Lịch sử gửi'];
      for(const h of headers)tr.append(el('th',h));head.append(tr);table.append(head,body);region.append(table);
      for(const r of rows){if(transaction&&Number(r.amount)===0)continue;const row=el('tr');body.append(row);
        if(transaction){
          for(const v of [r.transaction_at,`${r.provider} / ${r.payment_method}`,r.transaction_id])row.append(el('td',v));
          const bookingCell=el('td'),booking=el('div',null,'payment-transaction-booking');booking.append(el('strong',r.customer_name??'Khách chưa xác định'),el('span',r.booking_code??(r.booking_id?`Đơn #${r.booking_id}`:'Chưa xác định đơn đặt')));if(r.room_number)booking.append(el('small',`Phòng ${r.room_number}`));bookingCell.append(booking);row.append(bookingCell);
          for(const v of [r.folio_id??'Chưa xác định',Number(r.amount).toLocaleString('vi-VN')+' đ',`${r.applied_amount} / ${r.excess_amount}`])row.append(el('td',v));
          const status=el('td');status.append(el('span',paymentLabel(r),paymentBadge(r)));row.append(status,el('td',`${r.reason} · webhook: ${r.webhook_deliveries}`));
        }else{
          for(const v of [`${r.event_type} / #${r.booking_id??'—'}`,labels[r.state]??r.state,r.attempts,r.next_attempt_at,r.last_error??'—'])row.append(el('td',v));
          const td=el('td'),button=el('button','Xem lần gửi','btn btn-sm');td.append(button);row.append(td);
          button.onclick=async()=>{button.disabled=true;try{const result=await api.get(`/payment-operations/attempts?id=${r.id}`),attempts=Array.isArray(result)?result:result?.data||[];
            const detail=el('tr'),cell=el('td');cell.colSpan=headers.length;cell.textContent=attempts.length?attempts.map(a=>`#${a.attempt_number}: ${labels[a.state]??a.state} ${a.error_code??''} (${a.started_at})`).join(' · '):'Chưa có lần gửi nào.';detail.append(cell);row.after(detail);button.remove();
          }catch{message.textContent='Chưa tải được lịch sử gửi.';button.disabled=false;}};
        }
      }
      if(!rows.length){const emptyRow=el('tr'),emptyCell=el('td','Chưa có dữ liệu phù hợp.');emptyCell.colSpan=headers.length;emptyRow.append(emptyCell);body.append(emptyRow);}
      message.textContent='';renderPaginationComponent(pagination.id,total,pageSize,page,
        nextPage=>{page=nextPage;load();},
        nextPageSize=>{pageSize=nextPageSize;page=1;load();});
    }catch(error){if(current===version){region.replaceChildren();message.textContent=error.message||'Chưa tải được lịch sử.';}}
    finally{if(current===version)reload.disabled=false;}
  }
  reload.onclick=()=>{page=1;load();};select.onchange=()=>{page=1;load();};search.onchange=()=>{page=1;load();};await load();
}
