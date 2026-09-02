/* ============================================================
 * charts.js — LineChart / DualLineChart Canvas 引擎
 * 直译 ui/components/charts/LineChart.kt：
 *  高 120dp / pad 8dp / 网格线 DividerCyber #4C1D95
 *  cubicTo 中点平滑 / 3.5f Round cap / 纯色描边(可选渐变)+面积渐变+尾点
 * ============================================================ */
'use strict';

const CHART_PAD = 8;
/* LineChart.kt:130 repeat(gridLines + 1)，gridLines 默认 5 → 6 条（含顶边与底边） */
const GRID_DIVIDERS = 6;
const COL_GRID = '#4C1D95';          // DividerCyber
const COL_LINE_A = '#7C3AED';        // NeonPurple
const COL_LINE_B = '#F43F5E';        // NeonMagenta (DualLine 第二条)
const LINE_WIDTH = 3.5;
const LINE_WIDTH_SECOND = 2;         // DualLineChart drawOneLine: Stroke(2f)

/**
 * 是否启用三段渐变描边。
 * 源码 LineChart.kt:73 useGradient 默认 false → 纯色 lineColor(=ChartLinePurple #7C3AED)，
 * 只有 useGradient=true 才走 NeonCyan → NeonPurple → NeonPurpleBright。
 * 向后兼容：调用方若显式传了 gradFrom/gradMid/gradTo 任一，视为开启渐变。
 */
function wantsGradient(opts) {
  return opts.useGradient === true || !!(opts.gradFrom || opts.gradMid || opts.gradTo);
}
/** 解析主折线颜色：渐变 → grad，否则纯色（默认 COL_LINE_A）。 */
function resolveLineColor(opts, grad) {
  if (wantsGradient(opts)) return grad;
  return opts.lineColor || COL_LINE_A;
}

