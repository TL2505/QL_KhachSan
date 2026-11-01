import { apiFetch } from '../../api.js?v=2';

export async function renderAiSettings(container) {
    container.innerHTML = `
      <section class="ai-settings-view" aria-label="Quản lý API key AI">
        <div class="page-header"><div><h1>Quản lý API key AI</h1><p>Kết nối nhà cung cấp và thiết lập câu trả lời cho Quản gia AI.</p></div></div>
        <div class="ai-settings-card">
          <div class="ai-settings-topline"><h2>Cấu hình chatbot</h2><div class="ai-settings-state"><span id="ai-busy" class="ais-loading ais-loading-spinner ais-loading-sm" aria-hidden="true" hidden></span><span class="ais-badge ais-badge-outline" id="ai-config-state" role="status">Đang tải…</span></div></div>
          <fieldset id="ai-settings-fields" class="ai-settings-grid" aria-label="Cấu hình nhà cung cấp AI" disabled>
            <form id="ai-key-form" class="ais-card ais-card-border ai-settings-step" autocomplete="off" aria-labelledby="ai-key-heading">
              <div class="ais-card-body">
                <div class="ai-step-heading"><span class="ais-badge ais-badge-outline ai-step-number" aria-hidden="true">1</span><div><h3 id="ai-key-heading">Kết nối nhà cung cấp</h3><p>Nhập key để lấy danh sách model.</p></div></div>
                <fieldset class="ais-fieldset ai-settings-fields-group">
                  <legend class="ais-fieldset-legend">Thông tin kết nối</legend>
                  <div class="ai-form-group"><label for="ai-provider">Nhà cung cấp</label><select class="ais-select" id="ai-provider"><option value="openai">OpenAI</option><option value="gemini">Google Gemini</option><option value="claude">Claude (Anthropic)</option><option value="groq">Groq</option></select></div>
                  <div class="ai-form-group"><label class="ai-key-label" for="ai-api-key">API key <span class="ais-badge ais-badge-ghost ais-badge-sm" id="ai-key-state"></span></label><input class="ais-input" id="ai-api-key" name="provider-credential" type="password" autocomplete="new-password" spellcheck="false" maxlength="512" placeholder="Nhập API key" aria-describedby="ai-key-hint"><small id="ai-key-hint">Key được mã hóa khi lưu. Để trống để giữ key đã lưu của cùng nhà cung cấp.</small></div>
                  <div class="ai-form-group" id="ai-workspace-field" hidden><label for="ai-workspace">Workspace ID Claude <span class="ai-optional">(tùy chọn)</span></label><input class="ais-input" id="ai-workspace" type="text" maxlength="107" placeholder="wrkspc_…" autocomplete="off"><small>Chỉ cần khi API key Claude dùng nhiều workspace.</small></div>
                </fieldset>
                <button class="ais-btn ais-btn-outline ai-models-button" id="ai-load-models" type="submit">Lấy danh sách model <span aria-hidden="true">→</span></button>
                <small>Bước này chưa lưu key hoặc bật chatbot.</small>
                <p id="ai-model-status" class="ais-alert ais-alert-soft ai-inline-feedback" role="status" aria-live="polite"></p>
                <details class="ais-collapse ais-collapse-arrow ai-settings-details"><summary class="ais-collapse-title">Lưu ý về API key và model</summary><div class="ais-collapse-content"><p>Dùng key do nhà cung cấp cấp. Tên bạn đặt cho key chỉ là nhãn quản lý, khác với Model ID. Danh sách model phụ thuộc quyền của key; hãy kiểm tra kết nối sau khi lưu.</p></div></details>
              </div>
            </form>
            <form id="ai-settings-form" class="ais-card ais-card-border ai-settings-step" autocomplete="off" aria-labelledby="ai-model-heading">
              <div class="ais-card-body">
                <div class="ai-step-heading"><span class="ais-badge ais-badge-outline ai-step-number" aria-hidden="true">2</span><div><h3 id="ai-model-heading">Model & hành vi chatbot</h3><p id="ai-model-guide">Chọn model từ bước 1, bổ sung thông tin rồi lưu cấu hình.</p></div></div>
                <div class="ai-form-group">
                  <div class="ai-form-group" id="ai-model-picker" hidden><label for="ai-model-select">Model từ nhà cung cấp</label><select class="ais-select" id="ai-model-select"><option value="">Chọn model…</option></select></div>
                  <p class="ai-selected-model" id="ai-selected-model" role="status">Chưa chọn model</p>
                  <details class="ais-collapse ais-collapse-arrow ai-settings-details" id="ai-manual-model"><summary class="ais-collapse-title">Nhập Model ID thủ công</summary><div class="ais-collapse-content ai-form-group"><label for="ai-model">Model ID</label><input class="ais-input" id="ai-model" type="text" maxlength="100" placeholder="Chỉ nhập nếu bạn đã biết Model ID"><small id="ai-model-hint">Model là mô hình AI dùng để trả lời, khác với tên key bạn tự đặt.</small></div></details>
                </div>
                <div class="ai-form-group"><label for="ai-knowledge">Thông tin khách sạn bổ sung</label><textarea class="ais-textarea" id="ai-knowledge" rows="5" maxlength="10000" placeholder="Bổ sung chính sách, dịch vụ hoặc thông tin đã được xác nhận…"></textarea><small>Chỉ nhập thông tin đã xác nhận. Ngữ cảnh mặc định vẫn được sử dụng khi để trống.</small></div>
                <details class="ais-collapse ais-collapse-arrow ai-settings-details"><summary class="ais-collapse-title">Ngữ cảnh mặc định của dự án</summary><div class="ais-collapse-content ai-form-group"><p id="ai-project-state">Đang tải ngữ cảnh dự án…</p><label for="ai-project-context">Thông tin từ website và các chức năng hiện có</label><textarea class="ais-textarea" id="ai-project-context" rows="7" readonly></textarea><small id="ai-project-sources"></small></div></details>
                <div class="ai-enable-panel"><label class="ai-enable-label" for="ai-enabled"><span><strong>Bật chatbot</strong><small>Sử dụng nhà cung cấp này để trả lời khách.</small></span><input class="ais-toggle" id="ai-enabled" type="checkbox"></label><p>Khi bật, câu hỏi khách và thông tin khách sạn được gửi tới nhà cung cấp đã chọn. Gemini có thể nhận các lượt hội thoại gần nhất.</p></div>
                <div class="ais-card-actions ai-settings-actions"><button class="ais-btn ais-btn-primary" id="ai-save" type="submit" disabled>Lưu cấu hình</button><button class="ais-btn ais-btn-outline" id="ai-test" type="button">Kiểm tra kết nối</button><button class="ais-btn ais-btn-ghost ais-btn-error" id="ai-delete" type="button">Xóa key</button></div>
                <small>Lưu cấu hình thành công trước khi kiểm tra kết nối. Lần kiểm tra có thể dùng một lượng nhỏ hạn mức API.</small>
              </div>
            </form>
          </fieldset>
          <p class="ais-alert ais-alert-soft ai-inline-feedback" id="ai-settings-feedback" role="status" aria-live="polite"></p>
          <button class="ais-btn ais-btn-outline" id="ai-reload" type="button" hidden>Tải lại cấu hình</button>
        </div>
      </section>`;
    if (!document.getElementById('ai-settings-ui')) {
        const stylesheet = document.createElement('link');
        stylesheet.id = 'ai-settings-ui';
        stylesheet.rel = 'stylesheet';
        stylesheet.href = new URL('../../css/ai-settings-ui.css?v=2', import.meta.url).href;
        document.head.append(stylesheet);
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
        modelStatus.removeAttribute('data-state');
    };
    let saved = null;
    let busy = false;
    let accessDenied = false;
    const active = () => container.contains(fields);
    const updateSavedActions = () => {
        const sameProvider = saved?.has_key && provider.value === saved.provider;
        test.disabled = busy || !sameProvider || model.value.trim() !== saved.model || !!key.value.trim() || (provider.value === 'claude' && workspace.value.trim() !== (saved.workspace_id || ''));
        remove.disabled = busy || !sameProvider;
        loadModels.disabled = busy || (!sameProvider && !key.value.trim());
        // A complete new key and selected model may replace an unreadable saved config.
        // Do not require `saved` here: the backend accepts complete replacement drafts.
        saveButton.disabled = busy || !model.value.trim() || (!sameProvider && !key.value.trim());
        $('#ai-selected-model').textContent = model.value.trim() ? `Model đã chọn: ${model.value.trim()}` : 'Chưa chọn model. Lấy danh sách ở bước 1 để chọn.';
        $('#ai-config-state').classList.toggle('ais-badge-success', !!(sameProvider && saved.enabled));
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
        accessDenied = false;
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
    const failure = (error, context = 'settings') => error.status === 403 ? 'Chỉ quản trị viên được quản lý API key AI.'
        : error.status === 404 ? 'Máy chủ chưa có API quản lý AI. Cần cập nhật và khởi động lại backend.'
        : error.status === 503 && context === 'load' ? 'Chưa tải được cấu hình AI đã lưu. Hãy tải lại; nếu lỗi vẫn còn, bạn vẫn có thể nhập bản nháp bằng key mới, lấy danh sách model rồi lưu để khôi phục.'
        : error.status === 503 && context === 'models' ? (error.message === 'Hệ thống đang tạm bận. Vui lòng thử lại sau.' ? 'Máy chủ chưa thể hoàn tất việc lấy danh sách model. Kiểm tra kết nối backend tới Internet hoặc nhà cung cấp rồi thử lại.' : error.message)
        : error.status === 502 && context === 'models' ? error.message
        : error.status === 503 ? 'Không thể lưu cấu hình hoặc kết nối AI. Kiểm tra dịch vụ máy chủ và kết nối tới nhà cung cấp.'
        : error.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : error.name === 'AbortError' ? 'Yêu cầu đã hết thời gian chờ. Vui lòng tải lại cấu hình để kiểm tra trước khi thử lại.'
        : error.message || 'Chưa thể hoàn tất thao tác. Vui lòng thử lại.';
    async function run(operation, context = 'settings') {
        if (busy) return;
        busy = true; fields.disabled = true;
        feedback.textContent = context === 'test' ? 'Đang kiểm tra kết nối tới nhà cung cấp… (tối đa 20 giây)' : 'Đang xử lý…';
        $('#ai-busy').hidden = false;
        try { await operation(); }
        catch (error) {
            if (error.status === 401 || error.status === 403) accessDenied = true;
            if (active()) {
                feedback.textContent = failure(error, context);
                if (!saved && !accessDenied && context !== 'load') feedback.textContent += ' Nếu cấu hình cũ vẫn không tải được, nhập key mới, lấy model rồi lưu để thay cấu hình.';
            }
        }
        finally { busy = false; if (active()) { $('#ai-busy').hidden = true; fields.disabled = accessDenied; updateSavedActions(); } }
    }
    const load = () => run(async () => {
        const data = await request('GET');
        if (!active()) return;
        apply(data); feedback.textContent = ''; $('#ai-reload').hidden = true;
    }, 'load');
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
        clearModels(); modelStatus.dataset.state = 'loading'; modelStatus.textContent = 'Đang lấy model từ nhà cung cấp…';
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
            modelStatus.dataset.state = data.models.length ? 'success' : 'error';
            modelStatus.textContent = data.models.length ? `Đã lấy ${data.models.length} model${data.more_available ? ' (nhà cung cấp còn trang kết quả khác)' : ''}. Chọn model hỗ trợ chat văn bản, sau đó lưu và kiểm tra kết nối; có trong danh sách chưa đảm bảo hạn mức hoặc quyền sinh câu trả lời.` : 'Nhà cung cấp chưa trả về model phù hợp. Kiểm tra quyền của key hoặc nhập Model ID từ tài liệu nhà cung cấp.';
            feedback.textContent = '';
        } catch (error) {
            if (active()) { modelStatus.dataset.state = 'error'; modelStatus.textContent = failure(error, 'models'); feedback.textContent = ''; }
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
    }, 'test'));
    remove.addEventListener('click', async () => {
        if (busy || !saved?.has_key) return;
        const confirmed = await window.showCustomConfirm('Xóa API key đã lưu và tắt kết nối nhà cung cấp AI?', { confirmLabel: 'Xóa key' });
        if (!confirmed || !active()) return;
        run(async () => { const data = await request('DELETE'); if (active()) { apply(data); feedback.textContent = 'Đã xóa API key và tắt kết nối nhà cung cấp.'; } });
    });
    $('#ai-reload').addEventListener('click', load);
    await load();
    if (active() && !saved) { $('#ai-config-state').textContent = 'Chưa tải được cấu hình'; $('#ai-reload').hidden = false; }
}
