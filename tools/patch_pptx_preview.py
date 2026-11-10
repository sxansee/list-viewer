#!/usr/bin/env python3
"""Apply List's small hooks to the vendored pptx-preview build (idempotent).

Run after replacing app/src/main/assets/lib/pptx-preview.umd.js with a new upstream build:
    python3 tools/patch_pptx_preview.py

The hooks call into app/src/main/assets/pptx/fix.js; without that file they do nothing, so the
library keeps working exactly as upstream.

 shape    Before the library's own `switch(t.shape)`, ask `window.__ppShape(t)`. It returns an SVG
          element (plus an optional text rectangle) for shapes the library cannot draw (stars,
          hearts, callouts, flow-chart symbols, freeforms with arcs ...) or null to keep the
          library's code path.
 user     Layout/master nodes were drawn only if they carried userDrawn="1". Non-placeholder shapes
          from other producers (Google Slides, WPS, LibreOffice) often lack the attribute and
          vanished. `window.__ppUser(t)` also accepts every node that is not a placeholder.
 group    Children of a group were drawn grouped by type, not in document order, so text could end
          up under a picture. They are now sorted by document order like the top level.
 picture  `window.__ppPic(t, el)` applies rotation, flips and the real outline (circle / rounded
          photos) to the picture element, which the library ignores.
 chart    A chart without a title got the placeholder text "图表标题" ("chart title" in Chinese). The
          placeholder is now empty, and `window.__ppChart(options, w, h)` can tidy the ECharts options
          (compact grid, font size that follows the chart size, no animation). The parsed chart XML
          is handed over as `options.__cs`, so legend, title and data labels can follow the file.
 load     The loader wrapped everything in one try/catch that threw errors away: a single missing or
          broken part (a [Content_Types].xml entry without a file is common in generated decks) left
          the slide list empty and the viewer showed nothing. Layouts and slides now load one by one,
          a bad one is skipped, and the error is logged.
 media    Pictures were only picked up from ppt/media/image*; any other name never showed.
 safe-*   One broken object (bad picture reference, odd table, ...) is skipped instead of blanking the
          whole slide or sending the viewer to its text fallback.
"""
import sys
from pathlib import Path

LIB = Path(__file__).resolve().parent.parent / "app/src/main/assets/lib/pptx-preview.umd.js"