/** reveal 入场：400ms，从左向右展开（对齐 animateFloatAsState tween(400)） */
function drawLineChart(canvas, values, opts = {}) {
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const wCss = canvas.clientWidth || canvas.parentElement.clientWidth || 0;
  const hCss = opts.heightPx || 120;
  /* 宽度为 0（屏未激活/尚未布局）时放弃本帧：否则 backing store 被设成 0，之后每帧都空转 */
  if (!wCss || !hCss) return;
  const wantW = Math.round(wCss * dpr), wantH = Math.round(hCss * dpr);
  /* 变化检测须同时含高度：等宽只改 opts.heightPx 的情况此前被忽略 */
  if (canvas.width !== wantW || canvas.height !== wantH) {
    canvas.width = wantW;
    canvas.height = wantH;
    canvas.style.height = hCss + 'px';
  }
  const w = canvas.width, h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const n = values.length;
  if (!n) return;
  /* opts.normalized === true → 调用方已把数据归一化到 0..1
     （对齐 NetworkScreen.kt:60-65：ChartUtils.normalizeChartData / normalizeSignalStrength），
     图表内部不再做 min/max 缩放，Y 轴固定 0..1 映射（LineChart.kt:284 safeCoerceIn(0f, 1f)）。
     默认 false，保持原有自动缩放行为。 */
  const normalized = opts.normalized === true;
  let min = 0, max = 1;
  if (!normalized) {
    /* 双线共享同一 scale：必须在算任何点之前就把第二条线并入 min/max。
       原写法先用 values 的量程算出 pts，再把 values2 并入 min/max 去算 pts2，
       导致两条线落在两个不同坐标系上（注释声称共享 scale，实际没有）。 */
    const scaleSrc = opts.values2 ? values.concat(opts.values2) : values;
    min = Infinity; max = -Infinity;
    for (const v of scaleSrc) { if (v < min) min = v; if (v > max) max = v; }
    if (max - min < 1e-6) { max += 1; min -= 1; }
  }

  const pad = CHART_PAD * dpr;
  const plotW = w - pad * 2, plotH = h - pad * 2;
  const xStep = n > 1 ? plotW / (n - 1) : plotW;
  const yOf = normalized
    ? (v => { const s = Number.isFinite(v) ? v : 0; return pad + plotH * (1 - clamp(s, 0, 1)); })
    : (v => pad + plotH * (1 - (v - min) / (max - min)));

  /* ── 6 条水平网格线（含顶边与底边）── */
  ctx.strokeStyle = COL_GRID;
  ctx.globalAlpha = .55;
  ctx.lineWidth = 1;
  for (let i = 0; i < GRID_DIVIDERS; i++) {
    const gy = pad + plotH * i / (GRID_DIVIDERS - 1);
    ctx.beginPath(); ctx.moveTo(pad, gy); ctx.lineTo(w - pad, gy); ctx.stroke();
  }
  ctx.globalAlpha = 1;

  /* ── reveal 剪裁进度 ── */
  const revealPct = opts.reveal === undefined ? 1 : clamp(opts.reveal, 0, 1);
  const clipX = pad + plotW * easeOutCubic(revealPct);
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, 0, clipX, h);
  ctx.clip();

  /* 路径点 */
  const pts = [];
  for (let i = 0; i < n; i++) pts.push([pad + xStep * i, yOf(values[i])]);

  /* 平滑路径：cubicTo 中点控制（Qt 风格 midpoint smoothing） */
  function tracePath(pArr, xLimit) {
    ctx.beginPath();
    ctx.moveTo(pArr[0][0], pArr[0][1]);
    let lastVisible = pArr[0];
    for (let i = 1; i < pArr.length; i++) {
      const [x0, y0] = pArr[i - 1], [x1, y1] = pArr[i];
      const mx = (x0 + x1) / 2;
      if (mx > xLimit && pArr[i-1][0] > xLimit) { break; }
      ctx.bezierCurveTo(mx, y0, mx, y1, x1, y1);
      lastVisible = pArr[i];
    }
    return lastVisible;
  }

  const grad = ctx.createLinearGradient(0, 0, w, 0);   // horizontalGradient
  grad.addColorStop(0, opts.gradFrom || '#00D4FF');
  grad.addColorStop(.5, opts.gradMid || '#7C3AED');
  grad.addColorStop(1, opts.gradTo || '#A78BFA');
  /* 源码默认 useGradient=false → 纯色 ChartLinePurple；仅显式要求渐变时才用三段渐变 */
  const strokeMain = resolveLineColor(opts, grad);

  /* 面积渐变 (ChartAreaPurple → transparent) */
  if (!opts.noArea) {
    ctx.beginPath();
    tracePath(pts, clipX);
    const tailX = Math.min(clipX, w - pad);
    ctx.lineTo(tailX, h - pad); ctx.lineTo(pad, h - pad); ctx.closePath();
    const areaG = ctx.createLinearGradient(0, pad, 0, h - pad);
    /* LineChart.kt:96 verticalGradient(areaColor.copy(alpha=0.3f) → 0.05f) */
    areaG.addColorStop(0, opts.areaTop || 'rgba(124,58,237,0.3)');
    areaG.addColorStop(1, opts.areaBottom || 'rgba(124,58,237,0.05)');
    ctx.fillStyle = areaG;
    ctx.fill();
  }

  /* 主描边 */
  ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.lineWidth = LINE_WIDTH * dpr;
  const visible = pts.filter(p => p[0] <= clipX + xStep);
  ctx.strokeStyle = strokeMain;
  ctx.shadowColor = opts.glow || 'rgba(124,58,237,0.31)';
  ctx.shadowBlur = 8 * dpr;
  ctx.beginPath();
  tracePath(visible.length >= 2 ? visible : pts, clipX);
  ctx.stroke();

  /* DualLineChart 第二条线 (magenta，无面积) */
  if (opts.values2) {
    const vals = opts.values2;
    /* min/max 已在函数开头并入第二条线（双线共享同一 scale），此处直接取点即可；
       normalized=true 时两条线各自按 0..1 固定区间映射，内部不做缩放。 */
    const pts2 = vals.map((v, i) => [pad + xStep * i, yOf(v)]);
    ctx.shadowColor = 'rgba(244,63,94,.4)';
    ctx.lineWidth = LINE_WIDTH_SECOND * dpr;   // DualLineChart 第二线 Stroke(2f)
    ctx.strokeStyle = opts.lineColor2 || COL_LINE_B;
    ctx.beginPath();
    tracePath(pts2.filter(p => p[0] <= clipX + xStep).length >= 2 ? pts2.filter(p => p[0] <= clipX + xStep) : pts2, clipX);
    ctx.stroke();
  }
  ctx.restore();

  /* 尾点 */
  const tail = pts[Math.min(n - 1, Math.floor(easeOutCubic(revealPct) * (n - 1)))];
  if (tail[0] <= clipX + 1) {
    /* LineChart.kt:176 drawCircle(lineColor, 4f) — 线色圆点，半径 4 */
    ctx.fillStyle = opts.tailColor || strokeMain;
    ctx.shadowColor = opts.glow || 'rgba(167,139,250,.9)';
    ctx.shadowBlur = 10 * dpr;
    ctx.beginPath();
    ctx.arc(tail[0], tail[1], 4 * dpr, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;
  }
}

function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }

