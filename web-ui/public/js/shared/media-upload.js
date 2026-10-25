import { api } from '../../api.js';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png']);

export function validateImageFile(file) {
  if (!file || !IMAGE_TYPES.has(file.type) || file.size < 1 || file.size > MAX_IMAGE_BYTES) {
    throw new Error('Chọn ảnh JPEG hoặc PNG, dung lượng tối đa 5 MB.');
  }
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(new Error('Không đọc được ảnh đã chọn.'));
    reader.readAsDataURL(file);
  });
}

export async function uploadMedia(file, endpoint = '/admin/catalog/media') {
  validateImageFile(file);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 60000);
  try {
    return await api.post(endpoint, {
      fileName: file.name,
      dataBase64: await readFile(file)
    }, { signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}
