#!/usr/bin/env python3
"""Builds Loom's adaptive icon (foreground + monochrome vector drawables) from one description
of the artwork. Coordinates are in the design's own units (the source art at 1932 px), mapped
onto the 108dp adaptive canvas by one transform.

usage: gen.py launcher3/res/drawable [PREVIEW_DIR]
With PREVIEW_DIR it also writes preview.svg and preview-mono.svg there (render them with
rsvg-convert to check the result)."""
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

BG = "#201B43"  # the background layer: keep @color/folio_icon_background the same
WARP = "#96B2BE"
BEAM = "#5E7292"
ROOF = "#ED8127"
CORAL = "#E35F48"
RED = "#E3482D"
YELLOW = "#EEAA23"
YELLOW_UNDER = "#EA781F"
PINK, TEAL, PURPLE = "#EA3C7E", "#39A7A4", "#91549F"
DOTS = [["#F09522", "#EE7628", "#E6482B"],
        ["#E84A30", "#9457A4", "#35A7A3"],
        ["#35A7A4", "#676CB0", "#8F55A2"]]

# Content: 300..1607 tall, centred at x 986. 56dp tall keeps every part inside the 66dp
# safe circle (warp tips and beam ends just touch it), so any mask shape shows it whole.
CX, CY, HEIGHT = 986.0, 953.5, 1307.0
SCALE = 56.0 / HEIGHT
TX, TY = 54 - CX * SCALE, 54 - CY * SCALE

WARPS = [560, 685, 808, 1162, 1287, 1411]
WARP_R = 31
WARP_Y = (300, 1607)
BEAMS = [414.5, 1494.5]
BEAM_R = 34.5
BEAM_X = (448, 1522)
ROOF_W = 67
APEX = (986, 519)
ROOF_SLOPE = 0.835
ROOF_ENDS_X = (453, 1519)


def f(v):
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


def line_y(p, m, x):
    return p[1] + m * (x - p[0])


def band(p, m, half, x_cut, end_line):
    """A strand: centre line through p with slope m, half its vertical thickness `half`, cut
    straight down at x = x_cut on one end and along end_line (point, slope) on the other."""
    q, n = end_line

    def meet(off):
        # Edge y = p.y + off + m (x - p.x) meets y = q.y + n (x - q.x)
        x = (q[1] - p[1] - off + m * p[0] - n * q[0]) / (m - n)
        return (x, line_y(q, n, x))

    a1 = (x_cut, line_y(p, m, x_cut) - half)
    a2 = (x_cut, line_y(p, m, x_cut) + half)
    b1, b2 = meet(-half), meet(half)
    return "M{},{}L{},{}L{},{}L{},{}Z".format(*map(f, (*a1, *b1, *b2, *a2)))


def dot(x, y, r):
    return f"M{f(x - r)},{f(y)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0Z"


def rect(x1, y1, x2, y2):
    return f"M{f(x1)},{f(y1)}H{f(x2)}V{f(y2)}H{f(x1)}Z"


def warp_line(x):
    return f"M{f(x)},{f(WARP_Y[0] + WARP_R)}V{f(WARP_Y[1] - WARP_R)}"


# The strands, from the measurements (centre line point, slope, half thickness).
H = 44.5
CORAL_L = ((985, 671.5), -0.815)       # rises to the right
RED_L = ((1110, 765.5), 0.812)          # falls to the right
YEL_A = ((985, 819.5), -0.755)          # left leg, rises to the right
YEL_B = ((1110, 911.5), 0.75)           # right leg, falls to the right
ROOF_LEFT = (APEX, -ROOF_SLOPE)
ROOF_RIGHT = (APEX, ROOF_SLOPE)

coral = band(*CORAL_L, H, 593, ROOF_RIGHT)        # top end hidden under the roof
red = band(*RED_L, H, 1443, ROOF_LEFT)
yel_a = band(*YEL_A, 42.5, 716, RED_L)            # top end hidden under red
yel_b = band(*YEL_B, 42.5, 1256, CORAL_L)          # top end hidden under coral
# The right leg's top part, where it runs under the left leg, is deeper orange.
yel_b_under = band(*YEL_B, 42.5, 985, CORAL_L)
SH = 28  # strand shadows on the warps: this much above and below
coral_sh = band(CORAL_L[0], CORAL_L[1], H + SH, 593, ROOF_RIGHT)
red_sh = band(RED_L[0], RED_L[1], H + SH, 1443, ROOF_LEFT)
yel_a_sh = band(YEL_A[0], YEL_A[1], 42.5 + SH, 716, RED_L)
yel_b_sh = band(YEL_B[0], YEL_B[1], 42.5 + SH, 1256, CORAL_L)

