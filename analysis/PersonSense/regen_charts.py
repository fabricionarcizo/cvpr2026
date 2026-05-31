#!/usr/bin/env python3
"""Regenerate the 6 final charts from personsense-bench-results.csv.

Output: 3 S25 + 3 FP5, all focused on the Q3VL 2B Q8_0/Q8_0 recommended config.

  s25_01_total_time_min.png   — wall-clock per image, natural-resolution configs
  s25_02_total_time_max.png   — wall-clock per image, max-token-capped configs
  s25_03_cliff_and_ttft.png   — mAP vs max-tokens + TTFT vs max-tokens side-by-side

  fp5_01_total_time_min.png   — same as S25 but FP5
  fp5_02_total_time_max.png
  fp5_03_cliff_and_ttft.png
"""
from __future__ import annotations
import csv
import statistics
from pathlib import Path
import matplotlib.pyplot as plt

HERE = Path(__file__).parent.resolve()
CSV = HERE / "personsense-bench-results.csv"
OUTDIR = HERE / "charts"
OUTDIR.mkdir(exist_ok=True)


def load(device: str):
    rows = []
    for r in csv.DictReader(open(CSV)):
        if r["device"] != device: continue
        if r["family"] != "q3vl": continue
        for k in ("encode_ms", "decode_ms", "prompt_eval_ms", "gen_ms",
                  "tok_per_sec", "ttft_ms", "iou"):
            r[k] = float(r[k]) if r[k] not in ("", "None") else None
        r["tok"] = int(r["tok"])
        r["ok"] = bool(int(r["ok"]))
        rows.append(r)
    return rows


def aggregate(rows):
    keyed = {}
    for r in rows:
        k = (r["quant"], r["mmproj"], r["bound"], r["tok"])
        keyed.setdefault(k, []).append(r)
    out = []
    for k, rs in sorted(keyed.items()):
        ious = [r["iou"] for r in rs if r["ok"]]
        out.append({
            "quant": k[0], "mmproj": k[1], "bound": k[2], "tok": k[3],
            "n": len(rs),
            "map50": sum(1 for x in ious if x >= 0.5) / max(1, len(ious)),
            "ttft_s": statistics.mean(
                [r["ttft_ms"] for r in rs if r["ttft_ms"] is not None]) / 1000.0,
            "gen_s": statistics.mean(
                [r["gen_ms"] for r in rs if r["gen_ms"] is not None]) / 1000.0,
        })
    return out


def chart_total_time(rows, device, bound, outfile):
    sub = sorted([r for r in rows if r["bound"] == bound],
                 key=lambda r: r["ttft_s"] + r["gen_s"])
    if not sub:
        return None
    labels = [f'LM={r["quant"]}\nmm={r["mmproj"]}\n{bound}={r["tok"]}' for r in sub]
    x = list(range(len(sub)))
    ttft = [r["ttft_s"] for r in sub]
    gen  = [r["gen_s"] for r in sub]
    total = [a + b for a, b in zip(ttft, gen)]

    width = max(12, 0.55 * len(sub) + 4)
    fig, ax = plt.subplots(figsize=(width, 6.5))
    ax.bar(x, ttft, color="#88CCEE", label="Time to first token (encode + prefill)")
    ax.bar(x, gen, bottom=ttft, color="#332288", label="Token generation time")

    for xi, r, t in zip(x, sub, total):
        ax.text(xi, t + max(total) * 0.015, f"{t:.1f}s",
                ha="center", fontsize=9, fontweight="bold")
        ax.text(xi, -max(total) * 0.06, f'mAP {r["map50"]:.2f}',
                ha="center", fontsize=8, color="#117733")

    bound_label = "image-min-tokens (natural resolution)" if bound == "min" else "image-max-tokens (capped)"
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=8)
    ax.set_ylabel("Wall-clock time (seconds)")
    ax.set_title(f"Qwen3-VL 2B on {device.upper()} — {bound_label}\nwall-clock per image, sorted fastest → slowest")
    ax.legend(loc="upper left", fontsize=9)
    ax.grid(axis="y", alpha=0.25)
    ax.set_ylim(-max(total) * 0.1, max(total) * 1.15)
    fig.tight_layout()
    fig.savefig(outfile, dpi=160); plt.close(fig)
    return outfile


