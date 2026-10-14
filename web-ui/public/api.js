// Shared legacy-portal transport. Server authorization remains authoritative.
export class ApiError extends Error {
 constructor(message, status = 0, retryAfter = null) { super(message); this.name = 'ApiError'; this.status = status; this.retryAfter = retryAfter; }
}
const storage = () => globalThis.localStorage;
function read(key) { try { return storage()?.getItem(key); } catch { return null; } }
function write(key,value) { try { if(value === null) storage()?.removeItem(key); else storage()?.setItem(key,value); } catch { /* A blocked store must not crash the page. */ } }
export function normalizeApiBase(url) {
 const normalized = new URL(url, window.location.origin);
 if (!['http:','https:'].includes(normalized.protocol) || normalized.username || normalized.password || normalized.search || normalized.hash) throw new ApiError('Địa chỉ kết nối không hợp lệ.');
 let path = normalized.pathname.replace(/\/+$/, '');
 if (!path || path === '/api') path = '/api/v1';
 if (!path.endsWith('/api/v1')) throw new ApiError('Địa chỉ kết nối phải sử dụng /api/v1.');
 return normalized.origin + path;
}
export function getApiBaseUrl() {
 const saved = read('hotel_api_url');
 if (saved) { try { return normalizeApiBase(saved); } catch { /* Ignore stale invalid configuration. */ } }
 return window.location.origin + '/api/v1';
}
export function setApiBaseUrl(url) { write('hotel_api_url', url ? normalizeApiBase(url) : null); }
export function getAuthSession() {
 try { const session = JSON.parse(read('hotel_auth_session')); return session && typeof session === 'object' && !Array.isArray(session) ? session : null; }
 catch { return null; }
}
export function setAuthSession(session) { write('hotel_auth_session',session ? JSON.stringify(session) : null); }
function messageFor(status) {
 return ({401:'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',403:'Bạn không có quyền thực hiện thao tác này.',409:'Dữ liệu vừa thay đổi. Vui lòng tải lại và kiểm tra trước khi thử lại.',503:'Hệ thống đang tạm bận. Vui lòng thử lại sau.'})[status] || (status >= 500 ? 'Chưa thể hoàn tất yêu cầu. Vui lòng thử lại hoặc liên hệ lễ tân.' : 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.');
}
export async function apiFetch(path, init = {}) {
 if (!path || /^(?:[a-z]+:|\/\/)/i.test(path)) throw new ApiError('Đường dẫn yêu cầu không hợp lệ.');
 const session = getAuthSession();
 const headers = new Headers(init.headers || {});
 headers.set('Accept','application/json');
 if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type','application/json; charset=utf-8');
 if (typeof session?.token === 'string' && session.token) headers.set('Authorization','Bearer ' + session.token);
 let response;
 try { response = await fetch(getApiBaseUrl() + (path.startsWith('/') ? path : '/' + path), {...init,headers}); }
 catch(error) { if(error.name === 'AbortError') throw error; throw new ApiError('Không thể kết nối. Vui lòng thử lại hoặc liên hệ lễ tân.'); }
 if(response.status === 401 && session?.token && getAuthSession()?.token === session.token) {
  setAuthSession(null);
  window.dispatchEvent(new Event('hotel:session-expired'));
 }
 if(response.status === 204) return null;
 const text = await response.text();
 let result;
 try { result = text ? JSON.parse(text) : null; }
 catch { throw new ApiError('Chưa thể đọc phản hồi. Vui lòng thử lại hoặc liên hệ lễ tân.',response.status); }
 if(!response.ok) throw new ApiError(messageFor(response.status),response.status,response.headers.get('Retry-After'));
 if(result && typeof result === 'object' && 'status' in result) {
  const status = String(result.status).toLowerCase();
  if(status === 'success') return result.data ?? null;
  if(['error','failed','failure'].includes(status)) throw new ApiError('Yêu cầu chưa được hoàn tất. Vui lòng kiểm tra lại.',response.status);
 }
 return result;
}
export async function fetchAuthenticatedFile(path, init = {}) {
 if (!path || /^(?:[a-z]+:|\/\/)/i.test(path)) throw new ApiError('Đường dẫn tải xuống không hợp lệ.');
 const session = getAuthSession();
 const headers = new Headers(init.headers || {});
 headers.set('Accept', 'application/pdf');
 if (typeof session?.token === 'string' && session.token) headers.set('Authorization', 'Bearer ' + session.token);
 let response;
 try { response = await fetch(getApiBaseUrl() + (path.startsWith('/') ? path : '/' + path), {...init, headers}); }
 catch (error) { if (error.name === 'AbortError') throw error; throw new ApiError('Không thể tải tệp. Vui lòng thử lại.'); }
 if (response.status === 401 && session?.token && getAuthSession()?.token === session.token) {
  setAuthSession(null);
  window.dispatchEvent(new Event('hotel:session-expired'));
 }
 if (!response.ok) throw new ApiError(messageFor(response.status), response.status, response.headers.get('Retry-After'));
 if (!response.headers.get('Content-Type')?.toLowerCase().includes('application/pdf')) throw new ApiError('Máy chủ không trả về tệp PDF hợp lệ.', response.status);
 return response.blob();
}
export const api = {
 get:(path,init={})=>apiFetch(path,{...init,method:'GET',cache:'no-store'}),
 post:(path,body,init={})=>apiFetch(path,{...init,method:'POST',body:JSON.stringify(body)}),
 put:(path,body,init={})=>apiFetch(path,{...init,method:'PUT',body:JSON.stringify(body)}),
 delete:(path,init={})=>apiFetch(path,{...init,method:'DELETE'})
};