roof = "M{},{}L{},{}L{},{}".format(
    f(ROOF_ENDS_X[0]), f(line_y(APEX, -ROOF_SLOPE, ROOF_ENDS_X[0])),
    f(APEX[0]), f(APEX[1]),
    f(ROOF_ENDS_X[1]), f(line_y(APEX, ROOF_SLOPE, ROOF_ENDS_X[1])))

wefts = []
for (y1, y2), c in zip(((1125, 1194), (1224, 1292), (1322, 1391)), (PINK, TEAL, PURPLE)):
    wefts.append((rect(529, y1, 777, y2) + rect(1193, y1, 1442, y2), c))

outer_warps = [WARPS[i] for i in (0, 1, 4, 5)]
inner_warps = [WARPS[i] for i in (2, 3)]
warp_clip = "".join(rect(x - WARP_R, WARP_Y[0], x + WARP_R, WARP_Y[1]) for x in outer_warps)
inner_clip = "".join(rect(x - WARP_R, WARP_Y[0], x + WARP_R, WARP_Y[1]) for x in inner_warps)

# Layers, bottom to top: ("fill", d, color[, alpha]) / ("stroke", d, color, width, join)
LAYERS = (
    [("stroke", "".join(warp_line(x) for x in outer_warps), WARP, 2 * WARP_R, "round")]
    + [("fill", d, c) for d, c in wefts]
    + [("clip", warp_clip, [("fill", coral_sh + red_sh, "#000000", 0.2)])]
    # The two middle warps pass over coral and red but under the yellow and the roof.
    + [("stroke", "".join(warp_line(x) for x in inner_warps), WARP, 2 * WARP_R, "round")]
    + [("clip", inner_clip, [
        ("fill", yel_a_sh + yel_b_sh, "#000000", 0.2),
        ("shift", 0, 31, [("stroke", roof, "#000000", ROOF_W, "round", 0.2)])])]
    + [("fill", yel_b, YELLOW), ("fill", yel_b_under, YELLOW_UNDER),
       ("clip", yel_b, [("fill", yel_a_sh, "#000000", 0.2)]),     # left leg's shadow on the right
       ("fill", yel_a, YELLOW)]
    # Red and coral everywhere except on the middle warps (they go under those).
    + [("clip", rect(0, 0, 777, 2000) + rect(839, 0, 1131, 2000) + rect(1193, 0, 2000, 2000), [
        ("fill", red, RED),
        ("clip", red, [("fill", coral_sh, "#000000", 0.2)]),       # coral's shadow on red
        ("fill", coral, CORAL)])]
    + [("stroke", "".join(f"M{f(BEAM_X[0] + BEAM_R)},{f(y)}H{f(BEAM_X[1] - BEAM_R)}" for y in BEAMS),
        BEAM, 2 * BEAM_R, "round")]
    + [("stroke", roof, ROOF, ROOF_W, "round")]
    + [("fill", dot(908 + 78 * c, 1187 + 81.5 * r, 23.5), DOTS[r][c]) for r in range(3) for c in range(3)]
)

