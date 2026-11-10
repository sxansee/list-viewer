/* List: fixes for the pptx-preview slide renderer.
 *
 * Loaded after lib/pptx-preview.umd.js (patched by tools/patch_pptx_preview.py) and pptx/shapes.js.
 *
 *   window.__ppShape(t)     geometry hook: preset shapes the library cannot draw and freeforms
 *                           (several paths, arcs, quadratic curves). Returns {el, rect?, nofill?, ends?}
 *                           or null to let the library draw the shape itself.
 *   window.__ppUser(t)      decides which layout/master objects are painted on a slide.
 *   window.__ppPic(t, el)   picture outline (circle, rounded, any preset), rotation, flips, effects.
 *   window.__ppPrep(buf)    normalises the .pptx package before the library reads it:
 *                           mc:AlternateContent, a:fld fields, hyperlink colour.
 *
 * Every hook is defensive: on any problem it returns null / the unchanged value and the library
 * behaves exactly as upstream.
 */
(function () {
  'use strict';

  var SVG = 'http://www.w3.org/2000/svg';
  var NS_A = 'http://schemas.openxmlformats.org/drawingml/2006/main';
  var NS_MC = 'http://schemas.openxmlformats.org/markup-compatibility/2006';
  var PRESETS = window.PPTX_PRESETS || {};
  var PI = Math.PI;
  var OFFICE = ['#4472C4', '#ED7D31', '#A5A5A5', '#FFC000', '#5B9BD5', '#70AD47'];

  /* ---------- helpers for the library's parsed-XML objects ---------- */
  function arr(x) { return x == null ? [] : Array.isArray(x) ? x : [x]; }
  function get(o, p) { for (var i = 0; i < p.length; i++) { if (o == null) return undefined; o = o[p[i]]; } return o; }
  function at(n, k) { return n && n.attrs ? n.attrs[k] : undefined; }
  function num(v, d) { v = parseFloat(v); return isFinite(v) ? v : d; }
  function r3(v) { return Math.round(v * 1000) / 1000; }

  /* ---------- geometry ---------- */

  // OOXML arcTo: begins at the current point (x0,y0); ellipse radii wR,hR; start angle st and sweep sw
  // in degrees, measured clockwise (y axis points down). Returns cubic Bezier segments.
  function arcSegs(x0, y0, wR, hR, st, sw) {
    if (!(wR > 0 && hR > 0) || !isFinite(st) || !isFinite(sw) || sw === 0) return { segs: [], x: x0, y: y0 };
    var a1 = st * PI / 180;
    var t1 = Math.atan2(wR * Math.sin(a1), hR * Math.cos(a1)); // visual angle -> parametric angle
    var cx = x0 - wR * Math.cos(t1), cy = y0 - hR * Math.sin(t1);
    var turns = sw < 0 ? Math.ceil(sw / 360) : Math.floor(sw / 360);
    var rem = sw - turns * 360, dt = 0;
    if (rem !== 0) {
      var a2 = a1 + rem * PI / 180;
      dt = Math.atan2(wR * Math.sin(a2), hR * Math.cos(a2)) - t1;
      if (rem > 0 && dt < -1e-9) dt += 2 * PI;
      if (rem < 0 && dt > 1e-9) dt -= 2 * PI;
    }
    dt += turns * 2 * PI;
    var n = Math.max(1, Math.ceil(Math.abs(dt) / (PI / 2) - 1e-9));
    var d = dt / n, k = 4 / 3 * Math.tan(d / 4), th = t1, segs = [];
    for (var i = 0; i < n; i++) {
      var c0 = Math.cos(th), s0 = Math.sin(th), c1 = Math.cos(th + d), s1 = Math.sin(th + d);
      segs.push(['C',
        cx + wR * (c0 - k * s0), cy + hR * (s0 + k * c0),
        cx + wR * (c1 + k * s1), cy + hR * (s1 - k * c1),
        cx + wR * c1, cy + hR * s1]);
      th += d;
    }
    return { segs: segs, x: cx + wR * Math.cos(t1 + dt), y: cy + hR * Math.sin(t1 + dt) };
  }

  // segs: [['M',x,y],['L',x,y],['C',x1,y1,x2,y2,x,y],['Q',x1,y1,x,y],['Z']]  ->  SVG path data
  function toD(segs, sx, sy) {
    var out = [];
    for (var i = 0; i < segs.length; i++) {
      var s = segs[i];
      if (s[0] === 'Z') { out.push('Z'); continue; }
      var p = [s[0]];
      for (var j = 1; j < s.length; j += 2) {
        var x = s[j] * sx, y = s[j + 1] * sy;
        if (!isFinite(x) || !isFinite(y)) return null;
        p.push(r3(x) + ' ' + r3(y));
      }
      out.push(p.join(' '));
    }
    return out.join(' ');
  }

  // paths: [{segs, sx, sy, flags}] ; flags: n no fill, k no stroke, d/e darken, l/m lighten
  function svgFromPaths(paths) {
    var els = [], flagged = false;
    paths.forEach(function (p) {
      var d = toD(p.segs, p.sx, p.sy);
      if (!d) return;
      var f = p.flags || '';
      var el = document.createElementNS(SVG, 'path');
      el.setAttribute('d', d);
      el.setAttribute('fill-rule', 'evenodd');
      if (f.indexOf('n') >= 0) el.setAttribute('fill', 'none');
      if (f.indexOf('k') >= 0) { el.setAttribute('stroke', 'none'); flagged = true; }
      els.push({ el: el, flags: f });
      var sh = /[delm]/.exec(f);
      if (sh && f.indexOf('n') < 0) {
        var ov = document.createElementNS(SVG, 'path');
        ov.setAttribute('d', d);
        ov.setAttribute('fill-rule', 'evenodd');
        ov.setAttribute('stroke', 'none');
        ov.setAttribute('fill', sh[0] === 'd' || sh[0] === 'e' ? '#000' : '#fff');
        ov.setAttribute('fill-opacity', sh[0] === 'd' || sh[0] === 'l' ? '0.4' : '0.2');
        els.push({ el: ov, flags: '' });
        flagged = true;
      }
    });
    if (!els.length) return null;
    // A lone path keeps being a <path> (arrow heads need it); the library paints fill/stroke on it.
    if (els.length === 1 && !flagged) return { el: els[0].el, nofill: els[0].flags.indexOf('n') >= 0 };
    var g = document.createElementNS(SVG, 'g');
    els.forEach(function (x) { g.appendChild(x.el); });
    return { el: g, nofill: false };
  }

  // Evaluate a generated preset (pptx/shapes.js) for a given box and adjustment values.
  function presetPaths(def, W, H, raw) {
    var A = def.ad.slice(), i;
    for (i = 0; i < def.an.length; i++) if (raw[def.an[i]] != null) A[i] = raw[def.an[i]];
    var E = def.f(W, H, A), out = [];
    for (var s = 0; s < def.p.length; s++) {
      var sp = def.p[s], cmds = sp[0], ix = sp[1], k = 0, segs = [];
      var cx = 0, cy = 0, mx = 0, my = 0, x, y;
      for (var c = 0; c < cmds.length; c++) {
        var ch = cmds.charAt(c);
        if (ch === 'M') { x = E[ix[k++]]; y = E[ix[k++]]; segs.push(['M', x, y]); cx = mx = x; cy = my = y; }
        else if (ch === 'L') { x = E[ix[k++]]; y = E[ix[k++]]; segs.push(['L', x, y]); cx = x; cy = y; }
        else if (ch === 'C') {
          var c6 = [E[ix[k]], E[ix[k + 1]], E[ix[k + 2]], E[ix[k + 3]], E[ix[k + 4]], E[ix[k + 5]]]; k += 6;
          segs.push(['C'].concat(c6)); cx = c6[4]; cy = c6[5];
        } else if (ch === 'Q') {
          var q4 = [E[ix[k]], E[ix[k + 1]], E[ix[k + 2]], E[ix[k + 3]]]; k += 4;
          segs.push(['Q'].concat(q4)); cx = q4[2]; cy = q4[3];
        } else if (ch === 'A') {
          var a = arcSegs(cx, cy, E[ix[k]], E[ix[k + 1]], E[ix[k + 2]], E[ix[k + 3]]); k += 4;
          for (var j = 0; j < a.segs.length; j++) segs.push(a.segs[j]);
          cx = a.x; cy = a.y;
        } else if (ch === 'Z') { segs.push(['Z']); cx = mx; cy = my; }
      }
      out.push({ segs: segs, sx: sp[3] ? W / sp[3][0] : 1, sy: sp[3] ? H / sp[3][1] : 1, flags: sp[2] });
    }
    return { paths: out, E: E };
  }

  function rawAdjust(source) {
    var raw = {};
    arr(get(source, ['p:spPr', 'a:prstGeom', 'a:avLst', 'a:gd'])).forEach(function (g) {
      var m = /^val\s+(-?[\d.]+)/.exec(at(g, 'fmla') || '');
      if (m) raw[at(g, 'name')] = parseFloat(m[1]);
    });
    return raw;
  }

  // <a:custGeom>: any number of <a:path>, with moveTo / lnTo / cubicBezTo / quadBezTo / arcTo / close.
  function customPaths(t) {
    var cg = get(t.source, ['p:spPr', 'a:custGeom']);
    var list = arr(get(cg, ['a:pathLst', 'a:path']));
    if (!list.length) return null;
    var W = t.extend.w || 1, H = t.extend.h || 1;
    var ext = get(t.source, ['p:spPr', 'a:xfrm', 'a:ext', 'attrs']) || {};
    var out = [];
    for (var pi = 0; pi < list.length; pi++) {
      var pn = list[pi];
      var w = num(at(pn, 'w'), 0), h = num(at(pn, 'h'), 0);
      if (!w || !h) { w = num(ext.cx, 0); h = num(ext.cy, 0); }
      if (!w || !h) return null;
      var items = [];
      ['a:moveTo', 'a:lnTo', 'a:cubicBezTo', 'a:quadBezTo', 'a:arcTo', 'a:close'].forEach(function (k) {
        arr(pn[k]).forEach(function (n) { items.push({ k: k, n: n, o: num(at(n, 'order'), 0) }); });
      });
      items.sort(function (a, b) { return a.o - b.o; });
      var segs = [], cx = 0, cy = 0, mx = 0, my = 0;
      for (var i = 0; i < items.length; i++) {
        var it = items[i];
        var pts = arr(it.n && it.n['a:pt']).map(function (p) { return [num(at(p, 'x'), NaN), num(at(p, 'y'), NaN)]; });
        if (it.k === 'a:moveTo' && pts[0]) { segs.push(['M', pts[0][0], pts[0][1]]); cx = mx = pts[0][0]; cy = my = pts[0][1]; }
        else if (it.k === 'a:lnTo' && pts[0]) { segs.push(['L', pts[0][0], pts[0][1]]); cx = pts[0][0]; cy = pts[0][1]; }
        else if (it.k === 'a:cubicBezTo' && pts.length >= 3) {
          segs.push(['C', pts[0][0], pts[0][1], pts[1][0], pts[1][1], pts[2][0], pts[2][1]]); cx = pts[2][0]; cy = pts[2][1];
        } else if (it.k === 'a:quadBezTo' && pts.length >= 2) {
          segs.push(['Q', pts[0][0], pts[0][1], pts[1][0], pts[1][1]]); cx = pts[1][0]; cy = pts[1][1];
        } else if (it.k === 'a:arcTo') {
          var a = arcSegs(cx, cy, num(at(it.n, 'wR'), 0), num(at(it.n, 'hR'), 0),
            num(at(it.n, 'stAng'), 0) / 60000, num(at(it.n, 'swAng'), 0) / 60000);
          for (var j = 0; j < a.segs.length; j++) segs.push(a.segs[j]);
          cx = a.x; cy = a.y;
        } else if (it.k === 'a:close') { segs.push(['Z']); cx = mx; cy = my; }
      }
      var f = '', fm = at(pn, 'fill'), sk = at(pn, 'stroke');
      if (fm === 'none') f += 'n';
      else if (fm === 'darken') f += 'd'; else if (fm === 'darkenLess') f += 'e';
      else if (fm === 'lighten') f += 'l'; else if (fm === 'lightenLess') f += 'm';
      if (sk === '0' || sk === 'false') f += 'k';
      out.push({ segs: segs, sx: W / w, sy: H / h, flags: f });
    }
    return out;
  }

  /* ---------- hook: shape geometry ---------- */
  window.__ppShape = function (t) {
    try {
      var sh = t.shape;
      if (!t.extend) return null;
      var W = t.extend.w || 1, H = t.extend.h || 1;
      if (sh === 'customGeom') {
        var paths = customPaths(t);
        var r = paths && svgFromPaths(paths);
        return r ? { el: r.el, nofill: r.nofill } : null;
      }
      var def = PRESETS[sh];
      if (!def) return null;
      var res = presetPaths(def, W, H, rawAdjust(t.source));
      var built = svgFromPaths(res.paths);
      if (!built) return null;
      var out = { el: built.el, nofill: built.nofill, ends: built.el.tagName === 'path' };
      if (def.t) {
        var E = res.E, l = E[def.t[0]], tp = E[def.t[1]], rr = E[def.t[2]], b = E[def.t[3]];
        if (isFinite(l + tp + rr + b) && rr > l && b > tp) out.rect = { left: l, top: tp, bottom: H - b, w: rr - l, h: b - tp };
      }
      return out;
    } catch (e) {
      if (window.console) console.warn('pptx shape skipped:', t && t.shape, e);
      return null;
    }
  };

  /* ---------- hook: which layout/master objects are painted ---------- */
  window.__ppUser = function (t) {
    if (t.userDrawn) return true;
    if (t.type !== undefined || t.idx !== undefined) return false; // placeholder prompt
    var s = t.source;
    var ph = get(s, ['p:nvSpPr', 'p:nvPr', 'p:ph']) || get(s, ['p:nvPicPr', 'p:nvPr', 'p:ph']) ||
      get(s, ['p:nvGraphicFramePr', 'p:nvPr', 'p:ph']);
    return !ph;
  };

  /* ---------- hook: pictures ---------- */
  window.__ppPic = function (t, el) {
    try {
      var w = t.extend.w, h = t.extend.h;
      var box = el.firstChild, img = box && box.firstChild;
      if (!box || !img) return el;
      var prst = get(t.source, ['p:spPr', 'a:prstGeom', 'attrs', 'prst']);
      if (prst === 'ellipse') {
        box.style.borderRadius = '50%';
      } else if (prst === 'roundRect') {
        var gd = rawAdjust(t.source);
        var adj = gd.adj != null ? gd.adj : 16667;
        box.style.borderRadius = Math.min(w, h) * Math.max(0, Math.min(adj, 50000)) / 100000 + 'px';
      } else if (prst && prst !== 'rect' && PRESETS[prst]) {
        var res = presetPaths(PRESETS[prst], w, h, rawAdjust(t.source));
        var d = res.paths.length && toD(res.paths[0].segs, res.paths[0].sx, res.paths[0].sy);
        if (d) box.style.clipPath = "path('" + d + "')";
      } else if (!prst && get(t.source, ['p:spPr', 'a:custGeom'])) {
        var cp = customPaths(t);
        var cd = cp && cp.length && toD(cp[0].segs, cp[0].sx, cp[0].sy);
        if (cd) box.style.clipPath = "path('" + cd + "')";
      }
      var blip = get(t.source, ['p:blipFill', 'a:blip']);
      if (blip) {
        var am = at(blip['a:alphaModFix'], 'amt');
        if (am != null) img.style.opacity = String(Math.max(0, Math.min(1, num(am, 100000) / 100000)));
        var f = [];
        if (blip['a:grayscl']) f.push('grayscale(1)');
        var lum = at(blip['a:lum'], 'bright') != null || at(blip['a:lum'], 'contrast') != null ? blip['a:lum'].attrs : null;
        if (lum) f.push('brightness(' + (1 + num(lum.bright, 0) / 100000) + ')', 'contrast(' + (1 + num(lum.contrast, 0) / 100000) + ')');
        if (f.length) img.style.filter = f.join(' ');
      }
      var tr = [];
      if (t.rotate) tr.push('rotate(' + t.rotate + 'deg)');
      if (t.flipH) tr.push('scaleX(-1)');
      if (t.flipV) tr.push('scaleY(-1)');
      if (tr.length) {
        el.style.width = w + 'px';
        el.style.height = h + 'px';
        el.style.transformOrigin = '50% 50%';
        el.style.transform = tr.join(' ');
      }
    } catch (e) {
      if (window.console) console.warn('pptx picture hook', e);
    }
    return el;
  };

  /* ---------- hook: chart options (ECharts) ---------- */
  // The library hands ECharts its default grid (60px above and below), which squeezes small charts
  // into a sliver, and always reserves room for a title. Fit the layout to the chart's own size.
  window.__ppChart = function (o, w, h) {
    try {
      if (!o) return o;
      // Series without an explicit colour take the theme's accents in PowerPoint; the library leaves
      // them empty, which ECharts paints black (invisible on dark slides).
      var pal = window.__ppTheme && window.__ppTheme.length ? window.__ppTheme : OFFICE;
      var okc = function (c) { return typeof c === 'string' && c && c !== 'transparent' && c !== 'undefined'; };
      var colors = Array.isArray(o.color) ? o.color.slice() : [];
      var want = Math.max(colors.length, Array.isArray(o.series) ? o.series.length : 0);
      var first = Array.isArray(o.series) && o.series[0];
      if (first && first.type === 'pie' && Array.isArray(first.data)) want = Math.max(want, first.data.length);
      for (var ci = 0; ci < want; ci++) if (!okc(colors[ci])) colors[ci] = pal[ci % pal.length];
      o.color = colors;
      var cs = o.__cs;
      delete o.__cs;
      var chart = get(cs, ['c:chartSpace', 'c:chart']);
      var yes = function (n) { var v = at(n, 'val'); return v === '1' || v === 'true'; };
      if (chart && yes(chart['c:autoTitleDeleted']) && !get(chart, ['c:title'])) o.title = { show: false };
      var hasTitle = !!(o.title && o.title.text && o.title.show !== false);
      if (o.title && !hasTitle) o.title = { show: false };
      var ser = Array.isArray(o.series) ? o.series : [];
      // legend: only when the chart has one, where the chart puts it
      var lg = chart && chart['c:legend'], lpos = 'b';
      if (chart && !lg) o.legend = { show: false };
      else if (lg) lpos = at(lg['c:legendPos'], 'val') || 'r';
      var hasLegend = !!o.legend && o.legend.show !== false;
      // data labels: PowerPoint shows none unless the chart asks for them (ECharts' pie default shows names)
      var plot = get(chart, ['c:plotArea']);
      var chartDl = null;
      if (plot) Object.keys(plot).forEach(function (k) { if (/Chart$/.test(k) && plot[k] && plot[k]['c:dLbls'] && !chartDl) chartDl = plot[k]['c:dLbls']; });
      var labelOf = function (dl, isPie) {
        if (!dl || Array.isArray(dl)) return null;
        var v = yes(dl['c:showVal']), c = yes(dl['c:showCatName']), p = yes(dl['c:showPercent']);
        if (!v && !c && !p) return null;
        var parts = [];
        if (c) parts.push('{b}');
        if (v) parts.push('{c}');
        if (p) parts.push('{d}%');
        var out = { show: true, formatter: parts.join(' ') };
        var pos = at(dl['c:dLblPos'], 'val');
        if (isPie) { if (pos === 'ctr' || pos === 'inEnd' || pos === 'inBase') out.position = 'inside'; }
        else if (pos) { var m = { ctr: 'inside', inEnd: 'insideTop', inBase: 'insideBottom', outEnd: 'top', t: 'top', b: 'bottom', l: 'left', r: 'right' }[pos]; if (m) out.position = m; }
        return out;
      };
      var rawSer = [];
      if (plot) Object.keys(plot).forEach(function (k) { if (/Chart$/.test(k)) arr(plot[k]).forEach(function (ct) { arr(ct && ct['c:ser']).forEach(function (sx) { rawSer.push(sx); }); }); });
      ser.forEach(function (sr, i) {
        if (!sr || sr.label) return;
        var isPie = sr.type === 'pie', dls = (rawSer[i] && rawSer[i]['c:dLbls']) || chartDl;
        sr.label = labelOf(dls, isPie) || { show: false };
        if (isPie) sr.labelLine = { show: !!sr.label.show };
        // a single data point can override the group (pptxgenjs writes one <c:dLbl> per slice)
        arr(dls && dls['c:dLbl']).forEach(function (d) {
          var idx = parseInt(at(d['c:idx'], 'val'), 10);
          if (!isFinite(idx) || !Array.isArray(sr.data) || sr.data[idx] == null) return;
          if (typeof sr.data[idx] !== 'object') sr.data[idx] = { value: sr.data[idx] };
          sr.data[idx].label = labelOf(d, isPie) || { show: false };
        });
      });
      var pie = ser.length > 0 && ser[0] && ser[0].type === 'pie';
      var fs = Math.max(9, Math.min(14, Math.round(h / 15)));
      o.animation = false;
      // text colour that stays readable on both light and dark slide backgrounds
      var ink = '#808890';
      o.textStyle = Object.assign({ fontSize: fs, color: ink }, o.textStyle || {});
      if (hasTitle) o.title = Object.assign({}, o.title, { textStyle: { fontSize: fs + 3, fontWeight: 'bold', color: ink } });
      if (hasLegend) {
        // the library asks for bottom:"bottom", which ECharts does not understand
        var where = lpos === 't' ? { top: 2, left: 'center' } : lpos === 'l' ? { left: 2, top: 'middle', orient: 'vertical' }
          : lpos === 'r' || lpos === 'tr' ? { right: 2, top: 'middle', orient: 'vertical' } : { bottom: 2, left: 'center' };
        o.legend = Object.assign({ itemWidth: 12, itemHeight: 8, textStyle: { fontSize: fs, color: ink } }, o.legend, where);
        ['top', 'bottom', 'left', 'right'].forEach(function (k) { if (!(k in where)) delete o.legend[k]; });
      }
      var side = hasLegend && (lpos === 'l' || lpos === 'r' || lpos === 'tr');
      if (!pie && !o.grid) {
        o.grid = {
          left: side && lpos === 'l' ? Math.min(110, w * 0.25) : 6, right: side && lpos !== 'l' ? Math.min(110, w * 0.25) : 14,
          top: hasTitle || (hasLegend && lpos === 't') ? fs * 3 : 12, bottom: hasLegend && lpos === 'b' ? fs * 3 : 8, containLabel: true
        };
      }
      if (pie) {
        var k = hasTitle || hasLegend ? 0.65 / 0.8 : 1;
        var scale = function (v) { return typeof v === 'string' && /%$/.test(v) ? parseFloat(v) * k + '%' : v; };
        ser.forEach(function (s) {
          s.radius = Array.isArray(s.radius) ? s.radius.map(scale) : scale(s.radius);
          var cx = side ? (lpos === 'l' ? '60%' : '40%') : '50%';
          var cy = side ? (hasTitle ? '56%' : '50%') : hasTitle && hasLegend ? '52%' : hasTitle ? '56%' : hasLegend ? (lpos === 't' ? '54%' : '46%') : '50%';
          s.center = [cx, cy];
        });
      }
    } catch (e) {
      if (window.console) console.warn('pptx chart hook', e);
    }
    return o;
  };

  /* ---------- package normalisation ---------- */

  function childNS(n, ns, name) {
    for (var c = n.firstChild; c; c = c.nextSibling) if (c.nodeType === 1 && c.localName === name && c.namespaceURI === ns) return c;
    return null;
  }

  var NS_P = 'http://schemas.openxmlformats.org/presentationml/2006/main';
  var DRAWABLE = { sp: 1, pic: 1, cxnSp: 1, grpSp: 1 };
  var GRAPHIC_OK = /drawingml\/2006\/(table|chart|diagram)$/;

  // Can the library draw what a <mc:Choice>/<mc:Fallback> branch contains?
  function drawable(branch) {
    for (var c = branch.firstElementChild; c; c = c.nextElementSibling) {
      if (c.namespaceURI === NS_P && DRAWABLE[c.localName]) return true;
      if (c.namespaceURI === NS_P && c.localName === 'graphicFrame') {
        var gd = c.getElementsByTagNameNS(NS_A, 'graphicData')[0];
        return !!gd && GRAPHIC_OK.test(gd.getAttribute('uri') || '');
      }
      if (c.namespaceURI === NS_A && /^(r|br|fld|p)$/.test(c.localName)) return true;
    }
    return false;
  }

  // PowerPoint wraps newer features in <mc:AlternateContent><mc:Choice/><mc:Fallback/>. The library
  // skips the wrapper completely, so the object disappeared. Use the modern <mc:Choice> when it is
  // ordinary shape markup (what PowerPoint itself shows), otherwise the plain <mc:Fallback>
  // (a picture of a 3-D model, the text of an equation ...).
  function unwrapAlternates(doc) {
    var changed = false, guard = 0, list;
    while ((list = doc.getElementsByTagNameNS(NS_MC, 'AlternateContent')).length && guard++ < 500) {
      var ac = list[list.length - 1], parent = ac.parentNode;
      var fb = childNS(ac, NS_MC, 'Fallback'), ch = childNS(ac, NS_MC, 'Choice');
      var pick = ch && drawable(ch) ? ch : (fb && fb.firstElementChild ? fb : ch);
      if (pick) {
        var kids = Array.prototype.slice.call(pick.childNodes);
        for (var i = 0; i < kids.length; i++) parent.insertBefore(kids[i], ac);
      }
      parent.removeChild(ac);
      changed = true;
    }
    return changed;
  }

  // Slide-number / date fields: the library has no <a:fld>, so their text vanished.
  function fieldsToRuns(doc, slideNo) {
    var list = doc.getElementsByTagNameNS(NS_A, 'fld'), changed = false;
    while (list.length) {
      var f = list[0], r = doc.createElementNS(NS_A, 'a:r');
      var rpr = childNS(f, NS_A, 'rPr'), tx = childNS(f, NS_A, 't');
      if (rpr) r.appendChild(rpr);
      var nt = doc.createElementNS(NS_A, 'a:t');
      var s = tx ? tx.textContent : '';
      if (slideNo && (!s || /\u2039#\u203A/.test(s)) && /slidenum/i.test(f.getAttribute('type') || '')) s = s ? s.replace(/\u2039#\u203A/g, String(slideNo)) : String(slideNo);
      nt.textContent = s;
      r.appendChild(nt);
      f.parentNode.replaceChild(r, f);
      changed = true;
    }
    return changed;
  }

  // The library never colours hyperlinks, so links took the text colour (black on dark slides).
  function colourLinks(doc) {
    var list = doc.getElementsByTagNameNS(NS_A, 'hlinkClick'), changed = false;
    for (var i = 0; i < list.length; i++) {
      var rpr = list[i].parentNode;
      if (!rpr || rpr.localName !== 'rPr' || rpr.namespaceURI !== NS_A) continue;
      if (!childNS(rpr, NS_A, 'solidFill')) {
        var sf = doc.createElementNS(NS_A, 'a:solidFill'), sc = doc.createElementNS(NS_A, 'a:schemeClr');
        sc.setAttribute('val', 'hlink');
        sf.appendChild(sc);
        var ln = childNS(rpr, NS_A, 'ln');
        rpr.insertBefore(sf, ln ? ln.nextSibling : rpr.firstChild);
        changed = true;
      }
      if (!rpr.getAttribute('u')) { rpr.setAttribute('u', 'sng'); changed = true; }
    }
    return changed;
  }

  // accent1..accent6 of the first theme: the default colours of chart series
  async function readTheme(z) {
    try {
      var f = z.file('ppt/theme/theme1.xml');
      if (!f) return null;
      var doc = new DOMParser().parseFromString(await f.async('string'), 'application/xml');
      var out = [];
      for (var i = 1; i <= 6; i++) {
        var el = doc.getElementsByTagNameNS(NS_A, 'accent' + i)[0], c = el && el.firstElementChild;
        var v = c && (c.getAttribute('val') || c.getAttribute('lastClr'));
        if (v && /^[0-9a-f]{6}$/i.test(v)) out.push('#' + v);
      }
      return out.length === 6 ? out : null;
    } catch (e) { return null; }
  }

  // Generated decks (pptxgenjs, some exporters) list parts in [Content_Types].xml that are not in the
  // package. The library asks for every listed part and gave up on the first missing one: no slides.
  async function dropMissingParts(z) {
    var f = z.file('[Content_Types].xml');
    if (!f) return false;
    var doc = new DOMParser().parseFromString(await f.async('string'), 'application/xml');
    if (doc.getElementsByTagName('parsererror').length) return false;
    var list = Array.prototype.slice.call(doc.getElementsByTagName('Override')), changed = false;
    list.forEach(function (o) {
      var name = (o.getAttribute('PartName') || '').replace(/^\//, '');
      if (name && !z.file(name)) { o.parentNode.removeChild(o); changed = true; }
    });
    if (changed) z.file('[Content_Types].xml', new XMLSerializer().serializeToString(doc));
    return changed;
  }

  var PART = /^ppt\/(slides\/slide|slideLayouts\/slideLayout|slideMasters\/slideMaster)(\d+)\.xml$/;
  var TRIGGER = /AlternateContent|<a:fld[\s>]|hlinkClick/;

  window.__ppPrep = async function (buf) {
    try {
      if (typeof JSZip === 'undefined') return buf;
      var z = await JSZip.loadAsync(buf), changed = false;
      window.__ppTheme = await readTheme(z);
      if (await dropMissingParts(z)) changed = true;
      var names = Object.keys(z.files).filter(function (n) { return PART.test(n); });
      for (var i = 0; i < names.length; i++) {
        var name = names[i], xml = await z.file(name).async('string');
        if (!TRIGGER.test(xml)) continue;
        var doc = new DOMParser().parseFromString(xml, 'application/xml');
        if (doc.getElementsByTagName('parsererror').length) continue;
        var no = PART.exec(name);
        var a = unwrapAlternates(doc), b = fieldsToRuns(doc, no[1] === 'slides/slide' ? +no[2] : 0), c = colourLinks(doc);
        if (a || b || c) { z.file(name, new XMLSerializer().serializeToString(doc)); changed = true; }
      }
      return changed ? await z.generateAsync({ type: 'arraybuffer', compression: 'STORE' }) : buf;
    } catch (e) {
      if (window.console) console.warn('pptx prep skipped', e);
      return buf;
    }
  };

  window.__ppFix = { version: 1, presets: Object.keys(PRESETS).length };
})();
