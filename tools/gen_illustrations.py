#!/usr/bin/env python3
"""Generate the animated exercise illustrations (app/.../ui/IllustrationData.kt).

Each exercise is a side-view stick figure on a machine, with a start pose and an end pose.
The app animates between them. Joints come from simple 2-link inverse kinematics so the
limbs keep their length while moving.

    python3 tools/gen_illustrations.py            # writes the Kotlin file
    python3 tools/gen_illustrations.py --preview  # also writes PNG previews to build/illustrations/
"""
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/java/com/feidbo/marathon/ui/IllustrationData.kt"

TORSO, NECK, HEAD_R = 27.0, 4.0, 6.0
UPPER_ARM, FOREARM = 14.0, 13.0
THIGH, SHIN, FOOT = 21.0, 20.0, 7.0


def pt(x, y):
    return (round(x, 2), round(y, 2))


def polar(origin, length, deg):
    """Screen coordinates: 0° points right, 90° points up."""
    r = math.radians(deg)
    return pt(origin[0] + length * math.cos(r), origin[1] - length * math.sin(r))


def ik(root, target, l1, l2, bend):
    """Middle joint of a 2-link chain from root to target. bend=+1 bends counter-clockwise."""
    dx, dy = target[0] - root[0], target[1] - root[1]
    d = math.hypot(dx, dy)
    d = min(d, l1 + l2 - 1e-6)
    a = (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)
    a = max(-1.0, min(1.0, a))
    base = math.atan2(dy, dx)
    ang = base - bend * math.acos(a)
    mid = pt(root[0] + l1 * math.cos(ang), root[1] + l1 * math.sin(ang))
    # Re-place the end so the second link keeps its exact length.
    end_ang = math.atan2(target[1] - mid[1], target[0] - mid[0])
    end = pt(mid[0] + l2 * math.cos(end_ang), mid[1] + l2 * math.sin(end_ang))
    return mid, end


def figure(hip, torso_deg, hand, elbow_bend, ankle, knee_bend, foot_deg,
           far_hand=None, far_ankle=None, far_foot_deg=None, far_offset=(2.5, -1.5)):
    """Returns (head, chains) where each chain is (points, far)."""
    shoulder = polar(hip, TORSO, torso_deg)
    head = polar(hip, TORSO + NECK + HEAD_R, torso_deg)
    elbow, hand_p = ik(shoulder, hand, UPPER_ARM, FOREARM, elbow_bend)
    knee, ankle_p = ik(hip, ankle, THIGH, SHIN, knee_bend)
    toe = polar(ankle_p, FOOT, foot_deg)
    chains = []
    ox, oy = far_offset
    if far_hand is not None or far_ankle is not None:
        fhip = pt(hip[0] + ox, hip[1] + oy)
        fsh = pt(shoulder[0] + ox, shoulder[1] + oy)
        if far_ankle is not None:
            fk, fa = ik(fhip, far_ankle, THIGH, SHIN, knee_bend)
            chains.append(([fhip, fk, fa, polar(fa, FOOT, far_foot_deg if far_foot_deg is not None else foot_deg)], True))
        if far_hand is not None:
            fe, fh = ik(fsh, far_hand, UPPER_ARM, FOREARM, elbow_bend)
            chains.append(([fsh, fe, fh], True))
    chains.append(([hip, knee, ankle_p, toe], False))
    chains.append(([hip, shoulder], False))
    chains.append(([shoulder, elbow, hand_p], False))
    return head, chains


# Shapes: (kind, start_coords, end_coords, width, style). kind: line (x1,y1,x2,y2), rect (x,y,w,h), circle (x,y,r)
def line(a, b, w=3.0, style="FRAME", a2=None, b2=None):
    return ("LINE", (*a, *b), (*(a2 or a), *(b2 or b)), w, style)


def rect(x, y, w, h, style="PAD", moved=None):
    return ("RECT", (x, y, w, h), moved or (x, y, w, h), 0.0, style)


def circle(x, y, r, style="WEIGHT", moved=None):
    return ("CIRCLE", (x, y, r), moved or (x, y, r), 0.0, style)


FLOOR = line((4, 92), (96, 92), 2.0, "FLOOR")


