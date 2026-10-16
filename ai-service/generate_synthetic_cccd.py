"""Generate conspicuously invalid, non-person OCR fixtures; no face data."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw


def generate(output):
    output.mkdir(parents=True, exist_ok=True)
    manifest = []
    for i in range(100):
        image = Image.new("RGB", (1000, 630), "#e8edef")
        draw = ImageDraw.Draw(image)
        draw.rectangle((8, 8, 991, 621), outline="red", width=8)
        lines = ["SYNTHETIC TEST ONLY - INVALID DOCUMENT", "NOT A VIETNAMESE ID CARD",
                 "000000000000", f"SYNTHETIC GUEST {i:03}", "2000-01-01", "SYNTHETIC TEST ADDRESS"]
        for y, text in enumerate(lines):
            draw.text((40, 45 + y * 80), text, fill="red" if y < 2 else "black", font_size=28)
        # A deliberately schematic placeholder cannot be mistaken for an actual face.
        draw.rectangle((700, 280, 940, 570), outline="red", width=4)
        draw.text((715, 400), "NO REAL FACE", fill="red", font_size=22)
        filename = f"invalid-synthetic-{i:03}.png"
        destination = output / filename
        if destination.exists():
            raise FileExistsError("Use a fresh output directory")
        image.save(destination)
        manifest.append({"file": filename, "id_card": "000000000000", "full_name": f"SYNTHETIC GUEST {i:03}",
                         "dob": "2000-01-01", "address": "SYNTHETIC TEST ADDRESS", "invalid_document": True})
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    generate(parser.parse_args().output)
