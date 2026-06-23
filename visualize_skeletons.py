"""Visualize skeleton graphs exported by SkeletonExportTest.

Reads build/skeleton_viz/skeletons.json and renders each skeleton as a PNG:
  - ENDPOINT nodes : red filled circles
  - JUNCTION nodes: blue filled squares
  - CORNER nodes   : green diamonds
  - Edges          : black polylines with curvature-colored segments

Usage:
    .venv/Scripts/python visualize_skeletons.py
"""

import json
import math
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np
from matplotlib.collections import LineCollection

SCRIPT_DIR = Path(__file__).resolve().parent
JSON_PATH = SCRIPT_DIR / "build" / "skeleton_viz" / "skeletons.json"
OUT_DIR = SCRIPT_DIR / "build" / "skeleton_viz"

# ── rendering constants ──
GRID_SIZE = 256
FIGSIZE = (6, 6)
ENDPOINT_RADIUS = 0.9
JUNCTION_RADIUS = 1.2
CORNER_RADIUS = 0.7

# curvature colormap: blue (low/straight) → red (high/sharp turn)
CURV_CMAP = plt.cm.coolwarm_r  # reversed: blue=cold=straight, red=hot=curved


def load_data(json_path: Path) -> list[dict]:
    with open(json_path) as f:
        return json.load(f)["images"]


def draw_skeleton(ax: plt.Axes, image: dict):
    """Draw one skeleton graph onto the given Axes."""
    name = image["name"]
    nodes = {n["id"]: n for n in image["nodes"]}
    edges = image["edges"]
    cycle_count = image.get("cycleCount", 0)
    total_len = image.get("totalSkeletonLength", 0)

    # ── edges (polyline with curvature color) ──
    for edge in edges:
        path_pts = edge["path"]  # list of [x, y]
        if len(path_pts) < 2:
            continue
        xs = [p[0] for p in path_pts]
        ys = [GRID_SIZE - 1 - p[1] for p in path_pts]  # flip Y for image coords

        curv_profile = edge.get("curvatureProfile", [])
        if curv_profile and len(curv_profile) >= len(path_pts) - 1:
            # Color each segment by its curvature
            segments = []
            colors = []
            max_abs_curv = max(max(abs(c) for c in curv_profile), 1e-6)
            for i in range(len(path_pts) - 1):
                segments.append([(xs[i], ys[i]), (xs[i + 1], ys[i + 1])])
                # Map curvature to [0, 1] — 0=straight, 1=max bend
                c = min(abs(curv_profile[i]) / max_abs_curv, 1.0)
                colors.append(c)

            lc = LineCollection(segments, cmap=CURV_CMAP, array=np.array(colors),
                                linewidths=2.5, alpha=0.85)
            ax.add_collection(lc)
        else:
            # No curvature data — plain gray line
            ax.plot(xs, ys, "-", color="gray", linewidth=1.5, alpha=0.6)

    # ── nodes ──
    for nid, n in nodes.items():
        x = n["x"]
        y = GRID_SIZE - 1 - n["y"]  # flip Y
        ntype = n["type"]

        if ntype == "ENDPOINT":
            ax.plot(x, y, "o", color="red", markersize=ENDPOINT_RADIUS * 4,
                    markeredgecolor="darkred", markeredgewidth=0.8, zorder=5)
        elif ntype == "JUNCTION":
            ax.plot(x, y, "s", color="dodgerblue", markersize=JUNCTION_RADIUS * 4,
                    markeredgecolor="darkblue", markeredgewidth=0.8, zorder=5)
        elif ntype == "CORNER":
            ax.plot(x, y, "D", color="limegreen", markersize=CORNER_RADIUS * 3.5,
                    markeredgecolor="darkgreen", markeredgewidth=0.6, zorder=4)

    # ── labels ──
    n_endpoints = sum(1 for n in nodes.values() if n["type"] == "ENDPOINT")
    n_junctions = sum(1 for n in nodes.values() if n["type"] == "JUNCTION")
    n_corners = sum(1 for n in nodes.values() if n["type"] == "CORNER")

    title = (f"{name}\n"
             f"nodes:{len(nodes)} (E:{n_endpoints} J:{n_junctions} C:{n_corners})  "
             f"edges:{len(edges)}  cycles:{cycle_count}  len:{total_len}")
    ax.set_title(title, fontsize=9, family="monospace")

    # ── grid & layout ──
    ax.set_xlim(-1.5, GRID_SIZE + 0.5)
    ax.set_ylim(-1.5, GRID_SIZE + 0.5)
    ax.set_aspect("equal")
    ax.set_xticks(range(GRID_SIZE + 1), minor=True)
    ax.set_yticks(range(GRID_SIZE + 1), minor=True)
    ax.grid(True, which="minor", color="#e8e8e8", linewidth=0.3)
    ax.grid(False, which="major")

    # Hide tick labels but keep ticks for grid
    ax.tick_params(left=False, bottom=False, labelleft=False, labelbottom=False)

    # Legend
    legend_patches = [
        mpatches.Patch(color="red", label="ENDPOINT"),
        mpatches.Patch(color="dodgerblue", label="JUNCTION"),
        mpatches.Patch(color="limegreen", label="CORNER"),
    ]
    ax.legend(handles=legend_patches, loc="upper right", fontsize=7,
              framealpha=0.7, borderpad=0.4)


def main():
    out_dir = OUT_DIR
    out_dir.mkdir(parents=True, exist_ok=True)

    images = load_data(JSON_PATH)
    print(f"Loaded {len(images)} skeleton graphs from {JSON_PATH}")

    for idx, image in enumerate(images):
        name = image["name"]
        fig, ax = plt.subplots(figsize=FIGSIZE, dpi=100)
        draw_skeleton(ax, image)
        fig.tight_layout()

        out_path = out_dir / f"{name}.png"
        fig.savefig(out_path, dpi=100, bbox_inches="tight",
                    facecolor="white", edgecolor="none")
        plt.close(fig)

        if (idx + 1) % 10 == 0 or idx == len(images) - 1:
            print(f"  Rendered {idx + 1}/{len(images)}: ...{name}")

    print(f"\nDone. {len(images)} PNGs saved to {out_dir}")
    print(f"Open {out_dir / images[0]['name']}.png to view first image.")


if __name__ == "__main__":
    main()