def leg_press():
    hip = (36, 74)
    t = 128
    sled = 34  # sled direction (deg)
    def ankle_at(dist):
        return polar(hip, dist, sled)
    bent, straight = ankle_at(31), ankle_at(39.5)
    s_head, s_chains = figure(hip, t, (44, 76), -1, bent, 1, sled + 90, far_hand=(46, 75), far_ankle=bent)
    e_head, e_chains = figure(hip, t, (44, 76), -1, straight, 1, sled + 90, far_hand=(46, 75), far_ankle=straight)
    def platform(c):
        a, b = polar(c, 9, sled + 90), polar(c, 7, sled - 90)
        return a, b
    pa, pb = platform(polar(hip, 33, sled))
    qa, qb = platform(polar(hip, 41.5, sled))
    props = [
        FLOOR,
        line((30, 92), (36, 80), 3), line((56, 92), (50, 80), 3),             # seat legs
        line((25, 82), (54, 80), 5, "PAD"),                                   # seat
        line((31, 82), polar((31, 82), 34, t), 6, "PAD"),                     # back rest
        line(polar(hip, 20, sled), polar(hip, 58, sled), 2.5, "RAIL"),        # sled rail
        line((86, 92), polar(hip, 58, sled), 3),
        line(pa, pb, 4, "WEIGHT", qa, qb),                                    # foot platform
        rect(78, 30, 10, 14, "WEIGHT", (82, 22, 10, 14)),                     # weight stack moves with the sled
    ]
    return props, (s_head, s_chains), (e_head, e_chains)


def calf_raise():
    hip = (36, 74)
    t, sled = 128, 34
    ankle_lo = polar(hip, 40.0, sled)
    ankle_hi = polar(hip, 40.5, sled)
    s = figure(hip, t, (44, 76), -1, ankle_lo, 1, sled + 70, far_hand=(46, 75), far_ankle=ankle_lo)
    e = figure(hip, t, (44, 76), -1, ankle_hi, 1, sled + 130, far_hand=(46, 75), far_ankle=ankle_hi)
    # Platform edge under the balls of the feet.
    def plat(foot_deg, ankle):
        ball = polar(ankle, FOOT * 0.8, foot_deg)
        c = polar(ball, 1.5, sled)
        return polar(c, 3, sled - 90), polar(c, 12, sled + 90)
    pa, pb = plat(sled + 70, ankle_lo)
    qa, qb = plat(sled + 130, ankle_hi)
    props = [
        FLOOR,
        line((30, 92), (36, 80), 3), line((56, 92), (50, 80), 3),
        line((25, 82), (54, 80), 5, "PAD"),
        line((31, 82), polar((31, 82), 34, t), 6, "PAD"),
        line(polar(hip, 20, sled), polar(hip, 58, sled), 2.5, "RAIL"),
        line((86, 92), polar(hip, 58, sled), 3),
        line(pa, pb, 4, "WEIGHT", qa, qb),
    ]
    return props, s, e


def pull_up():
    # Kneeling on the assist pad, facing right. Bar overhead.
    bar_y = 10
    hand = (60, bar_y + 2)
    s_hip = (50, 62)
    e_hip = (50, 44)
    s = figure(s_hip, 92, hand, 1, (31, 85), 1, 180, far_hand=(62, bar_y + 2), far_ankle=(32, 84))
    e = figure(e_hip, 96, hand, 1, (31, 67), 1, 180, far_hand=(62, bar_y + 2), far_ankle=(32, 66))
    props = [
        FLOOR,
        line((72, 92), (72, 6), 4), line((72, 6), (52, 6), 4),                 # upright and top beam
        line((54, bar_y), (70, bar_y), 3, "PAD"),                              # handles
        line((72, 82), (56, 82), 3, "FRAME", (72, 64), (56, 64)),             # pad arm
        line((28, 88), (56, 88), 6, "PAD", (28, 70), (56, 70)),               # knee pad moves up
        rect(76, 60, 12, 16, "WEIGHT", (76, 42, 12, 16)),                       # counterweight
        line((82, 6), (82, 60), 1.5, "RAIL", (82, 6), (82, 42)),
        line((72, 6), (82, 6), 1.5, "RAIL"),
    ]
    return props, s, e


def hip_thrust():
    # Upper back on a bench at left, feet on the floor at right, facing up.
    s_hip = (44, 82)
    e_hip = (46, 66)
    ankle = (72, 88)
    s = figure(s_hip, 160, (47, 82), 1, ankle, 1, 0, far_hand=(49, 81), far_ankle=(73, 87))
    e = figure(e_hip, 178, (49, 66), 1, ankle, 1, 0, far_hand=(51, 65), far_ankle=(73, 87))
    props = [
        FLOOR,
        rect(6, 72, 18, 6, "PAD"),                                             # bench top
        line((10, 78), (10, 92), 3), line((20, 78), (20, 92), 3),
        circle(47, 79, 4.5, "WEIGHT", (49, 63, 4.5)),                           # bar / belt across hips
        line((40, 79), (54, 79), 2.5, "FRAME", (42, 63), (56, 63)),
    ]
    return props, s, e


