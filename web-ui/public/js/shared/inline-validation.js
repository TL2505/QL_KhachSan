const VALIDITY_KEYS = [
  'valueMissing',
  'typeMismatch',
  'patternMismatch',
  'badInput',
  'rangeUnderflow',
  'rangeOverflow',
  'stepMismatch',
  'tooShort',
  'tooLong'
];

const DEFAULT_MESSAGES = {
  valueMissing: 'Vui lòng nhập thông tin này.',
  typeMismatch: 'Vui lòng nhập đúng định dạng.',
  patternMismatch: 'Thông tin chưa đúng định dạng.',
  badInput: 'Vui lòng nhập giá trị hợp lệ.',
  rangeUnderflow: 'Giá trị nhỏ hơn mức cho phép.',
  rangeOverflow: 'Giá trị vượt quá mức cho phép.',
  stepMismatch: 'Giá trị chưa đúng mức cho phép.',
  tooShort: 'Thông tin chưa đủ độ dài tối thiểu.',
  tooLong: 'Thông tin vượt quá độ dài tối đa.'
};

/** Replace native browser validation bubbles with accessible, localized field errors. */
export function enableInlineValidation(form, messagesByField = {}) {
  if (!form) throw new TypeError('A form element is required.');
  form.noValidate = true;

  const fields = Object.entries(messagesByField).flatMap(([id, messages]) => {
    const field = [...form.querySelectorAll('[id]')].find(element => element.id === id);
    if (!field) return [];

    const errorId = `${id}-validation-error`;
    let error = [...form.querySelectorAll('[id]')].find(element => element.id === errorId);
    if (!error) {
      error = form.ownerDocument.createElement('small');
      error.id = errorId;
      error.className = 'rooms-form-field-error';
      error.setAttribute('role', 'alert');
      error.hidden = true;
      (field.closest('.form-group') || field.parentElement || form).append(error);
    }

    const describedBy = new Set((field.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
    describedBy.add(errorId);
    field.setAttribute('aria-describedby', [...describedBy].join(' '));

    return [{ field, error, messages }];
  });

  const clearError = ({ field, error }) => {
    error.textContent = '';
    error.hidden = true;
    field.removeAttribute('aria-invalid');
  };

  const updateError = ({ field, error, messages }) => {
    const validity = field.validity;
    if (!validity || validity.valid) {
      clearError({ field, error });
      return false;
    }

    const key = VALIDITY_KEYS.find(candidate => validity[candidate]);
    if (!key) {
      clearError({ field, error });
      return false;
    }
    error.textContent = messages[key] || DEFAULT_MESSAGES[key];
    error.hidden = false;
    field.setAttribute('aria-invalid', 'true');
    return true;
  };

  for (const binding of fields) {
    const eventName = binding.field.tagName === 'SELECT' ? 'change' : 'input';
    binding.field.addEventListener(eventName, () => {
      if (binding.field.hasAttribute('aria-invalid')) updateError(binding);
    });
    if (eventName !== 'change') binding.field.addEventListener('change', () => {
      if (binding.field.hasAttribute('aria-invalid')) updateError(binding);
    });
  }

  form.addEventListener('submit', event => {
    let firstInvalid = null;
    for (const binding of fields) {
      if (updateError(binding) && !firstInvalid) firstInvalid = binding.field;
    }
    if (!firstInvalid) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    firstInvalid.focus();
  }, true);

  return { fields: fields.map(({ field }) => field), validate: () => fields.every(binding => !updateError(binding)) };
}
