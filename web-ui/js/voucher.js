import {api} from './booking-api.js';
/** A preview never supplies a client total; booking always recalculates. */
export function voucherFlow(context, display) {
  const input=document.getElementById('voucher-code'), button=document.getElementById('apply-voucher'), status=document.getElementById('voucher-status');
  let applied, generation=0;
  function reset(){generation++;applied=undefined;input.value='';status.textContent='';button.disabled=false;}
  input.addEventListener('input',()=>{generation++;applied=undefined;status.textContent='';display(null);button.disabled=false;});
  button.addEventListener('click',async()=>{
    const current=++generation;applied=undefined;button.disabled=true;status.className='';status.textContent='Đang kiểm tra mã…';
    try {
      const details=context();if(!details)throw new Error('Vui lòng chọn phòng trước.');
      const code=input.value.trim();if(!code)throw new Error('Vui lòng nhập mã giảm giá.');
      const preview=await api('/promotions/validate',{method:'POST',body:{...details,voucher_code:code}});
      if(current!==generation)return;
      applied=code;status.className='voucher-success';status.textContent='Mã hợp lệ · được kiểm tra lại khi đặt phòng';display(preview);
    }catch(error){if(current===generation){status.className='error';status.textContent=error.message;display(null);}}
    finally{if(current===generation)button.disabled=false;}
  });
  return {reset,code:()=>applied,freeze:disabled=>{input.disabled=disabled;button.disabled=disabled;}};
}