def hip_abduction():
    # Front view, seated: knees push apart against pads.
    head_c = (50, 18)
    shoulder_y, hip_y = 31, 58
    knee_y = 66
    def pose(spread):
        lh, rh = (45, hip_y), (55, hip_y)
        lk, rk = (45 - spread, knee_y), (55 + spread, knee_y)
        la, ra = (lk[0] + spread * 0.25, 88), (rk[0] - spread * 0.25, 88)
        chains = [
            ([(50, shoulder_y), (50, hip_y)], False),
            ([lh, rh], False),
            ([(42, shoulder_y + 1), (58, shoulder_y + 1)], False),
            ([(42, shoulder_y + 1), (36, 44), (36, 56)], False),
            ([(58, shoulder_y + 1), (64, 44), (64, 56)], False),
            ([lh, lk, la, (la[0] - 5, 89)], False),
            ([rh, rk, ra, (ra[0] + 5, 89)], False),
        ]
        return head_c, chains
    s, e = pose(3), pose(13)
    props = [
        FLOOR,
        rect(30, 56, 40, 6, "PAD"),                                            # seat
        rect(34, 22, 32, 34, "BACKREST"),                                      # backrest behind
        line((50, 62), (50, 92), 3),
        rect(36, 62, 4, 12, "WEIGHT", (26, 62, 4, 12)),                         # knee pads
        rect(60, 62, 4, 12, "WEIGHT", (70, 62, 4, 12)),
        line((34, 56), (34, 60), 3), line((66, 56), (66, 60), 3),              # handles
    ]
    return props, s, e


def leg_curl():
    # Seated, facing right. Legs go from straight out in front to curled under the seat.
    hip = (38, 66)
    s = figure(hip, 100, (34, 68), -1, polar(hip, 40.5, -5), 1, 85, far_hand=(36, 67), far_ankle=polar(hip, 40.5, -5))
    e = figure(hip, 100, (34, 68), -1, polar((hip[0] + THIGH * 0.97, hip[1] + 4), SHIN, -115), 1, 0,
               far_hand=(36, 67), far_ankle=polar((hip[0] + THIGH * 0.97, hip[1] + 4), SHIN, -115))
    s_ankle = s[1][2][0][2]
    e_ankle = e[1][2][0][2]
    props = [
        FLOOR,
        line((26, 70), (58, 70), 6, "PAD"),                                    # seat
        line((28, 70), (24, 34), 6, "PAD"),                                    # backrest
        line((40, 70), (40, 92), 3), line((24, 92), (28, 70), 3),
        line((44, 60), (58, 60), 4, "PAD"),                                    # thigh pad
        circle(59, 68, 2.5, "FRAME"),                                          # pivot at the knee
        line((59, 68), (s_ankle[0] + 1, s_ankle[1] + 3), 2.5, "FRAME", (59, 68), (e_ankle[0] + 3, e_ankle[1] + 1)),
        circle(s_ankle[0] + 1, s_ankle[1] + 3, 3.5, "WEIGHT", (e_ankle[0] + 3, e_ankle[1] + 1, 3.5)),  # heel pad
    ]
    return props, s, e


def quad_stretch():
    # Kneeling lunge facing right: left knee down, right foot forward. Hand draws the back heel to the glute.
    hip_s, hip_e = (44, 60), (45, 61)
    front_ankle = (68, 88)
    def pose(hip, back_ankle, foot_deg, hand, torso):
        shoulder = polar(hip, TORSO, torso)
        head = polar(hip, TORSO + NECK + HEAD_R, torso)
        # Front leg: knee up.
        fk, fa = ik(hip, front_ankle, THIGH, SHIN, 1)
        # Back leg: knee on the floor behind the hip, shin rising to the hand.
        back_knee = (hip[0] - 6, 88)
        ba = polar(back_knee, SHIN, back_ankle)
        toe = polar(ba, FOOT, foot_deg)
        knee_top = polar(fk, 2.5, 90)
        reach = knee_top if hand == "knee" else polar(ba, 1.5, back_ankle + 90)
        el, hd = ik(shoulder, reach, UPPER_ARM, FOREARM, 1 if hand == "knee" else -1)
        fe, fh = ik(shoulder, knee_top, UPPER_ARM, FOREARM, 1)
        chains = [
            ([hip, back_knee, ba, toe], True),
            ([shoulder, fe, fh], True),
            ([hip, fk, fa, polar(fa, FOOT, 0)], False),
            ([hip, shoulder], False),
            ([shoulder, el, hd], False),
        ]
        return head, chains
    s = pose(hip_s, 165, 175, "knee", 88)
    e = pose(hip_e, 105, 120, "ankle", 96)
    props = [FLOOR, rect(30, 88, 22, 4, "PAD")]
    return props, s, e


