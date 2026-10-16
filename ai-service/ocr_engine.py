import re
import threading
from datetime import datetime
import numpy as np
from face_engine import ModelUnavailable


def parse_lines(lines):
    text = "\n".join(lines)
    number = re.search(r"(?<!\d)\d{12}(?!\d)", text)
    name = re.search(r"(?:Họ và tên|Full name)\s*[:：]?\s*\n?([^\n]+)", text, re.I)
    birth = re.search(r"(?<!\d)(\d{2}/\d{2}/\d{4})(?!\d)", text)
    address = re.search(r"(?:Nơi thường trú|Place of residence)\s*[:：]?\s*\n?([^\n]+)", text, re.I)
    if not number or not name or not birth:
        raise ValueError("Incomplete identity extraction; perform manual verification")
    try:
        dob = datetime.strptime(birth.group(1), "%d/%m/%Y").date().isoformat()
    except ValueError as exc:
        raise ValueError("Unrecognized birth date; perform manual verification") from exc
    return {"id_card": number.group(), "full_name": name.group(1).strip(),
            "dob": dob, "address": address.group(1).strip() if address else ""}


class OcrEngine:
    def __init__(self, settings):
        self.settings, self.model = settings, None
        self.lock = threading.Lock()

    def recognize(self, image):
        with self.lock:
            if self.model is None:
                directories = [self.settings.models / part for part in ("ocr-det", "ocr-rec", "ocr-cls")]
                if not all((p / "inference.pdmodel").is_file() for p in directories):
                    raise ModelUnavailable("Approved OCR models are unavailable")
                from paddleocr import PaddleOCR
                self.model = PaddleOCR(lang="vi", use_gpu=False, use_angle_cls=True,
                    det_model_dir=str(directories[0]), rec_model_dir=str(directories[1]),
                    cls_model_dir=str(directories[2]), show_log=False, cpu_threads=2)
            pages = self.model.ocr(np.asarray(image), cls=True)
            return parse_lines([line[1][0] for page in (pages or []) if page for line in page])