# (tag, old, new, expected number of occurrences of `old`)
PATCHES = [
    (
        "list-shape",
        'g=!0;switch(t.shape){case"customGeom":',
        'g=!0;var __h=window.__ppShape&&window.__ppShape(t);if(__h){d=__h.el;__h.rect&&(f=__h.rect);__h.nofill&&(g=!1);__h.ends&&Jr(t,p,d)}else switch(t.shape){case"customGeom":',
        1,
    ),
    (
        "list-user",
        ".filter((function(t){return t.userDrawn}))",
        ".filter((function(t){return window.__ppUser?window.__ppUser(t):t.userDrawn}))",
        2,
    ),
    (
        "list-group",
        "for(var d=0;d<t.nodes.length;d++){var p=t.nodes[d],f=void 0;p instanceof Mr?f=zr(p):p instanceof Sr?f=ii(p):p instanceof Cr&&(f=oi(p)),f&&e.appendChild(f)}return e}var ai=function",
        "for(var d=0,__g=t.nodes.slice().sort((function(a,b){return(a.order||0)-(b.order||0)}));d<__g.length;d++){var p=__g[d],f=void 0;p instanceof Mr?f=zr(p):p instanceof Sr?f=ii(p):p instanceof Cr&&(f=oi(p)),f&&e.appendChild(f)}return e}var ai=function",
        1,
    ),
    (
        "list-picture",
        "function zr(t){var e=t.extend,n=t.offset,r=t.clip,i=t.base64,",
        "function zr(t){var __d=__zr(t);return window.__ppPic&&window.__ppPic(t,__d)||__d}function __zr(t){var e=t.extend,n=t.offset,r=t.clip,i=t.base64,",
        1,
    ),
    # --- robustness: one broken object must not blank the whole slide -------------------------
    (
        "list-safe-sp",
        "t.push(new Sr(m,n,r,i))",
        "(function(){try{t.push(new Sr(m,n,r,i))}catch(e){}})()",
        2,
    ),
    (
        "list-safe-pic",
        'f=p["p:blipFill"]["a:blip"].attrs["r:embed"],g=r.rels[f].target,y=new Mr(g,p,n,r,i),t.push(y)',
        '(function(){try{f=p["p:blipFill"]["a:blip"].attrs["r:embed"],g=r.rels[f].target,y=new Mr(g,p,n,r,i),t.push(y)}catch(e){}})()',
        1,
    ),
    (
        "list-safe-table",
        "case 7:return t.push(new Ir(x,n,r,i)),[3,12];",
        "case 7:try{t.push(new Ir(x,n,r,i))}catch(__e){}return[3,12];",
        1,
    ),
    (
        "list-safe-diagram",
        "[4,(w=new Ar(x,n,r,i)).parseNode()];case 9:return u.sent(),t.push(w),[3,12];",
        "[4,(w=new Ar(x,n,r,i)).parseNode().catch((function(){w=null}))];case 9:return u.sent(),w&&t.push(w),[3,12];",
        1,
    ),
    (
        "list-safe-chart",
        "[4,(S=new Tr(x,n,r,i)).parseNode()];case 11:return u.sent(),t.push(S),[3,12];",
        "[4,(S=new Tr(x,n,r,i)).parseNode().catch((function(){S=null}))];case 11:return u.sent(),S&&t.push(S),[3,12];",
        1,
    ),
    (
        "list-safe-group",
        "C=M[I],t.push(new Cr(C,n,r,i))",
        "C=M[I],(function(){try{t.push(new Cr(C,n,r,i))}catch(e){}})()",
        1,
    ),
    # --- loading ------------------------------------------------------------------------------
    (
        "list-load-layout",
        "[4,(g=new Rr(m,_,this)).load()];case 14:u.sent(),this.slideLayouts.push(g),",
        "[4,function(){try{return(g=new Rr(m,_,this)).load().catch((function(e){g=null,window.console&&console.warn('pptx layout skipped',e)}))}catch(e){g=null;window.console&&console.warn('pptx layout skipped',e);return Promise.resolve()}}.call(this)];case 14:u.sent(),g&&this.slideLayouts.push(g),",
        1,
    ),
    (
        "list-load-slide",
        "[4,(b=new Pr(m,_,this)).load()];case 19:u.sent(),this.slides.push(b),",
        "[4,function(){try{return(b=new Pr(m,_,this)).load().catch((function(e){b=null,window.console&&console.warn('pptx slide skipped',e)}))}catch(e){b=null;window.console&&console.warn('pptx slide skipped',e);return Promise.resolve()}}.call(this)];case 19:u.sent(),b&&this.slides.push(b),",
        1,
    ),
    (
        "list-load-log",
        "case 22:return u.sent(),[3,23];case 23:return[2]}}))}))},t.prototype._loadMedia=",
        "case 22:return window.console&&console.warn('pptx load error',u.sent()),[3,23];case 23:return[2]}}))}))},t.prototype._loadMedia=",
        1,
    ),
    (
        "list-media-names",
        'return t.startsWith("ppt/media/image")}))',
        r'return/^ppt\/media\/.+\.(png|jpe?g|gif|bmp|webp|svg|emf|wmf|tiff?)$/i.test(t)}))',
        1,
    ),
    (
        "list-media-mime",
        'case"emf":e="image/x-emf";break;case"wmf":e="image/x-wmf";break;default:e="image/*"',
        'case"emf":e="image/x-emf";break;case"wmf":e="image/x-wmf";break;case"svg":e="image/svg+xml";break;case"webp":e="image/webp";break;case"bmp":e="image/bmp";break;default:e="image/*"',
        1,
    ),
    # --- charts -------------------------------------------------------------------------------
    (
        "list-chart-title",
        '})).join("")||"图表标题"}',
        '})).join("")||""}',
        1,
    ),
    (
        "list-chart-xml",
        'return n=r.apply(void 0,[a.sent()]),i=Yn.get(n,["c:chartSpace","c:chart"]),',
        'return n=r.apply(void 0,[a.sent()]),this.options.__cs=n,i=Yn.get(n,["c:chartSpace","c:chart"]),',
        1,
    ),
    (
        "list-chart-options",
        't.setOption(s),PZ("destroy"',
        't.setOption(window.__ppChart?window.__ppChart(s,c,h):s),PZ("destroy"',
        1,
    ),
    (
        "list-safe-render",
        "t.prototype._renderNode=function(t){return t instanceof Mr?zr(t)",
        "t.prototype._renderNode=function(t){try{return this._renderNode0(t)}catch(e){window.console&&console.warn('pptx object skipped',e);return null}},t.prototype._renderNode0=function(t){return t instanceof Mr?zr(t)",
        1,
    ),
]


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else LIB
    s = path.read_text(encoding="utf-8")
    changed = 0
    for tag, old, new, want in PATCHES:
        if new in s:
            print("already patched:", tag)
            continue
        n = s.count(old)
        if n != want:
            sys.exit("cannot patch %s: expected %d anchor(s), found %d (new upstream build? update the anchors)" % (tag, want, n))
        s = s.replace(old, new)
        changed += 1
        print("patched:", tag)
    if changed:
        path.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    main()
