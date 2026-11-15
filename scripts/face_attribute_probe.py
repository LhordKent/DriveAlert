"""Inspect a local image with DriveAlert's bundled model; never contacts a device.

This is a diagnostic, not an accuracy test. Bounds are manually supplied face
bounds in source pixels. Screenshots containing dimming/dialogs are unsuitable
for establishing raw-camera accuracy. Output includes local face images.

Dependencies: ai-edge-litert, numpy, pillow (use an isolated virtual environment).
"""

import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--box", type=float, nargs=4, required=True,
                        metavar=("LEFT", "TOP", "RIGHT", "BOTTOM"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=Path(__file__).resolve().parents[1]
                        / "app/src/main/assets/face_attrib_net.tflite")
    args = parser.parse_args()
    source = Image.open(args.image).convert("RGB")
    left, top, right, bottom = args.box
    if not (0 <= left < right <= source.width and 0 <= top < bottom <= source.height):
        parser.error("Face bounds must be nonempty and inside the image")
    args.output.mkdir(parents=True, exist_ok=True)
    interpreter = Interpreter(model_path=str(args.model), num_threads=2)
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    assert list(inp["shape"]) == [1, 128, 128, 3] and inp["dtype"] == np.uint8
    assert list(out["shape"]) == [1, 5] and out["dtype"] == np.uint8
    in_scale, in_zero = inp["quantization"]
    out_scale, out_zero = out["quantization"]
    labels = ["leftEyeOpen", "rightEyeOpen", "eyeglasses", "mask", "sunglasses"]
    report = {
        "source": str(args.image.resolve()),
        "source_size": list(source.size),
        "manual_face_bounds": args.box,
        "model_sha256": hashlib.sha256(args.model.read_bytes()).hexdigest(),
        "input_quantization": [in_scale, in_zero],
        "output_quantization": [out_scale, out_zero],
        "limitations": "Manually specified bounds; Pillow bilinear resize approximates Android Canvas. "
                        "Screenshot UI/dimming remains present. Scores do not establish camera accuracy.",
        "samples": [],
    }
    for padding in (0.0, 0.1, 0.2, 0.3):
        # Same square/edge-clamping policy as FaceCropCalculator.
        side = min(max(right - left, bottom - top) * (1 + 2 * padding),
                   min(source.size))
        x = min(max((left + right - side) / 2, 0), source.width - side)
        y = min(max((top + bottom - side) / 2, 0), source.height - side)
        x0, y0 = math.floor(x), math.floor(y)
        pixel_side = min(math.ceil(x + side) - x0, math.ceil(y + side) - y0)
        crop_box = (x0, y0, x0 + pixel_side, y0 + pixel_side)
        crop = source.crop(crop_box).resize((128, 128), Image.Resampling.BILINEAR)
        crop.save(args.output / f"input-padding-{padding:.1f}.png")
        # Kotlin roundToInt for nonnegative values is floor(x + 0.5).
        tensor = np.clip(np.floor(np.asarray(crop, dtype=np.float32) / np.float32(255)
                                  / np.float32(in_scale) + in_zero + np.float32(0.5)),
                         0, 255).astype(np.uint8)[None, ...]
        interpreter.set_tensor(inp["index"], tensor)
        interpreter.invoke()
        values = (interpreter.get_tensor(out["index"])[0].astype(np.float32)
                  - out_zero) * out_scale
        report["samples"].append({"padding": padding, "crop_box": crop_box,
                                  "scores": dict(zip(labels, map(float, values)))})
    destination = args.output / "report.json"
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
