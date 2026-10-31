// Controle na tela do iPhone: o TouchPad.java e o TouchLayout.java do Android, em canvas. Mesmo
// visual, mesmos esquemas por console, mesmo editor e o mesmo touch.json. Vale para o jogo
// (game.html) e para o editor aberto das Configurações (edit.html).
'use strict';
window.OnixPad = (function () {
  const RB = { B: 0, Y: 1, SELECT: 2, START: 3, UP: 4, DOWN: 5, LEFT: 6, RIGHT: 7, A: 8, X: 9, L: 10, R: 11 };
  const ROUND = 0, PILL = 1, MENU = 2, FAST = 3;
  const G_DPAD = 0, G_L = 2, G_R = 3, G_SELECT = 4, G_START = 5, G_MENU = 6, G_FAST = 7, G_FACE = 8;
  const T_SMALLER = -10, T_BIGGER = -11, T_LESS = -12, T_MORE = -13, T_RESET = -14, T_DONE = -15, T_SIZE = -16, T_ALPHA = -17;
  const MIN_SCALE = 0.7, MAX_SCALE = 1.5, MIN_OPACITY = 0.3, MIN_SIZE = 0.5, MAX_SIZE = 2;
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const round1 = v => Math.round(v * 10) / 10;
  const hexA = (rgb, a) => 'rgba(' + (rgb >> 16 & 255) + ',' + (rgb >> 8 & 255) + ',' + (rgb & 255) + ',' + a + ')';

  /* ---------------- touch.json (Layout v4: Retrato e Paisagem 100% independentes) ---------------- */
  function defaultSide() {
    return { scale: 1, opacity: 0.85, pos: {}, each: {} };
  }

  function Layout(text) {
    let o = {};
    try { o = JSON.parse(text || '{}') || {}; } catch (_) { o = {}; }
    this.port = defaultSide();
    this.land = defaultSide();

    if (o.v === 4 && (o.portrait || o.landscape)) {
      if (o.portrait) {
        this.port.scale = clamp(Number(o.portrait.scale) || 1, MIN_SCALE, MAX_SCALE);
        this.port.opacity = clamp(o.portrait.opacity === undefined ? 0.85 : Number(o.portrait.opacity), MIN_OPACITY, 1);
        this.port.pos = o.portrait.pos && typeof o.portrait.pos === 'object' ? o.portrait.pos : {};
        this.port.each = o.portrait.each && typeof o.portrait.each === 'object' ? o.portrait.each : {};
      }
      if (o.landscape) {
        this.land.scale = clamp(Number(o.landscape.scale) || 1, MIN_SCALE, MAX_SCALE);
        this.land.opacity = clamp(o.landscape.opacity === undefined ? 0.85 : Number(o.landscape.opacity), MIN_OPACITY, 1);
        this.land.pos = o.landscape.pos && typeof o.landscape.pos === 'object' ? o.landscape.pos : {};
        this.land.each = o.landscape.each && typeof o.landscape.each === 'object' ? o.landscape.each : {};
      }
    } else {
      const scale = clamp(Number(o.scale) || 1, MIN_SCALE, MAX_SCALE);
      const opacity = clamp(o.opacity === undefined ? 0.85 : Number(o.opacity), MIN_OPACITY, 1);
      this.port = { scale, opacity, pos: {}, each: {} };
      this.land = { scale, opacity, pos: {}, each: {} };
      if (o.pos && typeof o.pos === 'object') {
        for (const [k, v] of Object.entries(o.pos)) {
          if (k.startsWith('p.')) this.port.pos[k.slice(2)] = v;
          else if (k.startsWith('l.')) this.land.pos[k.slice(2)] = v;
          else { this.port.pos[k] = v; this.land.pos[k] = v; }
        }
      }
      if (o.each && typeof o.each === 'object') {
        this.port.each = JSON.parse(JSON.stringify(o.each));
        this.land.each = JSON.parse(JSON.stringify(o.each));
      }
    }
  }
  Layout.prototype = {
    side(land) { return land ? this.land : this.port; },
    text() {
      return JSON.stringify({
        v: 4,
        portrait: this.port,
        landscape: this.land
      });
    },
    scale(land) { return this.side(land).scale; },
    setScale(land, v) { this.side(land).scale = clamp(v, MIN_SCALE, MAX_SCALE); },
    opacity(land) { return this.side(land).opacity; },
    setOpacityAll(land, v) { this.side(land).opacity = clamp(v, MIN_OPACITY, 1); },
    get(land, key) {
      const a = this.side(land).pos[key];
      return Array.isArray(a) && a.length === 2 ? [clamp(Number(a[0]), 0, 1), clamp(Number(a[1]), 0, 1)] : null;
    },
    set(land, key, fx, fy) {
      this.side(land).pos[key] = [clamp(fx, 0, 1), clamp(fy, 0, 1)];
    },
    size(land, key) {
      const o = this.side(land).each[key];
      return o && o.s !== undefined ? clamp(Number(o.s), MIN_SIZE, MAX_SIZE) : 1;
    },
    opacityOf(land, key) {
      const s = this.side(land);
      const o = s.each[key];
      return o && o.o !== undefined ? clamp(Number(o.o), MIN_OPACITY, 1) : s.opacity;
    },
    put(land, key, field, v) {
      const s = this.side(land);
      (s.each[key] || (s.each[key] = {}))[field] = Math.round(v * 100) / 100;
    },
    setSize(land, key, v) { this.put(land, key, 's', clamp(v, MIN_SIZE, MAX_SIZE)); },
    setOpacity(land, key, v) { this.put(land, key, 'o', clamp(v, MIN_OPACITY, 1)); },
    resetButton(land, key) {
      const s = this.side(land);
      delete s.each[key];
      delete s.pos[key];
    },
    reset(land) {
      const s = this.side(land);
      s.scale = 1;
      s.opacity = 0.85;
      s.pos = {};
      s.each = {};
    },
  };

  /* ---------------- Controle ---------------- */
  class Btn {
    constructor(label, bit, color, alpha, kind, group, rx, ry, size) { Object.assign(this, { label, bit, color, alpha, kind, group, rx, ry, size, cx: 0, cy: 0, r: 0, w: 0, h: 0 }); }
    hit(x, y, slack) {
      if (this.kind !== PILL) { const dx = x - this.cx, dy = y - this.cy, rr = this.r * slack; return dx * dx + dy * dy <= rr * rr; }
      const sw = this.w / 2 * slack + (slack - 1) * this.h, sh = this.h / 2 * slack + (slack - 1) * this.h;
      return Math.abs(x - this.cx) <= sw && Math.abs(y - this.cy) <= sh;
    }
    bounds() {
      return this.kind === PILL ? { l: this.cx - this.w / 2, t: this.cy - this.h / 2, r: this.cx + this.w / 2, b: this.cy + this.h / 2 }
        : { l: this.cx - this.r, t: this.cy - this.r, r: this.cx + this.r, b: this.cy + this.r };
    }
  }

  class Pad {
    /* listener: buttons(bits), fast(on), menu(), done(), save(texto do touch.json). */
    constructor(canvas, consoleId, listener, layoutText, es) {
      this.c = canvas; this.g = canvas.getContext('2d'); this.l = listener; this.es = es; this.console = consoleId;
      this.layout = new Layout(layoutText);
      this.face = []; this.shoulders = []; this.system = []; this.tools = [];
      this.pointers = new Map(); this.bits = 0; this.dpadBits = 0; this.isFast = false;
      this.editing = false; this.dragGroup = -1; this.dragId = -1; this.pressTool = 0; this.dragging = false; this.selected = -1;
      this.w = 0; this.h = 0; this.dpadX = 0; this.dpadY = 0; this.dpadR = 0; this.landscape = false;
      const nes = 0xFF5A6E, snes = 0xA78BFA, gba = 0x38BDF8, md = 0xFBBF24;
      let sel = 'SELECT';
      this.six = consoleId === 'md';
      const add = (list, ...a) => list.push(new Btn(...a));
      if (consoleId === 'snes') {
        this.accent = snes;
        add(this.face, 'X', RB.X, 0x38BDF8, 1, ROUND, G_FACE, 0, -1.35, 1);
        add(this.face, 'A', RB.A, 0xFF5A6E, 1, ROUND, G_FACE, 1.35, 0, 1);
        add(this.face, 'B', RB.B, 0xFBBF24, 1, ROUND, G_FACE, 0, 1.35, 1);
        add(this.face, 'Y', RB.Y, 0x4ADE80, 1, ROUND, G_FACE, -1.35, 0, 1);
        add(this.shoulders, 'L', RB.L, snes, 1, PILL, G_L, -1, 0, 1);
        add(this.shoulders, 'R', RB.R, snes, 1, PILL, G_R, 1, 0, 1);
      } else if (consoleId === 'gba') {
        this.accent = gba;
        add(this.face, 'B', RB.B, gba, 1, ROUND, G_FACE, -0.62, 0.38, 1.12);
        add(this.face, 'A', RB.A, gba, 1, ROUND, G_FACE, 0.62, -0.38, 1.12);
        add(this.shoulders, 'L', RB.L, gba, 1, PILL, G_L, -1, 0, 1);
        add(this.shoulders, 'R', RB.R, gba, 1, PILL, G_R, 1, 0, 1);
      } else if (this.six) {
        this.accent = md;
        add(this.face, 'A', RB.Y, md, 1, ROUND, G_FACE, -1.45, 0.55, 0.95);
        add(this.face, 'B', RB.B, md, 1, ROUND, G_FACE, 0, 0.35, 0.95);
        add(this.face, 'C', RB.A, md, 1, ROUND, G_FACE, 1.45, 0.15, 0.95);
        add(this.face, 'X', RB.L, md, 0.8, ROUND, G_FACE, -1.45, -0.85, 0.7);
        add(this.face, 'Y', RB.X, md, 0.8, ROUND, G_FACE, 0, -1.05, 0.7);
        add(this.face, 'Z', RB.R, md, 0.8, ROUND, G_FACE, 1.45, -1.25, 0.7);
        sel = 'MODE';
      } else {
        this.accent = nes;
        add(this.face, 'B', RB.B, nes, 1, ROUND, G_FACE, -0.62, 0.38, 1.12);
        add(this.face, 'A', RB.A, nes, 1, ROUND, G_FACE, 0.62, -0.38, 1.12);
      }
      add(this.system, sel, RB.SELECT, 0xA99FD0, 1, PILL, G_SELECT, -1, 0, 1);
      add(this.system, 'START', RB.START, 0xA99FD0, 1, PILL, G_START, 1, 0, 1);
      add(this.system, '', -1, 0xF1EDFF, 1, MENU, G_MENU, -1, 0, 1);
      add(this.system, '»', -2, 0x3DE1FF, 1, FAST, G_FAST, 1, 0, 1);
      this.face.forEach((b, i) => { b.group = G_FACE + i; });
      this.groups = G_FACE + this.face.length;
      this.all = [...this.face, ...this.shoulders, ...this.system];
      const t = 0xF1EDFF, cyan = 0x3DE1FF;
      add(this.tools, '−', T_SMALLER, t, 1, PILL, -1, 0, 0, 0.6);
      add(this.tools, '', T_SIZE, t, 1, PILL, -1, 1, 0, 1.6);
      add(this.tools, '+', T_BIGGER, t, 1, PILL, -1, 2, 0, 0.6);
      add(this.tools, '−', T_LESS, t, 1, PILL, -1, 3, 0, 0.6);
      add(this.tools, '', T_ALPHA, t, 1, PILL, -1, 4, 0, 1.6);
      add(this.tools, '+', T_MORE, t, 1, PILL, -1, 5, 0, 0.6);
      add(this.tools, '', T_RESET, t, 1, PILL, -1, 6, 1, 2.2);
      add(this.tools, '', T_DONE, cyan, 1, PILL, -1, 7, 1, 1.3);
      for (const e of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) canvas.addEventListener(e, ev => this.onPointer(ev));
      canvas.addEventListener('contextmenu', e => e.preventDefault());
    }

    /* ---------------- Posições ---------------- */
    resize(w, h) {
      const dpr = window.devicePixelRatio || 1;
      this.c.width = Math.round(w * dpr); this.c.height = Math.round(h * dpr);
      this.dpr = dpr;
      this.place(w, h);
    }
    place(w, h) {
      if (!w || !h) return;
      this.w = w; this.h = h;
      const land = this.landscape = w > h, s = Math.min(w, h), k = this.layout.scale(land);
      const reachX = this.faceReach(true), reachY = this.faceReach(false);
      if (land) {
        this.dpadR = Math.min(s * 0.17, 92) * k;
        const m = this.dpadR * 1.25 + 16;
        this.dpadX = m + 8; this.dpadY = h - m;
        this.placeFace(w - m - 8, h - m, this.dpadR);
        this.placeShoulders(m * 0.9, 30, w - m * 0.9, this.dpadR);
        this.placeSystem(w / 2, h - 26, 26 * k, w / 2, 30, k);
      } else {
        const edge = 12, mid = 18, top = 26, bottom = h - 34 - 30 * k;
        const fitW = (w - 2 * edge - mid) / (2 + 2 * reachX);
        const fitH = (h - 26 - 34 - 30 - 24) / (2 * Math.max(1, reachY) + 0.42);
        this.dpadR = Math.min(w * 0.2, h * 0.24, fitW, fitH) * k;
        const cy = (top + this.dpadR * 0.42 + bottom) / 2;
        this.dpadX = edge + this.dpadR; this.dpadY = cy;
        this.placeFace(w - edge - reachX * this.dpadR, cy, this.dpadR);
        this.placeShoulders(this.dpadR * 1.1, top, w - this.dpadR * 1.1, this.dpadR);
        this.placeSystem(w / 2, h - 34, 30 * k, w / 2, 28, k);
      }
      for (let g = 0; g < this.groups; g++) {
        const m = this.layout.size(land, this.key(g));
        if (m === 1) continue;
        if (g === G_DPAD) this.dpadR *= m;
        for (const b of this.all) if (b.group === g) { b.r *= m; b.w *= m; b.h *= m; }
      }
      for (let g = 0; g < this.groups; g++) {
        const p = this.layout.get(land, this.key(g));
        if (!p) continue;
        const box = this.center(g);
        this.move(g, p[0] * w - (box.l + box.r) / 2, p[1] * h - (box.t + box.b) / 2);
      }
      for (let g = 0; g < this.groups; g++) {
        const box = this.center(g);
        if (!box) continue;
        const dx = box.l < 0 ? -box.l : box.r > w ? w - box.r : 0, dy = box.t < 0 ? -box.t : box.b > h ? h - box.b : 0;
        if (dx || dy) this.move(g, dx, dy);
      }
      this.placeTools(w, h);
      this.invalidate();
    }
    faceReach(horizontal) {
      const br = 0.42, gap = br * 1.25 * (this.six ? 1.05 : 1);
      return this.face.reduce((out, b) => Math.max(out, Math.abs(horizontal ? b.rx : b.ry) * gap + br * b.size), 0);
    }
    key(g) { return g >= G_FACE ? this.console + '.' + this.face[g - G_FACE].label : String(g); }
    placeFace(cx, cy, r) {
      const br = r * 0.42, gap = br * 1.25 * (this.six ? 1.05 : 1);
      for (const b of this.face) { b.r = br * b.size; b.cx = cx + b.rx * gap; b.cy = cy + b.ry * gap; }
    }
    placeShoulders(lx, y, rx, r) {
      for (const b of this.shoulders) { b.w = r * 1.25; b.h = r * 0.42; b.cx = b.rx < 0 ? lx : rx; b.cy = y + b.h / 2; }
    }
    placeSystem(cx, bottomY, pillH, topX, topY, k) {
      const small = 30 * 0.62 * k;
      for (const b of this.system) {
        if (b.kind === PILL) {
          b.h = pillH; b.w = pillH * 3.1; b.cx = cx + b.rx * (b.w / 2 + 10); b.cy = bottomY - b.h / 2;
        } else {
          b.r = small; b.w = b.h = b.r * 2; b.cx = topX + b.rx * (b.r + 8); b.cy = topY + b.r;
        }
      }
    }
    placeTools(w, areaH) {
      const h = 36, gap = 8, unit = 54;
      let row0 = 0, row1 = 0;
      for (const b of this.tools) { b.h = h; b.w = unit * b.size; if (b.ry === 0) row0 += b.w + gap; else row1 += b.w + gap; }
      const barH = 2 * h + gap, left = (w - Math.max(row0, row1) + gap) / 2;
      let top = 12;
      const tries = [top];
      for (let g = 0; g < this.groups; g++) { const box = this.center(g); if (box) tries.push(box.b + 14); }
      tries.sort((a, b) => a - b);
      for (const y of tries) {
        if (y + barH > areaH - 8) break;
        if (this.free(left, y, w - left, y + barH)) { top = y; break; }
      }
      let x0 = (w - row0 + gap) / 2, x1 = (w - row1 + gap) / 2;
      const y0 = top + h / 2, y1 = y0 + h + gap;
      for (const b of this.tools) {
        if (b.ry === 0) { b.cx = x0 + b.w / 2; b.cy = y0; x0 += b.w + gap; } else { b.cx = x1 + b.w / 2; b.cy = y1; x1 += b.w + gap; }
      }
    }
    free(l, t, r, b) {
      for (let g = 0; g < this.groups; g++) {
        const box = this.center(g);
        if (box && box.l - 10 < r && box.r + 10 > l && box.t - 10 < b && box.b + 10 > t) return false;
      }
      return true;
    }
    /** Caixa do grupo, ou null se ele não existir neste console. */
    center(g) {
      if (g === G_DPAD) return { l: this.dpadX - this.dpadR, t: this.dpadY - this.dpadR, r: this.dpadX + this.dpadR, b: this.dpadY + this.dpadR };
      let out = null;
      for (const b of this.all) {
        if (b.group !== g) continue;
        const q = b.bounds();
        out = out ? { l: Math.min(out.l, q.l), t: Math.min(out.t, q.t), r: Math.max(out.r, q.r), b: Math.max(out.b, q.b) } : q;
      }
      return out;
    }
    move(g, dx, dy) {
      if (g === G_DPAD) { this.dpadX += dx; this.dpadY += dy; return; }
      for (const b of this.all) if (b.group === g) { b.cx += dx; b.cy += dy; }
    }

    /* ---------------- Editor ---------------- */
    setEditing(on) {
      this.editing = on; this.dragGroup = -1; this.selected = -1;
      this.pointers.clear();
      this.update();
      this.invalidate();
    }
    tool(id) {
      const land = this.landscape;
      const step = id === T_BIGGER || id === T_MORE ? 0.1 : -0.1, L = this.layout;
      if (this.selected >= 0 && id !== T_DONE) {
        const k = this.key(this.selected);
        if (id === T_SMALLER || id === T_BIGGER) L.setSize(land, k, round1(L.size(land, k) + step));
        else if (id === T_LESS || id === T_MORE) L.setOpacity(land, k, round1(L.opacityOf(land, k) + step));
        else if (id === T_RESET) L.resetButton(land, k);
        else return;
      } else if (id === T_SMALLER || id === T_BIGGER) L.setScale(land, clamp(round1(L.scale(land) + step), MIN_SCALE, MAX_SCALE));
      else if (id === T_LESS || id === T_MORE) L.setOpacityAll(land, clamp(round1(L.opacity(land) + step), MIN_OPACITY, 1));
      else if (id === T_RESET) L.reset(land);
      else if (id === T_DONE) { this.save(); this.setEditing(false); this.l.done(); return; }
      else return;
      this.save();
      this.place(this.w, this.h);
    }
    save() { this.l.save(this.layout.text()); }
    editPointer(e, x, y) {
      if (e.type === 'pointerdown') {
        if (this.dragId !== -1) return;
        this.pressTool = 0;
        for (const b of this.tools) if (b.bit !== T_SIZE && b.bit !== T_ALPHA && b.hit(x, y, 1.1)) this.pressTool = b.bit;
        this.dragGroup = this.groupAt(x, y);
        this.dragging = false; this.dragId = e.pointerId;
        this.lastX = this.downX = x; this.lastY = this.downY = y;
        if (!this.pressTool) this.invalidate();
        return;
      }
      if (e.pointerId !== this.dragId) return;
      if (e.type === 'pointermove') {
        if (this.dragGroup < 0) return;
        if (!this.dragging) {
          if (Math.hypot(x - this.downX, y - this.downY) < 10) return;
          if (this.selected >= 0) this.selected = this.dragGroup;
        }
        this.dragging = true;
        const box = this.center(this.dragGroup);
        const dx = Math.max(-box.l, Math.min(this.w - box.r, x - this.lastX)), dy = Math.max(-box.t, Math.min(this.h - box.b, y - this.lastY));
        this.move(this.dragGroup, dx, dy);
        this.lastX = x; this.lastY = y;
        this.invalidate();
        return;
      }
      this.dragId = -1;
      if (e.type === 'pointerup' && !this.dragging) {
        const id = this.pressTool;
        this.pressTool = 0;
        if (id) { this.dragGroup = -1; this.tool(id); return; }
        this.selected = this.dragGroup === this.selected ? -1 : this.dragGroup;
        this.dragGroup = -1;
        this.invalidate();
      } else if (this.dragGroup >= 0) {
        const box = this.center(this.dragGroup);
        this.layout.set(this.landscape, this.key(this.dragGroup), (box.l + box.r) / 2 / this.w, (box.t + box.b) / 2 / this.h);
        this.save();
        this.dragGroup = -1; this.pressTool = 0;
        this.placeTools(this.w, this.h);
        this.invalidate();
      }
    }
    groupAt(x, y) {
      let best = -1, bestD = Infinity;
      for (let g = 0; g < this.groups; g++) {
        const box = this.center(g);
        if (!box) continue;
        const out = Math.hypot(Math.max(0, box.l - x, x - box.r), Math.max(0, box.t - y, y - box.b));
        if (out > 14) continue;
        const d = out * 1000 + Math.hypot(x - (box.l + box.r) / 2, y - (box.t + box.b) / 2);
        if (d < bestD) { bestD = d; best = g; }
      }
      return best;
    }

    /* ---------------- Desenho ---------------- */
    invalidate() {
      if (this.raf) return;
      this.raf = requestAnimationFrame(() => { this.raf = 0; this.draw(); });
    }
    draw() {
      const g = this.g, dpr = this.dpr || 1;
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      g.clearRect(0, 0, this.w, this.h);
      if (!this.landscape) {
        const q = g.createLinearGradient(0, 0, 0, this.h);
        q.addColorStop(0, hexA(this.accent, 0)); q.addColorStop(1, hexA(this.accent, 40 / 255));
        g.fillStyle = q; g.fillRect(0, 0, this.w, this.h);
      }
      if (this.editing) { g.fillStyle = 'rgba(4,2,10,.43)'; g.fillRect(0, 0, this.w, this.h); }
      this.drawDpad(this.alpha(G_DPAD));
      for (const b of this.all) this.drawButton(b, this.alpha(b.group));
      if (this.editing) this.drawEditor();
    }
    alpha(group) { const o = this.layout.opacityOf(this.landscape, this.key(group)); return this.editing ? Math.max(o, 0.6) : o; }
    name(group) {
      if (group === G_DPAD) return this.es ? 'Cruceta' : 'Direcional';
      if (group === G_MENU) return 'Menu';
      const b = this.all.find(x => x.group === group);
      return b ? b.label : '';
    }
    stroke(color, alpha, width) {
      const g = this.g;
      g.strokeStyle = hexA(color, alpha); g.lineWidth = width; g.shadowColor = hexA(color, alpha * 0.6); g.shadowBlur = 7;
      g.lineJoin = g.lineCap = 'round';
    }
    round(x, y, w, h, r) { const g = this.g; g.beginPath(); g.roundRect(x, y, w, h, r); }
    drawEditor() {
      const g = this.g;
      for (let i = 0; i < this.groups; i++) {
        const b = this.center(i);
        if (!b) continue;
        this.round(b.l - 10, b.t - 10, b.r - b.l + 20, b.b - b.t + 20, 14);
        if (i === this.selected) { this.stroke(0x3DE1FF, 1, 2.2); g.setLineDash([]); g.stroke(); continue; }
        g.shadowBlur = 0; g.lineWidth = 1.5; g.setLineDash([6, 5]);
        g.strokeStyle = i === this.dragGroup ? '#3de1ff' : 'rgba(255,255,255,.53)';
        g.stroke();
      }
      g.setLineDash([]);
      const one = this.selected >= 0, L = this.layout, k = one ? this.key(this.selected) : '', land = this.landscape;
      for (const b of this.tools) {
        if (b.bit === T_SIZE) b.label = (this.es ? 'Tamaño ' : 'Tamanho ') + Math.round((one ? L.size(land, k) : L.scale(land)) * 100) + '%';
        else if (b.bit === T_ALPHA) b.label = (this.es ? 'Opacidad ' : 'Opacidade ') + Math.round((one ? L.opacityOf(land, k) : L.opacity(land)) * 100) + '%';
        else if (b.bit === T_RESET) b.label = one ? 'Restaurar ' + this.name(this.selected) : 'Restaurar';
        else if (b.bit === T_DONE) b.label = this.es ? 'Listo' : 'Concluir';
        const x = b.cx - b.w / 2, y = b.cy - b.h / 2, info = b.bit === T_SIZE || b.bit === T_ALPHA;
        g.shadowBlur = 0; g.fillStyle = 'rgba(14,8,32,.9)';
        this.round(x, y, b.w, b.h, b.h / 2); g.fill();
        const col = one && b.bit !== T_DONE ? 0x3DE1FF : b.color;
        this.stroke(col, info ? (one ? 0.63 : 0.27) : 0.86, 1.6); g.stroke();
        g.shadowBlur = 0; g.fillStyle = hexA(col, 1);
        let size = b.label.length <= 1 ? 20 : 13;
        g.font = '700 ' + size + 'px system-ui,sans-serif';
        const fit = (b.w - 14) / g.measureText(b.label).width;
        if (fit < 1) { size *= fit; g.font = '700 ' + size + 'px system-ui,sans-serif'; }
        g.textAlign = 'center'; g.textBaseline = 'middle';
        g.fillText(b.label, b.cx, b.cy);
      }
    }
    drawDpad(base) {
      const g = this.g, r = this.dpadR, a = r * 0.34, X = this.dpadX, Y = this.dpadY, ac = this.accent;
      g.shadowBlur = 0; g.fillStyle = hexA(ac, 90 / 255 * base);
      if (this.dpadBits & 1 << RB.UP) g.fillRect(X - a, Y - r, 2 * a, r - a);
      if (this.dpadBits & 1 << RB.DOWN) g.fillRect(X - a, Y + a, 2 * a, r - a);
      if (this.dpadBits & 1 << RB.LEFT) g.fillRect(X - r, Y - a, r - a, 2 * a);
      if (this.dpadBits & 1 << RB.RIGHT) g.fillRect(X + a, Y - a, r - a, 2 * a);
      g.beginPath();
      for (const [x, y] of [[-a, -r], [a, -r], [a, -a], [r, -a], [r, a], [a, a], [a, r], [-a, r], [-a, a], [-r, a], [-r, -a], [-a, -a]]) g.lineTo(X + x, Y + y);
      g.closePath();
      this.stroke(ac, base, 2.4); g.stroke();
      g.shadowBlur = 0; g.fillStyle = hexA(ac, base);
      const t = a * 0.55, o = r * 0.72;
      this.arrow(X, Y - o, 0, -1, t); this.arrow(X, Y + o, 0, 1, t); this.arrow(X - o, Y, -1, 0, t); this.arrow(X + o, Y, 1, 0, t);
      this.stroke(ac, base / 2, 1.6);
      g.beginPath(); g.arc(X, Y, a * 0.45, 0, 7); g.stroke();
    }
    arrow(x, y, dx, dy, s) {
      const g = this.g;
      g.beginPath();
      g.moveTo(x + dx * s * 0.6, y + dy * s * 0.6);
      g.lineTo(x - dx * s * 0.4 + dy * s * 0.6, y - dy * s * 0.4 + dx * s * 0.6);
      g.lineTo(x - dx * s * 0.4 - dy * s * 0.6, y - dy * s * 0.4 - dx * s * 0.6);
      g.closePath(); g.fill();
    }
    drawButton(b, base) {
      const g = this.g, on = b.bit >= 0 ? (this.bits & 1 << b.bit) !== 0 : (b.bit === -2 && this.isFast);
      const alpha = base * b.alpha, col = b.color;
      if (b.kind === PILL) this.round(b.cx - b.w / 2, b.cy - b.h / 2, b.w, b.h, b.h / 2);
      else { g.beginPath(); g.arc(b.cx, b.cy, b.r, 0, 7); }
      if (on) { g.shadowBlur = 0; g.fillStyle = hexA(col, 110 / 255); g.fill(); }
      this.stroke(col, alpha, b.kind === ROUND ? 2.6 : 2); g.stroke();
      if (b.kind === MENU) {
        this.stroke(col, alpha, 2);
        const hw = b.r * 0.45;
        for (let i = -1; i <= 1; i++) { g.beginPath(); g.moveTo(b.cx - hw, b.cy + i * b.r * 0.32); g.lineTo(b.cx + hw, b.cy + i * b.r * 0.32); g.stroke(); }
        return;
      }
      const size = b.kind === ROUND ? b.r * 0.85 : b.kind === FAST ? b.r * 1.15 : b.h * 0.42;
      g.font = '700 ' + size + 'px system-ui,sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
      g.letterSpacing = b.kind === PILL && b.label.length > 1 ? (size * 0.12) + 'px' : '0px';
      g.fillStyle = hexA(col, alpha); g.shadowColor = hexA(col, alpha / 2); g.shadowBlur = 6;
      g.fillText(b.label, b.cx, b.cy);
      g.letterSpacing = '0px'; g.shadowBlur = 0;
    }

    /* ---------------- Toque ---------------- */
    onPointer(e) {
      e.preventDefault();
      const rect = this.c.getBoundingClientRect(), x = e.clientX - rect.left, y = e.clientY - rect.top;
      if (this.editing) { this.editPointer(e, x, y); return; }
      const id = e.pointerId;
      if (e.type === 'pointercancel') this.pointers.delete(id);
      else if (e.type === 'pointerup') {
        if (this.pointers.get(id) === -1 && this.hitMenu(x, y)) this.l.menu();
        this.pointers.delete(id);
      } else {
        if (e.type === 'pointermove' && !this.pointers.has(id) && e.pointerType === 'mouse') return;
        let t = this.target(x, y);
        if (t === -1 && e.type === 'pointermove' && this.pointers.get(id) !== -1) t = 0;
        this.pointers.set(id, t);
      }
      this.update();
    }
    hitMenu(x, y) { const b = this.system.find(q => q.kind === MENU); return !!b && b.hit(x, y, 1.4); }
    target(x, y) {
      const dx = x - this.dpadX, dy = y - this.dpadY, dist = Math.hypot(dx, dy), R = this.dpadR;
      let best = null, bestD = Infinity;
      for (const b of this.all) {
        if (!b.hit(x, y, 1.3)) continue;
        const d = (x - b.cx) * (x - b.cx) + (y - b.cy) * (y - b.cy);
        if (d < bestD) { bestD = d; best = b; }
      }
      if (dist <= R || (!best && dist <= R * 1.35)) {
        if (dist < R * 0.16) return 0;
        let out = 0;
        if (dy < -0.38 * dist) out |= 1 << RB.UP;
        if (dy > 0.38 * dist) out |= 1 << RB.DOWN;
        if (dx < -0.38 * dist) out |= 1 << RB.LEFT;
        if (dx > 0.38 * dist) out |= 1 << RB.RIGHT;
        return out | 1 << 30;   // marca: veio do direcional
      }
      if (!best) return 0;
      return best.bit < 0 ? best.bit : 1 << best.bit;
    }
    update() {
      let nb = 0, nd = 0, nf = false;
      for (const t of this.pointers.values()) {
        if (t === -2) nf = true;
        else if (t > 0) { const clean = t & ~(1 << 30); nb |= clean; if (t & 1 << 30) nd |= clean; }
      }
      const changed = nb !== this.bits || nf !== this.isFast || nd !== this.dpadBits;
      if (nb !== this.bits) this.l.buttons(nb);
      if (nf !== this.isFast) this.l.fast(nf);
      this.bits = nb; this.dpadBits = nd; this.isFast = nf;
      if (changed) this.invalidate();
    }
    release() { this.pointers.clear(); this.update(); }
  }
  return { Pad };
})();
