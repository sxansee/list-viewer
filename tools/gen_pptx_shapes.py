#!/usr/bin/env python3
"""Generate app/src/main/assets/pptx/shapes.js: OOXML preset geometries that pptx-preview lacks.

pptx-preview (the library that draws PowerPoint slides) implements ~70 of the 187 preset shapes
from ECMA-376. Everything else (stars, hearts, clouds, callouts, flow-chart symbols, braces,
ribbons, math signs, elbow connectors ...) is silently skipped, so slides lose objects.

LibreOffice ships the complete preset table (converted from ECMA-376's presetShapeDefinitions.xml)
in `share/filter/oox-drawingml-cs-presets`. This script turns it into compact JavaScript:
every shape becomes a function that evaluates the guide formulas for a given size and
adjustment values, plus a list of sub-paths (M/L/C/Q/A/Z) that pptx-fix.js renders as SVG.

Usage:
    python3 tools/gen_pptx_shapes.py [--presets PATH] [--lib PATH] [--out PATH]

The preset table is MPL-2.0 licensed (LibreOffice); the geometry itself comes from ECMA-376.
See THIRD_PARTY.txt.
"""
import argparse
import re
import sys
from pathlib import Path

DEFAULT_PRESETS = "/usr/lib/libreoffice/share/filter/oox-drawingml-cs-presets"
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LIB = ROOT / "app/src/main/assets/lib/pptx-preview.umd.js"
DEFAULT_OUT = ROOT / "app/src/main/assets/pptx/shapes.js"

# Shapes that make no sense on a slide (chart marks with a 400000x400000 view box).
SKIP = {"chartPlus", "chartStar", "chartX"}

# EnhancedCustomShapeSegmentCommand values used by the table, and how many
# coordinate pairs one "Count" unit consumes.
MOVETO, LINETO, CURVETO, CLOSE, END, NOFILL, NOSTROKE = 1, 2, 3, 4, 5, 6, 7
QUAD, ARCANGLETO, DARKEN, DARKENLESS, LIGHTEN, LIGHTENLESS = 16, 17, 18, 19, 20, 21
PAIRS = {MOVETO: 1, LINETO: 1, CURVETO: 3, QUAD: 2, ARCANGLETO: 2}
LETTER = {MOVETO: "M", LINETO: "L", CURVETO: "C", QUAD: "Q", ARCANGLETO: "A", CLOSE: "Z"}
FLAG = {NOFILL: "n", NOSTROKE: "k", DARKEN: "d", DARKENLESS: "e", LIGHTEN: "l", LIGHTENLESS: "m"}
# how many index values one letter consumes in the flat index list
ARITY = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 4, "Z": 0}

PARAM_RE = re.compile(r"Value = \(any\) \{ \((?:long|double|short)\) (-?[0-9.eE+-]+) \}, Type = \(short\) (\d+)")


def num(v):
    return float(v) if ("." in v or "e" in v.lower()) else int(v)


def split_shapes(text):
    parts = re.split(r"^/\* (\w+) \*/\n", text, flags=re.M)
    return {parts[i]: parts[i + 1] for i in range(1, len(parts), 2)}


def section(t, name):
    m = re.search(r"^" + name + r"\n(.*?)(?=^[A-Z]\w*\n|\Z)", t, flags=re.M | re.S)
    return m.group(1).strip() if m else ""


def path_prop(path_txt, name):
    m = re.search(
        r'Name = "' + name + r'", Handle = \(long\) 0, Value = \(any\) \{(.*?)\}, State = \(com\.sun\.star\.beans\.PropertyState\) DIRECT_VALUE \}',
        path_txt, flags=re.S)
    return m.group(1) if m else ""


def params(txt):
    return [(num(v), int(ty)) for v, ty in PARAM_RE.findall(txt)]


def parse_shape(t):
    adj = []
    for m in re.finditer(
            r'Value = \(any\) \{ \((?:long|double)\) (-?[0-9.eE+-]+) \}, State = \(com\.sun\.star\.beans\.PropertyState\) DIRECT_VALUE, Name = "(\w+)"',
            section(t, "AdjustmentValues")):
        adj.append((m.group(2), num(m.group(1))))
    eq = re.findall(r'"([^"]*)"', section(t, "Equations"))
    path = section(t, "Path")
    p = params(path_prop(path, "Coordinates"))
    pairs = [(p[i], p[i + 1]) for i in range(0, len(p) - 1, 2)]
    segs = [(int(a), int(b)) for a, b in re.findall(r"Command = \(short\) (\d+), Count = \(short\) (\d+)", path_prop(path, "Segments"))]
    subs = [(int(a), int(b)) for a, b in re.findall(r"Width = \(long\) (-?\d+), Height = \(long\) (-?\d+)", path_prop(path, "SubViewSize"))]
    tp = params(path_prop(path, "TextFrames"))
    tfs = [tuple(tp[i:i + 4]) for i in range(0, len(tp) - 3, 4)]
    return dict(adj=adj, eq=eq, pairs=pairs, segs=segs, subs=subs, tfs=tfs)


# ---------- guide formulas: LibreOffice syntax -> JavaScript ----------

FUNCS = {"if": "I", "sin": "S", "cos": "C", "tan": "T", "atan2": "AT", "min": "MN", "max": "MX", "abs": "AB", "sqrt": "SQ"}
ALLOWED = re.compile(r"^[0-9A-Z\[\].+\-*/(),\s]*$")