def chart_cliff_and_ttft(rows, device, outfile):
    """mAP vs max-tokens + TTFT vs max-tokens, side-by-side."""
    sub = sorted([r for r in rows if r["bound"] == "max"
                  and r["quant"] == "Q8_0" and r["mmproj"] == "Q8_0"],
                 key=lambda r: r["tok"])
    if not sub:
        return None
    # min=256 baseline for reference
    baseline = next((r for r in rows
                     if r["bound"] == "min" and r["quant"] == "Q8_0"
                     and r["mmproj"] == "Q8_0" and r["tok"] == 256), None)
    base_map = baseline["map50"] if baseline else 0.60

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5.5))

    xs = [r["tok"] for r in sub]
    ys_map = [r["map50"] for r in sub]
    ys_ttft = [r["ttft_s"] for r in sub]

    # left — accuracy
    ax1.plot(xs, ys_map, marker="o", linewidth=3, markersize=12, color="#4477AA")
    for x, y in zip(xs, ys_map):
        ax1.annotate(f"{y:.2f}", (x, y), textcoords="offset points",
                     xytext=(0, 12), fontsize=10, ha="center", fontweight="bold")
    ax1.axhline(base_map, color="#117733", linestyle="--", alpha=0.6,
                label=f"natural-resolution baseline (mAP {base_map:.2f})")
    # mark the cliff region if S25 (cliff probes at 72/80/88 only on S25)
    has_cliff_probe = any(r["tok"] in (72, 80, 88) for r in sub)
    if has_cliff_probe:
        ax1.axvspan(64, 72, alpha=0.18, color="orange", label="cliff (64→72)")
    ax1.set_xlabel("--image-max-tokens", fontsize=11)
    ax1.set_ylabel("mAP@.5", fontsize=11)
    ax1.set_title(f"{device.upper()} — accuracy cliff\n(Qwen3-VL 2B, Q8_0 LM + Q8_0 mmproj)", fontsize=12)
    ax1.set_xticks(xs)
    ax1.grid(alpha=0.3)
    ax1.legend(loc="lower right", fontsize=9)
    if device == "fp5":
        ax1.set_ylim(0.20, 0.70)
    else:
        ax1.set_ylim(0.18, 0.65)

    # right — TTFT
    ax2.plot(xs, ys_ttft, marker="o", linewidth=3, markersize=12, color="#332288")
    for x, y in zip(xs, ys_ttft):
        ax2.annotate(f"{y:.1f}s", (x, y), textcoords="offset points",
                     xytext=(0, 12), fontsize=10, ha="center", fontweight="bold")
    ax2.set_xlabel("--image-max-tokens", fontsize=11)
    ax2.set_ylabel("TTFT (seconds)", fontsize=11)
    ax2.set_title(f"{device.upper()} — TTFT cost\n(same config)", fontsize=12)
    ax2.set_xticks(xs)
    ax2.grid(alpha=0.3)

    fig.tight_layout()
    fig.savefig(outfile, dpi=160); plt.close(fig)
    return outfile


def main():
    for device, prefix in [("s25", "s25"), ("fp5", "fp5")]:
        rows = aggregate(load(device))
        n_min = sum(1 for r in rows if r["bound"] == "min")
        n_max = sum(1 for r in rows if r["bound"] == "max")
        print(f"\n[{device}] {n_min} min-tokens configs · {n_max} max-tokens configs")
        out = chart_total_time(rows, device, "min", OUTDIR / f"{prefix}_01_total_time_min.png")
        if out: print(f"  → {out.name}")
        out = chart_total_time(rows, device, "max", OUTDIR / f"{prefix}_02_total_time_max.png")
        if out: print(f"  → {out.name}")
        out = chart_cliff_and_ttft(rows, device, OUTDIR / f"{prefix}_03_cliff_and_ttft.png")
        if out: print(f"  → {out.name}")


if __name__ == "__main__":
    main()