EXERCISES = {
    "leg_press": leg_press,
    "calf_raise": calf_raise,
    "assisted_pull_up": pull_up,
    "hip_thrust": hip_thrust,
    "hip_abduction": hip_abduction,
    "leg_curl": leg_curl,
    "quad_stretch": quad_stretch,
}


def kt_f(x):
    return f"{x:.2f}f"


def to_kotlin():
    out = [
        "// GENERATED by tools/gen_illustrations.py. Do not edit.",
        "package com.feidbo.marathon.ui",
        "",
        "internal val illustrations: Map<String, Illustration> = mapOf(",
    ]
    for name, fn in EXERCISES.items():
        props, (sh, sc), (eh, ec) = fn()
        assert len(sc) == len(ec), name
        out.append(f'    "{name}" to Illustration(')
        out.append("        shapes = listOf(")
        for kind, a, b, w, style in props:
            vals = ", ".join(kt_f(v) for v in (*a, *b))
            out.append(f"            Shape(ShapeKind.{kind}, floatArrayOf({vals}), {kt_f(w)}, ShapeStyle.{style}),")
        out.append("        ),")
        out.append(f"        head = floatArrayOf({kt_f(sh[0])}, {kt_f(sh[1])}, {kt_f(eh[0])}, {kt_f(eh[1])}),")
        out.append(f"        headRadius = {kt_f(HEAD_R)},")
        out.append("        chains = listOf(")
        for (spts, far), (epts, _) in zip(sc, ec):
            assert len(spts) == len(epts), name
            vals = ", ".join(kt_f(v) for p in spts for v in p)
            evals = ", ".join(kt_f(v) for p in epts for v in p)
            out.append(f"            Chain(floatArrayOf({vals}), floatArrayOf({evals}), far = {'true' if far else 'false'}),")
        out.append("        ),")
        out.append("    ),")
    out.append(")")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}", file=sys.stderr)


def preview():
    from PIL import Image, ImageDraw
    outdir = ROOT / "build" / "illustrations"
    outdir.mkdir(parents=True, exist_ok=True)
    S = 4
    colors = {"FLOOR": (180, 180, 180), "FRAME": (90, 100, 110), "RAIL": (150, 160, 170), "PAD": (40, 60, 80),
              "WEIGHT": (230, 160, 40), "BACKREST": (200, 210, 220)}
    sheet = Image.new("RGB", (len(EXERCISES) * 100 * S, 2 * 100 * S), "white")
    for col, (name, fn) in enumerate(EXERCISES.items()):
        props, s, e = fn()
        for row, (pose, idx) in enumerate(((s, 1), (e, 2))):
            img = Image.new("RGB", (100 * S, 100 * S), "white")
            d = ImageDraw.Draw(img)
            for kind, a, b, w, style in props:
                c = b if idx == 2 else a
                if kind == "LINE":
                    d.line([c[0] * S, c[1] * S, c[2] * S, c[3] * S], fill=colors[style], width=int(w * S))
                elif kind == "RECT":
                    d.rounded_rectangle([c[0] * S, c[1] * S, (c[0] + c[2]) * S, (c[1] + c[3]) * S], 2 * S, fill=colors[style])
                else:
                    d.ellipse([(c[0] - c[2]) * S, (c[1] - c[2]) * S, (c[0] + c[2]) * S, (c[1] + c[2]) * S], fill=colors[style])
            head, chains = pose
            for pts, far in chains:
                d.line([v * S for p in pts for v in p], fill=(140, 200, 170) if far else (14, 107, 77), width=int(4.5 * S), joint="curve")
            d.ellipse([(head[0] - HEAD_R) * S, (head[1] - HEAD_R) * S, (head[0] + HEAD_R) * S, (head[1] + HEAD_R) * S], fill=(14, 107, 77))
            d.text((4, 4), f"{name} {'start' if idx == 1 else 'end'}", fill="black")
            sheet.paste(img, (col * 100 * S, row * 100 * S))
    sheet.save(outdir / "sheet.png")
    print(f"preview: {outdir / 'sheet.png'}", file=sys.stderr)


if __name__ == "__main__":
    to_kotlin()
    if "--preview" in sys.argv:
        preview()
