#!/usr/bin/env bash
# Regenerate charuco_board.png from specification.json (canonical printable target).
# Requires: uv (https://github.com/astral-sh/uv)
set -euo pipefail
CHARUCO_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC="$CHARUCO_DIR/specification.json"
OUT="$CHARUCO_DIR/charuco_board.png"
export PATH="${HOME}/.local/bin:${PATH}"

uv run --with opencv-python-headless --with numpy python - <<PY
import json
from pathlib import Path
import cv2
import numpy as np

spec = json.loads(Path(r"""$SPEC""").read_text())
sx, sy = int(spec["squaresX"]), int(spec["squaresY"])
square_mm = float(spec["squareSizeMm"])
marker_mm = float(spec["markerSizeMm"])
margin_mm = float(spec.get("marginMm", 10))
dpi = int(spec.get("dpi", [300])[0])
assert spec["dictionary"] == "DICT_5X5_1000", spec["dictionary"]
dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_5X5_1000)
board = cv2.aruco.CharucoBoard((sx, sy), square_mm, marker_mm, dictionary)

board_w_mm = sx * square_mm + 2 * margin_mm
board_h_mm = sy * square_mm + 2 * margin_mm
px_per_mm = dpi / 25.4
out_w = int(round(board_w_mm * px_per_mm))
out_h = int(round(board_h_mm * px_per_mm))
margin_px = int(round(margin_mm * px_per_mm))
inner_w = out_w - 2 * margin_px
inner_h = out_h - 2 * margin_px

board_img = board.generateImage((inner_w, inner_h), marginSize=0, borderBits=1)
canvas = np.full((out_h, out_w), 255, dtype=np.uint8)
canvas[margin_px:margin_px + board_img.shape[0], margin_px:margin_px + board_img.shape[1]] = board_img
out = Path(r"""$OUT""")
cv2.imwrite(str(out), canvas)
print(f"Wrote {out} ({out_w}x{out_h} @ {dpi} DPI) board={sx}x{sy}")
PY