/* 带 reveal 动画的渲染管理：首次出现时 400ms 从 0→1 */
const chartAnims = new WeakMap();
/** 进入某屏幕时清空该屏 canvas 的动画标记 → 图表重新播放 reveal（对齐进入 composition 的 tween(400)） */
function clearChartAnims(container) {
  if (!container) return;
  container.querySelectorAll('canvas').forEach(c => {
    chartAnims.delete(c);
    if (c._revealRaf) { cancelAnimationFrame(c._revealRaf); c._revealRaf = 0; }
  });
}
function drawLineChartAnimated(canvas, getValues, opts = {}) {
  if (!canvas) return false;
  if (!chartAnims.has(canvas)) {
    chartAnims.set(canvas, { start: performance.now() });
  }
  const anim = chartAnims.get(canvas);
  const elapsed = performance.now() - anim.start;
  const dur = opts.revealMs || 400;
  const r = clamp(elapsed / dur, 0, 1);
  drawLineChart(canvas, getValues(), { ...opts, reveal: r });
  /* 调用方普遍丢弃返回值，重绘只能等下一个 tick（2s）→ 自行续帧，400ms reveal 才真正播放。
     句柄挂在 canvas 上并在调度前取消旧的，避免多次调用叠加出重复 rAF 循环。 */
  if (canvas._revealRaf) { cancelAnimationFrame(canvas._revealRaf); canvas._revealRaf = 0; }
  if (r < 1) canvas._revealRaf = requestAnimationFrame(() => drawLineChartAnimated(canvas, getValues, opts));
  return r < 1;
}

/* DualLineChart 便捷封装 */
function drawDualLineChart(canvas, v1, v2, opts = {}) {
  /* DualLineChart 默认 useGradient1/2 = false → 纯色 lineColor1(ChartLinePurple) / lineColor2(NeonMagenta)。
     原先这里强塞 gradFrom/Mid/To 会让两条线都走渐变，与源码不符。 */
  drawLineChart(canvas, v1, { ...opts, values2: v2, noArea: true,
    lineColor2: opts.lineColor2 || COL_LINE_B });
}

/* 多轴传感器波形（X/Y/Z 三序列，共享 scale，对齐 SensorDetailScreen.MultiAxisChart） */
function drawMultiLineChart(canvas, seriesList, colors, opts = {}) {
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const wCss = canvas.clientWidth || canvas.parentElement.clientWidth || 0;
  const hCss = opts.heightPx || 200;
  if (!wCss || !hCss) return;
  const wantW = Math.round(wCss * dpr), wantH = Math.round(hCss * dpr);
  if (canvas.width !== wantW || canvas.height !== wantH) {
    canvas.width = wantW;
    canvas.height = wantH;
    canvas.style.height = hCss + 'px';
  }
  const w = canvas.width, h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const all = seriesList.flat();
  let min = Infinity, max = -Infinity;
  for (const v of all) { if (v < min) min = v; if (v > max) max = v; }
  if (!isFinite(min)) { min = 0; max = 1; }
  if (max - min < 1e-6) { max += 1; min -= 1; }

  const pad = CHART_PAD * dpr;
  const plotW = w - pad * 2, plotH = h - pad * 2;
  const n = seriesList[0] ? seriesList[0].length : 0;
  const xStep = n > 1 ? plotW / (n - 1) : plotW;
  const yOf = v => pad + plotH * (1 - (v - min) / (max - min));

  /* 网格线 */
  ctx.strokeStyle = COL_GRID; ctx.globalAlpha = .55; ctx.lineWidth = 1;
  for (let i = 0; i < GRID_DIVIDERS; i++) {
    const gy = pad + plotH * i / (GRID_DIVIDERS - 1);
    ctx.beginPath(); ctx.moveTo(pad, gy); ctx.lineTo(w - pad, gy); ctx.stroke();
  }
  ctx.globalAlpha = 1;
  if (!n) return;

  function tracePath(pArr) {
    ctx.beginPath(); ctx.moveTo(pArr[0][0], pArr[0][1]);
    for (let i = 1; i < pArr.length; i++) {
      const [x0, y0] = pArr[i - 1], [x1, y1] = pArr[i];
      const mx = (x0 + x1) / 2;
      ctx.bezierCurveTo(mx, y0, mx, y1, x1, y1);
    }
  }

  seriesList.forEach((vals, si) => {
    const col = colors[si] || '#A78BFA';
    const pts = vals.map((v, i) => [pad + xStep * i, yOf(v)]);
    if (si === 0 && !opts.noArea) {
      ctx.beginPath(); tracePath(pts);
      ctx.lineTo(pts[pts.length - 1][0], h - pad); ctx.lineTo(pad, h - pad); ctx.closePath();
      const areaG = ctx.createLinearGradient(0, pad, 0, h - pad);
      areaG.addColorStop(0, 'rgba(167,139,250,0.10)');
      areaG.addColorStop(1, 'rgba(167,139,250,0)');
      ctx.fillStyle = areaG; ctx.fill();
    }
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    ctx.lineWidth = 2.5 * dpr;
    ctx.strokeStyle = col;
    ctx.shadowColor = col; ctx.shadowBlur = 6 * dpr;
    ctx.beginPath(); tracePath(pts); ctx.stroke();
    ctx.shadowBlur = 0;
    /* 尾点 */
    const tail = pts[pts.length - 1];
    ctx.fillStyle = col; ctx.shadowColor = col; ctx.shadowBlur = 8 * dpr;
    ctx.beginPath(); ctx.arc(tail[0], tail[1], 4 * dpr, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
  });
}
