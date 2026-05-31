# PersonSense — VLM Benchmark Analysis

This directory contains the benchmark data, Jupyter notebook, and chart scripts
used to select the best vision-language model (VLM) configuration for the
[EdgeVisionAI-PersonSense](../../android/EdgeVisionAI-PersonSense/README.md)
Android app.

This analysis is part of the CVPR 2026 tutorial
[*Edge AI in Action: Mastering On-Device Inference*](../../README.md).

---

## Goal

Find the fastest on-device VLM configuration that can detect people and return
tight bounding boxes with acceptable accuracy, running **CPU-only** on a
Snapdragon phone.

---

## Hardware

| Device | Short name | SoC | GPU | RAM |
|---|---|---|---|---|
| Samsung Galaxy S25 Ultra | `s25` | Snapdragon 8 Elite (SM8750) | Adreno 830 | 12 GB |
| Fairphone 5 | `fp5` | Snapdragon QCM6490 | Adreno 643 | 8 GB |

Both devices run **CPU-only** inference (`-ngl 0 --no-mmproj-offload`), 4
threads, flash-attention enabled, Q8_0 KV cache.

---

## What was evaluated

| Axis | Options tested |
|---|---|
| Model family | Qwen3.5 Mamba-hybrid (`q35`) · Qwen3-VL transformer (`q3vl`) |
| Model size | 0.8 B · 2 B |
| LM weight quantization | Q4_0 · Q8_0 |
| Vision projector (mmproj) quantization | F16 · Q8_0 |
| Visual token bound mode | `min` (natural resolution) · `max` (capped) |
| `--image-max-tokens` cap (max mode only) | 32, 48, 64, 72, 80, 88, 96, 128, 196, 256 |

---

## Directory contents

```text
analysis/PersonSense/
├── personsense-bench-results.csv      Raw per-image benchmark measurements
├── personsense_qwen3vl_analysis.ipynb Jupyter notebook — full analysis
├── regen_charts.py                    Script to regenerate the 6 final charts
└── charts/
    ├── s25_01_total_time_min.png      S25 — wall-clock, natural-resolution configs
    ├── s25_02_total_time_max.png      S25 — wall-clock, max-token-capped configs
    ├── s25_03_cliff_and_ttft.png      S25 — mAP@.5 cliff + TTFT vs max-tokens
    ├── fp5_01_total_time_min.png      FP5 — wall-clock, natural-resolution configs
    ├── fp5_02_total_time_max.png      FP5 — wall-clock, max-token-capped configs
    └── fp5_03_cliff_and_ttft.png      FP5 — mAP@.5 cliff + TTFT vs max-tokens
```

---

## CSV schema

`personsense-bench-results.csv` — one row per inference call.

| Column | Type | Description |
|---|---|---|
| `device` | string | `s25` or `fp5` |
| `family` | string | `q35` (Qwen3.5) or `q3vl` (Qwen3-VL) |
| `model` | string | Model size label, e.g. `2B` |
| `quant` | string | LM weight quantization: `Q4_0` or `Q8_0` |
| `mmproj` | string | Vision projector quantization: `F16` or `Q8_0` |
| `bound` | string | Token bound mode: `min` (natural res) or `max` (capped) |
| `tok` | int | Visual token count (natural) or cap value (capped) |
| `img` | string | COCO image filename used for this run |
| `iou` | float | IoU of the predicted bounding box vs ground truth |
| `encode_ms` | float | Image encoding time (ms) |
| `decode_ms` | float | Token decode time (ms) |
| `prompt_eval_ms` | float | Prompt evaluation time (ms) |
| `prompt_tokens` | int | Number of prompt tokens |
| `gen_ms` | float | Token generation time (ms) |
| `n_tokens` | int | Number of generated tokens |
| `tok_per_sec` | float | Generation throughput (tokens/s) |
| `load_ms` | float | Model load time for this run (ms) |
| `ttft_ms` | float | Time to first token (ms) — `encode_ms + prompt_eval_ms` |
| `ok` | int | `1` if a valid bounding box was returned, `0` otherwise |
| `note` | string | Optional notes |

mAP@.5 is derived from `iou` at analysis time:
`mAP@.5 = count(iou ≥ 0.5) / count(ok == 1)`.

---

## Jupyter notebook

`personsense_qwen3vl_analysis.ipynb` contains the full exploratory analysis:

- Filtering and aggregation of the raw CSV
- Model family comparison (Qwen3.5 Mamba vs Qwen3-VL transformer)
- Size and quantization trade-off charts
- Visual-token accuracy cliff identification
- Final operating point selection

To run the notebook:

```bash
pip install jupyterlab pandas matplotlib
jupyter lab personsense_qwen3vl_analysis.ipynb
```

---

## Regenerating charts

`regen_charts.py` produces the six final summary charts from the CSV without
requiring the full Jupyter environment:

```bash
python regen_charts.py
```

Output PNGs are written to `charts/`. Dependencies:

```bash
pip install matplotlib
```

---

## Key findings

### Recommended configuration

**Qwen3-VL 2B · Q8_0 LM · Q8_0 mmproj · `--image-max-tokens 72`**

| Device | TTFT | mAP@.5 |
|---|---|---|
| S25 Ultra (CPU) | ~2.8 s | 0.48 |
| Fairphone 5 (CPU) | ~11 s | 0.43 |

### Visual token accuracy cliff

For Qwen3-VL 2B (Q8_0 / Q8_0) on S25 Ultra:

| `--image-max-tokens` | mAP@.5 | TTFT (s) |
|---|---|---|
| 32 | ~0.25 | ~1.2 |
| 48 | ~0.36 | ~1.7 |
| 64 | ~0.35 | ~2.0 |
| **72** | **~0.48** | **~2.8** |
| 88 | ~0.48 | ~3.3 |
| 128 | ~0.48 | ~4.5 |
| 256 (min/natural) | ~0.50 | ~7.5 |

The accuracy cliff sits between 64 and 72 visual tokens. Using `max=72`
captures essentially the same mAP as natural resolution (`min`) while cutting
TTFT by ~2.7×. This is the operating point shipped in the
[EdgeVisionAI-PersonSense](../../android/EdgeVisionAI-PersonSense/README.md)
app.

### Model family

Qwen3-VL (standard transformer) consistently outperforms the Qwen3.5
Mamba-hybrid family for this structured bounding-box task. The Mamba hybrid
shows lower generation throughput and lower mAP on both devices.

---

## License

[MIT License](../../LICENSE) — Copyright (c) 2026 Fabricio Batista Narcizo