# Monochrome (themed icons): one colour, depth from alpha. Frame dimmer, threads solid.
MONO = (
    [("stroke", "".join(warp_line(x) for x in WARPS), "#FFFFFF", 2 * WARP_R, "round", 0.5)]
    + [("stroke", "".join(f"M{f(BEAM_X[0] + BEAM_R)},{f(y)}H{f(BEAM_X[1] - BEAM_R)}" for y in BEAMS),
        "#FFFFFF", 2 * BEAM_R, "round", 0.5)]
    + [("fill", "".join(d for d, _ in wefts), "#FFFFFF")]
    + [("fill", yel_b + yel_a + red + coral, "#FFFFFF")]
    + [("stroke", roof, "#FFFFFF", ROOF_W, "round")]
    + [("fill", "".join(dot(908 + 78 * c, 1187 + 81.5 * r, 23.5) for r in range(3) for c in range(3)),
        "#FFFFFF")]
)

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
     Copyright (C) 2026 The Folio Authors

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->
"""


def vd_layer(l, ind):
    kind = l[0]
    if kind == "shift":
        inner = "".join(vd_layer(x, ind + "    ") for x in l[3])
        return (f'{ind}<group\n{ind}    android:translateX="{l[1]}"\n'
                f'{ind}    android:translateY="{l[2]}">\n{inner}{ind}</group>\n')
    if kind == "clip":
        inner = "".join(vd_layer(x, ind + "    ") for x in l[2])
        return (f'{ind}<group>\n{ind}    <clip-path android:pathData="{l[1]}" />\n'
                f'{inner}{ind}</group>\n')
    if kind == "fill":
        alpha = f'\n{ind}    android:fillAlpha="{l[3]}"' if len(l) > 3 else ""
        return (f'{ind}<path\n{ind}    android:fillColor="{l[2]}"{alpha}\n'
                f'{ind}    android:pathData="{l[1]}" />\n')
    alpha = f'\n{ind}    android:strokeAlpha="{l[5]}"' if len(l) > 5 else ""
    return (f'{ind}<path\n{ind}    android:strokeColor="{l[2]}"\n'
            f'{ind}    android:strokeWidth="{f(l[3])}"\n'
            f'{ind}    android:strokeLineCap="round"\n'
            f'{ind}    android:strokeLineJoin="{l[4]}"{alpha}\n'
            f'{ind}    android:pathData="{l[1]}" />\n')


def vector(layers, comment):
    body = "".join(vd_layer(l, "        ") for l in layers)
    return (HEADER + comment + """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{s}"
        android:scaleY="{s}"
        android:translateX="{tx}"
        android:translateY="{ty}">
{body}    </group>
</vector>
""".format(s=f"{SCALE:.6f}", tx=f"{TX:.4f}", ty=f"{TY:.4f}", body=body))


def svg_layer(l, n=[0]):
    kind = l[0]
    if kind == "shift":
        return f'<g transform="translate({l[1]},{l[2]})">' + "".join(svg_layer(x) for x in l[3]) + "</g>"
    if kind == "clip":
        n[0] += 1
        cid = f"c{n[0]}"
        inner = "".join(svg_layer(x) for x in l[2])
        return f'<clipPath id="{cid}"><path d="{l[1]}"/></clipPath><g clip-path="url(#{cid})">{inner}</g>'
    if kind == "fill":
        a = f' fill-opacity="{l[3]}"' if len(l) > 3 else ""
        return f'<path d="{l[1]}" fill="{l[2]}"{a}/>'
    a = f' stroke-opacity="{l[5]}"' if len(l) > 5 else ""
    return (f'<path d="{l[1]}" fill="none" stroke="{l[2]}" stroke-width="{l[3]}" '
            f'stroke-linecap="round" stroke-linejoin="{l[4]}"{a}/>')


def svg(layers, bg):
    body = "".join(svg_layer(l) for l in layers)
    back = f'<rect width="108" height="108" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
            f'{back}<g transform="translate({TX:.4f},{TY:.4f}) scale({SCALE:.6f})">{body}</g></svg>')


open(f"{OUT}/folio_ic_launcher_foreground.xml", "w").write(vector(
    LAYERS, "<!-- Loom app icon: a loom whose threads weave a house. Foreground of a 108dp adaptive\n"
            "     icon; everything sits inside the 66dp safe zone so any mask shape shows it whole.\n"
            "     Generated by build-support/icon/gen.py; edit that, not this. -->\n"))
open(f"{OUT}/folio_ic_launcher_monochrome.xml", "w").write(vector(
    MONO, "<!-- Loom app icon, one colour, for themed icons. Generated by build-support/icon/gen.py. -->\n"))
if PREVIEW:
    open(f"{PREVIEW}/preview.svg", "w").write(svg(LAYERS, BG))
    open(f"{PREVIEW}/preview-mono.svg", "w").write(svg(MONO, "#3A3A3A"))
