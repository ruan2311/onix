// Controle na tela em Canvas 2D ultra otimizado (60-120 FPS sólido).
// Compatível com todos os WebViews (Android e iOS), sem blur pesado, sem crashes.
'use strict';
window.OnixPad = (function () {
  const RB = { B: 0, Y: 1, SELECT: 2, START: 3, UP: 4, DOWN: 5, LEFT: 6, RIGHT: 7, A: 8, X: 9, L: 10, R: 11 };
  const ROUND = 0, PILL = 1, MENU = 2, FAST = 3;
  const G_DPAD = 0, G_L = 1, G_R = 2, G_SELECT = 3, G_START = 4, G_MENU = 5, G_FAST = 6, G_FACE = 7;
  const T_SMALLER = -10, T_BIGGER = -11, T_LESS = -12, T_MORE = -13, T_RESET = -14, T_DONE = -15, T_SIZE = -16, T_ALPHA = -17;
  const MIN_SCALE = 0.7, MAX_SCALE = 1.5, MIN_OPACITY = 0.25, MIN_SIZE = 0.6, MAX_SIZE = 1.8;
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const round1 = v => Math.round(v * 10) / 10;
  const hexA = (rgb, a) => 'rgba(' + (rgb >> 16 & 255) + ',' + (rgb >> 8 & 255) + ',' + (rgb & 255) + ',' + (Math.round(a * 100) / 100) + ')';

  function drawRoundRect(g, x, y, w, h, r) {
    r = Math.max(0, Math.min(r, w / 2, h / 2));
    g.beginPath();
    if (g.roundRect) {
      try { g.roundRect(x, y, w, h, r); return; } catch (_) {}
    }
    g.moveTo(x + r, y);
    g.lineTo(x + w - r, y);
    g.arcTo(x + w, y, x + w, y + r, r);
    g.lineTo(x + w, y + h - r);
    g.arcTo(x + w, y + h, x + w - r, y + h, r);
    g.lineTo(x + r, y + h);
    g.arcTo(x, y + h, x, y + h - r, r);
    g.lineTo(x, y + r);
    g.arcTo(x, y, x + r, y, r);
    g.closePath();
  }

  /* ---------------- Layout (touch.json v4: Retrato e Paisagem 100% independentes e calibrados) ---------------- */
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
      // Começa com os novos layouts padrão oficiais perfeitamente posicionados e proporcionais
      this.port = defaultSide();
      this.land = defaultSide();
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
      return Array.isArray(a) && a.length === 2 ? [clamp(Number(a[0]), 0.05, 0.95), clamp(Number(a[1]), 0.08, 0.95)] : null;
    },
    set(land, key, fx, fy) {
      this.side(land).pos[key] = [clamp(fx, 0.05, 0.95), clamp(fy, 0.08, 0.95)];
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

  /* ---------------- Botão ---------------- */
  class Btn {
    constructor(label, bit, color, alpha, kind, group, rx, ry, size) {
      Object.assign(this, { label, bit, color, alpha, kind, group, rx, ry, size, cx: 0, cy: 0, r: 0, w: 0, h: 0 });
    }
    hit(x, y, slack) {
      if (this.kind !== PILL) {
        const dx = x - this.cx, dy = y - this.cy, rr = this.r * slack;
        return dx * dx + dy * dy <= rr * rr;
      }
      const sw = (this.w / 2) * slack + (slack - 1) * this.h, sh = (this.h / 2) * slack + (slack - 1) * this.h;
      return Math.abs(x - this.cx) <= sw && Math.abs(y - this.cy) <= sh;
    }
    bounds() {
      return this.kind === PILL
        ? { l: this.cx - this.w / 2, t: this.cy - this.h / 2, r: this.cx + this.w / 2, b: this.cy + this.h / 2 }
        : { l: this.cx - this.r, t: this.cy - this.r, r: this.cx + this.r, b: this.cy + this.r };
    }
  }

  /* ---------------- Gamepad Principal ---------------- */
  class Pad {
    constructor(canvas, consoleId, listener, layoutText, es, options) {
      this.c = canvas;
      this.g = canvas.getContext('2d', { alpha: true, desynchronized: true });
      this.l = listener || {};
      this.es = !!es;
      this.console = consoleId || 'nes';
      this.options = options || {};
      this.hideSystemMenu = this.options.hideSystemMenu !== undefined ? !!this.options.hideSystemMenu : true;
      this.layout = new Layout(layoutText);

      this.face = [];
      this.shoulders = [];
      this.system = [];
      this.tools = [];
      this.pointers = new Map();
      this.bits = 0;
      this.dpadBits = 0;
      this.isFast = false;
      this.editing = false;
      this.dragGroup = -1;
      this.dragId = -1;
      this.pressTool = 0;
      this.dragging = false;
      this.selected = -1;
      this.w = 0;
      this.h = 0;
      this.dpadX = 0;
      this.dpadY = 0;
      this.dpadR = 0;
      this.landscape = false;
      this.raf = 0;

      const nes = 0xFF5A6E, snes = 0xA78BFA, gba = 0x38BDF8, md = 0xFBBF24;
      let sel = 'SELECT';
      this.six = this.console === 'md';
      const add = (list, ...a) => list.push(new Btn(...a));

      if (this.console === 'snes') {
        this.accent = snes;
        add(this.face, 'X', RB.X, 0x38BDF8, 1, ROUND, G_FACE + 0,  0.00, -1.05, 1.0);
        add(this.face, 'A', RB.A, 0xFF5A6E, 1, ROUND, G_FACE + 1,  1.05,  0.00, 1.0);
        add(this.face, 'B', RB.B, 0xFBBF24, 1, ROUND, G_FACE + 2,  0.00,  1.05, 1.0);
        add(this.face, 'Y', RB.Y, 0x4ADE80, 1, ROUND, G_FACE + 3, -1.05,  0.00, 1.0);
        add(this.shoulders, 'L', RB.L, snes, 1, PILL, G_L, -1, 0, 1);
        add(this.shoulders, 'R', RB.R, snes, 1, PILL, G_R,  1, 0, 1);
      } else if (this.console === 'gba') {
        this.accent = gba;
        add(this.face, 'B', RB.B, gba, 1, ROUND, G_FACE + 0, -0.80,  0.40, 1.15);
        add(this.face, 'A', RB.A, gba, 1, ROUND, G_FACE + 1,  0.80, -0.40, 1.15);
        add(this.shoulders, 'L', RB.L, gba, 1, PILL, G_L, -1, 0, 1);
        add(this.shoulders, 'R', RB.R, gba, 1, PILL, G_R,  1, 0, 1);
      } else if (this.six) {
        this.accent = md;
        // Mega Drive: grade 2x3 limpa e alinhada (X, Y, Z acima de A, B, C)
        add(this.face, 'X', RB.L, md, 1, ROUND, G_FACE + 0, -1.25, -0.65, 0.95);
        add(this.face, 'Y', RB.X, md, 1, ROUND, G_FACE + 1,  0.00, -0.65, 0.95);
        add(this.face, 'Z', RB.R, md, 1, ROUND, G_FACE + 2,  1.25, -0.65, 0.95);
        add(this.face, 'A', RB.Y, md, 1, ROUND, G_FACE + 3, -1.25,  0.65, 0.95);
        add(this.face, 'B', RB.B, md, 1, ROUND, G_FACE + 4,  0.00,  0.65, 0.95);
        add(this.face, 'C', RB.A, md, 1, ROUND, G_FACE + 5,  1.25,  0.65, 0.95);
        sel = 'MODE';
      } else {
        this.accent = nes;
        // NES: botões B e A lado a lado na horizontal (mesma linha)
        add(this.face, 'B', RB.B, nes, 1, ROUND, G_FACE + 0, -0.95, 0.00, 1.15);
        add(this.face, 'A', RB.A, nes, 1, ROUND, G_FACE + 1,  0.95, 0.00, 1.15);
      }

      add(this.system, sel, RB.SELECT, 0xA99FD0, 1, PILL, G_SELECT, -1, 0, 1);
      add(this.system, 'MENU', -1, 0xA99FD0, 1, PILL, G_MENU, 0, 0, 1);
      add(this.system, 'START', RB.START, 0xA99FD0, 1, PILL, G_START, 1, 0, 1);

      this.groups = G_FACE + this.face.length;
      this.all = [...this.face, ...this.shoulders, ...this.system];

      // Ferramentas do editor
      const t = 0xF1EDFF, cyan = 0x3DE1FF;
      add(this.tools, '-', T_SMALLER, t, 1, PILL, -1, 0, 0, 0.6);
      add(this.tools, '', T_SIZE, t, 1, PILL, -1, 1, 0, 1.6);
      add(this.tools, '+', T_BIGGER, t, 1, PILL, -1, 2, 0, 0.6);
      add(this.tools, '-', T_LESS, t, 1, PILL, -1, 3, 0, 0.6);
      add(this.tools, '', T_ALPHA, t, 1, PILL, -1, 4, 0, 1.6);
      add(this.tools, '+', T_MORE, t, 1, PILL, -1, 5, 0, 0.6);
      add(this.tools, '', T_RESET, t, 1, PILL, -1, 6, 1, 2.2);
      add(this.tools, '', T_DONE, cyan, 1, PILL, -1, 7, 1, 1.4);

      this.destroyed = false;
      this._boundPointer = ev => this.onPointer(ev);
      this._boundContextMenu = e => e.preventDefault();

      for (const e of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) {
        canvas.addEventListener(e, this._boundPointer, { passive: false });
      }
      canvas.addEventListener('contextmenu', this._boundContextMenu);
    }

    destroy() {
      this.destroyed = true;
      if (this.raf) {
        cancelAnimationFrame(this.raf);
        this.raf = 0;
      }
      if (this.c && this._boundPointer) {
        for (const e of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) {
          this.c.removeEventListener(e, this._boundPointer);
        }
      }
      if (this.c && this._boundContextMenu) {
        this.c.removeEventListener('contextmenu', this._boundContextMenu);
      }
      this.pointers.clear();
      if (this.g && this.c) {
        this.g.clearRect(0, 0, this.c.width, this.c.height);
      }
    }

    /* ---------------- Posições e Layout ---------------- */
    resize(w, h) {
      if (!w || !h) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      this.c.width = Math.round(w * dpr);
      this.c.height = Math.round(h * dpr);
      this.dpr = dpr;
      this.place(w, h);
    }

    faceReach(horizontal) {
      const isNes = !this.six && this.face.length === 2 && !this.shoulders.length;
      const br = this.six ? 0.32 : isNes ? 0.44 : this.face.length > 2 ? 0.38 : 0.44;
      const gap = br * (this.six ? 1.80 : isNes ? 1.85 : this.face.length > 2 ? 1.65 : 1.80);
      return this.face.reduce((out, b) => {
        const offset = Math.abs(horizontal ? b.rx : b.ry) * gap;
        const radius = br * b.size;
        return Math.max(out, offset + radius);
      }, 0);
    }

    place(w, h) {
      if (!w || !h) return;
      this.w = w; this.h = h;
      const land = this.landscape = w > h;
      const k = this.layout.scale(land);
      const reachX = this.faceReach(true);

      if (land) {
        // Modo Paisagem: botões de ação e direcional bem separados nas laterais, com ampla folga
        const topBarH = 46;
        const usableH = h - topBarH;
        this.dpadR = clamp(Math.min(w * 0.12, usableH * 0.34), 52, 78) * k;

        const edge = Math.max(34, w * 0.055);
        this.dpadX = edge + this.dpadR;
        this.dpadY = topBarH + usableH * 0.58;

        const faceX = w - edge - reachX * this.dpadR;
        const faceY = this.dpadY;
        this.placeFace(faceX, faceY, this.dpadR);

        const shW = Math.max(68, this.dpadR * 1.3), shH = Math.max(30, this.dpadR * 0.44);
        const shY = topBarH + 16 + shH / 2;
        this.placeShoulders(Math.max(edge + shW / 2, this.dpadX), shY, Math.min(w - edge - shW / 2, faceX), shW, shH);

        const sysH = Math.max(26, this.dpadR * 0.40);
        const sysW = sysH * 2.8;
        this.placeSystem(w / 2, h - 26, sysW, sysH, w / 2, topBarH + 14, k);
      } else {
        // Modo Retrato: espaçamento amplo no centro para os polegares não ficarem espremidos
        const topBarH = 50;
        const minGap = 100;
        const fitW = (w - 32 - minGap) / (2 + 2 * reachX);
        this.dpadR = clamp(Math.min(w * 0.165, h * 0.095, fitW), 46, 68) * k;

        const edge = 18;
        const cy = clamp(h * 0.71, h - this.dpadR - 170, h - this.dpadR - 85);
        this.dpadX = edge + this.dpadR;
        this.dpadY = cy;

        const faceX = w - edge - reachX * this.dpadR;
        const faceY = cy;
        this.placeFace(faceX, faceY, this.dpadR);

        // L e R logo acima do direcional e dos botões de ação (ao alcance natural dos polegares)
        const shW = Math.max(64, this.dpadR * 1.35), shH = Math.max(30, this.dpadR * 0.50);
        const shY = cy - this.dpadR - shH / 2 - 28;
        this.placeShoulders(this.dpadX, shY, faceX, shW, shH);

        const sysH = Math.max(26, this.dpadR * 0.44);
        const sysW = Math.max(76, sysH * 2.8);
        this.placeSystem(w / 2, h - 32, sysW, sysH, w / 2, topBarH + 14, k);
      }

      // Aplica tamanho individual se configurado no editor
      for (let g = 0; g < this.groups; g++) {
        const m = this.layout.size(land, this.key(g));
        if (m === 1) continue;
        if (g === G_DPAD) this.dpadR *= m;
        for (const b of this.all) if (b.group === g) { b.r *= m; b.w *= m; b.h *= m; }
      }

      // Aplica posições salvas
      for (let g = 0; g < this.groups; g++) {
        const p = this.layout.get(land, this.key(g));
        if (!p) continue;
        const box = this.center(g);
        if (box) {
          const bw = (box.r - box.l) / 2, bh = (box.b - box.t) / 2;
          const targetX = clamp(p[0] * w, bw + 8, w - bw - 8);
          const targetY = clamp(p[1] * h, bh + 48, h - bh - 8);
          this.move(g, targetX - (box.l + box.r) / 2, targetY - (box.t + box.b) / 2);
        }
      }

      // Clamping seguro para NUNCA vazar da tela
      for (let g = 0; g < this.groups; g++) {
        const box = this.center(g);
        if (!box) continue;
        const dx = box.l < 8 ? 8 - box.l : box.r > w - 8 ? (w - 8) - box.r : 0;
        const dy = box.t < 46 ? 46 - box.t : box.b > h - 8 ? (h - 8) - box.b : 0;
        if (dx || dy) this.move(g, dx, dy);
      }

      this.placeTools(w, h);
      this.invalidate();
    }

    key(g) {
      if (g >= G_FACE) return this.console + '.' + this.face[g - G_FACE].label;
      return String(g);
    }

    placeFace(cx, cy, r) {
      const isNes = !this.six && this.face.length === 2 && !this.shoulders.length;
      const br = r * (this.six ? 0.32 : isNes ? 0.44 : this.face.length > 2 ? 0.38 : 0.44);
      const gap = br * (this.six ? 1.80 : isNes ? 1.85 : this.face.length > 2 ? 1.65 : 1.80);
      for (const b of this.face) {
        b.r = br * b.size;
        b.cx = cx + b.rx * gap;
        b.cy = cy + b.ry * gap;
      }
    }

    placeShoulders(lx, y, rx, w, h) {
      for (const b of this.shoulders) {
        b.w = w; b.h = h;
        b.cx = b.rx < 0 ? lx : rx;
        b.cy = y;
      }
    }

    placeSystem(cx, bottomY, w, h, topX, topY, k) {
      const gap = 12;
      for (const b of this.system) {
        if (b.kind === PILL) {
          b.h = h; b.w = w;
          b.cx = cx + b.rx * (b.w + gap);
          b.cy = bottomY - b.h / 2;
        }
      }
    }

    clampAll(w, h) {
      // 1. D-Pad
      const dp = this.center(G_DPAD);
      if (dp) {
        const dx = dp.l < 8 ? 8 - dp.l : dp.r > w - 8 ? w - 8 - dp.r : 0;
        const dy = dp.t < 48 ? 48 - dp.t : dp.b > h - 8 ? h - 8 - dp.b : 0;
        if (dx || dy) this.move(G_DPAD, dx, dy);
      }

      // 2. Face cluster (como um bloco único para não deformar o diamante)
      let fb = null;
      for (const b of this.face) {
        const q = b.bounds();
        fb = fb ? { l: Math.min(fb.l, q.l), t: Math.min(fb.t, q.t), r: Math.max(fb.r, q.r), b: Math.max(fb.b, q.b) } : q;
      }
      if (fb) {
        const dx = fb.l < 8 ? 8 - fb.l : fb.r > w - 8 ? w - 8 - fb.r : 0;
        const dy = fb.t < 48 ? 48 - fb.t : fb.b > h - 8 ? h - 8 - fb.b : 0;
        if (dx || dy) {
          for (const b of this.face) { b.cx += dx; b.cy += dy; }
        }
      }

      // 3. Demais botões (ombros e sistema)
      for (let g = 1; g < this.groups; g++) {
        if (g >= G_FACE) continue;
        const box = this.center(g);
        if (!box) continue;
        const dx = box.l < 8 ? 8 - box.l : box.r > w - 8 ? w - 8 - box.r : 0;
        const dy = box.t < 48 ? 48 - box.t : box.b > h - 8 ? h - 8 - box.b : 0;
        if (dx || dy) this.move(g, dx, dy);
      }
    }

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

    placeTools(w, areaH) {
      const h = 34, gap = 6;
      const unit = Math.min(50, Math.max(34, (w - 32) / 6.8));
      let row0 = 0, row1 = 0;
      for (const b of this.tools) { b.h = h; b.w = unit * b.size; if (b.ry === 0) row0 += b.w + gap; else row1 += b.w + gap; }
      let x0 = (w - row0 + gap) / 2, x1 = (w - row1 + gap) / 2;
      const top = this.landscape ? Math.max(50, Math.min(100, (areaH - 2 * h - gap) / 2)) : 56;
      const y0 = top + h / 2, y1 = y0 + h + gap;
      for (const b of this.tools) {
        if (b.ry === 0) { b.cx = x0 + b.w / 2; b.cy = y0; x0 += b.w + gap; }
        else { b.cx = x1 + b.w / 2; b.cy = y1; x1 += b.w + gap; }
      }
    }

    /* ---------------- Editor ---------------- */
    setEditing(on) {
      this.editing = on; this.dragGroup = -1; this.selected = -1;
      this.pointers.clear();
      this.update();
      this.invalidate();
    }

    tool(id) {
      const step = id === T_BIGGER || id === T_MORE ? 0.1 : -0.1, L = this.layout, land = this.landscape;
      if (this.selected >= 0 && id !== T_DONE) {
        const k = this.key(this.selected);
        if (id === T_SMALLER || id === T_BIGGER) L.setSize(land, k, round1(L.size(land, k) + step));
        else if (id === T_LESS || id === T_MORE) L.setOpacity(land, k, round1(L.opacityOf(land, k) + step));
        else if (id === T_RESET) L.resetButton(land, k);
        else return;
      } else if (id === T_SMALLER || id === T_BIGGER) L.setScale(land, clamp(round1(L.scale(land) + step), MIN_SCALE, MAX_SCALE));
      else if (id === T_LESS || id === T_MORE) L.setOpacityAll(land, clamp(round1(L.opacity(land) + step), MIN_OPACITY, 1));
      else if (id === T_RESET) L.reset(land);
      else if (id === T_DONE) { this.save(); this.setEditing(false); if (this.l.done) this.l.done(); return; }
      else return;
      this.save();
      this.place(this.w, this.h);
    }

    save() { if (this.l.save) this.l.save(this.layout.text()); }

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
        if (box) {
          const dx = Math.max(-box.l + 8, Math.min(this.w - box.r - 8, x - this.lastX));
          const dy = Math.max(-box.t + 48, Math.min(this.h - box.b - 8, y - this.lastY));
          this.move(this.dragGroup, dx, dy);
          this.lastX = x; this.lastY = y;
          this.invalidate();
        }
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
        if (box) {
          this.layout.set(this.landscape, this.key(this.dragGroup), (box.l + box.r) / 2 / this.w, (box.t + box.b) / 2 / this.h);
          this.save();
        }
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
        if (out > 16) continue;
        const d = out * 1000 + Math.hypot(x - (box.l + box.r) / 2, y - (box.t + box.b) / 2);
        if (d < bestD) { bestD = d; best = g; }
      }
      return best;
    }

    /* ---------------- Desenho 100% Otimizado (Sem ShadowBlur) ---------------- */
    invalidate() {
      if (this.destroyed) return;
      if (this.raf) return;
      this.raf = requestAnimationFrame(() => {
        this.raf = 0;
        if (!this.destroyed) this.draw();
      });
    }

    draw() {
      const g = this.g, dpr = this.dpr || 1;
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      g.clearRect(0, 0, this.w, this.h);

      if (this.editing) {
        g.fillStyle = 'rgba(4,2,10,.55)';
        g.fillRect(0, 0, this.w, this.h);
      }

      this.drawDpad(this.alpha(G_DPAD));
      for (const b of this.all) this.drawButton(b, this.alpha(b.group));
      if (this.editing) this.drawEditor();
    }

    alpha(group) {
      const o = this.layout.opacityOf(this.landscape, this.key(group));
      return this.editing ? Math.max(o, 0.6) : o;
    }

    name(group) {
      if (group === G_DPAD) return this.es ? 'Cruceta' : 'Direcional';
      if (group === G_MENU) return 'Menu';
      const b = this.all.find(x => x.group === group);
      return b ? b.label : '';
    }

    stroke(color, alpha, width) {
      const g = this.g;
      g.shadowBlur = 0;
      g.strokeStyle = hexA(color, alpha);
      g.lineWidth = width;
      g.lineJoin = g.lineCap = 'round';
    }

    drawEditor() {
      const g = this.g, land = this.landscape;
      for (let i = 0; i < this.groups; i++) {
        const b = this.center(i);
        if (!b) continue;
        drawRoundRect(g, b.l - 6, b.t - 6, b.r - b.l + 12, b.b - b.t + 12, 10);
        if (i === this.selected) {
          this.stroke(0x3DE1FF, 1, 2.2);
          g.setLineDash([]);
          g.stroke();
          continue;
        }
        g.lineWidth = 1.5;
        g.setLineDash([5, 4]);
        g.strokeStyle = i === this.dragGroup ? '#3de1ff' : 'rgba(255,255,255,.45)';
        g.stroke();
      }
      g.setLineDash([]);

      const one = this.selected >= 0, L = this.layout, k = one ? this.key(this.selected) : '';
      for (const b of this.tools) {
        if (b.bit === T_SIZE) b.label = (this.es ? 'Tamaño ' : 'Tamanho ') + Math.round((one ? L.size(land, k) : L.scale(land)) * 100) + '%';
        else if (b.bit === T_ALPHA) b.label = (this.es ? 'Opacidad ' : 'Opacidade ') + Math.round((one ? L.opacityOf(land, k) : L.opacity(land)) * 100) + '%';
        else if (b.bit === T_RESET) b.label = one ? 'Restaurar ' + this.name(this.selected) : 'Restaurar';
        else if (b.bit === T_DONE) b.label = this.es ? 'Listo' : 'Concluir';

        const x = b.cx - b.w / 2, y = b.cy - b.h / 2, info = b.bit === T_SIZE || b.bit === T_ALPHA;
        g.fillStyle = 'rgba(14,8,32,.92)';
        drawRoundRect(g, x, y, b.w, b.h, b.h / 2);
        g.fill();

        const col = one && b.bit !== T_DONE ? 0x3DE1FF : b.color;
        this.stroke(col, info ? (one ? 0.65 : 0.3) : 0.85, 1.6);
        g.stroke();

        g.fillStyle = hexA(col, 1);
        const size = b.label.length <= 1 ? 18 : 12;
        g.font = '700 ' + size + 'px system-ui,sans-serif';
        g.textAlign = 'center';
        g.textBaseline = 'middle';
        g.fillText(b.label, b.cx, b.cy);
      }
    }

    drawDpad(base) {
      const g = this.g, r = this.dpadR, a = r * 0.34, X = this.dpadX, Y = this.dpadY, ac = this.accent;

      // 1. Caminho em cruz do direcional
      g.beginPath();
      for (const [x, y] of [[-a, -r], [a, -r], [a, -a], [r, -a], [r, a], [a, a], [a, r], [-a, r], [-a, a], [-r, a], [-r, -a], [-a, -a]]) {
        g.lineTo(X + x, Y + y);
      }
      g.closePath();

      // 2. Fundo suave
      g.fillStyle = 'rgba(255,255,255,0.06)';
      g.fill();

      // 3. Direções ativas iluminadas
      const up = !!(this.dpadBits & (1 << RB.UP));
      const down = !!(this.dpadBits & (1 << RB.DOWN));
      const left = !!(this.dpadBits & (1 << RB.LEFT));
      const right = !!(this.dpadBits & (1 << RB.RIGHT));

      g.fillStyle = hexA(ac, 0.45);
      if (up) g.fillRect(X - a, Y - r, 2 * a, r - a);
      if (down) g.fillRect(X - a, Y + a, 2 * a, r - a);
      if (left) g.fillRect(X - r, Y - a, r - a, 2 * a);
      if (right) g.fillRect(X + a, Y - a, r - a, 2 * a);

      // 4. Borda externa nítida
      this.stroke(ac, base, 2.4);
      g.stroke();

      // 5. Setas indicadoras
      const t = a * 0.55, o = r * 0.72;
      g.fillStyle = up ? '#FFFFFF' : hexA(ac, base);
      this.arrow(X, Y - o, 0, -1, t);
      g.fillStyle = down ? '#FFFFFF' : hexA(ac, base);
      this.arrow(X, Y + o, 0, 1, t);
      g.fillStyle = left ? '#FFFFFF' : hexA(ac, base);
      this.arrow(X - o, Y, -1, 0, t);
      g.fillStyle = right ? '#FFFFFF' : hexA(ac, base);
      this.arrow(X + o, Y, 1, 0, t);

      // 6. Círculo do pivô central
      this.stroke(ac, base * 0.45, 1.8);
      g.beginPath();
      g.arc(X, Y, a * 0.45, 0, Math.PI * 2);
      g.stroke();
    }

    arrow(x, y, dx, dy, s) {
      const g = this.g;
      g.beginPath();
      g.moveTo(x + dx * s * 0.6, y + dy * s * 0.6);
      g.lineTo(x - dx * s * 0.4 + dy * s * 0.6, y - dy * s * 0.4 + dx * s * 0.6);
      g.lineTo(x - dx * s * 0.4 - dy * s * 0.6, y - dy * s * 0.4 - dx * s * 0.6);
      g.closePath();
      g.fill();
    }

    drawButton(b, base) {
      const g = this.g;
      let isDown = false;
      if (b.bit >= 0) isDown = (this.bits & (1 << b.bit)) !== 0;
      else if (b.bit === -1) {
        for (const val of this.pointers.values()) { if (val === -1) { isDown = true; break; } }
      } else if (b.bit === -2) isDown = this.isFast;
      const on = isDown;
      const alpha = base * b.alpha, col = b.color;

      // 1. Fundo do botão translúcido
      if (b.kind === PILL) {
        drawRoundRect(g, b.cx - b.w / 2, b.cy - b.h / 2, b.w, b.h, b.h / 2);
      } else {
        g.beginPath();
        g.arc(b.cx, b.cy, b.r, 0, Math.PI * 2);
      }
      g.fillStyle = on ? hexA(col, 0.42) : 'rgba(255,255,255,0.06)';
      g.fill();

      // 2. Borda nítida vetorial (brilha branca quando pressionado)
      this.stroke(on ? 0xFFFFFF : col, on ? 1 : alpha, on ? (b.kind === ROUND ? 3.2 : 2.5) : (b.kind === ROUND ? 2.4 : 1.8));
      g.stroke();

      // 3. Ícone ou rótulo
      if (b.kind === MENU) {
        this.stroke(col, alpha, 2.2);
        const hw = b.r * 0.45;
        for (let i = -1; i <= 1; i++) {
          g.beginPath();
          g.moveTo(b.cx - hw, b.cy + i * b.r * 0.32);
          g.lineTo(b.cx + hw, b.cy + i * b.r * 0.32);
          g.stroke();
        }
        return;
      }

      const size = b.kind === ROUND ? b.r * 0.85 : b.kind === FAST ? b.r * 1.15 : b.h * 0.42;
      g.font = '800 ' + Math.round(size) + 'px system-ui, -apple-system, sans-serif';
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillStyle = on ? '#FFFFFF' : hexA(col, alpha);
      g.fillText(b.label, b.cx, b.cy);
    }

    /* ---------------- Toque / Input de baixa latência ---------------- */
    onPointer(e) {
      if (this.destroyed) return;
      if (e.cancelable) e.preventDefault();
      const rect = this.c.getBoundingClientRect();
      const x = e.clientX - rect.left, y = e.clientY - rect.top;
      if (this.editing) { this.editPointer(e, x, y); return; }

      const id = e.pointerId;
      if (e.type === 'pointercancel') {
        this.pointers.delete(id);
      } else if (e.type === 'pointerup') {
        if (this.pointers.get(id) === -1 && this.hitMenu(x, y) && this.l.menu) this.l.menu();
        this.pointers.delete(id);
      } else {
        if (e.type === 'pointermove' && !this.pointers.has(id) && e.pointerType === 'mouse') return;
        let t = this.target(x, y);
        if (t === -1 && e.type === 'pointermove' && this.pointers.get(id) !== -1) t = 0;
        this.pointers.set(id, t);
      }
      this.update();
    }

    hitMenu(x, y) {
      const b = this.system.find(q => q.label === 'MENU' || q.kind === MENU || q.group === G_MENU);
      return !!b && b.hit(x, y, 1.4);
    }

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
        return out | (1 << 30);
      }

      if (!best) return 0;
      return best.bit < 0 ? best.bit : (1 << best.bit);
    }

    update() {
      let nb = 0, nd = 0, nf = false;
      for (const t of this.pointers.values()) {
        if (t === -2) nf = true;
        else if (t > 0) {
          const clean = t & ~(1 << 30);
          nb |= clean;
          if (t & (1 << 30)) nd |= clean;
        }
      }
      const changed = nb !== this.bits || nf !== this.isFast || nd !== this.dpadBits;
      if (nb !== this.bits && this.l.buttons) this.l.buttons(nb);
      if (nf !== this.isFast && this.l.fast) this.l.fast(nf);
      this.bits = nb; this.dpadBits = nd; this.isFast = nf;
      if (changed) this.invalidate();
    }

    release() {
      this.pointers.clear();
      this.update();
    }
  }

  return { Pad };
})();
