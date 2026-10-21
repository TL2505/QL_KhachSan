import { apiFetch } from '../../api.js';

export async function renderAiSettings(container) {
    container.innerHTML = `
      <div class="page-header"><div><p class="portal-eyebrow">AURELIA · AI CONCIERGE</p><h1>Quản lý API key AI</h1><p>Kết nối nhà cung cấp AI cho Quản gia trên trang chủ.</p></div></div>
      <div class="card ai-settings-card">
        <div class="card-body">
          <div class="flex-row justify-between"><h2 class="card-title">Cấu hình chatbot</h2><span id="ai-config-state" role="status">Đang tải…</span></div>
          <fieldset class="fieldset" id="ai-settings-fields" disabled>
            <form id="ai-key-form" autocomplete="off" aria-labelledby="ai-key-heading">
              <h3 id="ai-key-heading">1. Nhập API key và lấy model</h3>
              <p class="ai-settings-note">Bước này chỉ cần nhà cung cấp và API key. Bạn chưa cần biết tên model.</p>
              <div class="form-group"><label for="ai-provider">Nhà cung cấp</label><select class="select form-input" id="ai-provider"><option value="openai">OpenAI</option><option value="gemini">Google Gemini</option><option value="claude">Claude (Anthropic)</option><option value="groq">Groq</option></select></div>
              <div class="form-group"><label for="ai-api-key">API key <span id="ai-key-state"></span></label><input class="input form-input" id="ai-api-key" name="provider-credential" type="password" autocomplete="new-password" spellcheck="false" maxlength="512" placeholder="Nhập API key"><small id="ai-key-hint">Dán chuỗi key do nhà cung cấp hiển thị. Tên bạn đặt khi tạo key chỉ là nhãn quản lý, không phải tên model. Key được mã hóa khi lưu trên máy chủ.</small></div>
              <div class="form-group" id="ai-workspace-field" hidden><label for="ai-workspace">Workspace ID Claude (tùy chọn)</label><input class="input form-input" id="ai-workspace" type="text" maxlength="107" placeholder="wrkspc_…" autocomplete="off"><small>Chỉ cần nhập khi API key Claude dùng nhiều workspace. Key dành cho một workspace có thể để trống.</small></div>
              <div class="form-group">
                <button class="btn btn-primary" id="ai-load-models" type="submit">Tiếp tục: Lấy danh sách model</button>
                <small>Key được dùng để hỏi nhà cung cấp có những model nào. Chưa lưu hoặc bật chatbot ở bước này. Nếu đã lưu key cùng nhà cung cấp, có thể để trống ô key.</small>
              </div>
            </form>
            <p id="ai-model-status" role="status" aria-live="polite"></p>
            <form id="ai-settings-form" autocomplete="off" aria-labelledby="ai-model-heading">
              <h3 id="ai-model-heading">2. Chọn model và lưu cấu hình</h3>
              <p class="ai-settings-note" id="ai-model-guide">Hoàn thành bước 1 để xem danh sách model. Chọn một model rồi lưu API key và cấu hình chatbot.</p>
              <div class="form-group">
                <div id="ai-model-picker" hidden><label for="ai-model-select">Chọn model từ nhà cung cấp</label><select class="select form-input" id="ai-model-select"><option value="">Chọn model…</option></select></div>
                <p id="ai-selected-model" role="status">Chưa chọn model</p>
                <details id="ai-manual-model"><summary>Nhập Model ID thủ công (nâng cao)</summary><label for="ai-model">Model ID</label><input class="input form-input" id="ai-model" type="text" maxlength="100" placeholder="Chỉ nhập nếu bạn đã biết Model ID"><small id="ai-model-hint">Model là mô hình AI dùng để trả lời, khác với tên key bạn tự đặt.</small></details>
              </div>
              <div class="form-group">
                <details><summary>Ngữ cảnh mặc định của dự án</summary><label for="ai-project-context">Thông tin từ website và các chức năng hiện có</label><textarea class="textarea form-input" id="ai-project-context" rows="10" readonly></textarea><small id="ai-project-sources"></small></details>
                <small id="ai-project-state" role="status">Đang tải ngữ cảnh dự án…</small>
              </div>
              <div class="form-group"><label for="ai-knowledge">Thông tin khách sạn bổ sung cho AI</label><textarea class="textarea form-input" id="ai-knowledge" rows="6" maxlength="10000" placeholder="Bổ sung thông tin đã xác nhận chưa có trong ngữ cảnh dự án…"></textarea><small>Ngữ cảnh mặc định luôn được gửi cho AI, kể cả khi để trống ô này. Không nhập dữ liệu mẫu như giá hiện hành; nội dung chưa có cần lễ tân xác nhận.</small></div>
              <label class="ai-enable-label"><input id="ai-enabled" type="checkbox"> Bật nhà cung cấp này cho chatbot</label>
              <p class="ai-settings-note">Khi bật, câu hỏi khách gửi cho chatbot và thông tin khách sạn ở trên được gửi tới nhà cung cấp đã chọn. Với Gemini, các lượt hội thoại gần nhất cũng được gửi để hiểu câu hỏi tiếp nối. Kiểm tra kết nối có thể dùng một lượng nhỏ hạn mức API.</p>
              <div class="card-actions flex-row" style="flex-wrap:wrap;gap:10px;margin-top:20px">
                <button class="btn btn-secondary" id="ai-save" type="submit" disabled>Lưu API key và model đã chọn</button>
                <button class="btn btn-secondary" id="ai-test" type="button">Kiểm tra kết nối đã lưu</button>
                <button class="btn btn-danger" id="ai-delete" type="button">Xóa key</button>
              </div>
            </form>
          </fieldset>
          <p id="ai-settings-feedback" role="status" aria-live="polite"></p>
          <button class="btn btn-secondary" id="ai-reload" type="button" hidden>Tải lại cấu hình</button>
        </div>
      </div>`;
    // Scope layout additions to this view; existing portal styles supply the components.
    if (!document.getElementById('ai-settings-layout')) {
        const style = document.createElement('style'); style.id = 'ai-settings-layout';
        style.textContent = '.ai-settings-card{max-width:900px;padding:0}.ai-settings-card [hidden]{display:none!important}.ai-settings-card .card-body{padding:24px}.ai-settings-card fieldset{border:0;padding:0;margin:18px 0 0;min-width:0}.ai-settings-card .form-group{display:grid;gap:8px;margin-bottom:20px}.ai-settings-card .form-input{width:100%;max-width:100%}.ai-settings-card small,.ai-settings-note{color:var(--portal-muted);font-size:12px;line-height:1.65}.ai-enable-label{display:flex;gap:10px;align-items:center}.ai-settings-card textarea{resize:vertical}.ai-settings-note{margin:12px 0 20px}#ai-settings-form{border-top:1px solid var(--portal-border);padding-top:24px;margin-top:24px}.ai-settings-card h3{margin:0;font-size:18px}.ai-settings-card summary{cursor:pointer;font-size:13px;color:var(--portal-muted)}.ai-settings-card details[open] summary{margin-bottom:12px}#ai-selected-model{margin:0;font-size:13px;overflow-wrap:anywhere}#ai-settings-feedback,#ai-model-status{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}#ai-model-status{font-size:13px}#ai-config-state,#ai-key-state{font-size:12px;color:var(--portal-muted)}';
        document.head.append(style);
    }
    const $ = selector => container.querySelector(selector);
    const fields = $('#ai-settings-fields');
    const provider = $('#ai-provider');
    const model = $('#ai-model');
    const key = $('#ai-api-key');
    const knowledge = $('#ai-knowledge');
    const workspace = $('#ai-workspace');
    const enabled = $('#ai-enabled');
    const feedback = $('#ai-settings-feedback');
    const test = $('#ai-test');
    const remove = $('#ai-delete');
    const loadModels = $('#ai-load-models');
    const modelSelect = $('#ai-model-select');
    const modelStatus = $('#ai-model-status');
    const saveButton = $('#ai-save');
    const clearModels = () => {
        const placeholder = document.createElement('option'); placeholder.value = ''; placeholder.textContent = 'Chọn model…';
        modelSelect.replaceChildren(placeholder);
        $('#ai-model-picker').hidden = true;
        modelStatus.textContent = '';
    };
    let saved = null;
    let busy = false;
    const active = () => container.contains(fields);
    const updateSavedActions = () => {
        const sameProvider = saved?.has_key && provider.value === saved.provider;
        test.disabled = busy || !sameProvider || model.value.trim() !== saved.model || !!key.value.trim() || (provider.value === 'claude' && workspace.value.trim() !== (saved.workspace_id || ''));
        remove.disabled = busy || !sameProvider;
        loadModels.disabled = busy || (!sameProvider && !key.value.trim());
        saveButton.disabled = busy || !saved || !model.value.trim() || (!sameProvider && !key.value.trim());
        $('#ai-selected-model').textContent = model.value.trim() ? `Model đã chọn: ${model.value.trim()}` : 'Chưa chọn model. Lấy danh sách ở bước 1 để chọn.';
    };
    const syncKey = () => {
        $('#ai-workspace-field').hidden = provider.value !== 'claude';
        $('#ai-model-hint').textContent = provider.value === 'gemini'
            ? 'Chọn model Gemini hỗ trợ generateContent từ danh sách. Tên chatbot/dự án/API key không phải mã model.'
            : provider.value === 'claude' ? 'Chọn model Claude từ danh sách trả về bởi Anthropic. Tên key bạn tự đặt không phải mã model.'
            : provider.value === 'groq' ? 'Chọn model Groq từ danh sách, mã có thể chứa tiền tố nhà-phát-triển/model. Tên key bạn tự đặt không phải mã model.'
            : 'Chọn model có khả năng trả lời văn bản. Danh sách OpenAI có thể bao gồm model cho các tác vụ khác.';
        const retained = saved?.provider === provider.value && saved?.has_key;
        key.required = !retained;
        key.placeholder = retained ? 'Để trống để giữ key đã lưu; nhập để thay key' : 'Nhập API key cho nhà cung cấp này';
        $('#ai-key-state').textContent = retained ? '· Đã lưu ••••••••' : '· Chưa có key';
        updateSavedActions();
    };
    const apply = data => {
        clearModels();
        saved = data;
        provider.value = data.provider;
        model.value = data.model;
        knowledge.value = data.knowledge || '';
        const project = data.project_context;
        $('#ai-project-context').value = project?.text || '';
        $('#ai-project-sources').textContent = Array.isArray(project?.sources) ? `Nguồn: ${project.sources.join(' · ')}` : '';
        $('#ai-project-state').textContent = project?.text ? 'Đã nạp ngữ cảnh dự án: chính sách nhận/trả phòng, liên hệ, tiện ích và hướng dẫn sử dụng website. AI tự động nhận phần này cùng ghi chú bổ sung.' : 'Máy chủ chưa cung cấp ngữ cảnh dự án. Cần cập nhật backend để nạp thông tin mặc định.';
        workspace.value = data.workspace_id || '';
        enabled.checked = !!data.enabled;
        key.value = '';
        $('#ai-config-state').textContent = data.enabled ? 'Chatbot đang bật' : 'Chưa bật nhà cung cấp';
        test.disabled = remove.disabled = !data.has_key;
        syncKey();
    };
    const request = async (method, suffix = '', body) => {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 55000);
        try { return await apiFetch('/admin/ai-settings' + suffix, { method, signal: controller.signal, ...(body ? { body: JSON.stringify(body) } : {}) }); }
        finally { clearTimeout(timeout); }
    };
    const failure = error => error.status === 403 ? 'Chỉ quản trị viên được quản lý API key AI.'
        : error.status === 404 ? 'Máy chủ chưa có API quản lý AI. Cần cập nhật và khởi động lại backend.'
        : error.status === 503 ? 'Không thể lưu cấu hình hoặc kết nối AI. Kiểm tra dịch vụ máy chủ và kết nối tới nhà cung cấp.'
        : error.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : error.name === 'AbortError' ? 'Yêu cầu đã hết thời gian chờ. Vui lòng tải lại cấu hình để kiểm tra trước khi thử lại.'
        : error.message || 'Chưa thể hoàn tất thao tác. Vui lòng thử lại.';
    async function run(operation) {
        if (busy) return;
        busy = true; fields.disabled = true; feedback.textContent = 'Đang xử lý…';
        try { await operation(); }
        catch (error) { if (active()) feedback.textContent = failure(error); }
        finally { busy = false; if (active()) { fields.disabled = !saved; updateSavedActions(); } }
    }
    const load = () => run(async () => {
        const data = await request('GET');
        if (!active()) return;
        apply(data); feedback.textContent = ''; $('#ai-reload').hidden = true;
    });
    provider.addEventListener('change', () => {
        clearModels();
        $('#ai-manual-model').open = false;
        key.value = '';
        model.value = provider.value === saved?.provider ? saved.model : '';
        workspace.value = provider.value === saved?.provider ? saved.workspace_id || '' : '';
        syncKey();
        $('#ai-config-state').textContent = provider.value !== saved?.provider ? 'Nhà cung cấp mới chưa được lưu' : saved.enabled ? 'Chatbot đang bật' : 'Chưa bật nhà cung cấp';
    });
    [model, key, workspace].forEach(field => field.addEventListener('input', updateSavedActions));
    [key, workspace].forEach(field => field.addEventListener('input', clearModels));
    model.addEventListener('input', () => { modelSelect.value = model.value; });
    modelSelect.addEventListener('change', () => {
        if (modelSelect.value) model.value = modelSelect.value;
        updateSavedActions();
    });
    $('#ai-key-form').addEventListener('submit', event => {
        event.preventDefault();
        if (busy) return;
        if (!key.value.trim() && !(saved?.has_key && provider.value === saved.provider)) {
            modelStatus.textContent = 'Vui lòng nhập API key cho nhà cung cấp đã chọn.'; key.focus(); return;
        }
        run(async () => {
        clearModels(); modelStatus.textContent = 'Đang lấy model từ nhà cung cấp…';
        const body = { provider: provider.value, api_key: key.value.trim(), workspace_id: provider.value === 'claude' ? workspace.value.trim() : '' };
        try {
            const data = await request('POST', '/models', body);
            if (!active()) return;
            for (const item of data.models) {
                const option = document.createElement('option'); option.value = item.id;
                option.textContent = item.name && item.name !== item.id ? `${item.name} · ${item.id}` : item.id;
                modelSelect.append(option);
            }
            $('#ai-model-picker').hidden = !data.models.length;
            modelSelect.value = model.value || '';
            if (!modelSelect.value) modelSelect.value = '';
            modelStatus.textContent = data.models.length ? `Đã lấy ${data.models.length} model${data.more_available ? ' (nhà cung cấp còn trang kết quả khác)' : ''}. Chọn model hỗ trợ chat văn bản, sau đó lưu và kiểm tra kết nối; có trong danh sách chưa đảm bảo hạn mức hoặc quyền sinh câu trả lời.` : 'Nhà cung cấp chưa trả về model phù hợp. Kiểm tra quyền của key hoặc nhập Model ID từ tài liệu nhà cung cấp.';
            feedback.textContent = '';
        } catch (error) {
            if (active()) { modelStatus.textContent = failure(error); feedback.textContent = ''; }
        } finally { body.api_key = ''; }
        }).then(() => {
            if (active() && !$('#ai-model-picker').hidden) modelSelect.focus();
        });
    });
    $('#ai-settings-form').addEventListener('submit', event => {
        event.preventDefault();
        if (busy) return;
        if (!model.value.trim()) {
            modelStatus.textContent = 'Hãy lấy danh sách ở bước 1 rồi chọn model trước khi lưu cấu hình.';
            loadModels.focus(); return;
        }
        if (!key.value.trim() && !(saved?.has_key && provider.value === saved.provider)) {
            modelStatus.textContent = 'Vui lòng nhập API key ở bước 1 trước khi lưu cấu hình.';
            key.focus(); return;
        }
        // Capture before disabling fields; never persist credentials in browser storage.
        const body = { provider: provider.value, model: model.value.trim(), api_key: key.value.trim(), knowledge: knowledge.value.trim(), enabled: enabled.checked, workspace_id: provider.value === 'claude' ? workspace.value.trim() : '' };
        run(async () => {
            try {
                const data = await request('PUT', '', body);
                if (active()) { apply(data); feedback.textContent = 'Đã lưu cấu hình. Bạn có thể kiểm tra kết nối trước khi sử dụng chatbot.'; }
            } finally { key.value = ''; body.api_key = ''; }
        });
    });
    test.addEventListener('click', () => run(async () => {
        const data = await request('POST', '/test');
        if (active()) feedback.textContent = `${data.message} Nhà cung cấp: ${data.provider} · Model: ${data.model}`;
    }));
    remove.addEventListener('click', async () => {
        if (busy || !saved?.has_key) return;
        const confirmed = window.showCustomConfirm ? await window.showCustomConfirm('Xóa API key đã lưu và tắt kết nối nhà cung cấp AI?', { confirmLabel: 'Xóa key' }) : window.confirm('Xóa API key đã lưu và tắt chatbot?');
        if (!confirmed || !active()) return;
        run(async () => { const data = await request('DELETE'); if (active()) { apply(data); feedback.textContent = 'Đã xóa API key và tắt kết nối nhà cung cấp.'; } });
    });
    $('#ai-reload').addEventListener('click', load);
    await load();
    if (active() && !saved) { $('#ai-config-state').textContent = 'Chưa tải được cấu hình'; $('#ai-reload').hidden = false; }
}