def translate(expr):
    e = re.sub(r"\$(\d+)", r"A[\1]", expr)
    e = re.sub(r"\?(\d+)", r"E[\1]", e)

    def ident(m):
        w = m.group(0)
        if w == "logwidth":
            return "W"
        if w == "logheight":
            return "H"
        if w == "pi":
            return "PI"
        if w in FUNCS:
            return FUNCS[w]
        raise ValueError("unknown identifier %r in %r" % (w, expr))

    e = re.sub(r"\b[a-z][a-z0-9]*\b", ident, e)
    if not ALLOWED.match(e):
        raise ValueError("unexpected characters in %r -> %r" % (expr, e))
    return e


def fmt(v):
    if isinstance(v, float) and v == int(v):
        v = int(v)
    return repr(v) if isinstance(v, float) else str(v)


def build(name, d):
    eq = [translate(x) for x in d["eq"]]
    consts = {}

    def param_to_index(p):
        v, ty = p
        if ty == 1:
            return int(v)
        if ty == 2:
            key = ("A", int(v))
            expr = "A[%d]" % int(v)
        elif ty == 0:
            key = ("N", v)
            expr = fmt(v)
        else:
            raise ValueError("%s: unsupported parameter type %d" % (name, ty))
        if key not in consts:
            consts[key] = len(eq)
            eq.append(expr)
        return consts[key]

    pairs = [(param_to_index(a), param_to_index(b)) for a, b in d["pairs"]]
    subpaths = []
    cur = dict(cmds="", idx=[], flags="")
    pi = 0  # next unconsumed pair
    for cmd, cnt in d["segs"]:
        if cmd in PAIRS:
            for _ in range(max(cnt, 1) if cmd == MOVETO else cnt):
                n = PAIRS[cmd]
                chunk = pairs[pi:pi + n]
                pi += n
                cur["cmds"] += LETTER[cmd]
                for a, b in chunk:
                    cur["idx"] += [a, b]
        elif cmd == CLOSE:
            cur["cmds"] += "Z"
        elif cmd in FLAG:
            if FLAG[cmd] not in cur["flags"]:
                cur["flags"] += FLAG[cmd]
        elif cmd == END:
            subpaths.append(cur)
            cur = dict(cmds="", idx=[], flags="")
        else:
            raise ValueError("%s: unsupported segment command %d" % (name, cmd))
    if cur["cmds"]:
        subpaths.append(cur)
    if pi != len(pairs):
        raise ValueError("%s: consumed %d of %d coordinate pairs" % (name, pi, len(pairs)))
    # sanity: index list length must match the commands
    for sp in subpaths:
        need = sum(ARITY[c] for c in sp["cmds"])
        if need != len(sp["idx"]):
            raise ValueError("%s: sub-path %r needs %d indices, has %d" % (name, sp["cmds"], need, len(sp["idx"])))
    subs = d["subs"]
    if subs and len(subs) != len(subpaths):
        raise ValueError("%s: %d SubViewSize entries for %d sub-paths" % (name, len(subs), len(subpaths)))

    tx = None
    if d["tfs"]:
        tf = d["tfs"][0]
        tx = [param_to_index(tf[0]), param_to_index(tf[1]), param_to_index(tf[2]), param_to_index(tf[3])]

    body = ";".join("E[%d]=%s" % (i, e.strip()) for i, e in enumerate(eq))
    js = '"%s":{an:%s,ad:%s,f:function(W,H,A){var E=[];%s;return E},p:[%s]' % (
        name,
        "[" + ",".join('"%s"' % a for a, _ in d["adj"]) + "]",
        "[" + ",".join(fmt(v) for _, v in d["adj"]) + "]",
        body,
        ",".join(
            '["%s",[%s],"%s",%s]' % (
                sp["cmds"], ",".join(map(str, sp["idx"])), sp["flags"],
                ("[%d,%d]" % subs[i]) if subs and subs[i][0] and subs[i][1] else "0")
            for i, sp in enumerate(subpaths)))
    js += ",t:%s}" % ("[" + ",".join(map(str, tx)) + "]" if tx else "0")
    return js


def lib_shapes(lib_path):
    """Names the library's own shape switch already draws."""
    s = Path(lib_path).read_text(encoding="utf-8")
    i = s.index("function ii(t){var e=t.extend,n=t.offset")
    j = s.index("u.appendChild(p);var v=function(t,e,n)", i)
    return set(re.findall(r'case"([A-Za-z0-9]+)":', s[i:j]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--presets", default=DEFAULT_PRESETS, help="LibreOffice oox-drawingml-cs-presets file")
    ap.add_argument("--lib", default=str(DEFAULT_LIB), help="pptx-preview.umd.js (shapes it draws are skipped); 'none' = emit all")
    ap.add_argument("--out", default=str(DEFAULT_OUT))
    a = ap.parse_args()

    shapes = split_shapes(Path(a.presets).read_text(encoding="utf-8"))
    have = set() if a.lib == "none" else lib_shapes(a.lib)
    out, skipped = [], []
    for name in sorted(shapes):
        if name in SKIP or name in have:
            skipped.append(name)
            continue
        out.append(build(name, parse_shape(shapes[name])))
    head = (
        "/* GENERATED by tools/gen_pptx_shapes.py from LibreOffice's OOXML preset table (MPL-2.0; geometry: ECMA-376). DO NOT EDIT. */\n"
        "(function(){\n"
        "var PI=Math.PI,I=function(c,a,b){return c>0?a:b},S=Math.sin,C=Math.cos,T=Math.tan,AT=Math.atan2,MN=Math.min,MX=Math.max,AB=Math.abs,SQ=Math.sqrt;\n"
        "window.PPTX_PRESETS={\n")
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text(head + ",\n".join(out) + "\n};\n})();\n", encoding="utf-8")
    print("wrote %s: %d shapes (%d bytes); skipped %d (already drawn by the library or irrelevant)" % (
        a.out, len(out), Path(a.out).stat().st_size, len(skipped)), file=sys.stderr)


if __name__ == "__main__":
    main()
