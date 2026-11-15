"""Replay exported Android inputs, checking native-score parity and detail loss.

Synthetic downsampling is a sensitivity check, not a live-camera accuracy test.
Requires the same isolated dependencies as face_attribute_probe.py.
"""

import argparse
import json
import re
from pathlib import Path

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    interpreter = Interpreter(model_path=str(root / "app/src/main/assets/face_attrib_net.tflite"), num_threads=2)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    assert list(inp["shape"]) == [1, 128, 128, 3] and inp["dtype"] == np.uint8
    assert list(out["shape"]) == [1, 5] and out["dtype"] == np.uint8
    scale, zero = inp["quantization"]
    output_scale, output_zero = out["quantization"]
    native = {}
    pattern = r"phase=(\w+) fixedCrop=\w+ sample=(\d+).*padding=([\d.]+).*mask=([\d.]+)"
    for match in re.finditer(pattern, (args.capture / "scores.log").read_text(encoding="utf-8-sig")):
        phase, sample, padding, mask = match.groups()
        native[f"{phase}-{sample}-input-{padding}.png"] = float(mask)
    rows = []
    for path in sorted((args.capture / "files/crop-diagnostic").glob("*-input-*.png")):
        image = Image.open(path).convert("RGB")
        assert image.size == (128, 128)
        for detail in (128, 96, 64, 48, 32):
            degraded = image if detail == 128 else image.resize((detail, detail), Image.Resampling.BILINEAR).resize((128, 128), Image.Resampling.BILINEAR)
            tensor = np.clip(np.floor(np.asarray(degraded, dtype=np.float32) / np.float32(255)
                                      / np.float32(scale) + zero + np.float32(0.5)), 0, 255).astype(np.uint8)[None]
            interpreter.set_tensor(inp["index"], tensor)
            interpreter.invoke()
            scores = (interpreter.get_tensor(out["index"])[0].astype(np.float32) - output_zero) * output_scale
            rows.append({"file": path.name, "detail": detail, "mask": float(scores[3]),
                         "leftEyeOpen": float(scores[0]), "rightEyeOpen": float(scores[1]),
                         "nativeMask": native.get(path.name) if detail == 128 else None})
    if not rows:
        parser.error("No exported model inputs found")
    parity = [abs(row["mask"] - row["nativeMask"]) for row in rows if row["nativeMask"] is not None]
    report = {"limitations": "Exact exported RGB input replay; different runtime kernels. Downsampling is synthetic, without new landmark detection or camera pose changes.",
              "matched_native_samples": len(parity), "maximum_mask_difference": max(parity) if parity else None,
              "by_detail": [{"detail": detail, "mask_min": min(r["mask"] for r in rows if r["detail"] == detail),
                             "mask_max": max(r["mask"] for r in rows if r["detail"] == detail)}
                            for detail in (128, 96, 64, 48, 32)], "samples": rows}
    (args.capture / "replay.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "samples"}, indent=2))


if __name__ == "__main__":
    main()
