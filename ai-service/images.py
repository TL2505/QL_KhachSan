"""Decode bounded JPEG/PNG bytes in memory; never write identity images to disk."""
import base64
import binascii
import io
import warnings
from PIL import Image, UnidentifiedImageError

MAX_IMAGE = 5 * 1024 * 1024


def decode_image(encoded):
    if not isinstance(encoded, str) or len(encoded) > 4 * ((MAX_IMAGE + 2) // 3):
        raise ValueError("JPEG/PNG image must be at most 5 MB")
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ValueError("Invalid image encoding") from exc
    if not raw or len(raw) > MAX_IMAGE:
        raise ValueError("JPEG/PNG image must be at most 5 MB")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(raw)) as image:
                if image.format not in ("PNG", "JPEG") or image.width * image.height > 12_000_000:
                    raise ValueError("Unsupported image format or dimensions")
                image.load()
                return image.convert("RGB")
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError,
            Image.DecompressionBombWarning) as exc:
        raise ValueError("Invalid image") from exc
