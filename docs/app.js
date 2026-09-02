/* ============================================================
 * app.js — 渲染层 v2（交互全量对齐源码）
 *  - ic_cyber_* 真实矢量图标（icons.js，currentColor tint）
 *  - HorizontalPager 左右滑动手势 + 方向感知入场
 *  - cardRipple 按压缩点（Material3 原生 Ripple 观感）
 *  - 快速访问/指标卡拖拽排序
 *  - FancySlider 实时 onValueChange 驱动数据层
 *  - 语言 Dialog / 夜光条 / 全局光照 / TurboXDR 实际生效
 * ============================================================ */
'use strict';

const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let activeTab = 0;
/* Tab 名走 i18n：值为 strings.xml 的 tab_* key，显示时经 t() 取当前语言 */
const TAB_KEYS = ['tab_dashboard','tab_cpu','tab_gpu','tab_memory','tab_battery','tab_network','tab_gps','tab_sensors','tab_device'];
/* 与 MainActivity topTabIcons 一一对应 */
const TAB_ICONS = ['dashboard','cpu','gpu','memory','battery','network','gps','sensors','device'];

/* ── 语言：取 LANGS 的原生自称标签（LocaleManager.SUPPORTED_LANGUAGES.nativeName） ── */
function langLabelOf(code) {
  const o = LANGS.find(x => x.code === code);
  return o ? o.label : LANGS[0].label;
}
/* 重渲染单屏：renderScreen 自带 renderedOnce 短路（:551/:553），
   必须先摘掉该屏的记录，否则 reenter=true 也只会走 updateScreen、不重建 innerHTML。 */
function rerenderScreen(i) {
  renderedOnce.delete(i);
  renderScreen(i);
}

/* ══════════════════════════════════════════════════════════════
 * CyberJoystickSwitch 运行时（CyberJoystickSwitch.kt:71-407）
 *
 * 几何（:121-129，轨道 52×32 / 高 32）：
 *   skewPx      = 32 × 0.15      = 4.8
 *   thumbR      = 32 × 0.40      = 12.8   → 拇指直径 25.6
 *   thumbStartX = 2.4 + 12.8×1.1 = 16.48  → 拇指左缘  3.68
 *   thumbEndX   = 52 − 2.4 − 14.08 = 35.52 → 拇指左缘 22.72（行程 19.04px）
 *   markR       = 32 × 0.06 = 1.92；trail 长 thumbR×1.5 = 19.2 / 粗 ×0.6 = 7.68
 *
 * 驱动量（全部由 JS 写到 .switch 上，CSS 只做插值）：
 *   --p    进度 0..1（点击 → CSS @property 过渡；拖拽 → .dragging 摘过渡逐帧 snapTo）
 *   --tilt 倾斜符号 关 −1 / 开 +1（:119 tiltSign）
 *   --dir  拖尾方向 关 +1 / 开 −1（:201 dirSign = if (checked) -1f else 1f）
 *   --press 按压 scale（:110-117 pressScale 0.9）
 * ══════════════════════════════════════════════════════════════ */
const JOY_TRAVEL_PX = 19;          // thumbEndX − thumbStartX，拖拽位移→进度的分母

/* 开关内部三层结构（.knob 为旧 CSS 遗留名，新逻辑一律用 .thumb） */
function joySwitchHtml() {
  return '<div class="track"></div><div class="trail"></div><div class="thumb"></div>';
}

(function injectJoySwitchCss() {
  /* style.css 本轮冻结 → JoySwitch 的 @property / .track / .trail / .thumb 由 app.js 注入，
     注入表位于 <link rel=stylesheet> 之后，同特异性下后者胜出，可安全覆盖旧 .switch 规则。 */
  const css = `
@property --p { syntax: '<number>'; inherits: true; initial-value: 0; }
.switch {
  --p: 0; --tilt: -1; --dir: 1; --press: 1;
  --steel: #3D70B8; --pink: #C2185B;
  --sp: calc(sin(var(--p) * 180deg));
  --thumbL: calc(3.7px + 19px * var(--p));
  --trailc: color-mix(in srgb, var(--steel) calc((1 - var(--p)) * 100%), var(--pink) calc(var(--p) * 100%));
  background: none;
  perspective: 400px;
  transition: --p .55s cubic-bezier(.34, 1.56, .64, 1), border-color .25s ease, box-shadow .25s ease;
  border-color: color-mix(in srgb, rgba(61,112,184,.5) calc((1 - var(--p)) * 100%), rgba(217,70,239,.6) calc(var(--p) * 100%));
  box-shadow: 0 0 14px color-mix(in srgb, rgba(194,24,91,0) calc((1 - var(--p)) * 100%), rgba(194,24,91,.55) calc(var(--p) * 100%));
}
/* sin() 不可用时回落到同端点的抛物线 4p(1−p)（sin(πp) 的一阶近似） */
@supports not (width: calc(sin(0.25 * 180deg) * 1px)) {
  .switch { --sp: calc(4 * var(--p) * (1 - var(--p))); }
}
.switch.dragging { transition: none; }
.switch.on {
  border-color: color-mix(in srgb, rgba(61,112,184,.5) calc((1 - var(--p)) * 100%), rgba(217,70,239,.6) calc(var(--p) * 100%));
  box-shadow: 0 0 14px color-mix(in srgb, rgba(194,24,91,0) calc((1 - var(--p)) * 100%), rgba(194,24,91,.55) calc(var(--p) * 100%));
}
.switch.pink.on { box-shadow: 0 0 16px color-mix(in srgb, rgba(194,24,91,0) calc((1 - var(--p)) * 100%), rgba(194,24,91,.6) calc(var(--p) * 100%)); }
/* 旧 .switch::after 拖尾由 .trail 元素接管 */
.switch::after { display: none; }
.switch:active { transform: skewX(-9deg); --press: .9; }

/* ── 轨道 4 层：T1 底色渐变 + T2 内阴影 + T3(描边在 .switch 上) + T4 左右方向标记 ── */
.switch > .track {
  position: absolute; inset: 0; border-radius: 5px; pointer-events: none;
  --t1: color-mix(in srgb, rgba(61,112,184,.45) calc((1 - var(--p)) * 100%), rgba(194,24,91,.60) calc(var(--p) * 100%));
  --t2: color-mix(in srgb, rgba(61,112,184,.25) calc((1 - var(--p)) * 100%), rgba(194,24,91,.38) calc(var(--p) * 100%));
  background:
    linear-gradient(180deg, rgba(0,0,0,.12), rgba(0,0,0,0) 45%, rgba(0,0,0,.06)),
    linear-gradient(120deg, var(--t1), var(--t2), var(--t1));
}
.switch > .track::before, .switch > .track::after {
  content: ''; position: absolute; top: 50%;
  width: 3.84px; height: 3.84px; border-radius: 50%; transform: translateY(-50%);
}
.switch > .track::before { left: 4.4px;  background: rgba(61,112,184,.5); opacity: calc(1 - var(--p)); }
.switch > .track::after  { right: 4.4px; background: rgba(194,24,91,.5); opacity: var(--p); }

/* ── 光晕拖尾（:194-216）：alpha = sin(pπ)×0.35，自拇指向后延伸 19.2px ── */
.switch > .trail {
  position: absolute; left: 0; top: 50%;
  width: 19.2px; height: 7.68px; border-radius: 3.84px; pointer-events: none;
  opacity: calc(var(--sp) * .35);
  transform: translateY(-50%) translateX(calc(var(--thumbL) + var(--dir) * 19.2px)) skewX(9deg);
  background: linear-gradient(to right, var(--trailc), transparent);
}
.switch.on > .trail { background: linear-gradient(to left, var(--trailc), transparent); }

/* ── 拇指 6 层（:323-407）：L1 外光晕 / L2 主体径向 / L3 左上高光 /
      L4 底部暗角 / L5 边缘描边 / L6 顶部反光弧 ── */
.switch > .thumb {
  position: absolute; top: 3.2px; left: 0;
  width: 25.6px; height: 25.6px; border-radius: 50%; pointer-events: none;
  --c95: color-mix(in srgb, rgba(111,143,208,.95) calc((1 - var(--p)) * 100%), rgba(194,24,91,.95) calc(var(--p) * 100%));
  --c60: color-mix(in srgb, rgba(111,143,208,.60) calc((1 - var(--p)) * 100%), rgba(194,24,91,.60) calc(var(--p) * 100%));
  --c85: color-mix(in srgb, rgba(111,143,208,.85) calc((1 - var(--p)) * 100%), rgba(194,24,91,.85) calc(var(--p) * 100%));
  --c40: color-mix(in srgb, rgba(111,143,208,.40) calc((1 - var(--p)) * 100%), rgba(194,24,91,.40) calc(var(--p) * 100%));
  --glow: color-mix(in srgb, rgba(111,143,208,.45) calc((1 - var(--p)) * 100%), rgba(255,143,196,.85) calc(var(--p) * 100%));
  transform:
    skewX(9deg)
    translateX(var(--thumbL))
    translateY(calc(var(--sp) * -4px))
    rotateY(calc(var(--sp) * 24deg * var(--tilt)))
    scale(calc((1 + var(--sp) * 0.06) * var(--press)));
  box-shadow:
    0 0 10px 1px var(--glow),
    inset 0 0 0 .5px var(--c40),
    0 1px 3px rgba(0,0,0,.4);
  background:
    radial-gradient(circle at 50% 65%, rgba(0,0,0,.25), transparent 36%),
    radial-gradient(circle at 37% 35%, var(--c95), var(--c60) 55%, var(--c85));
}
.switch > .thumb::before {   /* L3 高光点 (c×0.68, c×0.62) r×0.5 */
  content: ''; position: absolute; left: 18%; top: 16%;
  width: 50%; height: 50%; border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, rgba(255,255,255,.6), rgba(255,255,255,.15) 55%, transparent 75%);
}
.switch > .thumb::after {    /* L6 顶部反光弧 drawArc(200°,140°) */
  content: ''; position: absolute; left: 10%; top: 10%;
  width: 80%; height: 80%; border-radius: 50%;
  border: 1px solid transparent;
  border-top-color: rgba(255,255,255,.25);
  border-left-color: rgba(255,255,255,.12);
  transform: rotate(-20deg);
}
`;
  const st = document.createElement('style');
  st.id = 'joy-switch-runtime';
  st.textContent = css;
  (document.head || document.documentElement).appendChild(st);
})();

/**
 * 实例化一个 JoySwitch。
 * @param el   .switch 容器（保留原有 data-toggle / data-layout-toggle 属性）
 * @param get  () => boolean   读当前状态
 * @param set  (v) => void     写状态（只在状态真的变化时才调用）
 * @param onChange (v) => void 切换后的副作用（toast / 重建界面 / 同步联动）
 */
function makeSwitch(el, get, set, onChange) {
  if (!el || el.dataset.joy === '1') return el;
  el.dataset.joy = '1';
  if (!el.querySelector('.thumb')) el.innerHTML = joySwitchHtml();

  const sync = () => {
    const on = !!get();
    el.classList.toggle('on', on);
    el.style.setProperty('--p', on ? '1' : '0');
    el.style.setProperty('--tilt', on ? '1' : '-1');
    el.style.setProperty('--dir', on ? '-1' : '1');
  };
  el._joySync = sync;
  sync();

  const apply = v => {
    if (!!get() !== !!v) set(!!v);
    sync();
    onChange && onChange(!!v);
  };

  let down = false, moved = false, startX = 0, base = 0, suppressClickUntil = 0;
  el.addEventListener('pointerdown', e => {
    down = true; moved = false; startX = e.clientX; base = get() ? 1 : 0;
    el.classList.add('dragging');
    try { el.setPointerCapture(e.pointerId); } catch (_) {}
  });
  el.addEventListener('pointermove', e => {
    if (!down) return;
    const dx = e.clientX - startX;
    if (Math.abs(dx) > 3) moved = true;
    if (!moved) return;
    /* :155-159 水平拖拽：accumulatedPx / (thumbEndX - thumbStartX)，松手过半即切 */
    el.style.setProperty('--p', clamp(base + dx / JOY_TRAVEL_PX, 0, 1).toFixed(3));
  });
  const finish = () => {
    if (!down) return;
    down = false;
    el.classList.remove('dragging');
    if (!moved) return;                       // 纯点击 → 交给 click 分支，避免双重切换
    moved = false;
    /* 拖拽结束后浏览器还会补发一次 click。此处不是用持久布尔位去吞它（若该 click 因拖拽
       终点在元素外而没派发，标志位会残留、把之后的正常点击一起吞掉），而是开一个 400ms
       的抑制窗口：窗口内的 click 一律忽略，窗口过期后自动恢复点击能力。 */
    suppressClickUntil = performance.now() + 400;
    const p = parseFloat(el.style.getPropertyValue('--p')) || 0;
    apply(p > 0.5);                           // :161-171 过半切换 / 未过半弹回原态
  };
  el.addEventListener('pointerup', finish);
  el.addEventListener('pointercancel', finish);
  el.addEventListener('click', () => {
    if (performance.now() < suppressClickUntil) return;
    apply(!get());
  });
  return el;
}

/* 批量接管覆盖层内的 .switch[data-toggle] 与 [data-layout-toggle] */
function bindSwitches(scope) {
  if (!scope) return;
  $$('.switch[data-toggle]', scope).forEach(el => {
    const key = el.dataset.toggle;
    makeSwitch(el, () => Sim.settings[key], v => toggleSetting(el, v));
  });
  const lay = $('[data-layout-toggle]', scope);
  lay && makeSwitch(lay,
    () => layoutMode() === 'adaptive',
    v => setLayoutMode(v ? 'adaptive' : 'portrait'),
    v => {
      const st = $('#st-layout'); st && (st.textContent = v ? t('settings_haptic_on') : t('settings_haptic_off'));
      toast(`${t('web_sim_adaptive_layout')}：${v ? t('web_sim_on') : t('web_sim_off')}`);
    });
}

/* ══════════════ 骨架 HTML 生成 ══════════════ */
function buildSkeleton() {
  /* .deco-particle 已从 style.css 移除 —— 不再生成，否则残留无样式节点 */

  /* Icon size(17.dp)（MainActivity.kt:869 / :882） */
  $('#btn-float').innerHTML = iconSvg('window', 17);
  $('#btn-settings').innerHTML = iconSvg('settings', 17);
  $('#btn-float').addEventListener('click', openFloatConfig);
  $('#btn-settings').addEventListener('click', openSettings);

  const tr = $('#tab-row');
  TAB_KEYS.forEach((k, i) => {
    const b = document.createElement('button');
    b.className = 'tab-item' + (i === 0 ? ' active' : '');
    /* 文本保持**裸文本节点**（.tab-item 是 inline-flex + gap:5px，裸文本即匿名 flex 项），
       切语言时直接改 lastChild.nodeValue，不重建 DOM、不动 icon。 */
    b.innerHTML = `<span class="tab-icon">${iconSvg(TAB_ICONS[i], 16)}</span>${esc(t(k))}`;
    b.dataset.tabKey = k;
    b.addEventListener('click', () => switchTab(i));
    tr.appendChild(b);
  });

  const pager = $('#pager');
  for (let i = 0; i < 9; i++) {
    const d = document.createElement('div');
    d.className = 'screen' + (i === 0 ? ' active' : '');
    d.id = `screen-${i}`;
    d.dataset.pi = i;
    pager.appendChild(d);
  }
}

/* ════════════════ HorizontalPager 引擎（滑移 + 卡片级联甩尾） ══════════════ */
let pagerOffset = 0;          // 当前页码（可为小数，由拖拽/动画驱动）
let pagerAnim = null;

function pagerWidth() { return $('#pager').clientWidth || 1; }

/* 每次实时查询：屏幕在 switchTab 时才首次填充 innerHTML，缓存会让 1-8 屏永久拿到空列表 */
function cardList(screen) { return screen.querySelectorAll('.cyber-card'); }

/* 单弹簧 + 级联相位映射（对齐 StaggeredPageTransition.kt） */
function applyParallax() {
  const w = pagerWidth();
  const STEP = 0.13, MAXC = 3.0, PAR = 0.20, SCALE = 0.05, ALPHA = 0.15;
  $$('.screen').forEach(screen => {
    const pi = +screen.dataset.pi;
    const raw = pi - pagerOffset;            // 0 = 本页居中
    if (Math.abs(raw) > 1.06) {             // 远离视口：清理残留变换
      cardList(screen).forEach(c => { if (c.style.transform) { c.style.transform = ''; c.style.opacity = ''; } });
      return;
    }
    cardList(screen).forEach((c, idx) => {
      const cascade = Math.min(1 + idx * STEP, MAXC);
      const eff = Math.max(-1, Math.min(1, raw)) * cascade;
      const tx = w * eff * PAR;
      const sc = (1 - Math.abs(eff) * SCALE).toFixed(3);
      const al = (1 - Math.abs(eff) * ALPHA).toFixed(3);
      c.style.transform = `translateX(${tx.toFixed(1)}px) scale(${sc})`;
      c.style.opacity = al;
    });
  });
}

/* 平移对象是各 .screen，不是 #pager —— #pager 自身是 overflow:hidden 的裁剪容器
   （style.css:200），容器一旦平移，裁剪框跟着移出视口，屏 1-8 会被整体裁掉不绘制 */
function applyPager() {
  const dx = (-pagerOffset * pagerWidth()).toFixed(1);
  $$('.screen').forEach(s => { s.style.transform = `translateX(${dx}px)`; });
  applyParallax();
}

function animatePagerTo(target, onDone) {
  if (pagerAnim) cancelAnimationFrame(pagerAnim);
  const start = pagerOffset, end = target, dur = 320, t0 = performance.now();
  function step(now) {
    const k = Math.min(1, (now - t0) / dur);
    const e = k < .5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;   // easeInOutQuad
    pagerOffset = start + (end - start) * e;
    applyPager();
    if (k < 1) pagerAnim = requestAnimationFrame(step);
    else { pagerOffset = end; applyPager(); onDone && onDone(); }
  }
  pagerAnim = requestAnimationFrame(step);
}

/* 切 Tab 触发夜光条一次性边缘闪光 */
function flashNightBar() {
  const nb = $('#night-bar');
  if (!nb || !nb.classList.contains('show')) return;
  nb.classList.remove('flash'); void nb.offsetWidth; nb.classList.add('flash');
}

function switchTab(i) {
  i = Math.max(0, Math.min(8, i));
  if (i === activeTab) return;
  activeTab = i;
  /* 切 Tab 时收起传感器详情 —— 否则停靠态下详情面板会浮在新屏之上 */
  const ovSensor = $('#overlay-sensor');
  if (ovSensor && ovSensor.classList.contains('active')) closeOverlay('#overlay-sensor');
  $$('.tab-item').forEach((b, j) => b.classList.toggle('active', j === i));
  $$('.screen').forEach((s, j) => s.classList.toggle('active', j === i));
  clearChartAnims($(`#screen-${i}`));
  flashNightBar();
  renderScreen(i, true);
  animatePagerTo(i);
}

/* ═══════════════════════════════════════════════════════════════
 * M3 自适应布局（WindowSizeClass）—— **web 端增强，非源码对齐**
 * 源码分支未实现自适应（无 WindowSizeClass / ListDetailPaneScaffold /
 * values-sw600dp；SensorDetailContent 是全屏覆盖层），故按 Material 3 官方断点设计：
 *   宽度档 compact <600dp / medium 600–839dp / expanded ≥840dp / large ≥1240dp（电视式）
 *   高度档 compact <480dp / medium 480–899dp / expanded ≥900dp
 * 具体排布见 style.css「M3 自适应布局」分节。
 * ═══════════════════════════════════════════════════════════════ */
function sizeClassOf(wDp) {
  if (wDp < 600) return 'compact';
  if (wDp < 840) return 'medium';
  if (wDp < 1240) return 'expanded';
  return 'large';                    /* 电视式大屏 */
}
function heightClassOf(hDp) {
  if (hDp < 480) return 'compact';
  if (hDp < 900) return 'medium';
  return 'expanded';
}
/* ══════════════ 布局模式：portrait（原生竖屏单栏，默认） / adaptive（多栏） ══════════════
 * style.css 的多栏 / Rail / 详情停靠规则全部只在 #phone[data-layout="adaptive"] 下生效，
 * 默认 portrait 即原生形态。开关写 localStorage['cw-layout-mode']。 */
const LAYOUT_MODE_KEY = 'cw-layout-mode';
function layoutMode() {
  try { return localStorage.getItem(LAYOUT_MODE_KEY) === 'adaptive' ? 'adaptive' : 'portrait'; }
  catch (_) { return 'portrait'; }
}
function applyLayoutMode() {
  const phone = $('#phone');
  if (phone) phone.dataset.layout = layoutMode();
  return layoutMode();
}
function setLayoutMode(mode) {
  try { localStorage.setItem(LAYOUT_MODE_KEY, mode === 'adaptive' ? 'adaptive' : 'portrait'); } catch (_) {}
  applyLayoutMode();
  /* 切回 portrait 时收起停靠态（停靠属多栏特性） */
  if (layoutMode() !== 'adaptive') clearPaneDocked();
  applyPager();
}
/* 详情面板是否停靠为右侧双栏 —— 需同时满足：布局模式 adaptive + 展开档及以上 */
function isDetailDockable() {
  if (layoutMode() !== 'adaptive') return false;
  const sc = document.documentElement.dataset.sizeClass;
  return sc === 'expanded' || sc === 'large';
}
/* 停靠态的 #phone 类：传感器详情与 HDR 实验室各用一个，
   二者共用同一套 CSS 停靠规则与 pager 收窄规则。 */
const PANE_DOCK_CLASS = { sensor: 'sensor-detail-docked', hdr: 'hdr-docked' };
function setPaneDocked(kind, on) {
  const phone = $('#phone');
  if (!phone) return;
  const cls = PANE_DOCK_CLASS[kind];
  if (!cls) return;
  phone.classList.toggle(cls, !!on && isDetailDockable());
  applyPager();
}
/** 清掉所有停靠态（切回 portrait / 缩回紧凑档 / 关闭覆盖层时用） */
function clearPaneDocked() {
  const phone = $('#phone');
  if (!phone) return;
  Object.values(PANE_DOCK_CLASS).forEach(c => phone.classList.remove(c));
}
let _lastDpr = 0;
function applySizeClass() {
  const de = document.documentElement;
  de.dataset.sizeClass = sizeClassOf(window.innerWidth);
  de.dataset.heightClass = heightClassOf(window.innerHeight);

  /* dpr 变化（跨显示器拖动 / 系统缩放变更）→ canvas backing store 需按新 dpr 重建。
     charts.js 仅在 canvas.width !== wantW 时才重建，故把 width 归零强制其重算。 */
  const dpr = window.devicePixelRatio || 1;
  if (_lastDpr && Math.abs(dpr - _lastDpr) > 0.01) {
    document.querySelectorAll('.screen canvas').forEach(cv => { cv.width = 0; cv.height = 0; });
    renderedOnce.forEach(i => updateScreen(i));
  }
  _lastDpr = dpr;

  /* 缩回紧凑档时收起停靠态（对应 CSS 里停靠规则只在 ≥840dp 生效） */
  if (!isDetailDockable()) clearPaneDocked();
  return de.dataset.sizeClass;
}

/* 拖拽翻页（区分横滑/竖滚；仅在非交互元素上接管） */
function setupPagerDrag() {
  const pager = $('#pager');
  let dragging = false, decided = false, vertical = false, moved = false;
  let startX = 0, startY = 0, startOffset = 0, pid = null;
  const blockSel = 'button,a,input,textarea,select,.fancy-slider,.switch,[draggable="true"],.back-btn,.overlay-bg,.tab-item,.chip';
  pager.addEventListener('pointerdown', e => {
    /* 覆盖层（设置/悬浮窗配置）打开时不接管拖拽 —— 与已删除的 pagerSwipe 引擎行为一致 */
    if ($('.overlay.active')) return;
    if (e.target.closest(blockSel)) return;
    dragging = true; decided = false; vertical = false; moved = false;
    startX = e.clientX; startY = e.clientY; startOffset = pagerOffset; pid = e.pointerId;
  });
  pager.addEventListener('pointermove', e => {
    if (!dragging) return;
    const dx = e.clientX - startX, dy = e.clientY - startY;
    if (!decided) {
      if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) { decided = true; vertical = false; moved = true; try { pager.setPointerCapture(pid); } catch (_) {} }
      else if (Math.abs(dy) > 8) { decided = true; vertical = true; }
    }
    if (decided && !vertical) {
      if (pagerAnim) cancelAnimationFrame(pagerAnim);
      const w = pagerWidth();
      pagerOffset = Math.max(0, Math.min(8, startOffset - dx / w));
      applyPager();
      e.preventDefault();
    }
  });
  const end = e => {
    if (!dragging) return;
    dragging = false;
    if (decided && !vertical) {
      const target = Math.round(pagerOffset);
      if (target !== activeTab) switchTab(target);
      else animatePagerTo(activeTab);
    }
  };
  pager.addEventListener('pointerup', end);
  pager.addEventListener('pointercancel', () => { dragging = false; animatePagerTo(activeTab); });
}

/* ════════════════ 全局光照：卡片指针跟随揭示光 ════════════════ */
let _revealCard = null, _revealTimer = null;
function setupGlobalLight() {
  const phone = $('#phone');
  const cardOf = el => (el && el.closest) ? el.closest('.cyber-card,.quick-card,.info-card') : null;
  window.addEventListener('pointermove', e => {
    if (!phone.classList.contains('glow-on')) return;
    const target = cardOf(e.target);
    if (target && target !== _revealCard) {
      if (_revealCard) _revealCard.style.setProperty('--lint', '0');
      _revealCard = target;
    }
    if (_revealCard) {
      const r = _revealCard.getBoundingClientRect();
      _revealCard.style.setProperty('--lx', (e.clientX - r.left) + 'px');
      _revealCard.style.setProperty('--ly', (e.clientY - r.top) + 'px');
      _revealCard.style.setProperty('--lint', '0.34');
    }
    if (_revealTimer) clearTimeout(_revealTimer);
    _revealTimer = setTimeout(() => { if (_revealCard) { _revealCard.style.setProperty('--lint', '0'); _revealCard = null; } }, 1200);
  }, { passive: true });
  window.addEventListener('pointerdown', e => {
    if (!phone.classList.contains('glow-on')) return;
    const t = cardOf(e.target);
    if (t) {
      const r = t.getBoundingClientRect();
      t.style.setProperty('--lx', (e.clientX - r.left) + 'px');
      t.style.setProperty('--ly', (e.clientY - r.top) + 'px');
      t.style.setProperty('--lint', '0.50');
    }
  }, { passive: true });
}

/* ── 卡片构造 helpers ── */
function infoCard(iconName, title, subtitle, extraCls = '') {
  const iconHtml = ICONS[iconName] ? iconSvg(iconName, 24) : iconName;
  return `<div class="cyber-card info-row card-enter ${extraCls}">
    <div class="info-icon-wrap">${iconHtml}</div>
    <div class="info-titles">
      <div class="info-title">${title}</div>
      ${subtitle ? `<div class="info-subtitle">${subtitle}</div>` : ''}
    </div></div>`;
}
function metricCard({ label, valueId, subId, barId, canvasId, extraHtml = '', cls = '' }) {
  return `<div class="cyber-card metric-card card-enter ${cls}">
    <div class="metric-label" data-label>${label}</div>
    <div class="metric-value" id="${valueId}">--</div>
    ${barId !== undefined ? `<div class="progress-track"><div class="progress-fill" id="${barId}"></div></div>` : ''}
    ${subId ? `<div class="metric-subtitle" id="${subId}"></div>` : ''}
    ${canvasId ? `<canvas id="${canvasId}"></canvas>` : ''}
    ${extraHtml}
  </div>`;
}
function kvRow(label, valueHTML, cls = '') {
  return `<div class="kv-row"><span class="kv-label">${label}</span><span class="kv-value ${cls}">${valueHTML}</span></div>`;
}

/* ══════════════ 入场错峰（EntranceReveal.kt:35-44）══════════════
 * REVEAL_STAGGER_MS = 140 / FIRST_FRAME_DEFER_MS = 32；delay = 32 + order * 140。
 * 时长 .84s 与位移 18px 由 style.css 的 .card-enter 提供。
 * ⚠️ 绝不写 animation-fill-mode: both —— forwards 会锁死末帧，使 applyParallax
 *    每帧写入的级联位移 / 缩放 / 透明度全部失效。 */
const REVEAL_STAGGER_MS = 140, FIRST_FRAME_DEFER_MS = 32;
function staggerEnter(scope) {
  if (!scope) return;
  $$('.card-enter', scope).forEach((el, order) => {
    el.style.animationDelay = (FIRST_FRAME_DEFER_MS + order * REVEAL_STAGGER_MS) + 'ms';
  });
}

/* ══════════════ 覆盖层 scrim 逐帧透明度（MainActivity.kt:696-699）══════════════
 * alpha = (scrim * scrim) * 0.22f —— 纯黑平铺、二次曲线淡入、上限 0.22。
 * to=1 展开（550ms，对齐覆盖层入场）/ to=0 收起（450ms，对齐出场）。
 * style.css 的 .overlay-bg 已给 opacity:.22 静止态兜底。 */
const SCRIM_MAX_ALPHA = 0.22;
function animateScrim(ov, to) {
  const bg = ov && ov.querySelector('.overlay-bg');
  if (!bg) return;
  if (ov._scrimRaf) { cancelAnimationFrame(ov._scrimRaf); ov._scrimRaf = null; }
  const dur = to ? 550 : 450;
  const from = parseFloat(bg.style.opacity);
  const start = Number.isFinite(from) ? from : (to ? 0 : SCRIM_MAX_ALPHA);
  const t0 = performance.now();
  const step = now => {
    const k = Math.min(1, (now - t0) / dur);
    const p = start + ((to ? 1 : 0) - start) * k;   // 0..1 进度
    bg.style.opacity = (p * p * SCRIM_MAX_ALPHA).toFixed(4);
    if (k < 1) ov._scrimRaf = requestAnimationFrame(step);
    else { ov._scrimRaf = null; bg.style.opacity = ((to ? 1 : 0) * SCRIM_MAX_ALPHA).toFixed(4); }
  };
  ov._scrimRaf = requestAnimationFrame(step);
}

/* ══════════════ 屏幕构建/更新调度 ══════════════ */
const renderedOnce = new Set();

function renderScreen(i, reenter = false) {
  if (!reenter && renderedOnce.has(i)) return;
  const root = $(`#screen-${i}`);
  if (renderedOnce.has(i)) { updateScreen(i); return; }
  renderedOnce.add(i);

  switch (i) {
    case 0: root.innerHTML = screenDashboard(); bindDashboard(root); enableGridReorder('#metric-grid', '.cyber-card'); break;
    case 1: root.innerHTML = screenCpu(); bindCpu(root); break;
    case 2: root.innerHTML = screenGpu(); break;
    case 3: root.innerHTML = screenMemory(); break;
    case 4: root.innerHTML = screenBattery(); bindBatteryControls(root); break;
    case 5: root.innerHTML = screenNetwork(); break;
    case 6: root.innerHTML = screenGps(); break;
    case 7: root.innerHTML = screenSensors(); bindSensors(root); break;
    case 8: root.innerHTML = screenDevice(); bindDevice(root); break;
  }
  staggerEnter(root);        /* EntranceReveal 错峰：32 + order * 140 ms */
  updateScreen(i);
}

function updateScreen(i) {
  switch (i) {
    case 0: updateDashboard(); break;
    case 1: updateCpu(false); break;
    case 2: updateGpu(); break;
    case 3: updateMemory(); break;
    case 4: updateBattery(); break;
    case 5: updateNetwork(); break;
    case 6: updateGps(); break;
    case 7: break;
    case 8: break;
  }
}

/* ============================================================
 * Tab 0 — 概览 DashboardScreen
 * ============================================================ */
let dashOrder = ['cpu_temp', 'mem_usage', 'battery_level', 'gpu_load'];

function metricMeta(id) {
  return {
    cpu_temp:      { label: t('dashboard_metric_cpu_temp') },
    mem_usage:     { label: t('dashboard_metric_mem_usage') },
    battery_level: { label: t('dashboard_metric_battery_level') },
    gpu_load:      { label: t('dashboard_metric_gpu_load') },
  }[id];
}

function screenDashboard() {
  const grid = dashOrder.map(id => {
    const m = metricMeta(id); if (!m) return '';
    let sub = '';
    if (id === 'mem_usage') sub = `<div class="metric-subtitle" id="dash-mem-sub" style="margin-top:2px"></div>
      <div class="sub-block"><span class="sub-label">SWAP / ZRAM</span> <span class="sub-value" id="dash-swap">--</span>
      <div class="progress-track" style="margin-top:6px"><div class="progress-fill cyan" id="dash-swap-bar"></div></div>
      <div class="sub-block" style="margin-top:6px"><span class="sub-value" id="dash-swap-total" style="font-size:11px;color:var(--text-secondary)"></span></div></div>`;
    if (id === 'battery_level') sub = `<div class="metric-subtitle" id="dash-bat-sub" style="margin-top:2px"></div>
      <div class="sub-block"><span class="sub-label">${t('dashboard_metric_battery_temp')}</span> <span class="sub-value" id="dash-bat-temp">--</span></div>`;
    return `<div class="cyber-card metric-card card-enter" data-metric="${id}">
      <div class="metric-label">${m.label}</div>
      <div class="metric-value" id="dash-${id}">--</div>
      <div class="progress-track"><div class="progress-fill" id="dash-bar-${id}"></div></div>
      ${sub}
    </div>`;
  }).join('');

  /* T6 —— DashboardScreen.kt:219-250 QuickLinkCard：
       height(56.dp) + RoundedCornerShape(20.dp) + Row(padding 14.dp)
       → Box.size(32.dp).background(CyberMuted, RoundedCornerShape(8.dp)) 内放 Icon.size(16.dp)
       → Spacer(12.dp) → Column[title 13.sp SemiBold, subtitle 11.sp]
     此前是「图标+标题一行 / 描述另起一行」的两行结构；现改为单行 Row。 */
  const quick = QuickCards.map(q => `
    <div class="quick-card card-enter" data-quick="${q.id}" tabindex="0">
      <div class="quick-icon" style="color:${q.color}">${iconSvg(q.iconKey, 16)}</div>
      <div class="quick-text">
        <div class="quick-title">${q.title}</div>
        <div class="quick-desc">${q.id === 'mem' ? `<span id="qk-mem-desc">--</span>` : q.desc}</div>
      </div>
    </div>`).join('');

  /* InfoCard subtitle: joinNonBlank("  ·  ", "$uptimePrefix $uptimeStr", "$deepSleepPrefix $deepSleepTimeStr") */
  return `
    ${infoCard('home', `${Device.arch} · ${Device.coreCount}${t('dashboard_core_suffix')}`, `<span id="dash-uptime">${t('dashboard_uptime_prefix')} ${Fmt.uptime()}</span>  ·  ${t('dashboard_deep_sleep_prefix')} ${Fmt.uptimeStr(Device.deepSleepSeconds)}`)}
    <div id="health-strip"></div>
    <div class="metric-grid" id="metric-grid">${grid}</div>
    <div class="section-title" style="margin-top:16px">${t('dashboard_quick_access')}</div>
    <div id="quick-grid">${quick}</div>`;
}

function updateDashboard() {
  const Bt = Sim.battery;

  const up = $('#dash-uptime'); up && (up.textContent = `${t('dashboard_uptime_prefix')} ${Fmt.uptime()}`);

  /* mem 快速卡动态 desc："$memUsed / $memTotal"（DashboardScreen.kt:466 同源） */
  const qmd = $('#qk-mem-desc');
  qmd && (qmd.textContent = `${Fmt.mbMB(Sim.memory.usedMB)} / ${Fmt.mbMB(Device.memTotalMB)}`);

  const cpuT = Sim.cpu.temp.last();
  $('#dash-cpu_temp').textContent = Fmt.temp(cpuT);
  /* mem_usage 卡: value = formatBytes(已用), subtitle = "/ $memTotal" */
  $('#dash-mem_usage').textContent = Fmt.mbMB(Sim.memory.usedMB);
  const dms = $('#dash-mem-sub'); dms && (dms.textContent = `/ ${Fmt.mbMB(Device.memTotalMB)}`);
  $('#dash-battery_level').textContent = Bt.level + '%';
  /* 电池卡 subtitle: 充电中 / 已连接 · 未充 / 放电中 */
  const dbs = $('#dash-bat-sub');
  dbs && (dbs.textContent = Bt.charging ? t('common_charging') : (Bt.plugged ? t('common_connected_not_charging') : t('common_discharging')));
  $('#dash-gpu_load').textContent = Sim.overview.gpuLoad.last().toFixed(0) + '%';

  const setBar = (id, pct, cls = '') => {
    const b = $(id); if (!b) return;
    b.style.width = clamp(pct, 0, 100) + '%';
    b.className = 'progress-fill ' + cls;
  };
  const memPct = Math.round(Sim.memory.usedMB / Device.memTotalMB * 100);
  setBar('#dash-bar-cpu_temp', cpuT / 80 * 100, cpuT > 60 ? 'red' : cpuT > 48 ? 'amber' : 'cyan');
  setBar('#dash-bar-mem_usage', memPct, memPct > 85 ? 'amber' : '');
  setBar('#dash-bar-battery_level', Bt.level, Bt.level <= 15 ? 'red' : Bt.level <= 30 ? 'amber' : 'green');
  setBar('#dash-bar-gpu_load', Sim.overview.gpuLoad.last());

  /* SWAP / ZRAM 子块: "$szUsed 已使用" + 尾行 "总: $total" */
  const sw = $('#dash-swap');
  sw && (sw.textContent = t('web_sim_swap_in_use', Fmt.mbMB(Sim.memory.swapUsedMB)));
  const swt = $('#dash-swap-total');
  swt && (swt.textContent = `${t('export_total')} ${Fmt.mbMB(Sim.memory.swapTotalMB)}`);
  const swb = $('#dash-swap-bar'); swb && (swb.style.width = (Sim.memory.swapUsedMB / Sim.memory.swapTotalMB * 100) + '%');

  const tv = $('#dash-bat-temp');
  tv && (tv.textContent = Fmt.temp(Bt.tempC.last()));
  const batCard = $('[data-metric="battery_level"]');
  batCard && batCard.classList.toggle('titanium', Bt.tempC.last() > 40 && Bt.tempC.last() <= 44);
  batCard && batCard.classList.toggle('deep-red', Bt.tempC.last() > 44);

  /* HealthTracker 数据源健康条: ERROR 优先 "X 个数据源异常", 仅 WARN 时 "X 个数据源警告" */
  const strip = $('#health-strip');
  const bad = Sim.sources.filter(s => s.state !== 'ok');
  strip.classList.toggle('show', bad.length > 0);
  if (bad.length > 0) {
    const errCount = bad.filter(s => s.state === 'error').length;
    const msg = errCount > 0 ? t('dashboard_source_error', errCount) : t('dashboard_source_warning', bad.length);
    strip.innerHTML = `<span style="font-size:11px;color:var(--warning-neon)">⚠ ${msg}</span>` +
      Sim.sources.map(s => `<span class="src-dot ${s.state}">${s.id}</span>`).join('');
  }
}

/* ── 网格内拖拽重排（LazyGrid reorder 的网页等价） ── */
function enableGridReorder(gridSel, itemSel) {
  const grid = $(gridSel); if (!grid) return;
  let srcEl = null;
  $$(':scope > ' + itemSel, grid).forEach(el => {
    el.draggable = true;
    el.addEventListener('dragstart', () => { srcEl = el; requestAnimationFrame(() => el.classList.add('dragging')); });
    el.addEventListener('dragend', () => { el.classList.remove('dragging'); $$('.grid-reorder-hint', grid).forEach(x => x.classList.remove('grid-reorder-hint')); srcEl = null; });
    el.addEventListener('dragover', e => {
      e.preventDefault();
      if (!srcEl || srcEl === el) return;
      $$('.grid-reorder-hint', grid).forEach(x => x.classList.remove('grid-reorder-hint'));
      el.classList.add('grid-reorder-hint');
    });
    el.addEventListener('drop', e => {
      e.preventDefault();
      el.classList.remove('grid-reorder-hint');
      if (!srcEl || srcEl === el) return;
      const rb = el.getBoundingClientRect(), rs = srcEl.getBoundingClientRect();
      const before = gridSel.includes('metric')
        ? e.clientY - rb.top < rb.height / 2
        : e.clientX - rb.left < rb.width / 2;
      grid.insertBefore(srcEl, before ? el : el.nextSibling);
    });
  });
}

function bindDashboard(root) {
  $$('.quick-card', root).forEach(card => {
    card.addEventListener('click', () => {
      const q = QuickCards.find(x => x.id === card.dataset.quick);
      q && switchTab(q.tab);
    });
  });
  enableGridReorder('#quick-grid', '.quick-card');
}

/* ============================================================
 * Tab 1 — CPU CpuScreen
 * ============================================================ */
function clusterOf(coreIdx) {
  const c = Device.clusters.find(c => c.cores.includes(coreIdx));
  return c ? c.name : '?';
}

/* ABI 展示名 —— 逐枝照抄 CpuScreen.kt:188-195 when 链。
 * 两个易错点：
 *  ① 源码是 **contains 包含匹配**，不是全等匹配（arm64-v8a 命中 "arm64" 分支）；
 *  ② 源码**只有 5 个分支，else → 原样返回 abi 本身**。
 *     本仓 data.js:74 的 'armeabi' 不属于任何分支 → 应显示 'armeabi' 原串，
 *     不得臆造 'ARM (armeabi)'（此前网页误造该分支，已删除）。 */
function abiLabel(abi) {
  if (abi.includes('arm64')) return 'ARM 64-bit (arm64-v8a)';            // L189
  if (abi.includes('armeabi-v7a')) return 'ARM 32-bit (armeabi-v7a)';    // L190
  if (abi.includes('x86_64')) return 'x86 64-bit';                       // L191
  if (abi.includes('x86')) return 'x86 32-bit';                          // L192
  if (abi.includes('riscv64')) return 'RISC-V 64-bit';                   // L193
  return abi;                                                            // L194 else → 原样
}

function screenCpu() {
  const cs = Device.cstates.filter(c => c.level >= 1);
  /* InfoCard: title=arch, subtitle="$coreCount cores · ARMv8"；"温度状态: 正常" 为卡外独立 Text (16px #7C3AED Medium) */
  return `
    ${infoCard('play_arrow', Device.arch, `${Device.coreCount} cores · ARMv8`)}
    <div style="font-size:16px;font-weight:500;color:#7C3AED"><span id="cpu-status-text">${esc(t('cpu_temp_status_format', t('cpu_temp_status_normal')))}</span></div>
    ${metricCard({ label: 'CPU temperature', valueId: 'cpu-temp-v', canvasId: 'cpu-temp-chart',
      extraHtml: `<div class="metric-subtitle" id="cpu-src-note" style="margin-top:6px"></div>` })}
    <div id="cpu-deep-sleep-block">
      <div style="font-size:16px;font-weight:700;color:var(--text-primary)">${t('cpu_deep_sleep')}</div>
      ${metricCard({ label: t('cpu_title_deep_sleep'), valueId: 'cstate-total', canvasId: 'cstate-chart' })}
      ${cs.map(c => metricCard({
        label: `${esc(c.name)} (C${c.level}·${esc(c.desc)})`,
        valueId: `cstate-${c.level}-${cs.indexOf(c)}`,
        barId: `cstate-bar-${cs.indexOf(c)}`,
        subId: `cstate-sub-${cs.indexOf(c)}`,
        cls: c.level >= 2 ? '' : '',
      })).join('')}
    </div>
    ${metricCard({ label: 'L1 Cache', valueId: 'cache-l1' })}
    ${metricCard({ label: 'L2 Cache', valueId: 'cache-l2' })}
    ${metricCard({ label: 'L3 Cache', valueId: 'cache-l3' })}
    <div style="font-size:16px;font-weight:700;color:var(--text-primary)">${t('cpu_supported_abis')}</div>
    ${Device.abis.map((a, i) => metricCard({ label: i === 0 ? t('cpu_primary_abi') : t('cpu_compatible_abi'), valueId: `abi-${i}`,
        extraHtml: `<div class="metric-subtitle">${esc(a)}</div>` })).join('')}
    <div style="font-size:16px;font-weight:700;color:var(--text-primary)">${t('cpu_core_frequency')}</div>
    <div id="cpu-core-progress">
      ${Device.clusters.flatMap(cl => cl.cores.map(i => ({ i, max: cl.maxMHz }))).map(({ i, max }) => `
        <div class="cyber-card metric-card card-enter" style="margin-bottom:9px">
          <div class="metric-label">Core ${i}</div>
          <div class="metric-value" id="core-freq-${i}">--</div>
          <div class="progress-track" style="margin-top:4px"><div class="progress-fill" id="core-bar-${i}"></div></div>
          <div class="metric-subtitle">${esc(t('web_sim_max_freq', max))}</div>
        </div>`).join('')}
    </div>
    <div class="chip-row">
      <button class="filter-chip selected" id="chip-cluster">Per cluster</button>
      <button class="filter-chip" id="chip-core">Per core</button>
    </div>
    <div id="cpu-cluster-view"></div>`;
}

/* Per cluster / Per core 视图 —— 结构构建与数值填充分离：
 * renderClusterView 只在**切模式**时重建 DOM（否则每 tick 重建会让 canvas 的 400ms reveal
 * 入场动画反复重播）；updateClusterView 每 tick 只改文本 + 重绘图表。
 * 布局逐行照抄 CpuScreen.kt:238-271（Per cluster）与 :256-270 + :294-314（Per core）。 */
function renderClusterView(mode) {
  const box = $('#cpu-cluster-view'); if (!box) return;
  if (mode === 'cluster') {
    /* CpuScreen.kt:240-253 ClusterCard：
     *   name      = coreCluster，空则按 maxFreq 兜底 Prime/Performance/Efficiency
     *   subtitle  = "${group.size} cores · max ${maxFreq} MHz" + (clusterType?.let { " · $it" } ?: "")
     *   frequency = "${group.first().currentFreqKHz / 1000} MHz"   ← **首核**，Int 除法截断
     *   + 卡底 LineChart(freqData)（:288-289） */
    box.innerHTML = Device.clusters.map((cl, gi) => {
      const typeSuffix = cl.coreType ? ` · ${cl.coreType}` : '';   // :243,249
      return `<div class="cluster-card">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><span class="tag purple" style="margin-right:6px">${cl.name}</span>
          <span style="font-size:11.5px;color:var(--text-secondary)">${cl.cores.length} cores · max ${cl.maxMHz} MHz${typeSuffix}</span></div>
          <span id="cluster-freq-${gi}" style="font-family:var(--font-num);font-weight:700;color:var(--neon-purple-bright)">--</span>
        </div>
        <canvas id="cluster-freq-chart-${gi}"></canvas>
      </div>`;
    }).join('');
  } else {
    /* CpuScreen.kt:256-270：外层 Card 标题 = cpu_cluster_card_title → "Cluster ${gi+1} · ${n} cores"
     * 内部 FlowRow 逐个 CoreItem(core) */
    box.innerHTML = Device.clusters.map((cl, gi) => `
      <div class="cyber-card card-enter">
        <div class="section-title">Cluster ${gi + 1} · ${cl.cores.length} cores</div>
        <div class="core-grid">` + cl.cores.map(i => `
          <div class="core-item">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:4px">
              <span class="core-name">CPU ${i}</span>
              <span id="core-badge-${i}" style="font-size:11px;font-family:var(--font-num)"></span>
            </div>
            <div class="core-freq" id="core-freq-p-${i}">--</div>
            <div class="core-name" id="core-type-${i}"></div>
            <div class="core-name" id="core-max-${i}"></div>
          </div>`).join('') + `</div></div>`).join('');
  }
  updateClusterView(mode);
}

function updateClusterView(mode) {
  if (mode === 'cluster') {
    Device.clusters.forEach((cl, gi) => {
      /* C-3 频率：取**簇内首核**，且用截断（源码 `currentFreqKHz / 1000` 是 Kotlin Int 除法）。
       * 注：本模拟数据层 Sim.cpu.freqs 的单位本身就是 **MHz**（Device.cpuFreqMax=3300 亦为 MHz），
       *     所以等价写法是 Math.floor(f) 而非源码字面量的 /1000 —— 见回报中的"存疑"说明。 */
      const f = Sim.cpu.freqs[cl.cores[0]].history.last();
      const el = $(`#cluster-freq-${gi}`);
      el && (el.textContent = `${Math.floor(f)} MHz`);
      /* C-2 簇卡底部 LineChart（CpuScreen.kt:251,289）*/
      const cv = $(`#cluster-freq-chart-${gi}`);
      cv && drawLineChartAnimated(cv, () => Sim.cpu.freqs[cl.cores[0]].history.series(), {});
    });
  } else {
    Device.clusters.forEach(cl => {
      cl.cores.forEach(i => {
        const core = Sim.cpu.cores[i];
        const f = Sim.cpu.freqs[i].history.last();
        const fEl = $(`#core-freq-p-${i}`);
        /* C-5 核心名行右端：!online → "OFF"(11sp 洋红) / 否则 usagePercent 非 NaN → "%.0f%%"(11sp 青) */
        const badge = $(`#core-badge-${i}`);
        if (badge) {
          if (!core.online) {
            badge.textContent = 'OFF';
            badge.style.color = 'var(--neon-magenta)';
          } else if (!Number.isNaN(core.usagePercent)) {
            badge.textContent = `${core.usagePercent.toFixed(0)}%`;
            badge.style.color = 'var(--neon-cyan)';
          } else { badge.textContent = ''; }
        }
        if (fEl) {
          /* :296 freqColor = if (!online) NeonMagenta.copy(.5f) else NeonPurpleBright */
          fEl.textContent = `${Math.floor(f)} MHz`;
          fEl.style.color = core.online ? '' : 'rgba(244,63,94,.5)';
        }
        /* C-4 第三行 = coreType（:308-310 if isNotEmpty），第四行 = cpu_core_max_governor（:311）
         *  "max: %1$d MHz · %2$s"，governor 为空显示 "-"（strings.xml:1019） */
        const tEl = $(`#core-type-${i}`);
        tEl && (tEl.textContent = cl.coreType || '');
        const mEl = $(`#core-max-${i}`);
        mEl && (mEl.textContent = `max: ${cl.maxMHz} MHz · ${Sim.cpu.governor || '-'}`);
      });
    });
  }
}

function updateCpu() {
  const cpuTempLast = Sim.cpu.temp.last();
  const v = $('#cpu-temp-v');
  if (!v) return;
  v.textContent = Fmt.temp(cpuTempLast);
  drawLineChartAnimated($('#cpu-temp-chart'), () => Sim.cpu.temp.series(), {});
  $('#cpu-src-note').textContent = `读取路径: /sys/devices/system/cpu/cpu*/cpufreq (simulated)`;
  const deepPct = Sim.cpu.deepSleep.last();
  $('#cstate-total').textContent = `${deepPct.toFixed(0)}%`;
  drawLineChartAnimated($('#cstate-chart'), () => Sim.cpu.deepSleep.series(), {});
  /* C-1 C-State 明细 —— 占比改为**运行期动态归一**，逐行照抄 CpuScreen.kt:145-160：
   *   totalTime = cStates.sumOf { it.timeUs }
   *   pct       = if (totalTime > 0) (state.timeUs / totalTime * 100f).coerceIn(0f, 100f) else 0f
   * 值 `${pct.toInt()}%`（Int 截断）、进度 progress = pct/100f；
   * 副标题 cpu_c_state_subtitle 只用 latencyUs 与 usage（**进入次数**，绝不能当 timeUs）。 */
  const cs = Device.cstates.filter(c => c.level >= 1);
  const totalTime = cs.reduce((s, c) => s + c.timeUs, 0);
  cs.forEach((c, i) => {
    const pct = totalTime > 0 ? clamp(c.timeUs / totalTime * 100, 0, 100) : 0;
    const val = $(`#cstate-${c.level}-${i}`), sub = $(`#cstate-sub-${i}`), bar = $(`#cstate-bar-${i}`);
    val && (val.textContent = `${pct | 0}%`);
    sub && (sub.textContent = t('cpu_c_state_subtitle', c.latencyUs, c.usage));
    bar && (bar.style.width = pct + '%');
  });
  /* cpu_temp_status_format: "温度状态: %s" — 源码 CpuScreen.kt:106 仅两态：
     --- 时 common_detecting_short="检测中"，否则恒 cpu_temp_status_normal="正常"（全仓无"偏热"文案） */
  $('#cpu-status-text').textContent = t('cpu_temp_status_format', t('cpu_temp_status_normal'));
  /* 支持的 ABI（CpuScreen.kt:196-202）：value = abiLabel(abi)，subtitle = 原始 abi 串
     注：此前网页只在 render 期生成占位 '--'，从未回填 → 值恒为 "--"（连带 bug，一并修） */
  Device.abis.forEach((a, i) => {
    const el = $(`#abi-${i}`);
    el && (el.textContent = abiLabel(a));
  });
  /* 核心行: 值 "%.0f MHz" */
  Sim.cpu.freqs.forEach((f, i) => {
    const el = $(`#core-freq-${i}`), bar = $(`#core-bar-${i}`);
    const fv = Math.round(f.history.last());
    el && (el.textContent = `${fv} MHz`);
    bar && (bar.style.width = clamp(fv / Device.cpuFreqMax * 100, 3, 100) + '%');
  });
  /* 缓存三卡: L1 照抄源码 CpuCache.kt:810 格式 "I:… D:… (大核) · I:… D:… (小核)"（大/小核各一组 I/D） */
  const l1 = $('#cache-l1');
  if (l1) {
    const c = Device.caches;
    l1.textContent = `I:${c.L1Ibig} · D:${c.L1Dbig} (${t('web_sim_big_core')}) · I:${c.L1Ilittle} · D:${c.L1Dlittle} (${t('web_sim_little_core')})`;
    $('#cache-l2').textContent = c.L2;
    $('#cache-l3').textContent = c.L3;
  }
  /* 只更新数值，不重建 DOM（renderClusterView 仅在切模式时重建） */
  updateClusterView(Sim.cpu.perCoreMode);
}

function bindCpu() {
  $('#chip-cluster').addEventListener('click', () => {
    Sim.cpu.perCoreMode = 'cluster';
    $('#chip-cluster').classList.add('selected'); $('#chip-core').classList.remove('selected');
    renderClusterView('cluster');
  });
  $('#chip-core').addEventListener('click', () => {
    Sim.cpu.perCoreMode = 'core';
    $('#chip-core').classList.add('selected'); $('#chip-cluster').classList.remove('selected');
    renderClusterView('core');
  });
}

/* ============================================================
 * Tab 2 — GPU GpuScreen
 * ============================================================ */
function screenGpu() {
  const g = Sim.gpu;
  /* 结构照抄 GpuScreen.kt:78-174，三处容器此前均有误：
   * ① InfoCard（:78-89）：title = model（**只有型号，不带频率**）；
   *    subtitle = FormatUtils.joinNonBlank(" · ", frequency, maxFreq?.let{"/ $it"}, governor)
   *    —— 空/空白项自动跳过，所以**没有 "Governor: " 前缀**；
   *    节流警告是卡**外**独立 Text（:92-99），不塞进副标题。
   * ② Governor / Renderer（:133-151）是两张**独立** MetricCard，各自 if (xxx != null)，
   *    此前网页误做成一张卡内的 2 个 kvRow。
   * ③ Vulkan（:154-174）也是 MetricCard（值 + 副标题），此前误做成 kvRow 列表。 */
  return `
    ${infoCard('gpu', esc(Device.gpuModel), `<span id="gpu-info-sub">--</span>`)}
    <div id="gpu-throttle-warning"></div>
    <div class="cyber-card metric-card card-enter">
      <div class="metric-label">GPU load</div>
      <div class="metric-value" id="gpu-load-v">--</div>
      <div class="progress-track"><div class="progress-fill" id="gpu-load-bar"></div></div>
      <div style="font-size:12px;color:var(--success-neon);margin-top:6px">${t('gpu_effective_utilization')} <span id="gpu-util" style="font-family:var(--font-num)">--</span></div>
      <canvas id="gpu-load-chart"></canvas>
    </div>
    ${metricCard({ label: 'GPU frequency (DVFS)', valueId: 'gpu-freq-v', subId: 'gpu-freq-sub', canvasId: 'gpu-freq-chart' })}
    ${metricCard({ label: 'GPU temperature', valueId: 'gpu-temp-v', canvasId: 'gpu-temp-chart' })}
    ${g.governor ? metricCard({ label: 'Governor', valueId: 'gpu-gov-v', subId: 'gpu-gov-sub' }) : ''}
    ${g.renderer ? metricCard({ label: 'Renderer', valueId: 'gpu-renderer-v' }) : ''}
    ${g.vulkanDriver ? metricCard({ label: 'Vulkan Driver Version', valueId: 'gpu-vulkan-v', subId: 'gpu-vulkan-sub' }) : ''}`;
}

/* Vulkan 版本三段 —— 照抄 GpuScreen.kt:155-160
 * split(".") → size>=4 走带 build 的分支；size==3 走 gpu_vulkan_ver_3parts（strings.xml:1284
 * "主版本 %1$s · 次版本 %2$s · 补丁 %3$s"）；否则原样。
 * 每段经 **严格整型判定**（对齐 Kotlin toIntOrNull()）："290 developer build (build 27)" 非纯整数 → 0。 */
function vulkanDisplayVersion(driver) {
  const parts = String(driver).split('.');
  const intOr0 = s => (s != null && /^\d+$/.test(String(s).trim())) ? parseInt(s, 10) : 0;
  if (parts.length >= 4) {
    return `${parts[0]}.${parts[1]}.${parts[2]} (build ${parts.slice(3).join('.')})`;
  }
  if (parts.length === 3) {
    return t('gpu_vulkan_ver_3parts', intOr0(parts[0]), intOr0(parts[1]), intOr0(parts[2]));
  }
  return String(driver);
}

function updateGpu() {
  const g = Sim.gpu;
  const lv = $('#gpu-load-v'); if (!lv) return;
  lv.textContent = g.realLoad + '%';
  $('#gpu-load-bar').style.width = g.load.last() + '%';
  $('#gpu-util').textContent = g.realLoad + '%';
  drawLineChartAnimated($('#gpu-load-chart'), () => g.load.series(), {});
  /* InfoCard 副标题 = FormatUtils.joinNonBlank(" · ", frequency, maxFreq?.let{"/ $it"}, governor)
     （GpuScreen.kt:78-82）：三段非空才拼，空段自动跳过；**无 "Governor: " 前缀**。 */
  const gis = $('#gpu-info-sub');
  gis && (gis.textContent = [Fmt.mhz(g.freqMHz), `/ ${Fmt.mhz(g.freqMax)}`, g.governor]
    .filter(p => p != null && String(p).trim() !== '').join(' · '));
  $('#gpu-freq-v').textContent = Fmt.mhz(g.freqMHz);
  /* gpu_subtitle_max_normal / gpu_subtitle_max_throttled 逐字 */
  const fs = $('#gpu-freq-sub');
  fs.textContent = g.throttled ? t('gpu_subtitle_max_throttled', Fmt.mhz(g.freqMax)) : t('gpu_subtitle_max_normal', Fmt.mhz(g.freqMax));
  fs.style.color = g.throttled ? 'var(--warning-neon)' : '';
  /* C-6 GPU frequency (DVFS) 卡底部 LineChart（GpuScreen.kt:114-125 gpu.freq.chart） */
  drawLineChartAnimated($('#gpu-freq-chart'), () => g.freqHist.series(), {});
  /* gpu_dvfs_throttling_warning 逐字 */
  $('#gpu-throttle-warning').innerHTML = g.throttled
    ? `<span class="tag warn">${esc(t('gpu_dvfs_throttling_warning'))}</span>` : '';
  $('#gpu-temp-v').textContent = Fmt.temp(g.temp.last());
  drawLineChartAnimated($('#gpu-temp-chart'), () => g.temp.series(), {});
  /* Governor 卡（GpuScreen.kt:133-141）：value = governor，subtitle = availableGovernors */
  const gv = $('#gpu-gov-v');
  if (gv) {
    gv.textContent = g.governor;
    const gs = $('#gpu-gov-sub');
    gs && (gs.textContent = g.availableGovernors || '');
  }
  /* Renderer 卡（GpuScreen.kt:144-151）：value = renderer，无副标题 */
  const rv = $('#gpu-renderer-v');
  rv && (rv.textContent = g.renderer);
  /* Vulkan 卡（GpuScreen.kt:154-174）：value = 三段版本串，subtitle = 集成 / 独立 */
  const vv = $('#gpu-vulkan-v');
  if (vv) {
    vv.textContent = vulkanDisplayVersion(g.vulkanDriver);
    const vs = $('#gpu-vulkan-sub');
    vs && (vs.textContent = g.vulkanIntegrated === 'integrated' ? t('gpu_integrated') : t('gpu_discrete'));
  }
}

/* ============================================================
 * Tab 3 — 内存 MemoryScreen
 * ============================================================ */
function screenMemory() {
  return `
    ${metricCard({ label: t('memory_title'), valueId: 'mem-main-v', barId: 'mem-main-bar', subId: 'mem-main-sub' })}
    ${metricCard({ label: 'SWAP / ZRAM', valueId: 'mem-swap', barId: 'mem-swap-bar', subId: 'mem-swap-sub' })}
    ${metricCard({ label: 'ZRAM used', valueId: 'mem-zram', canvasId: 'zram-chart' })}
    <div class="cyber-card card-enter">
      <div class="section-title">${t('memory_distribution')}</div>
      <div class="mem-dist-total" id="mem-dist-total"></div>
      <div class="mem-dist-bar" id="mem-dist-bar"></div>
      <!-- 图例纵向逐行（MemoryDistributionCard.kt:134 一行一类） -->
      <div class="mem-legend" id="mem-dist-legend" style="display:block"></div>
    </div>
    ${metricCard({ label: 'Memory available', valueId: 'mem-avail-v', canvasId: 'mem-avail-chart' })}
    ${metricCard({ label: 'Memory used', valueId: 'mem-used-v', canvasId: 'mem-used-chart' })}
    ${metricCard({ label: 'Top processes', valueId: 'mem-procs', cls: 'mem-procs-card' })}`;
}

function updateMemory() {
  const m = Sim.memory;
  if (!$('#mem-main-v')) return;
  const pct = Math.round(m.usedMB / Device.memTotalMB * 100);
  $('#mem-main-v').textContent = Fmt.mbMB(m.availMB) + t('memory_available_suffix');
  $('#mem-main-bar').style.width = pct + '%';
  $('#mem-main-sub').textContent = t('web_sim_mem_used_of_total', Fmt.mbMB(m.usedMB), Fmt.mbMB(m.totalMB));

  /* D-1 SWAP / ZRAM 独立顶层卡（MemoryScreen.kt:63-72） */
  const swapPct = m.swapTotalMB > 0 ? Math.round(m.swapUsedMB / m.swapTotalMB * 100) : 0;
  $('#mem-swap').textContent = `${Fmt.mbMB(m.swapUsedMB)} in use`;
  $('#mem-swap-bar').style.width = clamp(swapPct, 0, 100) + '%';
  $('#mem-swap-sub').textContent = `${t('export_total')} ${Fmt.mbMB(m.swapTotalMB)}`;   // :69 memory_total_prefix

  /* D-1 ZRAM used 独立顶层卡（:73-79），含 LineChart */
  $('#mem-zram').textContent = Fmt.mbMB(m.zramUsedMB);
  const zc = $('#zram-chart');
  zc && drawLineChartAnimated(zc, () => Array.from({ length: 30 }, (_, i) =>
    900 + Math.sin(i / 5) * 260 + (Sim.tickCount % 37) * 12), {});

  /* D-4 总量概览行（MemoryDistributionCard.kt:99-108）：22sp 粗体等宽紫亮 */
  const dt = $('#mem-dist-total');
  dt && (dt.textContent = `${Fmt.mbMB(m.usedMB)} / ${Fmt.mbMB(m.totalMB)}`);

  $('#mem-dist-bar').innerHTML = Object.entries(m.dist).map(([k, p]) =>
    `<div class="mem-seg" style="width:${p * 100}%;background:${MemLegend.find(l => l.key === k)?.css}"></div>`).join('');
  /* 图例 —— 纵向逐行：名称 → 大小 → 占比(1位小数, 右对齐) */
  $('#mem-dist-legend').innerHTML = MemLegend.map(l => {
    const p = m.dist[l.key] || 0;
    const sizeBytes = m.totalMB * p * 1048576;
    return `<div style="display:flex;align-items:center;width:100%;padding:3px 0">`
      + `<span class="legend-dot" style="background:${l.css};flex:none"></span>`
      + `<span style="width:8px;flex:none"></span>`
      + `<span style="flex:none;width:32px;font-size:13px;color:var(--text-primary)">${l.label}</span>`
      + `<span style="width:6px;flex:none"></span>`
      + `<span style="flex:1;min-width:0;font-size:12px;font-family:var(--font-num);color:var(--text-secondary)">${Fmt.mb(sizeBytes)}</span>`
      + `<span style="flex:none;font-size:12px;font-family:var(--font-num);color:var(--text-secondary);text-align:right">${((p || 0) * 100).toFixed(1)}%</span>`
      + `</div>`;
  }).join('');

  /* D-5 Top processes → MetricCard（MemoryScreen.kt:106-111），多行等宽紫亮 */
  const mp = $('#mem-procs');
  mp && (mp.textContent = Sim.memory.topProcs.join('\n'));

  $('#mem-avail-v').textContent = Fmt.mbMB(m.availMB);
  drawLineChartAnimated($('#mem-avail-chart'), () => m.availableHist.series(), {});
  $('#mem-used-v').textContent = Fmt.mbMB(m.usedMB);
  drawLineChartAnimated($('#mem-used-chart'), () => m.usedHist.series(), {});
}

/* ============================================================
 * Tab 4 — 电池 BatteryScreen（BATTERY_CARD_IDS 19 卡有序）
 * ============================================================ */
/* ── 卡片可见性 gate（BatteryScreen.kt isCardVisible 一一对应） ── */
function isBatteryCardVisible(id) {
  const B = Sim.battery;
  const healthPercent = B.nowCap && B.designCap ? Math.floor(B.nowCap * 100 / B.designCap) : B.sohPct;
  switch (id) {
    case 'current_multiplier': return true;
    case 'power_save': return B.powerSave === true;
    case 'soh': return healthPercent != null;
    case 'design_capacity': return B.designCap != null;
    case 'rated_capacity': return B.nowCap != null;
    case 'cycle_count': return true;
    case 'protocol': return !!B.protocol;
    case 'power_source': return !!B.powerSource;
    case 'wattage': return B.wattage != null && B.wattage > 0;
      /* 源码 gate 仅 wattageNow != null；> 0 为模拟数据自卫条件，当前数据恒非空 */
    case 'internal_r': return B.internalROhms != null && B.internalROhms > 0;
    case 'level_chart': return B.level >= 0;
    case 'power': return powerWatts() > 0;
    case 'current': return B.currentMA !== 0;
    case 'realtime_power': return B.voltageMV > 0 && B.currentMA !== 0;
    case 'voltage': return B.voltageMV > 0;
    case 'charge_counter': return B.chargeCounterUAh > 0;
      /* 源码 gate 仅 counter != null；> 0 为模拟数据自卫条件，当前数据恒非空 */
    case 'temperature': return !isNaN(B.tempC.last());
    case 'health_status': return true;
    case 'dual_cell': return true;
    default: return false;
  }
}

function powerWatts() {
  const B = Sim.battery;
  return (B.voltageMV / 1000) * Math.abs(B.currentMA) / 1000;
}

function screenBattery() {
  const B = Sim.battery;
  const estHealth = estHealthFromCycles(B.cycleCount);
  const meta = {
    current_multiplier: () => `
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div style="flex:1;padding-right:12px">
          <div class="metric-label" style="font-size:15px;font-weight:700">${t('battery_current_multiplier_title')}</div>
          <div class="metric-subtitle" style="margin-top:2px">${t('battery_current_multiplier_subtitle')}</div>
        </div>
        <div style="display:flex;align-items:center;gap:10px">
          <span id="bat-mult" style="font-size:16px;font-weight:700;color:var(--neon-purple-bright)">×1.0</span>
          <div class="switch ${B.multiplierEnabled ? 'on' : ''}" id="bat-mult-sw">${joySwitchHtml()}</div>
        </div>
      </div>
      <div class="fancy-slider${B.multiplierEnabled ? '' : ' is-disabled'}" id="bat-mult-slider" data-min="0" data-max="3" data-step="0.05" data-value="0" data-fmt="tier" style="margin-top:8px">
        <div class="rail"></div><div class="fill"></div><div class="thumb"></div>
      </div>
      <div style="display:flex;align-items:center;gap:2px;margin-top:6px">
        ${[1, 10, 100, 1000].map(v => `<button class="filter-chip" data-mult-tier="${v}"${B.multiplierEnabled ? '' : ' disabled'} style="flex:1;font-size:12px">${v.toFixed(1)}×</button>`).join('')}
        <button class="filter-chip" data-mult-tier="reset"${B.multiplierEnabled ? '' : ' disabled'} style="font-size:12px;color:var(--neon-purple-bright)">${t('battery_current_multiplier_reset')}</button>
      </div>`,
    power_save: () => `<div class="metric-label">${t('battery_card_power_save_mode')}</div><div class="metric-value" style="color:#FFA726">🔋 ON</div>
      <div class="metric-subtitle">System performance throttled — refresh rate auto-reduced</div>`,
    soh: () => `<div class="metric-label">Battery health</div><div class="metric-value" id="bat-soh">--</div>
      <div class="metric-subtitle" id="bat-soh-sub"></div>`,
    design_capacity: () => `<div class="metric-label">${t('battery_design_capacity_title')}</div><div class="metric-value">${B.designCap} mAh</div>
      <div class="metric-subtitle">${B.capSource}</div>`,
    rated_capacity: () => `<div class="metric-label">${t('battery_rated_capacity_title')}</div><div class="metric-value">${B.nowCap} mAh</div>
      <div class="metric-subtitle">${t('web_sim_capacity_estimate_pct', Math.floor(B.chargeCounterUAh / 1000 / B.nowCap * 100) + '%')}  ·  ${B.capSource}</div>`,
    cycle_count: () => B.cycleCount != null
      ? `<div class="metric-label">Cycle count</div><div class="metric-value">${B.cycleCount} cycles</div>
         <div class="metric-subtitle">${t('web_sim_est_health_pct', estHealth + '%')}  |  ${B.cycleSource}</div>`
      /* zh-rCN 缺键回落英文: battery_cycle_not_detected / battery_cycle_no_data */
      : `<div class="metric-label">Cycle count</div><div class="metric-value" style="color:#FFA726">Not detected</div>
         <div class="metric-subtitle">This device does not expose cycle count data</div>`,
    protocol: () => `<div class="metric-label">Charging protocol</div><div class="metric-value success">${B.protocol}</div>`,
    power_source: () => `<div class="metric-label">${t('battery_card_power_source')}</div><div class="metric-value" id="bat-psrc">--</div>`,
    wattage: () => `<div class="metric-label">${t('battery_card_real_wattage')}</div><div class="metric-value" id="bat-watt">--</div>`,
    internal_r: () => `<div class="metric-label">Internal resistance</div><div class="metric-value">${B.internalROhms} mΩ</div>
      <div class="metric-subtitle">${B.internalROhms < 100 ? t('battery_resistance_excellent') : B.internalROhms < 200 ? t('battery_resistance_good') : t('battery_resistance_average')}</div>`,
    level_chart: () => `<div class="metric-label">Battery level</div><div class="metric-value" id="bat-lv">--%</div><canvas id="bat-lv-chart"></canvas>`,
    power: () => `<div class="metric-label" id="bat-pow-label">${t('battery_power')}</div><div class="metric-value" id="bat-pow-v">--</div><canvas id="bat-pow-chart"></canvas>`,
    current: () => `<div class="metric-label" id="bat-cur-label">${t('battery_current')}</div><div class="metric-value" id="bat-cur-v">--</div>
      <div class="metric-subtitle" id="bat-cur-sub"></div>`,
    realtime_power: () => `<div class="metric-label">Real-time power</div><div class="metric-value" id="bat-rp">--</div>
      <div class="metric-subtitle" id="bat-rp-sub"></div>`,
    voltage: () => `<div class="metric-label">Battery voltage</div><div class="metric-value" id="bat-volt">--</div>`,
    charge_counter: () => `<div class="metric-label">Charge counter</div><div class="metric-value" id="bat-cc">--</div>`,
    temperature: () => `<div class="metric-label">Battery temperature</div><div class="metric-value" id="bat-tv">--</div><canvas id="bat-tv-chart"></canvas>`,
    health_status: () => `<div class="metric-label">Health</div><div class="metric-value success" id="bat-health">--</div>`,
    dual_cell: () => `
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div style="padding-right:12px">
          <div style="font-size:15px;font-weight:700">${t('battery_dual_cell_title')}</div>
          <div class="metric-subtitle" style="margin-top:2px">${t('battery_dual_cell_subtitle')}</div>
        </div>
        <div class="switch ${B.dualCell ? 'on' : ''}" id="bat-dual-sw">${joySwitchHtml()}</div>
      </div>`,
  };
  /* 19 张卡全部建出，可见性交给 updateBattery 每 tick 按 isBatteryCardVisible 复算（用 display 门控），
     否则 renderScreen 的 renderedOnce 短路会让 gate 只求值一次、后面永远不更新 */
  return `
    ${infoCard('favorite', `<span id="bat-info-title">--</span>`, `<span id="bat-info-sub">${B.tech}</span>`)}
    ${BatteryCardIds.map(id => `<div class="cyber-card card-enter" data-bcard="${id}">${meta[id]()}</div>`).join('')}`;
}

/* 基于循环次数的健康度预估（业界通用: 500次≈80%） */
function estHealthFromCycles(c) {
  if (c == null) return 0;
  if (c === 0) return 100;
  if (c <= 200) return clamp(100 - Math.floor(c / 10), 85, 100);
  if (c <= 500) return clamp(100 - Math.floor(c / 20), 75, 90);
  if (c <= 1000) return Math.max(80 - Math.floor((c - 500) / 25), 60);
  return Math.max(60 - Math.floor((c - 1000) / 50), 30);
}

function updateBattery() {
  const B = Sim.battery;
  const titleEl = $('#bat-info-title'); if (!titleEl) return;
  /* 所有 #bat-* 写入统一走空值保护：卡片一旦被 gate 判定为不可见，其 DOM 节点即不存在，
     直接 .textContent 会抛 TypeError 打断整个 tick */
  const setTxt = (sel, txt) => { const el = $(sel); if (el) el.textContent = txt; };
  /* 可见性 gate 每次刷新重算（源码 BatteryScreen.kt 是 if(...) 在每次 recomposition 求值；
     此前只在建屏时算一次，wattage/current/realtime_power/charge_counter 会永久冻结） */
  $$('[data-bcard]').forEach(card => {
    card.style.display = isBatteryCardVisible(card.dataset.bcard) ? '' : 'none';
  });
  /* statusText: isPlugged&&isCharging→充电中 / isPlugged→已连接 · 未充 / else 放电中 + " · ${level}%" */
  const status = B.charging ? t('battery_status_charging') : (B.plugged ? t('battery_status_not_charging') : t('battery_status_discharging'));
  titleEl.textContent = `${status} · ${B.level}%`;
  /* techText = joinNonBlank("  |  ", technology, isPlugged? chargerFromPlugText, chargerType!=chargerFromPlug? chargerType)
     gate 用 isPlugged（BatteryScreen.kt:212-216），chargerFromPlug 与 chargerType 相同时去重只拼一段 */
  const plugSegs = [];
  if (B.plugged) {
    plugSegs.push(t('charger_ac'));
    if (B.chargerType && B.chargerType !== 'charger_ac') plugSegs.push(B.chargerType);
  }
  setTxt('#bat-info-sub', [B.tech, ...plugSegs].filter(Boolean).join('  |  '));

  const mult = B.multiplierEnabled ? B.currentMultiplier : 1.0;
  setTxt('#bat-mult', `×${mult.toFixed(1)}`);
  /* 走 _joySync 而非直接 toggle('on')：否则外部改 dualCell 时 --p 不同步，滑块停在旧位置 */
  const dsw = $('#bat-dual-sw'); dsw && dsw._joySync && dsw._joySync();
  setTxt('#bat-psrc', { ps_ac: t('ps_ac'), ps_usb: t('ps_usb'), ps_wireless: t('ps_wireless'), ps_external: t('ps_external'), ps_battery: t('ps_battery') }[B.powerSource] || B.powerSource);
  setTxt('#bat-watt', B.wattage.toFixed(2) + ' W');

  const healthPercent = Math.floor(B.nowCap * 100 / B.designCap);
  const soh = $('#bat-soh');
  if (soh) {
    soh.textContent = `${healthPercent}%`;
    soh.style.color = healthPercent >= 90 ? 'var(--neon-purple-bright)' : healthPercent >= 75 ? '#FFA726' : '#EF5350';
  }
  setTxt('#bat-soh-sub', `${B.nowCap} / ${B.designCap} mAh  ·  Capacity Ratio`);

  setTxt('#bat-lv', B.level + '%');
  drawLineChartAnimated($('#bat-lv-chart'), () => B.levelHist.series(), {});

  const powW = powerWatts();
  if (powW > 0) {
    setTxt('#bat-pow-v', powW.toFixed(1) + ' W');
    setTxt('#bat-pow-label', B.charging ? 'Charging power' : 'Discharge power');
    drawLineChartAnimated($('#bat-pow-chart'), () => Array.from({ length: 30 }, (_, i) =>
      powW * (1 + Math.sin(i / 4) * .18)), {});
  }

  setTxt('#bat-cur-v', `${Math.abs(B.currentMA)} mA`);
  const curV = $('#bat-cur-v'); if (curV) curV.className = 'metric-value';
  setTxt('#bat-cur-label', B.charging ? 'Charging current' : 'Discharge current');
  /* current 卡副标题 = listOfNotNull(currentSource, normalizedInfo).joinToString("  ·  ")（BatteryScreen.kt:392）；
     normalized 键 battery_current_normalized_format 仅 values/ 有 → 英文 "Normalized: %d mA" */
  setTxt('#bat-cur-sub', `${B.currentSource || 'power_supply'}  ·  Normalized: ${Math.abs(B.currentMA)} mA`);

  setTxt('#bat-rp', powW.toFixed(2) + ' W');
  setTxt('#bat-rp-sub', `${(B.voltageMV / 1000).toFixed(3)}V × ${Math.abs(B.currentMA).toFixed(0)}mA = ${(powW * 1000).toFixed(0)}mW`);
  setTxt('#bat-volt', (B.voltageMV / 1000).toFixed(3) + ' V');
  setTxt('#bat-cc', (B.chargeCounterUAh / 1000).toFixed(0) + ' mAh');
  setTxt('#bat-tv', Fmt.temp(B.tempC.last()));
  drawLineChartAnimated($('#bat-tv-chart'), () => B.tempC.series(), {});
  /* health_status: battery_health_good → "良好" */
  setTxt('#bat-health', { battery_health_good: t('battery_health_good'), battery_health_overheat: t('battery_health_overheat'), battery_health_dead: t('battery_health_dead'), battery_health_overvoltage: t('battery_health_overvoltage'), battery_health_failure: t('battery_health_failure'), battery_health_cold: t('battery_health_cold'), battery_health_unknown: t('battery_health_unknown') }[B.healthStatus] || B.healthStatus);

  const ic = $('[data-bcard="temperature"]');
  ic && ic.classList.toggle('titanium', B.tempC.last() > 40 && B.tempC.last() <= 44);
  ic && ic.classList.toggle('deep-red', B.tempC.last() > 44);
}

/* 电池电流校准滑条 + 挡位/开关绑定（电池卡位于主 Pager，非 overlay，需独立绑定） */
const MULT_TIERS = [1.0, 10.0, 100.0, 1000.0];
function bindBatteryControls(root) {
  /* T8 —— 两枚开关都改走 JoySwitch（拖拽 + 点击语义统一） */
  const sw = $('#bat-mult-sw', root);
  sw && makeSwitch(sw,
    () => Sim.battery.multiplierEnabled,
    v => { Sim.battery.multiplierEnabled = v; syncMultiplierUI(); toast(`${t('battery_current_multiplier_title')}：${v ? t('web_sim_on') : t('web_sim_off')}`); });
  const dsw = $('#bat-dual-sw', root);
  dsw && makeSwitch(dsw,
    () => Sim.battery.dualCell,
    v => { Sim.battery.dualCell = v; toast(`${t('battery_dual_cell_title')}：${v ? t('web_sim_on') : t('web_sim_off')}`); });
  $$('.filter-chip[data-mult-tier]', root).forEach(btn => btn.addEventListener('click', () => {
    /* 源码 BatteryScreen.kt:787/801 —— TextButton(onClick=…, enabled = enabled)：
       总开关关闭时按钮 disabled，点击根本不触发，也**不会**顺带把开关打开。
       此前网页会无条件 multiplierEnabled = true，等于点预设就强制开机，与源码相反。 */
    if (!Sim.battery.multiplierEnabled) return;
    const t = btn.dataset.multTier;
    Sim.battery.currentMultiplier = t === 'reset' ? 1.0 : parseFloat(t);
    syncMultiplierUI();
  }));
  /* 4 挡滑条: 自由滑动 → 松手 snap 最近挡位 */
  const sl = $('#bat-mult-slider', root); if (!sl || sl.dataset.init) return;
  sl.dataset.init = '1';
  const min = +sl.dataset.min, max = +sl.dataset.max;
  /* 头部 ×N 标签：v 是 0..3 的连续挡位坐标，显示其最近离散挡位（1.0/10.0/100.0/1000.0×）。
     此前 paint() 只动 fill/thumb，拖动中读数不动，要等 pointerup 的 syncMultiplierUI 才跳一次 */
  function paintMultLabel(v) {
    const head = $('#bat-mult', root);
    if (head) head.textContent = '×' + MULT_TIERS[Math.round(clamp(v, 0, MULT_TIERS.length - 1))].toFixed(1);
  }
  function paint(v) {
    const p = clamp((v - min) / (max - min), 0, 1);
    $('.fill', sl).style.width = p * 100 + '%';
    $('.thumb', sl).style.left = p * 100 + '%';
    /* rawValue = 拖动中的连续坐标(0..3，step .05)；value 留给 syncMultiplierUI 的整数挡位索引(0..3)。
       两者此前都写 dataset.value，互相覆盖，读出来的数分不清是哪个含义 */
    sl.dataset.rawValue = v;
    paintMultLabel(v);
  }
  function fromClientX(cx) {
    const r = sl.getBoundingClientRect();
    const v = clamp(min + clamp((cx - r.left) / r.width, 0, 1) * (max - min), min, max);
    paint(v);
    return v;
  }
  let dragging = false;
  /* 源码 BatteryScreen.kt:753-768 —— FancySlider(…, enabled = enabled)：
     总开关关闭时滑条 disabled，拖不动也不会顺带开机 */
  sl.addEventListener('pointerdown', e => {
    if (!Sim.battery.multiplierEnabled) return;
    dragging = true; sl.setPointerCapture(e.pointerId); fromClientX(e.clientX);
  });
  sl.addEventListener('pointermove', e => { if (!Sim.battery.multiplierEnabled) return; dragging && fromClientX(e.clientX); });
  sl.addEventListener('pointerup', e => {
    if (!dragging) return; dragging = false;
    const raw = fromClientX(e.clientX);
    const idx = Math.round(raw);
    Sim.battery.currentMultiplier = MULT_TIERS[idx];
    syncMultiplierUI();
  });
  syncMultiplierUI();
}

/* 同步校准卡头部倍率 / 滑块位置（总开关关闭 → 显示 1.0× 并归零滑块） */
function syncMultiplierUI() {
  const B = Sim.battery;
  const enabled = B.multiplierEnabled;
  const shown = enabled ? B.currentMultiplier : 1.0;
  const head = $('#bat-mult');
  head && (head.textContent = `×${shown.toFixed(1)}`);
  head && (head.style.color = enabled ? 'var(--neon-purple-bright)' : 'var(--text-secondary)');
  /* 总开关状态变化时同步禁用态（源码 BatteryScreen.kt:768/787/801 enabled = enabled） */
  $$('.filter-chip[data-mult-tier]').forEach(b => { b.disabled = !enabled; });
  const sl0 = $('#bat-mult-slider');
  if (sl0) sl0.classList.toggle('is-disabled', !enabled);
  const sl = $('#bat-mult-slider');
  if (sl) {
    const best = MULT_TIERS.reduce((bi, v, i) => Math.abs(v - shown) < Math.abs(MULT_TIERS[bi] - shown) ? i : bi, 0);
    /* value = 整数挡位索引(0..3)；rawValue 同步为同一值，避免与 paint() 的连续坐标语义打架 */
    sl.dataset.value = String(best);
    sl.dataset.rawValue = String(best);
    const p = best / (MULT_TIERS.length - 1);
    $('.fill', sl).style.width = p * 100 + '%';
    $('.thumb', sl).style.left = p * 100 + '%';
    $$('.filter-chip[data-mult-tier]').forEach(b => {
      const t = b.dataset.multTier;
      if (t !== 'reset') b.classList.toggle('selected', parseFloat(t) === shown);
    });
  }
}

/* ============================================================
 * Tab 5 — 网络 NetworkScreen
 * ============================================================ */
function signalLevelNr(rsrp) {
  if (rsrp >= -85) return { txt: t('network_signal_excellent'), cls: 'success' };
  if (rsrp >= -95) return { txt: t('network_signal_good'), cls: 'cyan' };
  if (rsrp >= -105) return { txt: t('network_signal_average'), cls: 'warning' };
  return { txt: t('network_signal_weak'), cls: 'error' };
}
function signalLevelLte(rsrp) {
  if (rsrp >= -85) return { txt: t('network_signal_excellent'), cls: 'success' };
  if (rsrp >= -100) return { txt: t('network_signal_good'), cls: 'cyan' };
  if (rsrp >= -115) return { txt: t('network_signal_average'), cls: 'warning' };
  return { txt: t('network_signal_weak'), cls: 'error' };
}

/* signalLevelColor(:301-307) —— >=good SuccessNeon / >=poor WarningNeon / else NeonMagenta */
function signalColor(v, good, poor) {
  return v >= good ? 'var(--success-neon)' : v >= poor ? 'var(--warning-neon)' : 'var(--neon-magenta)';
}
/* formatCellId(:293-299) */
function formatCellId(id) {
  return id > 0xFFFFFFFF ? `0x${id.toString(16).toUpperCase()} (${id})` : String(id);
}
/* FormatUtils.joinNonBlank(:80/102/116) —— 空串项整体跳过，分隔符原样输出（含两侧空格） */
function joinNonBlank(sep, ...parts) {
  return parts.filter(p => p !== null && p !== undefined && String(p) !== '').join(sep);
}

/* ── 渲染层补齐的模拟字段 ──
   data.js 的 Sim.network 没有 signalDbm / chipTempC / cell（cellId·pci·arfcn·rsrq·sinr·rssi），
   本轮只允许改 app.js，故在此按 NetworkScreen.kt 的取值语义构造：
     signalDbm — WifiDetailInfo.signalDbm 时间序列（:63-65 normalizeSignalStrength 的输入）
     chipTempC — WifiDetailInfo.chipTemperatureCelsius（:110）
     cell      — MobileNetworkInfo 小区字段（:231-275 CellDetailCard） */
function ensureNetExtras() {
  const N = Sim.network;
  if (!N.signalDbm) {
    N.signalDbm = new History(40, -75);
    let v = -75;
    for (let i = 0; i < 40; i++) { v = clamp(v + rnd(-4, 4), -108, -56); N.signalDbm.push(Math.round(v)); }
  }
  if (typeof N.chipTempC !== 'number') N.chipTempC = 42.4;
  if (!N.cell) {
    const is5g = /5G|NR/.test(N.netType || '');
    N.cell = {
      /* 0x1A2B3C4D5E6F > 0xFFFFFFFF → formatCellId 走 "0x%X (%d)" 分支 */
      cellId: 0x1A2B3C4D5E6F,
      pci: 213,
      band: N.cellBand,
      arfcn: is5g ? 627264 : 1850,           // n78 NR-ARFCN 627264 / LTE B3 EARFCN 1850
      dlBandwidth: N.dlBandwidth,
      ulConfigured: N.ulActive ? t('web_sim_ul_active') : t('web_sim_ul_idle'),
      rsrp: is5g ? N.nrRSRP : N.lteRSRP,
      rsrq: -11, sinr: 16, rssi: -63,
    };
  }
  return N;
}

/* MetricCard 同构（:87-201）：label + 大号 value（默认 NeonPurpleBright） */
function netCard(title, valueHtml, opts = {}) {
  return `<div class="cyber-card metric-card card-enter">
    <div class="metric-label">${title}</div>
    <div class="metric-value"${opts.id ? ` id="${opts.id}"` : ''}
      style="color:${opts.color || 'var(--neon-purple-bright)'}${opts.style ? ';' + opts.style : ''}">${valueHtml}</div>
    ${opts.extra || ''}
  </div>`;
}

function screenNetwork() {
  const N = ensureNetExtras();
  const C = N.cell;
  const is5g = /5G|NR/.test(N.netType || '');
  /* 值空时回落 "---"（:132/135/138/141/144/147/155） */
  const v = s => (s === null || s === undefined || String(s) === '') ? '---' : esc(s);

  /* ── 小区信息 CellDetailCard（:214-291）—— 10 行，逐行条件渲染 ── */
  const cellRow = (label, value, color) => `<div class="kv-row"><span class="kv-label">${label}</span><span class="kv-value mono" style="color:${color || 'var(--neon-purple-bright)'}">${value}</span></div>`;
  const cellBody = [
    C.cellId > 0 ? cellRow('Cell ID', formatCellId(C.cellId)) : '',
    C.pci >= 0 ? cellRow('PCI', C.pci) : '',
    C.band ? cellRow(t('network_band_title'), esc(C.band)) : '',
    C.arfcn > 0 ? cellRow(is5g ? 'NR ARFCN' : 'EARFCN', C.arfcn) : '',
    C.dlBandwidth ? cellRow(t('network_dl_bandwidth_title'), esc(C.dlBandwidth)) : '',
    C.ulConfigured ? cellRow(t('network_ul_status_title'), esc(C.ulConfigured)) : '',
    C.rsrp > -2147483648 ? cellRow(is5g ? 'SS-RSRP' : 'RSRP', `<span id="net-cell-rsrp">${C.rsrp}</span> dBm`, signalColor(C.rsrp, -95, -110)) : '',
    C.rsrq > -2147483648 ? cellRow('RSRQ', `${C.rsrq} dB`, signalColor(C.rsrq, -10, -15)) : '',
    C.sinr > -2147483648 ? cellRow('SINR', `${C.sinr} dB`, signalColor(C.sinr, 20, 10)) : '',
    C.rssi > -2147483648 ? cellRow('RSSI', `${C.rssi} dBm`, signalColor(C.rssi, -60, -80)) : '',
  ].join('');

  /* 附近 AP：WifiDataSource.kt:166 "SSID: -58dBm"，多行用 \n 拼接（:198） */
  const aps = (N.nearbyAps || []).map(a => `${esc(a.ssid)}: ${a.rssi}dBm`);

  return `
    ${infoCard('network', `<span>${esc(t('network_wifi_connected'))} (${esc(N.ssid)})</span>`, `<span id="net-speed">-- Mbps · ${esc(N.networkType)}</span>`)}

    ${/* :87-90 Network activity —— value = "${linkSpeedMbps} Mbps" + 双线图 */
      netCard('Network activity', `<span id="net-linkspeed">${N.linkSpeedMbps}</span> Mbps`, {
        extra: `<div style="display:flex;justify-content:space-between;margin:6px 0 4px">
          <span style="font-size:11.5px;color:var(--text-secondary)">↓ DL <span id="net-rx" style="color:#A78BFA;font-family:var(--font-num)">--</span></span>
          <span style="font-size:11.5px;color:var(--text-secondary)">↑ UL <span id="net-tx" style="color:#F43F5E;font-family:var(--font-num)">--</span></span>
        </div>
        <canvas id="net-activity-chart"></canvas>`,
      })}

    ${/* :96-107 WiFi 详情 —— 单卡：value = wifiStandard ?: "---"，subtitle = joinNonBlank("  ·  ", freq, chWidth) */
      netCard(t('network_wifi_details_title'), esc(N.wifiStandard || '---'), {
        extra: (N.wifiFreqMHz > 0 || N.channelWidth)
          ? `<div class="metric-subtitle">${esc(joinNonBlank('  ·  ', N.wifiFreqMHz > 0 ? N.wifiFreqMHz + ' MHz' : '', N.channelWidth))}</div>`
          : '',
      })}

    ${/* :112-128 WiFi 芯片 —— 单卡：joinNonBlank("  ·  ", "%.1f°C", powerSave 文案) */
      netCard(t('network_wifi_chip_title'), `<span id="wifi-chip-val">${N.chipTempC.toFixed(1)}°C  ·  ${N.powerSaveOn ? t('wifi_power_save_on') : t('wifi_power_save_off')}</span>`)}

    ${/* :131-147 六张独立卡（此前合并成单卡 6 个 kvRow） */
      netCard(t('network_ip_address'), v(N.ip))}
    ${netCard(t('network_gateway'), v(N.gateway))}
    ${netCard('DNS', v(N.dns))}
    ${netCard('MAC', v(N.mac))}
    ${netCard(t('network_subnet_mask_title'), v(N.subnet))}
    ${netCard('BSSID', v(N.bssid))}

    ${/* :150-155 网络类型（无数据不渲染）+ 运营商，两张独立卡 */
      N.netType ? netCard(t('network_type'), esc(N.netType)) : ''}
    ${netCard(t('network_operator'), v(N.operator))}

    ${/* :158-177 NR/LTE —— value = "$dbm dBm  ·  $level"，整串按 signalLevelColor 着色（等级不再拆成 tag） */
      netCard(t('network_nr_5g_signal_title'), `${N.nrRSRP} dBm  ·  ${signalLevelNr(N.nrRSRP).txt}`,
        { id: 'nr-rsrp', color: signalColor(N.nrRSRP, -95, -105) })}
    ${netCard(t('network_lte_4g_signal_title'), `${N.lteRSRP} dBm  ·  ${signalLevelLte(N.lteRSRP).txt}`,
      { id: 'lte-rsrp', color: signalColor(N.lteRSRP, -100, -115) })}

    ${/* :189-191 + :214-291 小区信息（频段/DL 带宽/UL 状态 三行从这里搬走） */
      `<div class="cyber-card card-enter">
        <div style="font-size:16px;font-weight:700;color:var(--text-primary)">${t('network_cell_info_title')}</div>
        <div style="padding-top:12px">${cellBody}</div>
      </div>`}

    ${/* :194-201 附近 AP —— 单卡多行，无 AP 时显示 network_no_aps_found */
      netCard(t('network_nearby_aps_title'), aps.length ? esc(aps.join('\n')) : t('network_no_aps_found'), {
        style: 'white-space:pre-line',
        color: aps.length ? 'var(--neon-purple-bright)' : 'var(--text-secondary)',
      })}`;
}

function updateNetwork() {
  const N = ensureNetExtras();
  const spEl = $('#net-speed'); if (!spEl) return;
  const is5g = /5G|NR/.test(N.netType || '');

  /* :80-84 subtitle = joinNonBlank(" · ", linkSpeed>0 → "$it Mbps", networkType) */
  spEl.textContent = joinNonBlank(' · ', N.linkSpeedMbps > 0 ? `${N.linkSpeedMbps} Mbps` : '', N.networkType) || t('web_sim_waiting_data');
  const ls = $('#net-linkspeed'); ls && (ls.textContent = N.linkSpeedMbps);
  $('#net-rx').textContent = N.rxMbps.last().toFixed(1) + ' Mbps';
  $('#net-tx').textContent = N.txMbps.last().toFixed(1) + ' Mbps';

  /* :60-65 —— data1 = normalizeChartData(wifi_speed, 1000f)
                 data2 = normalizeSignalStrength(signal_strength) = (v + 130) / 100 coerceIn(0,1)
     此前 data2 误用 txMbps×3，与源码完全无关 */
  walker(N.signalDbm, -108, -56, 3);
  N.chipTempC = clamp(N.chipTempC + rnd(-.4, .4), 36, 58);
  drawDualLineChart($('#net-activity-chart'),
    N.rxMbps.series().map(x => clamp(x / 1000, 0, 1)),
    N.signalDbm.series().map(x => clamp((x + 130) / 100, 0, 1)),
    { normalized: true, lineColor: '#7C3AED', lineColor2: '#F43F5E' });

  const nr = signalLevelNr(N.nrRSRP), lte = signalLevelLte(N.lteRSRP);
  const nrEl = $('#nr-rsrp');
  if (nrEl) { nrEl.textContent = `${N.nrRSRP} dBm  ·  ${nr.txt}`; nrEl.style.color = signalColor(N.nrRSRP, -95, -105); }
  const lteEl = $('#lte-rsrp');
  if (lteEl) { lteEl.textContent = `${N.lteRSRP} dBm  ·  ${lte.txt}`; lteEl.style.color = signalColor(N.lteRSRP, -100, -115); }
  const chipEl = $('#wifi-chip-val');
  chipEl && (chipEl.textContent = `${N.chipTempC.toFixed(1)}°C  ·  ${N.powerSaveOn ? t('wifi_power_save_on') : t('wifi_power_save_off')}`);
  const cellRsrp = $('#net-cell-rsrp');
  if (cellRsrp) {
    N.cell.rsrp = is5g ? N.nrRSRP : N.lteRSRP;
    cellRsrp.textContent = N.cell.rsrp;
  }
}

/* ============================================================
 * Tab 6 — GPS GpsScreen + SatelliteSkyView
 * ============================================================ */
/* constellationColor（SatelliteSkyView.kt:213-222） */
const CONSTELLATION_COLORS = {
  1: '#42A5F5',   // GPS
  2: '#FF8A65',   // SBAS
  3: '#66BB6A',   // GLONASS
  4: '#AB47BC',   // QZSS
  5: '#EF5350',   // BEIDOU
  6: '#FFCA28',   // GALILEO
  7: '#26C6DA',   // IRNSS
};
/* constellationLabel（GpsSatelliteInfo.kt:35-44） */
const CONSTELLATION_LABELS = { 1: 'GPS', 2: 'SBAS', 3: 'GLO', 4: 'QZSS', 5: 'BDS', 6: 'GAL', 7: 'IRN' };
/* 图例输出顺序（SatelliteSkyView.kt:240-248 typeOrder） */
const CONSTELLATION_TYPE_ORDER = [1, 5, 3, 6, 4, 2, 7];
/* data.js 的 GNSS_CONSTS.sys → GnssStatus constellation type（GpsSatelliteInfo.kt:23-32） */
const SYS_CONSTELLATION_TYPE = { GPS: 1, GLONASS: 3, BDS: 5, BEIDOU: 5, Galileo: 6, GALILEO: 6, QZSS: 4, SBAS: 2, IRNSS: 7 };
function constellationColor(type) { return CONSTELLATION_COLORS[type] || '#78909C'; }
function satConstellationType(s) { return SYS_CONSTELLATION_TYPE[s.sys] || -1; }

function screenGps() {
  /* 坐标/精度/定位速度/卫星数量拆四张独立 MetricCard（GpsScreen.kt 同构） */
  return `
    ${infoCard('play_arrow', `<span id="gps-state-title">${t('gps_fixed')}</span>`, `<span id="gps-state-sub">--</span>`)}
    ${metricCard({ label: t('gps_coordinates_title'), valueId: 'gps-coord', cls: 'mono' })}
    ${metricCard({ label: t('gps_accuracy_title'), valueId: 'gps-acc' })}
    ${metricCard({ label: t('gps_speed_title'), valueId: 'gps-kmh', subId: 'gps-ms' })}
    ${metricCard({ label: t('gps_satellite_count_title'), valueId: 'gps-satcount', subId: 'gps-satsub' })}
    ${/* SatelliteSkyView.kt:49-84 —— Card 圆角 12dp + padding 16dp + 标题 16sp Bold
           Canvas aspectRatio(1f).padding(8.dp)；图例仅 satellites 非空时渲染（:78-81） */
      `<div class="cyber-card sky-card card-enter">
        <div class="sky-title" id="sky-plot-title">${t('gps_sky_plot_title')}</div>
        <svg id="sky-plot" class="sky-plot" viewBox="0 0 300 300" style="aspect-ratio:1"></svg>
        <div id="sky-legend"></div>
      </div>`}
    ${/* GpsScreen.kt:127-139 —— 卫星列表：裸标题（16sp Bold, padding.top 4dp，**不在 Card 内**）
           其后每颗卫星一张独立 Card（圆角 20dp + padding 16dp） */
       Sim.gps.satellites.length > 0 ? `
    <div style="font-size:16px;font-weight:700;color:var(--text-primary);padding-top:4px">${t('gps_satellite_list_title')}</div>
    <div id="sat-list"></div>` : ''}`;
}

function updateGps() {
  const g = Sim.gps;
  const titleEl = $('#gps-state-title'); if (!titleEl) return;
  const fixing = g.satellites.filter(s => s.usedInFix);
  titleEl.textContent = g.state === 'fixed' ? t('gps_fixed') : t('gps_searching_format', g.satellites.length);
  $('#gps-state-sub').textContent = g.state === 'fixed'
    ? t('gps_fix_success_format', fixing.length) : t('gps_searching_satellites');

  $('#gps-coord').textContent = `${g.lat.toFixed(6)}, ${g.lng.toFixed(6)}`;
  $('#gps-acc').textContent = g.accuracyM.toFixed(1) + ' m';
  $('#gps-kmh').textContent = g.speedKmh.toFixed(1) + ' km/h';
  $('#gps-ms').textContent = g.speedMs.toFixed(1) + ' m/s';
  $('#gps-satcount').textContent = `${fixing.length} / ${g.satellites.length}`;
  $('#gps-satcount').style.color = fixing.length >= 4 ? 'var(--success-neon)' : 'var(--neon-purple-bright)';
  /* gps_locked_visible_format: "已锁定 %1$d 颗 · 可见 %2$d 颗" */
  $('#gps-satsub').textContent = t('gps_locked_visible_format', fixing.length, g.satellites.length);

  /* 天空图标题: 有星="卫星分布图", 无星="卫星分布图 · 无卫星信号" */
  const skyTitle = $('#sky-plot-title');
  skyTitle && (skyTitle.textContent = g.satellites.length === 0 ? t('gps_sky_plot_no_signal') : t('gps_sky_plot_title'));

  /* ── 天空图（SatelliteSkyView.kt:86-190）──
     drawSkyPlot(satellites, size.minDimension)：画布是 aspectRatio(1f).padding(8.dp) 之后
     的实际像素，radius = (minDim / 2) * 0.92 —— 此前半径写死 140、与容器尺寸无关。 */
  const svg = $('#sky-plot');
  if (svg) {
    const box = svg.getBoundingClientRect();
    /* 减 16 = Canvas 自身 padding(8.dp)×2；拿不到布局尺寸时回落到 300 */
    const minDim = Math.round(Math.max(120, (Math.min(box.width, box.height) || 300) - 16));
    const cx = minDim / 2, cy = minDim / 2, radius = (minDim / 2) * 0.92;
    svg.setAttribute('viewBox', `0 0 ${minDim} ${minDim}`);
    const RING = '#2A2A40', RING_MAJOR = '#3A3A55', SKY_TXT = '#7A7A9A';
    const f = n => n.toFixed(1);

    /* 背景圆（:97） */
    let inner = `<circle cx="${cx}" cy="${cy}" r="${f(radius + 4)}" fill="#0D0D15"/>`;
    /* 仰角圈 0/30/60（:100-104）—— 0° 用 ringMajorColor，30/60 用 ringColor */
    for (const a of [0, 30, 60]) {
      inner += `<circle cx="${cx}" cy="${cy}" r="${f(radius * (1 - a / 90))}" fill="none" stroke="${a === 0 ? RING_MAJOR : RING}" stroke-width="1"/>`;
    }
    /* 仰角标注（:107-113）—— offsetAngle = −90° + 45°，半径 ×0.7，10px */
    const oa = -Math.PI / 2 + Math.PI / 4;
    for (const a of [0, 30, 60]) {
      const r = radius * (1 - a / 90);
      inner += `<text x="${f(cx + r * Math.cos(oa) * .7)}" y="${f(cy + r * Math.sin(oa) * .7 + 3.3)}" fill="${SKY_TXT}" font-size="10" text-anchor="middle">${a}°</text>`;
    }
    /* 方位线 + N/E/S/W（:115-142）—— 线宽 .5，标签在 radius ×1.06 处，11px */
    for (const [az, lb] of [[0, 'N'], [90, 'E'], [180, 'S'], [270, 'W']]) {
      const rad = (90 - az) * Math.PI / 180;
      inner += `<line x1="${cx}" y1="${cy}" x2="${f(cx + radius * Math.cos(rad))}" y2="${f(cy - radius * Math.sin(rad))}" stroke="${RING}" stroke-width="0.5"/>`;
      const ld = radius * 1.06;
      inner += `<text x="${f(cx + ld * Math.cos(rad))}" y="${f(cy - ld * Math.sin(rad) + 3.7)}" fill="${SKY_TXT}" font-size="11" text-anchor="middle">${lb}</text>`;
    }
    /* 卫星（:145-186）—— 判据是 usedInFix，不是 SNR */
    for (const s of g.satellites) {
      const el = clamp(s.elDeg, 0, 90), rad = (90 - s.azDeg) * Math.PI / 180;
      const dist = radius * (1 - el / 90);
      const sx = cx + dist * Math.cos(rad), sy = cy - dist * Math.sin(rad);
      const col = constellationColor(satConstellationType(s));
      const dotR = s.usedInFix ? 5 : 3.5, alpha = s.usedInFix ? 1 : 0.6;
      /* 外圈光环（:163-169） */
      if (s.usedInFix) inner += `<circle cx="${f(sx)}" cy="${f(sy)}" r="${dotR + 2}" fill="${col}" opacity="0.3"/>`;
      inner += `<circle cx="${f(sx)}" cy="${f(sy)}" r="${dotR}" fill="${col}" opacity="${alpha}"/>`;
      /* SNR > 30 画小十字（:178-185） */
      if (s.snr > 30) {
        const cl = dotR + 1.5, op = (alpha * 0.6).toFixed(2);
        inner += `<line x1="${f(sx - cl)}" y1="${f(sy)}" x2="${f(sx + cl)}" y2="${f(sy)}" stroke="${col}" stroke-width="1" opacity="${op}"/>`
               + `<line x1="${f(sx)}" y1="${f(sy - cl)}" x2="${f(sx)}" y2="${f(sy + cl)}" stroke="${col}" stroke-width="1" opacity="${op}"/>`;
      }
    }
    /* 天顶中心点（:188-189） */
    inner += `<circle cx="${cx}" cy="${cy}" r="2.5" fill="#4A4A6A"/>`;
    svg.innerHTML = inner;
  }

  /* ── 星座图例（SatelliteSkyView.kt:227-280）── 仅 satellites 非空时显示 */
  const lg = $('#sky-legend');
  if (lg) {
    if (!g.satellites.length) lg.innerHTML = '';
    else {
      const stat = {};
      g.satellites.forEach(s => {
        const t = satConstellationType(s);
        stat[t] = stat[t] || { n: 0, used: 0 };
        stat[t].n++;
        if (s.usedInFix) stat[t].used++;
      });
      lg.innerHTML = `<div style="margin-top:8px">
        <div style="font-size:13px;font-weight:500;color:var(--text-secondary)">${t('gps_legend_title')}</div>
        <div style="display:flex;flex-wrap:wrap;gap:16px;margin-top:4px">
          ${CONSTELLATION_TYPE_ORDER.filter(t => stat[t]).map(t => `
            <span style="display:inline-flex;align-items:center;gap:3px">
              <span style="width:10px;height:10px;border-radius:5px;background:${constellationColor(t)};flex:none"></span>
              <span style="font-size:11px;color:var(--text-secondary)">${CONSTELLATION_LABELS[t]} ${stat[t].used}/${stat[t].n}</span>
            </span>`).join('')}
        </div>
      </div>`;
    }
  }

  /* ── 卫星卡（GpsScreen.kt:145-202）──
     第一行 Row(SpaceBetween)：PRN 16sp Bold onSurface 原色 + usedInFix 时 " ● 锁定" 12sp
       SuccessNeon padding(start 6dp)；右侧星座 14sp 按 constellationColor 着色。
     第二行 三等分 14sp padding(top 4dp)：SNR（硬编码英文）/ 仰角 / 方位。
     每星一张独立 .cyber-card.sat-card（圆角 20dp + padding 16dp），不再是单卡内嵌行。 */
  const satList = $('#sat-list');
  satList && (satList.innerHTML = g.satellites.map(s => `
    <div class="cyber-card sat-card card-enter" style="display:flex;flex-direction:column;align-items:stretch">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div style="display:flex;align-items:center;min-width:0">
          <span style="font-size:16px;font-weight:700;color:var(--text-primary);font-family:var(--font-num)">PRN ${s.prn}</span>
          ${s.usedInFix ? '<span style="font-size:12px;color:var(--success-neon);margin-left:6px"> ' + esc(t('gps_locked_indicator')) + '</span>' : ''}
        </div>
        <span style="font-size:14px;color:${constellationColor(satConstellationType(s))}">${esc(s.sys || '') || '?'}</span>
      </div>
      <div style="display:flex;padding-top:4px">
        <span style="flex:1;min-width:0;font-size:14px;color:var(--text-secondary);font-family:var(--font-num)">SNR: ${s.snr.toFixed(1)}</span>
        <span style="flex:1;min-width:0;font-size:14px;color:var(--text-secondary);font-family:var(--font-num)">${esc(t('web_sim_elevation_format', s.elDeg.toFixed(1)))}</span>
        <span style="flex:1;min-width:0;font-size:14px;color:var(--text-secondary);font-family:var(--font-num)">${esc(t('gps_bearing'))}: ${s.azDeg.toFixed(1)}°</span>
      </div>
    </div>`).join(''));
}

/* ============================================================
 * Tab 7 — 传感器 SensorsScreen（搜索单步定位 pulse）
 * ============================================================ */
function sensorTags(s) {
  let h = '';
  if (s.wake) h += `<span class="tag warn">${t('sensor_tag_wakeup')}</span> `;
  if (s.dynamic) h += `<span class="tag success">${t('sensor_tag_dynamic')}</span> `;
  return h;
}

/* 运行时权限态 —— data.js 不含该字段且本轮禁止改 data.js，故挂在 app.js 层（SensorsScreen.kt:102 permGranted） */
let SENSOR_PERM_GRANTED = false;

/* 权限提示行 —— SensorsScreen.kt:215-238
   条件 Build.VERSION.SDK_INT >= 29 && !permGranted；hint 12sp WarningNeon weight(1f) + Button(containerColor=NeonPurple, contentPadding 12/4.dp) */
function sensorPermRowHtml() {
  if (Device.sdkInt < 29 || SENSOR_PERM_GRANTED) return '';
  return `<div id="sensor-perm-row" style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
    <span style="flex:1;min-width:0;font-size:12px;color:var(--warning-neon);line-height:1.35">${t('sensor_perm_activity_hint')}</span>
    <button type="button" id="sensor-perm-grant" style="flex:none;font-size:12px;font-family:inherit;color:#fff;cursor:pointer;
      background:var(--neon-purple);border:none;border-radius:16px;padding:4px 12px">${t('sensor_perm_activity_grant')}</button>
  </div>`;
}

function screenSensors() {
  /* T7 —— SensorsScreen.kt:187-207 Row(fillMaxWidth, CenterVertically){
       Text(sensor_list_title, 18.sp, Bold, weight(1f)) + SensorSearchField(widthIn(max=210.dp)) }
     此前标题独占一行、搜索框另起一行；现收进 .search-box-row（style.css:594-600）。 */
  return `
    <div class="search-box-row">
      <div class="search-row-title" style="font-size:18px;font-weight:700;color:var(--text-primary)">${t('sensor_list_title')}</div>
      <div class="search-box">
        ${iconSvg('search', 17, '')}
        <input id="sensor-search" type="text" placeholder="${t('sensor_search_hint')}" autocomplete="off"/>
        ${/* trailingIcon ✕ —— 仅 query 非空时显示，点击 onClear()（SensorsScreen.kt:321-330）*/ ''}
        <span id="sensor-search-clear" title="${t('web_sim_clear')}" style="display:none;font-size:14px;color:var(--text-secondary);cursor:pointer;padding:0 2px">✕</span>
      </div>
    </div>
    /* 计数 —— SensorsScreen.kt:208-212 14.sp onSurfaceVariant，位于 Row 之下 */
    <div id="sensor-count-title" style="font-size:14px;color:var(--text-secondary);margin-bottom:10px">${esc(t('sensor_list_count', 0)).replace('0', '--')}</div>
    ${sensorPermRowHtml()}
    <div id="sensor-list"></div>
    <div style="font-size:11px;color:var(--text-secondary);opacity:.7;padding:6px 2px 20px">
      ${t('sensor_list_footnote')}
    </div>`;
}

/**
 * 搜索匹配 —— 逐字照抄 SensorsScreen.kt:282-295 matchesQuery()
 * 命中 searchAliases 中任一项即算匹配，大小写不敏感 contains（源码 L294 `contains(q, ignoreCase = true)`）。
 * 源码 L287-293：searchAliases 为空时防御性回退到 [显示名, 硬件名, 厂商]（手工构造对象未预填充的场景）。
 */
function matchesQuery(s, q) {
  if (q === '') return false;                                     // L284 空串恒不匹配
  const aliases = (s.searchAliases && s.searchAliases.length)      // L287 ifEmpty 防御性回退
    ? s.searchAliases
    : [sensorDisplayName(s), s.name, s.vendor].filter(v => v && String(v).trim() !== '');
  const lq = q.toLowerCase();
  return aliases.some(a => String(a).toLowerCase().includes(lq));
}

/* 列表**始终全量渲染**，不按查询过滤 —— 对齐 SensorsScreen.kt:252 forEachIndexed
 * （源码搜索只做定位跳转，不过滤列表；计数亦恒为未过滤总数，:209 sensors.size）。 */
function renderSensorList() {
  const list = $('#sensor-list');
  const items = Sim.sensors.list;
  // :209 stringResource(R.string.sensor_list_count, sensors.size) —— sensors 是未过滤全量
  $('#sensor-count-title').textContent = t('sensor_list_count', items.length);
  list.innerHTML = items.map((s, idx) => `
    <div class="sensor-card" data-idx="${idx}">
      <div class="sensor-title">${esc(sensorDisplayName(s) || s.name)} ${s.monitorable ? iconSvg('play_arrow', 15) : ''}</div>
      <div class="sensor-vendor">${esc(s.vendor)} &nbsp;${sensorTags(s)}</div>
      <div class="sensor-range">${esc(t('sensor_range_format', s.maxRange, s.unit))}</div>
    </div>`).join('');

  $$('.sensor-card', list).forEach(card => {
    card.addEventListener('click', () => openSensorDetail(+card.dataset.idx));
  });
  measureSensorCardTops();
}

/* ── 坐标采集：对齐 SensorsScreen.kt:249 / :267 ──
 * listRootTopPx = 滚动容器顶部在根坐标系的 Y（源码 onGloballyPositioned 于滚动 Column）
 * cardTops[idx] = 各卡片顶部在根坐标系的 Y（源码 onCardPositioned { cardTops[idx] = top }） */
function measureSensorCardTops() {
  const S = Sim.sensors;
  const list = $('#sensor-list');
  const scroller = list && list.closest('.screen');
  if (!list || !scroller) return;
  S.listRootTopPx = scroller.getBoundingClientRect().top;
  S.cardTops = {};
  $$('.sensor-card', list).forEach(c => { S.cardTops[+c.dataset.idx] = c.getBoundingClientRect().top; });
}

/* ── 搜索提交：对齐 SensorsScreen.kt:198-203 onCommit ── */
function sensorSearchCommit() {
  const S = Sim.sensors;
  const newQ = S.query.trim();                        // L199 query.trim()
  if (newQ !== S.submittedQuery) S.searchStep = 0;    // L200 查询变化 → 新查询从顶部重新开始
  S.submittedQuery = newQ;                            // L201
  S.searchTrigger++;                                  // L202
  runSensorSearch();                                  // LaunchedEffect(searchTrigger) L137
}

/* ── 清空：对齐 SensorsScreen.kt:204 onClear ── */
function sensorSearchClear() {
  const S = Sim.sensors;
  S.query = ''; S.submittedQuery = ''; S.searchStep = 0; S.searchTrigger++;
  // 注：源码 onClear (L204) 只做四项状态复位，不收起键盘（收键盘仅在卡片点击时做, L258-264），故此处不 blur
  const input = $('#sensor-search');
  if (input) input.value = '';
  syncSensorClearIcon();
  runSensorSearch();                                  // → q 空 → highlightedIdx = -1 (L141)
}

/* trailingIcon ✕ 仅 query 非空时显示（SensorsScreen.kt:322 if (query.isNotEmpty())） */
function syncSensorClearIcon() {
  const btn = $('#sensor-search-clear');
  if (btn) btn.style.display = Sim.sensors.query ? '' : 'none';
}

/* ── 单步定位本体：对齐 SensorsScreen.kt:137-163 LaunchedEffect(searchTrigger) ── */
function runSensorSearch() {
  const S = Sim.sensors;
  if (S.searchTrigger === 0) return;                  // L138 初始未搜索, 不触发
  const q = S.submittedQuery;                         // L140 用提交值, 非实时输入值
  if (q === '') { S.highlightedIdx = -1; return; }    // L141 空(含纯空格) → 复位并返回
  measureSensorCardTops();                            // 坐标就绪（源码 L152 等帧 + L154-157 2s 超时兜底; DOM 同步可测故无需等待）
  // L142-144 mapIndexedNotNull { idx, s -> if (matchesQuery(s, q)) idx else null }
  const matchList = [];
  S.list.forEach((s, idx) => { if (matchesQuery(s, q)) matchList.push(idx); });
  if (matchList.length === 0) { S.highlightedIdx = -1; return; }   // L145
  // L147-148 单步推进 + 环绕: 重复按搜索键依次定位下一条
  const matchIdx = matchList[S.searchStep % matchList.length];
  S.searchStep++;
  // L149-151 highlightedIdx 与 pulseTick 同批写入 → 脉冲只打在当前高亮卡上一次
  S.highlightedIdx = matchIdx;
  S.pulseTick++;
  pulseSensorCard(matchIdx);                          // L365-374 if (highlighted) 才播放
  scrollSensorCardIntoView(matchIdx);                 // L154-162
}

/* 一次性高亮脉冲 —— 对齐 SensorsScreen.kt:365-374
 * scale 1→1.04(180ms)→1(420ms) + glow 0→1(180ms)→0(600ms)，合计 600ms；
 * 复用既有 .sensor-card.pulse + @keyframes sensorPulse(0.6s, scale 1→1.04→1)。 */
function pulseSensorCard(idx) {
  const card = $(`#sensor-list .sensor-card[data-idx="${idx}"]`);
  if (!card) return;
  card.classList.remove('pulse');
  void card.offsetWidth;        // 强制重排: 保证同一张卡重复提交也能重播（对应 pulseTick 作为 key）
  card.classList.add('pulse');
  clearTimeout(card._pulseTimer);
  card._pulseTimer = setTimeout(() => card.classList.remove('pulse'), 640);
}

/* 滚动定位 —— 对齐 SensorsScreen.kt:154-162
 * target = cardTop + scrollState.value - listRootTopPx - 12.dp，再 animateScrollTo（平滑）。 */
function scrollSensorCardIntoView(idx) {
  const S = Sim.sensors;
  const card = $(`#sensor-list .sensor-card[data-idx="${idx}"]`);
  if (!card) return;
  const scroller = card.closest('.screen');
  if (!scroller) return;
  let cardTop = S.cardTops[idx];
  if (cardTop == null) { measureSensorCardTops(); cardTop = S.cardTops[idx]; }  // L154-157 兜底
  if (cardTop == null) return;
  const target = cardTop + scroller.scrollTop - S.listRootTopPx - 12;   // L159-161 (- 12.dp)
  const max = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
  scroller.scrollTo({ top: clamp(target, 0, max), behavior: 'smooth' }); // L161 animateScrollTo
}

function bindSensors() {
  renderSensorList();
  syncSensorClearIcon();
  const input = $('#sensor-search');
  // onQueryChange (SensorsScreen.kt:197): 仅更新实时值 + ✕ 显隐, **不触发定位**
  input.addEventListener('input', e => {
    Sim.sensors.query = e.target.value;
    syncSensorClearIcon();
  });
  // imeAction = ImeAction.Search → onSearch = onCommit (SensorsScreen.kt:340-341)
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); sensorSearchCommit(); }
  });
  const clearBtn = $('#sensor-search-clear');
  if (clearBtn) clearBtn.addEventListener('click', () => sensorSearchClear());
  // T5 —— 授权按钮（SensorsScreen.kt:227-236 permLauncher.launch(ACTIVITY_RECOGNITION)）
  // 网页端无运行时权限系统：点「授权」即视作授予 → 整行消失（对齐源码授权后 permGranted=true 触发重组）
  const grantBtn = $('#sensor-perm-grant');
  if (grantBtn) grantBtn.addEventListener('click', () => {
    SENSOR_PERM_GRANTED = true;
    const row = $('#sensor-perm-row');
    if (row) row.remove();
    toast && toast(t('web_sim_perm_granted'));
  });
}

/* ── 传感器详情覆盖层（中心缩放 0.3→1.0 + scrim + 上移 24dp，入 550ms 出 450ms） ── */
/* ============================================================
 * 传感器详情（对齐 SensorDetailScreen.kt：5 张卡 + 实时模拟）
 * ============================================================ */
/* 传感器类型元数据 —— 一律以 type 反查 TYPE_META（data.js），与源码
 * SensorTypeMeta.fromTypeId / getDisplayName / getAxisLabel 语义一致（根因 P0-1）。 */
function sensorMeta(type) {
  const t = TYPE_META[type];
  return {
    valueCount: t ? t.valueCount : 3,                  // SensorItemInfo.kt:73 (valueCount 默认 3)
    icon: t ? t.icon : '\u25C9',                       // SensorDetailScreen.kt:332 else → ◉ U+25C9
    unit: t && t.unit ? t.unit : '',                   // SensorDetailScreen.kt:279
    axisLabels: t ? t.axisLabels : ['X', 'Y', 'Z'],    // SensorItemInfo.kt:191-198 getAxisLabel 兜底
    // 运动健康摘要卡触发类型（SensorItemInfo.kt:261-264 isHealthMotionType）
    isHealthMotion: type === 18 || type === 19 || type === 21,
    // HEART_RATE → 走「最高/最低/平均」分支（SensorDetailScreen.kt:241-244）
    isHeart: type === 21,
    // STEP_COUNTER / STEP_DETECTOR → StepCounterCard（SensorDetailScreen.kt:201）
    isStep: type === 18 || type === 19,
    isStepCounter: type === 19,
    // PRESSURE → PressureAltimeterCard（SensorDetailScreen.kt:192）
    isPressure: type === 6,
    // LIGHT → 照度等级（SensorDetailScreen.kt:381）
    isLight: type === 5,
    // PROXIMITY → 多档距离状态（SensorDetailScreen.kt:398）
    isProximity: type === 8,
  };
}
/* 数值格式化：小数位由 TYPE_META.fmtSingle / fmtMulti 驱动，
 * 分别照抄 SensorDetailScreen.kt:345-362（单值）与 :480-492（多轴）。 */
function fmtSensorValue(type, v) {
  if (v == null || Number.isNaN(v)) return '---';
  const t = TYPE_META[type];
  if (!t) return v.toFixed(2);
  return t.valueCount === 1 ? v.toFixed(t.fmtSingle) : v.toFixed(t.fmtMulti);
}

/* ── 光线全量程照度等级（14 档）—— 逐字照抄 SensorItemInfo.kt:204-220
 *   + values-zh-rCN/strings.xml:1285-1299（源码 &gt; 运行时渲染为 >）── */
function describeLightLevel(lux) {
  if (lux <= 0.01) return t('light_level_pitch_black');
  if (lux <= 0.1) return t('light_level_starlight');
  if (lux <= 1) return t('light_level_moonlight');
  if (lux <= 3.4) return t('light_level_deep_dusk');
  if (lux <= 10) return t('light_level_dusk');
  if (lux <= 50) return t('light_level_twilight');
  if (lux <= 100) return t('light_level_dark_indoor');
  if (lux <= 500) return t('light_level_normal_indoor');
  if (lux <= 1000) return t('light_level_bright_indoor');
  if (lux <= 2500) return t('light_level_overcast');
  if (lux <= 10000) return t('light_level_cloudy');
  if (lux <= 25000) return t('light_level_shade');
  if (lux <= 50000) return t('light_level_half_daylight');
  if (lux <= 100000) return t('light_level_full_daylight');
  return t('light_level_direct_sun');
}

/* ── 距离多档状态（7 档）—— 逐字照抄 SensorItemInfo.kt:232-256
 *   + values-zh-rCN/strings.xml:1402-1408（≤ 为 U+2264）。
 *   要点：「较近/中等/较远」三档阈值为运行时动态值 safeMaxRange × 0.25/0.5/0.75 (L248-253)，不可写死。 */
function describeProximityState(distance, maxRange) {
  // maxRange 异常降级 (L234-238)
  const safeMaxRange = (!Number.isFinite(maxRange) || maxRange <= 0.001) ? 5 : maxRange;
  // distance 异常降级 (L240-243)：NaN/Infinity → safeMaxRange + 1 → 落到 else「远离」分支
  const safeDistance = (!Number.isFinite(distance)) ? safeMaxRange + 1 : distance;
  if (safeDistance <= 0) return t('proximity_state_contact');
  if (safeDistance <= 0.5) return t('proximity_state_near');
  if (safeDistance <= 2) return t('proximity_state_close');
  if (safeDistance <= safeMaxRange * 0.25) return t('web_sim_prox_fair_format', (safeMaxRange * 0.25).toFixed(1));
  if (safeDistance <= safeMaxRange * 0.5) return t('web_sim_prox_mid_format', (safeMaxRange * 0.5).toFixed(1));
  if (safeDistance <= safeMaxRange * 0.75) return t('web_sim_prox_far_format', (safeMaxRange * 0.75).toFixed(1));
  return t('proximity_state_out');
}

/* 心率 5 分钟滚动窗口聚合 —— 照抄 SensorDetailViewModel.kt:353-376
 *   HEART_RATE_WINDOW_MS = 5 * 60_000 (L376)；max/min/avg 取整数 (L362-363, Kotlin toInt() 截断) */
const HEART_RATE_WINDOW_MS = 5 * 60 * 1000;
function pushHeartRateSample(s, bpm, tsMs) {
  if (!Number.isFinite(bpm) || bpm <= 0) return;                       // L355
  const w = s._sim.heartWindow || (s._sim.heartWindow = []);
  const now = (tsMs && tsMs > 0) ? tsMs : Date.now();                  // L356
  w.push({ t: now, v: bpm });                                          // L357
  while (w.length && now - w[0].t > HEART_RATE_WINDOW_MS) w.shift();   // L358
}
function heartRateAggregate(s) {
  const w = (s._sim && s._sim.heartWindow) || [];
  if (!w.length) return null;                                          // L360 → 不 postValue → 显示 "---"
  let max = -Infinity, min = Infinity, sum = 0;
  for (const e of w) { if (e.v > max) max = e.v; if (e.v < min) min = e.v; sum += e.v; }
  return { max: Math.trunc(max), min: Math.trunc(min), avg: Math.trunc(sum / w.length) };
}
function reportingModeName(mode) {
  return [t('sensor_mode_continuous'), t('sensor_reporting_on_change'), t('sensor_reporting_one_shot'), t('sensor_reporting_special_trigger')][mode] || t('sensor_mode_unknown', mode);
}
function sensorAccuracyHtml(acc) {
  const map = {
    3: [t('sensor_accuracy_high'), 'var(--success-neon)'],
    2: [t('sensor_accuracy_medium'), 'var(--warning-neon)'],
    1: [t('sensor_accuracy_low'), 'var(--neon-magenta)'],
    0: [t('sensor_accuracy_unreliable'), 'var(--neon-magenta)'],
  };
  const [txt, col] = map[acc] || map[0];
  return `<span style="color:${col}">${txt}</span>`;
}
function sensorExtraHtml(s, m) {
  const L = s.live;
  // ── 光线传感器: 全量程照度等级 (SensorDetailScreen.kt:381-395) ──
  if (m.isLight && !Number.isNaN(L.x)) {
    // 文案 14 档由 describeLightLevel 提供；颜色阈值照抄 L388-393（保持不变）
    const col = L.x <= 10 ? 'var(--neon-cyan)'
      : L.x <= 500 ? 'var(--neon-purple)'
      : L.x <= 10000 ? 'var(--success-neon)'
      : 'var(--neon-magenta)';
    return `<div style="font-size:14px;font-weight:600;color:${col};text-align:center;margin-top:6px">${esc(describeLightLevel(L.x))}</div>`;
  }
  // ── 距离传感器: 多档连续追踪 (SensorDetailScreen.kt:398-410) ──
  if (m.isProximity && !Number.isNaN(L.x)) {
    const maxRange = Number(s.maxRange);
    const safeMax = !Number.isFinite(maxRange) || maxRange <= 0.001 ? 5 : maxRange; // L234-238
    // 颜色阈值照抄 L405-409（按原始 value 判定，保持不变）
    const col = L.x <= 0.5 ? 'var(--neon-magenta)'
      : L.x <= 2 ? 'var(--warning-neon)'
      : 'var(--success-neon)';
    const pct = clamp(L.x / safeMax, 0, 1);
    return `<div style="font-size:14px;font-weight:600;color:${col};text-align:center;margin-top:6px">${esc(describeProximityState(L.x, maxRange))}</div>
      <div style="display:flex;justify-content:center;align-items:center;margin-top:6px">
        <div style="width:120px;height:6px;border-radius:3px;background:rgba(76,29,149,.3);overflow:hidden">
          <div style="height:100%;width:${pct * 100}%;background:${col};border-radius:3px"></div></div>
        <span style="font-size:11px;color:var(--text-secondary);margin-left:6px">${Math.round(pct * 100)}%</span>
      </div>
      <div style="font-size:11px;color:rgba(148,163,184,.6);text-align:center;margin-top:4px">${esc(t('sensor_max_range_cm_format', safeMax.toFixed(1)))}</div>`;
  }
  return '';
}

/* ── 各卡片（对齐 SensorDetailScreen 子 Composable） ── */
function sensorHealthCard(s, m) {
  if (!m.isHealthMotion) return '';
  const sim = s._sim;
  // 心率(21) → 最高/最低/平均（SensorDetailScreen.kt:241-244 + strings.xml:932-934）
  //   数值由 5 分钟滚动窗口实时聚合，占位 "---" 对应源码 `?.toString() ?: "---"` (L242-244)
  // 步数(18/19) → 距离/卡路里/活跃时长（SensorDetailScreen.kt:245-251 + strings.xml:926-931）
  const cells = m.isHeart
    ? healthCell(t('health_heart_max_label'), '<span id="hr-max">---</span>')
      + healthCell(t('health_heart_min_label'), '<span id="hr-min">---</span>')
      + healthCell(t('health_heart_avg_label'), '<span id="hr-avg">---</span>')
    : healthCell(t('health_distance_label'), sim.distanceKm.toFixed(1) + ' km')
      + healthCell(t('health_calories_label'), sim.caloriesKcal + ' kcal')
      + healthCell(t('health_active_label'), t('web_sim_minutes_format', sim.activeMinutes));
  return `<div class="cyber-card card-enter">
    <div class="section-title">${t('health_summary_title')}</div>
    <div style="display:flex;justify-content:space-around;margin-top:8px">${cells}</div>
  </div>`;
}
function healthCell(label, value) {
  return `<div style="text-align:center">
    <div style="font-size:11px;color:var(--text-secondary)">${label}</div>
    <div style="font-size:20px;font-weight:700;color:var(--neon-purple-bright);margin-top:4px">${value}</div>
  </div>`;
}
function sensorValueCard(s, m) {
  // 单位/轴标签走 TYPE_META（SensorDetailScreen.kt:278-279），不再取 SensorDefs 的硬编码字段
  const unit = m.unit;
  const colors = ['#A78BFA', '#00D4FF', '#F43F5E'];
  let body;
  if (m.valueCount === 1) {
    body = `
      <div id="sensor-bigval" style="font-size:48px;font-weight:700;color:var(--neon-purple-bright);text-align:center">--</div>
      ${unit ? `<div style="font-size:16px;color:var(--text-secondary);text-align:center">${esc(unit)}</div>` : ''}
      <div id="sensor-extra"></div>`;
  } else {
    body = `<div style="display:flex;justify-content:space-evenly;margin-top:4px">
      ${m.axisLabels.map((label, k) => `<div style="text-align:center">
        <div style="width:10px;height:10px;border-radius:50%;background:${colors[k % colors.length]};display:inline-block;margin-bottom:6px"></div>
        <div style="font-size:13px;color:var(--text-secondary)">${esc(label)}</div>
        <div id="sensor-ax-${'xyz'[k] || 'x'}" style="font-size:18px;font-weight:700;color:${colors[k % colors.length]}">--</div>
      </div>`).join('')}
    </div>`;
  }
  return `<div class="cyber-card card-enter">
    <div style="display:flex;flex-direction:column;align-items:center;padding:4px 0">
      <div style="width:48px;height:48px;border-radius:50%;background:rgba(124,58,237,.15);display:grid;place-items:center;font-size:22px;color:var(--neon-purple-bright);margin-bottom:8px">${m.icon}</div>
      ${body}
      <div id="sensor-acc" style="font-size:12px;margin-top:8px"></div>
    </div>
  </div>`;
}
function sensorChartCard(s, m) {
  const legend = m.valueCount > 1
    ? `<div style="display:flex;justify-content:flex-end;gap:10px;margin-top:4px">
        ${m.axisLabels.map((ax, k) => `<span style="font-size:11px;color:var(--text-secondary)"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${['#A78BFA', '#00D4FF', '#F43F5E'][k % 3]};margin-right:3px"></span>${esc(ax)}</span>`).join('')}
      </div>` : '';
  return `<div class="cyber-card card-enter">
    <div style="display:flex;justify-content:space-between;align-items:center">
      <div class="section-title" style="margin-bottom:0">${t('sensor_realtime_waveform')}</div>
      <div id="sensor-pts" style="font-size:11px;color:rgba(148,163,184,.6)">0 pts</div>
    </div>
    ${legend}
    <canvas id="sensor-live-chart" style="width:100%;height:${m.valueCount > 1 ? 200 : 120}px;display:block;margin-top:8px"></canvas>
    <div style="font-size:11px;color:var(--text-secondary);margin-top:4px;display:flex;justify-content:space-between">
      <span>${esc(t('sensor_detail_realtime_label', ''))}<span id="sensor-live-val" style="font-family:var(--font-num)">--</span></span>
      <span id="sensor-unit-label"></span>
    </div>
  </div>`;
}
function sensorPressureCard(s) {
  const sim = s._sim;
  const rel = sim.relativeAltitude, abs = sim.absoluteAltitude;
  return `<div class="cyber-card card-enter">
    <div style="display:flex;justify-content:space-between;align-items:center">
      <div class="section-title" style="margin-bottom:0">${t('altitude_title')}</div>
      <div style="font-size:11px;color:var(--success-neon)">${t('altitude_relative')}</div>
    </div>
    <div style="font-size:40px;font-weight:700;color:var(--neon-purple-bright);text-align:center;margin-top:4px">${rel >= 0 ? '+' : ''}${rel.toFixed(1)} m</div>
    <div style="font-size:12px;color:var(--text-secondary);text-align:center">${t('altitude_absolute')} ${abs >= 0 ? '+' : ''}${abs.toFixed(1)} m</div>
    <div style="display:flex;justify-content:space-between;font-size:12px;color:var(--text-secondary);margin-top:10px">
      <span>EMA ${sim.emaPressure.toFixed(2)} hPa</span>
      <span style="color:${sim.rate > 0.5 ? 'var(--success-neon)' : sim.rate < -0.5 ? 'var(--warning-neon)' : 'var(--neon-cyan)'}">${t('web_sim_altitude_rate_format', (sim.rate >= 0 ? '+' : '') + sim.rate.toFixed(1))}</span>
    </div>
    <div style="display:flex;gap:8px;margin-top:12px">
      <button id="pressure-ref" style="flex:1;padding:8px;border-radius:12px;border:1px solid rgba(167,139,250,.4);background:rgba(124,58,237,.55);color:#fff;font-family:inherit;font-size:13px;cursor:pointer">${esc(t('altitude_set_reference'))}</button>
      <button id="pressure-gps" style="flex:1;padding:8px;border-radius:12px;border:1px solid rgba(167,139,250,.4);background:rgba(39,39,59,.6);color:#fff;font-family:inherit;font-size:13px;cursor:pointer">${t('altitude_gps_calibrate')}</button>
    </div>
  </div>`;
}
function sensorStepCard(s) {
  const sim = s._sim;
  return `<div class="cyber-card card-enter">
    <div class="section-title">${t('step_today_title')}</div>
    <div id="step-today" style="font-size:48px;font-weight:700;color:var(--neon-purple-bright);text-align:center;margin-top:4px">${sim.todaySteps}</div>
    <div style="display:flex;justify-content:space-between;font-size:12px;color:var(--text-secondary);margin-top:10px">
      <span>${esc(t('web_sim_cumulative'))} ${sim.totalSteps}</span>
      <span>${t('device_step_boot_label')} ${sim.bootSteps}</span>
    </div>
    <div id="step-rate" style="font-size:11px;color:var(--neon-cyan);text-align:center;margin-top:8px">${sim.ratePerMin} /min</div>
  </div>`;
}
function sensorInfoCard(s, m) {
  const res = !Number.isNaN(s.resolution) ? s.resolution.toFixed(6) : '-';
  const power = !Number.isNaN(s.powerMa) ? s.powerMa.toFixed(3) + ' mA' : '-';
  // 最大范围行的单位取 TYPE_META（SensorDetailScreen.kt:903-905: meta?.unit?.takeIf{ isNotEmpty() }）
  const maxR = !Number.isNaN(s.maxRange) ? (m.unit ? `${s.maxRange.toFixed(2)} ${m.unit}` : s.maxRange.toFixed(2)) : '-';
  const minD = s.minDelay > 0 ? `${s.minDelay} μs` : '-';
  const vendor = s.vendor || (s.name.split(' ')[0]) || t('common_unknown');
  return `<div class="cyber-card card-enter">
    <div class="section-title">${t('sensor_detail_info_title')}</div>
    ${kvRow(t('sensor_info_id'), String(s.sensorId))}
    ${kvRow(t('sensor_info_name'), esc(sensorDisplayName(s) || s.name))}
    ${kvRow(t('sensor_info_hardware'), esc(s.name))}
    ${kvRow(t('sensor_info_type_id'), `${s.type}  (${reportingModeName(s.reportingMode)})`)}
    ${kvRow(t('sensor_info_vendor'), esc(vendor))}
    ${kvRow(t('sensor_version_label'), s.version >= 0 ? String(s.version) : '-')}
    ${kvRow(t('sensor_resolution_label'), res)}
    ${kvRow(t('sensor_info_power'), power)}
    ${kvRow(t('sensor_info_max_range'), maxR)}
    ${kvRow(t('sensor_info_min_delay'), minD)}
    ${kvRow(t('sensor_dynamic_sensor_label'), s.dynamic ? t('common_yes') : t('common_no'), s.dynamic ? 'success' : '')}
    ${kvRow(t('sensor_wake_up_sensor_label'), s.wake ? t('common_yes') : t('common_no'), s.wake ? 'success' : '')}
  </div>`;
}

/* 实时传感器采样（对齐 snapshotFlow → onSample，模拟数据层） */
function sensorLiveTick(idx) {
  const s = Sim.sensors.list[idx];
  const m = sensorMeta(s.type);
  const L = s.live;
  L.accuracy = Math.random() > 0.9 ? 2 : 3;
  if (m.valueCount === 1) {
    let v = L.x || 0;
    // case 常量已随 typeId 校正同步迁移（旧 → 新）：8→5 光线, 12→8 距离, 18→21 心率,
    // 5→19 步数计数器, 11/19/21/25→17/29/30 检测型；新增 12 湿度 / 13 环境温度 / 18 步数检测器
    switch (s.type) {
      case 5:  v = clamp((L.x == null ? 320 : L.x) + rnd(-22, 22), 0, 60000); if (Math.random() < 0.05) v = rnd(2, 35); break; // LIGHT
      case 6:  v = (L.x == null ? 1013 : L.x) + rnd(-1.1, 1.1); break;                                                        // PRESSURE
      case 8:  v = Math.random() < 0.12 ? 0 : s.maxRange; break;                                                              // PROXIMITY
      case 21: v = clamp((L.x == null ? 74 : L.x) + rnd(-3, 4), 46, 190); break;                                              // HEART_RATE
      case 19: v = (L.x || 0) + rnd(0, 2.5); break;                                                                           // STEP_COUNTER 累计单调
      case 18: v = 1; break;                                                                                                   // STEP_DETECTOR 每事件 values[0] = 1
      case 17: case 29: case 30: v = 0; break;                                                                                // 检测型 (ON_CHANGE, 无持续值)
      case 12: v = clamp((L.x == null ? 48 : L.x) + rnd(-1.5, 1.5), 0, 100); break;                                           // HUMIDITY
      case 13: v = clamp((L.x == null ? 26 : L.x) + rnd(-0.3, 0.3), 0, 125); break;                                           // AMBIENT_TEMPERATURE
      default: v = (L.x == null ? 0 : L.x) + rnd(-0.4, 0.4);
    }
    L.x = v;
    // 心率 5min 滚动窗口聚合（SensorDetailViewModel.kt:353-376）
    if (s.type === 21) pushHeartRateSample(s, v);
    // STEP_DETECTOR 累计检出步数（硬件 values[0] 恒为 1，累计量在此维护）
    if (s.type === 18) s._sim.detectorSteps = (s._sim.detectorSteps || 0) + 1;
    if (liveChartLoop.hist && liveChartLoop.hist.single) liveChartLoop.hist.single.push(v);
  } else {
    // 多轴基线值（按校正后的 typeId 重建）
    const base = ({
      1: [0, 9.81, 0], 35: [0, 9.81, 0], 9: [0, 9.81, 0], 10: [0, 0, 0],      // ACCEL / ACCEL_UNCAL / GRAVITY / LINEAR_ACCEL
      4: [0, 0, 0], 16: [0, 0, 0],                                            // GYRO / GYRO_UNCAL
      2: [25, 12, -42], 14: [25, 12, -42],                                    // MAGNETIC_FIELD / UNCALIBRATED
      3: [0, 0, 0],                                                           // ORIENTATION
      11: [0, 0, 1], 15: [0, 0, 1], 20: [0, 0, 1], 37: [0, 0, 0],             // ROTATION_VECTOR / GAME_RV / GEOMAG_RV / HEAD_TRACKER
    })[s.type] || [0, 0, 0];
    ['x', 'y', 'z'].forEach((ax, k) => {
      const cur = L[ax] == null ? base[k] : L[ax];
      L[ax] = clamp(cur + rnd(-0.25, 0.25), base[k] - 4, base[k] + 4);
    });
    if (liveChartLoop.hist) {
      liveChartLoop.hist.x.push(L.x); liveChartLoop.hist.y.push(L.y); liveChartLoop.hist.z.push(L.z);
    }
  }
}

function openSensorDetail(idx) {
  const s = Sim.sensors.list[idx];
  const ov = $('#overlay-sensor');
  ov.dataset.idx = idx;      /* applyLang() 重建覆盖层时据此还原当前传感器 */
  const m = sensorMeta(s.type);
  const vendorLabel = s.vendor || (s.name.split(' ')[0]) || '';
  // 模拟静态状态（对齐 HealthSummaryCard / StepCounterCard / PressureAltimeterCard 数据口径）
  s._sim = {
    todaySteps: Math.round(rnd(3200, 11800)),
    totalSteps: Math.round(rnd(1.2e6, 4.8e6)),
    bootSteps: Math.round(rnd(600, 3200)),
    ratePerMin: Math.round(rnd(0, 118)),
    emaPressure: +(1013 + rnd(-4, 4)).toFixed(2),
    relativeAltitude: +(rnd(-6, 14)).toFixed(1),
    absoluteAltitude: +(rnd(8, 86)).toFixed(1),
    rate: +(rnd(-2.4, 2.4)).toFixed(1),
    // 心率不再预置静态 max/min/avg：改由 5min 滚动窗口实时聚合
    // （SensorDetailViewModel.kt:353-376；HEART_RATE_WINDOW_MS = 5 * 60_000）
    heartWindow: [],
    detectorSteps: 0,
  };
  // 由今日步数派生运动健康摘要 —— 系数逐字照抄 SensorDetailViewModel.kt:347-351 / 377-382
  s._sim.distanceKm = +(s._sim.todaySteps * 0.762 / 1000).toFixed(1);  // AVG_STRIDE_M = 0.762f (L378)
  s._sim.caloriesKcal = Math.round(s._sim.todaySteps * 0.04);          // KCAL_PER_STEP = 0.04f  (L380)
  s._sim.activeMinutes = Math.round(s._sim.todaySteps / 100);          // STEPS_PER_MIN = 100f   (L382)
  if (m.isStepCounter) s.live.x = s._sim.todaySteps;                   // 步数计数器基数（检测器 values[0] 恒为 1）

  ov.innerHTML = `
    <div class="overlay-bg"></div>
    ${/* T9 —— GlowBackButton 必须在模板内输出：ov.innerHTML 会抹掉 index.html 里的静态备份 */ ''}
    ${glowBackBtnHtml()}
    <div class="overlay-body"><div class="overlay-content">
      ${/* 标题 = 显示名 (SensorDetailScreen.kt:144)；副标题 = "厂商 · 硬件名" (L152)，仅 meta != null 时显示 */
        infoCard('sensors', esc(sensorDisplayName(s) || s.name),
          s.monitorable ? `${esc(vendorLabel)} · ${esc(s.name)}` : esc(s.name))}
      ${sensorHealthCard(s, m)}
      ${sensorValueCard(s, m)}
      ${/* 步数类不画波形，由 StepCounterCard 展示 (SensorDetailScreen.kt:180) */ ''}
      ${m.isStep ? '' : sensorChartCard(s, m)}
      ${m.isPressure ? sensorPressureCard(s) : ''}
      ${m.isStep ? sensorStepCard(s) : ''}
      ${sensorInfoCard(s, m)}
    </div></div>`;
  ov.classList.add('active', 'scrim-scale');
  animateScrim(ov, 1);         /* scrim 二次曲线淡入 alpha = t² × 0.22 */
  staggerEnter(ov);
  /* 展开档（≥840dp）：停靠为右侧详情面板，与左侧列表构成 list-detail 双栏。
     复用同一套 DOM 与渲染逻辑，仅切换定位（见 style.css 停靠规则）。 */
  setPaneDocked('sensor', true);     /* adaptive 下停靠为右侧面板，pager 同步让位 */
  if (!ov.dataset.bound) {
    ov.dataset.bound = '1';
    ov.addEventListener('click', e => { if (e.target.closest('.overlay-bg')) closeOverlay('#overlay-sensor'); });
  }
  liveChartLoop.start(idx);
}

/* 80 点采样窗（对齐 SensorLineChart / MultiAxisChart 80 采样点规格） */
const liveChartLoop = {
  timer: null, hist: null, idx: -1,
  start(idx) {
    this.stop();
    this.idx = idx;
    const s = Sim.sensors.list[idx];
    const m = sensorMeta(s.type);
    this.hist = m.valueCount === 1
      ? { single: new History(80, 0) }
      : { x: new History(80, 0), y: new History(80, 0), z: new History(80, 0) };
    const tick = () => {
      if (!$('#overlay-sensor').classList.contains('active')) return this.stop();
      sensorLiveTick(this.idx);
      this.draw();
      this.update();
    };
    tick();
    this.timer = setInterval(tick, 120);
  },
  draw() {
    const s = Sim.sensors.list[this.idx]; if (!s) return;
    const m = sensorMeta(s.type);
    const cv = $('#sensor-live-chart'); if (!cv) return;
    if (m.valueCount === 1) {
      drawLineChartAnimated(cv, () => this.hist.single.series(),
        { gradFrom: '#00D4FF', gradMid: '#7C3AED', gradTo: '#A78BFA' });
    } else {
      drawMultiLineChart(cv, [this.hist.x.series(), this.hist.y.series(), this.hist.z.series()],
        ['#A78BFA', '#00D4FF', '#F43F5E']);
    }
    // History.size 是缓冲容量数字属性（data.js:14），不是方法；
    // 采样点数取已缓冲长度 data.length，否则每 tick 抛 TypeError 导致 update() 永远不执行。
    const pts = (m.valueCount === 1 ? this.hist.single : this.hist.x).data.length;
    const pc = $('#sensor-pts'); if (pc) pc.textContent = pts + ' pts';
  },
  update() {
    const s = Sim.sensors.list[this.idx]; if (!s) return;
    const m = sensorMeta(s.type);
    const L = s.live;
    const rv = $('#sensor-live-val');
    if (rv) rv.textContent = m.valueCount === 1
      ? `${fmtSensorValue(s.type, L.x)} ${m.unit}`
      : ['x', 'y', 'z'].map(ax => fmtSensorValue(s.type, L[ax])).join(', ');
    const ul = $('#sensor-unit-label');
    if (ul) ul.textContent = m.unit ? t('sensor_detail_unit_label', m.unit) : '';
    const bv = $('#sensor-bigval');
    if (bv) bv.textContent = fmtSensorValue(s.type, L.x);
    ['x', 'y', 'z'].forEach(ax => {
      const el = $('#sensor-ax-' + ax);
      if (el) el.textContent = fmtSensorValue(s.type, L[ax]);
    });
    const ex = $('#sensor-extra');
    if (ex) ex.innerHTML = sensorExtraHtml(s, m);
    const acc = $('#sensor-acc');
    if (acc) acc.innerHTML = sensorAccuracyHtml(L.accuracy);
    // 心率健康卡「最高/最低/平均」实时刷新（SensorDetailScreen.kt:241-244）
    if (m.isHeart) {
      const agg = heartRateAggregate(s);
      const setTxt = (id, val) => { const el = $(id); if (el) el.textContent = val; };
      setTxt('#hr-max', agg ? agg.max : '---');
      setTxt('#hr-min', agg ? agg.min : '---');
      setTxt('#hr-avg', agg ? agg.avg : '---');
    }
    if (m.isStep) {
      // STEP_COUNTER(19) 直接读累计值；STEP_DETECTOR(18) = 基线 + 本次会话检出步数
      const today = m.isStepCounter
        ? Math.round(L.x)
        : (s._sim.todaySteps + (s._sim.detectorSteps || 0));
      const st = $('#step-today'); if (st) st.textContent = today;
      if (m.isStepCounter) s._sim.todaySteps = today;
    }
  },
  stop() { clearInterval(this.timer); this.timer = null; }
};

/* ============================================================
 * Tab 8 — 详情 DeviceScreen
 * ============================================================ */
function screenDevice() {
  const D = DeviceDetail;
  /* 详情页页首: Text "设备详情" 18px Bold（无 InfoCard），section 顺序照 DeviceScreen.kt */
  const sect = (title, rows) => `<div class="cyber-card card-enter">
    <div class="section-title">${title}</div>${rows}</div>`;
  const secTitle = t => `<div style="font-size:18px;font-weight:700;color:var(--text-primary);margin-top:2px">${t}</div>`;
  const propsRows = Array.from({ length: D.oem.props }, (_, i) =>
    kvRow(`ro.cyber.build.variant_${i}`, ['cyb_x7-user', 'release-keys', '2026-08-05'][i], 'mono')).join('');
  return `
    ${secTitle(t('device_title'))}
    ${sect(t('device_section_soc'), `
      ${kvRow(t('device_soc_model_label'), `${D.socManufacturer} ${esc(Device.socName.replace(' (simulated)', ''))}`)}
      ${kvRow(t('device_platform_codename_label'), esc(Device.platformCodename), 'mono')}
      ${kvRow(t('device_cpu_arch'), Device.arch)}
      ${kvRow(t('device_core_topology_label'), t('cpu_topology_format', Device.cpuBigCores, Device.cpuLittleCores))}
      ${kvRow(t('device_build_id_label'), esc(Device.buildId), 'mono')}
      ${kvRow(t('device_security_patch_label'), Device.patch)}`)}
    ${sect(t('device_section_cpu_cache'), `
      ${kvRow(t('device_l1_instruction_cache_label'), D.cache.L1I)}
      ${kvRow(t('device_l1_data_cache_label'), D.cache.L1D)}
      ${kvRow(t('device_l2_cache_label'), D.cache.L2)}
      ${kvRow(t('device_l3_cache_label'), D.cache.L3)}
      ${kvRow(t('device_data_source_label'), esc(D.cache.source), 'mono')}`)}
    ${sect(t('device_section_gpu'), `
      ${kvRow(t('device_gpu_model_label'), esc(Device.gpuModel))}
      ${kvRow(t('device_gpu_vendor_label'), esc(D.gpuVendor))}
      ${kvRow('OpenGL ES', D.glEsVersion)}
      ${kvRow(t('device_driver_version_label'), esc(D.gpuDriver))}`)}
    ${sect('Vulkan', `
      ${kvRow(t('device_api_version_label'), `Vulkan ${D.vulkan.version}`)}
      ${kvRow(t('device_hardware_level_label'), esc(D.vulkan.level))}
      ${kvRow(t('device_gpu_model_label'), esc(D.vulkan.gpuName))}
      ${kvRow(t('device_physical_devices_label'), t('web_sim_phys_dev_count', D.vulkan.devCount))}
      ${kvRow(t('device_gl_extensions_label'), t('web_sim_gl_ext_summary', D.vulkan.extEnabled, D.vulkan.extTotal))}
      ${D.vulkan.keyExts.map(e => kvRow(`  ${e}`, '✓', e.includes('ray_tracing') ? 'success' : 'purple')).join('')}
      ${kvRow(t('device_ray_tracing_label'), `${D.vulkan.rayTracing ? t('common_supported') : t('common_unsupported')} · ${D.vulkan.rayTracingSource}`, D.vulkan.rayTracing ? 'success' : 'warning')}`)}
    ${sect(t('device_section_display'), `
      ${kvRow(t('device_resolution_label'), `${Device.screenW} × ${Device.screenH}`)}
      ${kvRow(t('device_density_label'), `${parseInt(Device.dpi)} dpi (${(parseInt(Device.dpi) / 160).toFixed(1)}×)`)}
      ${kvRow(t('device_refresh_rate_label'), Device.refreshRate.toFixed(1) + ' Hz')}
      ${kvRow(t('device_physical_size_label'), D.display.physicalSize)}
      ${kvRow(t('device_panel_tech'), D.display.panelTech)}
      ${kvRow(t('device_color_depth_label'), D.display.colorDepth)}
      ${kvRow(t('device_color_gamut_label'), D.display.colorGamut)}
      ${kvRow('HDR', D.display.hdr)}
      ${kvRow(t('device_peak_brightness_label'), `${D.display.peakBrightness} nits`)}
      ${kvRow(t('device_touchscreen_label'), D.display.touch)}
      <div class="kv-row" id="hdr-entry" style="cursor:pointer"><span class="kv-label">${esc(t('device_hdr_lab_entry'))}</span><span class="kv-value" style="color:#00D4FF">${t('device_hdr_lab_entry_value')}</span></div>`)}
    ${sect(t('device_section_memory_spec'), `
      ${kvRow(t('device_memory_type_label'), D.memory.type)}
      ${kvRow(t('device_memory_frequency_label'), `${D.memory.speedMHz} MHz`)}
      ${kvRow(t('device_data_source_label'), esc(D.memory.source), 'mono')}`)}
    ${sect(t('device_section_storage_spec'), `
      ${kvRow(t('device_storage_type_label'), D.storage.type)}
      ${kvRow(t('device_protocol_label'), D.storage.protocol)}`)}
    ${sect(t('device_section_camera'), D.camera.map(c => `
      <div style="margin-bottom:8px"><div style="font-size:13px;font-weight:600;color:var(--neon-purple)">${c.facing}</div>
      ${kvRow(t('device_camera_resolution_label'), c.resolution)}${kvRow(t('device_aperture_label'), c.aperture)}${kvRow(t('device_focal_length_label'), c.focal)}${kvRow(t('device_pixel_size_label'), c.pixel)}${kvRow(t('device_features_label'), c.features)}</div>`).join(''))}
    ${sect(t('device_section_audio'), `
      ${kvRow(t('device_speaker_label'), D.audio.stereo ? t('audio_stereo') : t('audio_mono'))}
      ${kvRow(t('device_output_sample_rate_label'), D.audio.sampleRate)}
      ${kvRow(t('device_hires_audio_label'), D.audio.hiRes ? t('common_yes') : '-')}
      ${kvRow(t('device_audio_formats_label'), D.audio.formats)}`)}
    ${sect(t('device_section_sim'), `
      ${kvRow(t('network_operator'), esc(D.sim.operator))}
      ${kvRow('MCC / MNC', D.sim.mccMnc, 'mono')}
      ${kvRow(t('device_network_standard'), D.sim.network)}
      ${kvRow(t('device_dual_sim_label'), D.sim.dualSim ? t('common_yes') : t('common_unsupported'))}`)}
    ${sect(t('device_section_connection'), `
      ${kvRow(t('device_bluetooth_label'), D.connect.bt)}
      ${kvRow(t('device_bt_name'), esc(D.connect.btName))}
      ${kvRow(t('device_bt_address'), esc(D.connect.btAddr), 'mono')}
      ${kvRow('WiFi', esc(D.connect.wifi))}
      ${kvRow('NFC', D.connect.nfc ? t('common_yes') : t('common_unsupported'))}
      ${kvRow('USB', D.connect.usb)}
      ${kvRow(t('device_infrared_label'), D.connect.ir ? t('common_yes') : '')}
      ${kvRow('UWB', D.connect.uwb ? t('common_yes') : '')}
      ${kvRow(t('device_wireless_charging_label'), D.connect.wirelessCharging ? t('common_yes') : '')}`)}
    ${sect(t('health_summary_title'), `
      <div style="text-align:center;padding:4px 0">
        <div style="font-size:36px;font-weight:700;color:#00D4FF">${D.health.todaySteps}</div>
        <div style="font-size:11px;color:var(--text-secondary)">${t('step_today_title')}</div>
      </div>
      ${kvRow(t('device_step_total_label'), String(D.health.totalSteps))}
      ${kvRow(t('device_step_boot_label'), String(D.health.bootSteps))}`)}
    ${sect(t('device_section_codecs'), `
      ${kvRow(t('device_video_decode_label'), D.codecs.video)}
      ${kvRow(t('device_audio_decode_label'), D.codecs.audio)}
      ${kvRow(t('device_hw_acceleration_label'), D.codecs.hw)}`)}
    ${sect(t('device_section_thermal'), `
      ${kvRow(t('web_sim_thermal_zone_count'), String(D.thermal.zones))}
      ${kvRow(t('device_thermal_zone_types'), esc(D.thermal.types))}`)}
    ${sect('DRM / Widevine', `
      ${kvRow(t('web_sim_widevine_level'), D.drm.widevine, D.drm.widevine === 'L1' ? 'success' : 'warning')}
      ${kvRow(t('web_sim_drm_scheme'), D.drm.schemes)}`)}
    ${sect(t('device_section_security'), `
      ${kvRow('TEE / TrustZone', D.security.tee ? t('common_yes') : t('common_not_detected'), D.security.tee ? 'success' : 'warning')}
      ${kvRow('Verified Boot', D.security.secureBoot ? t('common_activated') : t('common_not_activated'), D.security.secureBoot ? 'success' : 'warning')}
      ${kvRow(t('device_file_encryption_label'), D.security.fileEncryption === 'enc_fbe' ? t('enc_fbe') : D.security.fileEncryption)}
      ${kvRow('SELinux', D.security.selinux ? t('selinux_enforcing') : t('selinux_permissive'), D.security.selinux ? 'success' : 'warning')}`)}
    ${sect(t('device_section_identifiers'), `
      ${kvRow(t('device_fingerprint_label'), esc(D.identifiers.fingerprint), 'mono cyan')}
      ${kvRow('Android ID', D.identifiers.androidId, 'mono cyan')}
      ${kvRow(t('device_serial_number_label'), esc(D.identifiers.serial), 'mono cyan')}`)}
    ${sect('Bootloader', `
      ${kvRow(t('device_version_label'), esc(D.bootloader.version), 'mono')}
      ${kvRow(t('device_unlock_status_label'), D.bootloader.unlocked ? t('device_unlocked') : t('device_locked'), D.bootloader.unlocked ? 'error' : 'success')}
      ${kvRow(t('device_verify_boot'), D.bootloader.verifyBoot ? t('common_enabled') : t('common_not_enabled'))}`)}
    ${sect(t('device_section_runtime'), `
      ${kvRow(t('device_java_runtime_label'), D.runtime.java, 'purple')}
      ${kvRow(t('device_openssl_version_label'), D.runtime.openssl, 'purple')}
      ${kvRow(t('device_build_time_label'), D.runtime.buildTime, 'purple')}`)}
    ${secTitle(t('oem_title'))}
    ${sect(`${D.oem.osName} · ${D.oem.oem}`, `
      ${kvRow(t('oem_version_label'), D.oem.osVersion)}
      ${kvRow(t('device_build_id_label'), esc(Device.buildId), 'mono')}
      ${kvRow(t('device_security_patch_label'), Device.patch)}`)}
    ${sect(t('device_performance_mode_title'), `
      ${kvRow(t('device_game_mode_label'), D.oem.perfGameMode ? t('common_yes') : t('common_not_activated'))}
      ${kvRow(t('oem_current_governor_label'), D.oem.perfMode)}`)}
    ${sect(t('device_oem_subsystem_title'), D.oem.subsystems.map(([k, v]) => kvRow(k, esc(v))).join(''))}
    ${sect(t('web_sim_props_count', D.oem.props), propsRows)}
    <div style="padding:6px 2px 24px;text-align:center;color:var(--text-secondary);font-size:11px;letter-spacing:.4px">
      Cyber Monitor Pro · v6.0.606.0 · Kotlin 2.2.10 · Compose · Bat Theme · by Rickeal-Boss
    </div>`;
}

function bindDevice() {
  $('#hdr-entry').addEventListener('click', openHdrLab);
}

/* ============================================================
 * 覆盖层 ① — 设置页（圆形水波纹展开）
 * ============================================================ */
function openSettings(e) {
  const ov = $('#overlay-settings');
  ov.innerHTML = overlayWaveBody(settingsContent());
  showWave(ov, e && e.currentTarget ? waveOriginFrom(e.currentTarget) : null);
  if (!ov.dataset.bound) {
    ov.dataset.bound = '1';
    ov.addEventListener('click', e => {
      /* T8 —— [data-toggle] 与 [data-layout-toggle] 一律交给 makeSwitch（bindSwitches），
         此处不再手写 toggle，否则 click 委托与 makeSwitch 的 click 各翻转一次、净效果为不变。 */
      if (e.target.closest('#lang-row')) openLangDialog(ov);
    });
    makeSliderInteractive(ov);
  }
  bindSwitches(ov);        // 每次重建 innerHTML 后重新接管（元素是新的）
  applyEffectsVisuals();
}

/* T9 —— GlowBackButton（GlowBackButton.kt:99 LightCircleBackButton）。
   它挂在 .overlay 下（index.html:34-45 静态备份），而 overlayWaveBody() 会整体重写
   #overlay-settings / #overlay-float 的 innerHTML → 必须由模板一并输出，否则丢失。 */
/* ── GlowBackButton 返回按钮 (GlowBackButton.kt) ──────────────────────────
 * ① 矢量图标：CyberIcons.ArrowBack (CyberIcons.kt:265-275) 三条 stroke path，
 *    逐字等同 icons.js 的 arrow_back。三层错位绘制模拟 soft-focus 景深：
 *      底层 ×1.40 α×0.14 偏移(0.8,0.8) / 中层 ×1.12 α×0.30 偏移(0.4,0.4) / 顶层 ×1.00 α
 * ② 拖动形变：非对称果冻拉伸 + tanh 饱和位移 + 取消态（拖距 ≥40dp 不触发返回）
 *    常量全部来自 GlowBackButton.kt:62-79 / :108-215 / :248-262
 * ------------------------------------------------------------------------ */
const GBB = {
  BTN: 48,                    // btnSize = 48.dp（4 处调用全传 48，:102 的 40 默认值未用上）
  CANCEL_DP: 40,              // :62 CANCEL_THRESHOLD_DP — 拖距 ≥ 此值 → 取消，不返回
  MAX_STRETCH: 1.48,          // :69 拖拽方向最大拉伸
  MIN_STRETCH: 0.89,          // :76 垂直方向收缩
  TANH_K: 0.15,               // :79 tanh 位移饱和初始斜率
};

/** ArrowBack 三条 stroke path（CyberIcons.kt:269-274 逐字），24×24 viewBox */
function arrowBackPaths() {
  return '<path class="p0" d="M15,4 L6,12 L15,20"/>' +
         '<path class="p1" d="M6,12 L20,12"/>' +
         '<path class="p2" d="M15,4 L12,7 L15,8 M15,20 L12,17 L15,16"/>';
}
function arrowBackSvg(cls) {
  return `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true">${arrowBackPaths()}</svg>`;
}
function glowBackBtnHtml() {
  return '<button class="glow-back-btn" data-close="1" type="button" title="' + t('common_back') + '" aria-label="' + t('common_back') + '">' +
         '<span class="gbb-glass"></span>' +
         '<span class="gbb-icon">' +
           arrowBackSvg('gbb-l1') + arrowBackSvg('gbb-l2') + arrowBackSvg('gbb-l3') +
         '</span></button>';
}

/**
 * 绑定返回键拖拽形变。松手判定（:248-262）：
 *   拖距 < 40dp → 触发返回（delay 20ms 后 onClick）；≥ 40dp → 取消，仅弹回原位
 */
function bindGlowBackDrag(btn) {
  if (!btn || btn.dataset.gbb === '1') return;
  btn.dataset.gbb = '1';

  let startX = 0, startY = 0, dx = 0, dy = 0, pid = null, interacting = false;
  /* setPointerCapture 后浏览器会把原生 click 一并重定向到本按钮（即使松手点已移出按钮范围），
     该 click 会直接命中 [data-close="1"] 的 document 委托 → 取消态也会被误关。
     故一律先抑制原生 click，改由下面按判定结果决定是否补发，时序对齐 :256 的 delay(20)。 */
  let suppressClick = false;
  btn.addEventListener('click', e => {
    if (!suppressClick) return;
    suppressClick = false;
    e.stopPropagation();
    e.preventDefault();
  }, true);   // 捕获阶段：先于 document 的冒泡委托执行

  /* 逐帧应用形变 —— 公式逐字对齐 GlowBackButton.kt:190-212 */
  function apply() {
    const dist = Math.hypot(dx, dy);
    /* :113-116 dragProgress = (dist / CANCEL_THRESHOLD_DP).coerceIn(0,1) —— 单位 dp，网页 1:1 px */
    const p = Math.min(1, Math.max(0, dist / GBB.CANCEL_DP));

    /* :122-127 pressScale：按下且几乎没拖动时才缩到 .92 */
    const pressScale = (interacting && p < 0.05) ? 0.92 : 1.0;
    /* :132-137 cancelAlpha：>0.95 才开始极快衰减，下限 .3 */
    const cancelAlpha = p > 0.95 ? Math.min(1, Math.max(0.3, 1 - (p - 0.95) / 0.05)) : 1.0;
    /* :139-143 cancelScale：>0.90 缩到 .80 */
    const cancelScale = p > 0.90 ? 0.80 : 1.0;
    /* :146-152 snapBackScale：松手后归 1 */
    const snapBack = interacting ? pressScale : 1.0;

    /* :164-168 stretchFactor：拖动超过 5% 阈值才起效 */
    const n = (interacting && p > 0.05) ? p : 0;
    /* :170-179 tanh 饱和位移，maxOffset = btnSize × 0.5 = 24 */
    const maxOffset = GBB.BTN * 0.5;
    const tx = n > 0 ? maxOffset * Math.tanh(GBB.TANH_K * dx / maxOffset) : 0;
    const ty = n > 0 ? maxOffset * Math.tanh(GBB.TANH_K * dy / maxOffset) : 0;

    /* :196-206 无旋转形变：cos²/sin² 分解到 X/Y（源码明确不加 rotationZ） */
    const ang = (dx === 0 && dy === 0) ? 0 : Math.atan2(dy, dx);
    const c = Math.cos(ang), s = Math.sin(ang);
    const along = 1 + (GBB.MAX_STRETCH - 1) * n;   // 拖拽方向 1.48x
    const perp = 1 + (GBB.MIN_STRETCH - 1) * n;    // 垂直方向 0.89x
    const sx = along * c * c + perp * s * s;
    const sy = along * s * s + perp * c * c;

    /* :277-300 iconAlpha —— 拖拽时不衰减（V3 恒定亮度策略） */
    const iconAlpha = Math.min(0.95, Math.max(0.40, 0.62 + 0.33 * (1 - Math.min(p, 0.7))));

    btn.style.setProperty('--sx', (snapBack * Math.min(Math.max(sx, 0.75), GBB.MAX_STRETCH) * cancelScale).toFixed(4));
    btn.style.setProperty('--sy', (snapBack * Math.min(Math.max(sy, 0.75), GBB.MAX_STRETCH) * cancelScale).toFixed(4));
    btn.style.setProperty('--tx', tx.toFixed(2) + 'px');
    btn.style.setProperty('--ty', ty.toFixed(2) + 'px');
    btn.style.setProperty('--ga', cancelAlpha.toFixed(3));
    btn.style.setProperty('--ia', iconAlpha.toFixed(3));
  }

  function reset() {
    interacting = false; dx = dy = 0;
    btn.classList.remove('dragging');
    apply();
  }

  btn.addEventListener('pointerdown', e => {
    startX = e.clientX; startY = e.clientY; dx = dy = 0;
    pid = e.pointerId; interacting = true; suppressClick = false;
    btn.classList.add('dragging');
    try { btn.setPointerCapture(pid); } catch (_) {}
    e.preventDefault();          /* 阻止文本选中/原生拖拽干扰形变 */
    apply();
  });
  btn.addEventListener('pointermove', e => {
    if (!interacting) return;
    dx = e.clientX - startX; dy = e.clientY - startY;
    apply();
  });
  const end = e => {
    if (!interacting) return;
    /* :248-262 松手判定：拖距 < 阈值 → 返回；≥ 阈值 → 取消（弹回原位，不触发） */
    const total = Math.hypot(e.clientX - startX, e.clientY - startY);
    const cancelled = total >= GBB.CANCEL_DP;
    suppressClick = true;                 // 先吞掉浏览器随后补发的原生 click
    reset();
    if (!cancelled) {
      /* :254-256 delay(20) → onClick()，让果冻回弹先起步再关闭 */
      setTimeout(() => {
        suppressClick = false;            // 放行这一次手动派发
        try { btn.click(); } catch (_) {}
      }, 20);
    }
  };
  btn.addEventListener('pointerup', end);
  btn.addEventListener('pointercancel', () => reset());
}

function overlayWaveBody(inner, title = t('settings_title')) {
  return `
    ${glowBackBtnHtml()}
    <div style="position:absolute;inset:0;background:linear-gradient(180deg,#12101c,#0A0A0F 40%)"></div>
    <div class="overlay-content" style="animation:cardReveal .45s ease both">
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
        <span style="font-size:17px;font-weight:700;padding-left:44px">${esc(title)}</span>
      </div>
      ${inner}
    </div>`;
}

/* FancySlider 构造。fmt: raw|sec|x|ms|sp|pct|tier|ms-snap|haptic；snap: 离散档位(csv) */
function slider(id, min, max, step, value, fmt = 'raw', snap = '', hideTxt = false) {
  return `<div style="flex:1;padding:0 10px;min-width:120px">
    <div class="fancy-slider" id="${id}" data-min="${min}" data-max="${max}" data-step="${step}"
         data-value="${value}" data-fmt="${fmt}" data-snap="${snap}">
      <div class="rail"></div><div class="fill"></div><div class="thumb"><span class="gear">${iconSvg('settings', 16)}</span></div>
    </div>
    <div style="font-size:10.5px;color:var(--neon-cyan);text-align:center${hideTxt ? ';display:none' : ''}" id="${id}-txt"></div>
  </div>`;
}
function fmtSliderText(sl, v) {
  switch (sl.dataset.fmt) {
    case 'sec': return (v / 1000).toFixed(1) + 's';
    case 'x':   return (+v).toFixed(1) + '×';
    /* 悬浮窗刷新档位精确显示: 500→"0.5s"，整数秒不带 .0（formatMs 逐字） */
    case 'ms':  return v === 500 ? '0.5s' : (v / 1000) + 's';
    /* 模块刷新：连续滑条，显示始终 snap 到最近档位 0.5/1/2/3/5s（msToLabel 逐字，整数秒不带 .0） */
    case 'ms-snap': {
      const snaps = (sl.dataset.snap || '').split(',').map(s => s.trim()).filter(s => s !== '').map(Number).filter(n => !isNaN(n));
      const nearest = snaps.length ? snaps.reduce((a, b) => Math.abs(b - v) < Math.abs(a - v) ? b : a) : v;
      return nearest < 1000 ? '0.5s' : (nearest / 1000) + 's';
    }
    /* 触觉强度档位：1=轻柔 2=标准 3=强烈（settings_haptic_light/standard/heavy 逐字） */
    case 'haptic': {
      const labels = ['', t('settings_haptic_light'), t('settings_haptic_standard'), t('settings_haptic_heavy')];
      return labels[Math.round(clamp(v, 1, 3))] || '';
    }
    /* 文字大小 "${v}sp" / 窗口透明度 "${(v*100)|0}%" */
    case 'sp':  return Math.round(v) + 'sp';
    case 'pct': return Math.round(v * 100) + '%';
    /* 电流校准 4 挡: 1.0×/10.0×/100.0×/1000.0× */
    case 'tier': return MULT_TIERS[Math.round(clamp(v, 0, 3))] ? MULT_TIERS[Math.round(clamp(v, 0, 3))].toFixed(1) + '×' : '1.0×';
    default:    return (+v).toString();
  }
}

function settingsContent() {
  const S = Sim.settings;
  const onText = t('settings_haptic_on'), offText = t('settings_haptic_off');
  const stTxt = on => on ? onText : offText;

  /* 模块刷新频率：CPU/GPU/内存/电池（与 SettingsScreen.moduleConfigs 一致，GPS 已移除） */
  const tiers = [500, 1000, 2000, 3000, 5000];
  const tierLabel = ms => ms < 1000 ? '0.5s' : (ms / 1000) + 's';
  const intervalSlider = (key, name, desc) => {
    const id = 'iv-' + key;
    return `
    <div class="cyber-card card-enter" style="padding:14px 16px;margin-bottom:12px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <div style="display:flex;align-items:center">
          <span style="font-size:15px;font-weight:600">${name}</span>
          <span style="font-size:11px;color:var(--text-secondary);margin-left:8px">${desc}</span>
        </div>
        <span style="font-size:24px;font-weight:700;color:var(--neon-purple-bright);font-family:var(--font-num)" id="${id}-bigval"></span>
      </div>
      ${slider(id, 500, 5000, 10, S.intervals[key], 'ms-snap', '500,1000,2000,3000,5000')}
      <div style="display:flex;align-items:center;font-size:10px;color:rgba(61,112,184,.7);margin-top:4px">
        <span>${tierLabel(tiers[0])}</span>
        <div style="flex:0.111"></div>
        <span>${tierLabel(tiers[1])}</span>
        <div style="flex:0.222"></div>
        <span>${tierLabel(tiers[2])}</span>
        <div style="flex:0.222"></div>
        <span>${tierLabel(tiers[3])}</span>
        <div style="flex:0.445"></div>
        <span>${tierLabel(tiers[4])}</span>
      </div>
    </div>`;
  };

  const hapticIntensityBlock = `
    <div id="haptic-intensity-block" style="display:${S.hapticsOn ? '' : 'none'}">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-top:12px">
        <span style="font-size:14px;color:var(--text-primary)">${t('settings_haptic_intensity')}</span>
        <span style="font-size:12px;color:var(--neon-purple-bright);font-weight:600" id="sl-haptic-bigval"></span>
      </div>
      <div style="margin-top:4px">${slider('sl-haptic', 1, 3, 1, S.hapticLevel, 'haptic', '', true)}</div>
      <div style="display:flex;justify-content:space-between;font-size:10px;color:rgba(61,112,184,.7);margin-top:2px;padding:0 4px">
        <span>${t('settings_haptic_weak')}</span><span>${t('settings_haptic_medium')}</span><span>${t('settings_haptic_strong')}</span>
      </div>
    </div>`;

  const lightIcon = iconSvg('light', 20);
  const globalLightCard = `
    <div class="cyber-card card-enter" style="padding:14px 16px;margin-bottom:12px">
      <div style="display:flex;align-items:flex-start;justify-content:space-between">
        <div style="display:flex;align-items:center">
          <span style="color:var(--neon-purple-bright);display:inline-flex">${lightIcon}</span>
          <div style="margin-left:8px">
            <div style="font-size:15px;font-weight:600;color:var(--text-primary)">${t('settings_globallight')}</div>
            <div style="font-size:11px;color:var(--text-secondary)">${t('settings_globallight_desc')}</div>
            <div style="font-size:11px;color:var(--text-secondary)" id="st-globallight">${stTxt(S.globalLight)}</div>
          </div>
        </div>
        <div class="switch ${S.globalLight ? 'on' : ''}" data-toggle="globalLight">${joySwitchHtml()}</div>
      </div>
    </div>`;

  const turboXdrCard = `
    <div class="cyber-card card-enter" style="padding:14px 16px;margin-bottom:12px">
      <div style="display:flex;align-items:flex-start;justify-content:space-between">
        <div style="display:flex;align-items:center">
          <span style="color:var(--neon-purple-bright);display:inline-flex">${lightIcon}</span>
          <div style="margin-left:8px">
            <div style="font-size:15px;font-weight:600;color:var(--text-primary)">${'CyberNightlight TurboXDR'}</div>
            <div style="font-size:11px;color:var(--text-secondary)">${t('settings_turboxdr_desc')}</div>
            <div style="font-size:11px;color:var(--text-secondary)" id="st-turboxdr">${stTxt(S.turboXdr)}</div>
          </div>
        </div>
        <div class="switch pink ${S.turboXdr ? 'on' : ''}" data-toggle="turboXdr">${joySwitchHtml()}</div>
      </div>
      <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-top:12px">
        <div><div style="font-size:14px;color:var(--text-primary)">${t('device_hdr_intensity')}</div>
          <div style="font-size:11px;color:var(--text-secondary)">${t('device_hdr_intensity_hint')}</div></div>
        <span style="font-size:14px;color:var(--neon-purple-bright);font-weight:600" id="sl-xdr-bigval"></span>
      </div>
      <div style="margin-top:4px">${slider('sl-xdr', 1.0, 8.0, 0.1, S.turboXdrStrength, 'x')}</div>
      <div style="display:flex;justify-content:space-between;font-size:10px;color:rgba(61,112,184,.7);margin-top:2px">
        <span>1.0×</span><span>8.0×</span>
      </div>
      <div id="xdr-intensity-off" style="font-size:11px;color:rgba(61,112,184,.7);margin-top:4px;display:${S.turboXdr ? 'none' : ''}">${t('device_hdr_intensity_off')}</div>
    </div>`;

  const nightBarCard = `
    <div class="cyber-card card-enter" style="padding:14px 16px;margin-bottom:12px">
      <div style="display:flex;align-items:flex-start;justify-content:space-between">
        <div style="display:flex;align-items:center">
          <span style="color:var(--neon-purple-bright);display:inline-flex">${lightIcon}</span>
          <div style="margin-left:8px">
            <div style="font-size:15px;font-weight:600;color:var(--text-primary)">${t('settings_nightlight_bar')}</div>
            <div style="font-size:11px;color:var(--text-secondary)">${t('settings_nightlight_bar_desc')}</div>
            <div style="font-size:11px;color:var(--text-secondary)" id="st-nightbar">${stTxt(S.nightBar)}</div>
          </div>
        </div>
        <div class="switch ${S.nightBar ? 'on' : ''}" data-toggle="nightBar">${joySwitchHtml()}</div>
      </div>
    </div>`;

  return `
    <div class="settings-row" id="lang-row" style="cursor:pointer;margin-bottom:12px">
      <div><div style="font-size:14px;font-weight:600">${t('settings_language_title')}</div>
        <div style="font-size:11px;color:var(--text-secondary)">${t('settings_language_desc')}</div></div>
      <span style="font-size:13px;color:var(--neon-purple-bright)" id="lang-cur">${S.langLabel} ›</span>
    </div>

    <div class="settings-row" style="margin-bottom:12px">
      <div><div style="font-size:14px;font-weight:600">${t('web_sim_adaptive_layout')}</div>
        <div style="font-size:11px;color:var(--text-secondary)">${t('web_sim_adaptive_layout_desc')}</div>
        <div style="font-size:11px;color:var(--text-secondary)" id="st-layout">${stTxt(layoutMode() === 'adaptive')}</div></div>
      <div class="switch ${layoutMode() === 'adaptive' ? 'on' : ''}" data-layout-toggle>${joySwitchHtml()}</div>
    </div>

    <div class="section-title" style="margin:4px 0 10px">${t('settings_module_refresh')}</div>
    ${intervalSlider('cpu', 'CPU', t('module_cpu_desc'))}
    ${intervalSlider('gpu', 'GPU', t('module_gpu_desc'))}
    ${intervalSlider('mem', t('tab_memory'), t('module_memory_desc'))}
    ${intervalSlider('battery', t('tab_battery'), t('module_battery_desc'))}

    <div class="cyber-card card-enter" style="padding:14px 16px;margin-bottom:12px">
      <div style="font-size:15px;font-weight:600;color:var(--text-primary)">${t('settings_haptic')}</div>
      <div style="font-size:12px;color:var(--text-secondary);margin-top:2px">${t('settings_haptic_desc')}</div>
      <div class="settings-row" style="margin-top:12px;padding:0">
        <div><div style="font-size:14px;color:var(--text-primary)">${t('settings_haptic_switch')}</div>
          <div style="font-size:11px;color:var(--text-secondary)" id="st-haptic">${stTxt(S.hapticsOn)}</div></div>
        <div class="switch ${S.hapticsOn ? 'on' : ''}" data-toggle="hapticsOn">${joySwitchHtml()}</div>
      </div>
      ${hapticIntensityBlock}
    </div>

    ${globalLightCard}
    ${turboXdrCard}
    ${nightBarCard}

    <div class="cyber-card card-enter" style="padding:14px 16px">
      <div style="font-size:16px;font-weight:700;color:var(--text-primary)">${'Cyber Android Monitor Pro'}</div>
      <div style="font-size:13px;color:var(--text-secondary);margin-top:2px">${'v6.0.606.0'}</div>
      <div style="font-size:12px;color:rgba(226,232,240,.6);margin-top:2px">${'Kotlin 2.2.10 · Compose · Bat Theme'}</div>
      <div style="font-size:13px;font-weight:600;color:var(--neon-purple-bright);margin-top:2px">${'by Rickeal-Boss'}</div>
      <div style="text-align:center;margin-top:12px;font-size:10.5px;color:var(--text-secondary)">
        ${t('web_sim_web_disclaimer')}
      </div>
    </div>`;
}

/* FancySlider 交互引擎：pointer 拖动即实时 onValueChange（源码为连续回调） */
/* 滑条松手吸附到 data-snap 档位（csv）。为空则原样返回。
   对齐 SettingsScreen.kt:327 snapToOption() / FloatingWindowScreen.kt:239 */
function snapToOptions(sl, v) {
  const snaps = (sl.dataset.snap || '').split(',').map(s => s.trim()).filter(s => s !== '').map(Number).filter(n => !isNaN(n));
  return snaps.length ? snaps.reduce((a, b) => Math.abs(b - v) < Math.abs(a - v) ? b : a) : v;
}

function makeSliderInteractive(container) {
  $$('.fancy-slider[data-step]', container).forEach(sl => {
    if (sl.dataset.init) return;
    sl.dataset.init = '1';
    const min = +sl.dataset.min, max = +sl.dataset.max, step = +sl.dataset.step;
    const txt = $('#' + sl.id + '-txt');

    function paint(v) {
      const p = clamp((v - min) / (max - min), 0, 1);
      $('.fill', sl).style.width = p * 100 + '%';
      $('.thumb', sl).style.left = p * 100 + '%';
      const gear = $('.thumb .gear', sl) || $('.thumb svg', sl);
      if (gear) gear.style.transform = 'rotate(' + (1080 * p) + 'deg)';   /* 齿轮随进度旋转（对齐 FancySlider.kt 1080°） */
      sl.dataset.value = v;
      txt && (txt.textContent = fmtSliderText(sl, v));
      const big = $('#' + sl.id + '-bigval');
      big && (big.textContent = fmtSliderText(sl, v));
    }
    function fromClientX(cx, live) {
      const r = sl.getBoundingClientRect();
      let v = min + clamp((cx - r.left) / r.width, 0, 1) * (max - min);
      /* (v/step)*step 恒等于 v，只做了两位取整 → 实际吸附到 step 网格。
         ⚠ 吸附后必须消除二进制浮点残差：Math.round(7.3/0.1)*0.1 === 7.300000000000001，
         脏值会写进 Sim.settings，并让同一挡位的重复拖动被误判为"值变了"。 */
      v = clamp(+(Math.round(v / step) * step).toFixed(6), min, max);
      /* 松手才吸附：源码 steps=0 拖动过程自由，onRelease 走 snapToOption()
         （SettingsScreen.kt:317,327 / FloatingWindowScreen.kt:239） */
      if (!live) v = snapToOptions(sl, v);
      paint(v);
      applySliderChange(sl.id, v, live);
    }
    let dragging = false;
    sl.addEventListener('pointerdown', e => {
      dragging = true; sl.classList.add('dragging');
      sl.setPointerCapture(e.pointerId);
      fromClientX(e.clientX, true);
    });
    sl.addEventListener('pointermove', e => dragging && fromClientX(e.clientX, true));
    const end = e => { if (!dragging) return; dragging = false; sl.classList.remove('dragging'); fromClientX(e.clientX, false); };
    sl.addEventListener('pointerup', end);
    sl.addEventListener('pointercancel', end);
    paint(+sl.dataset.value);
  });
}

/** 滑条值变化 → 数据层/效果联动（live=true 表示拖动中的连续回调） */
function applySliderChange(id, v, live) {
  if (id.startsWith('iv-')) {
    Sim.settings.intervals[id.slice(3)] = v;
    if (!live) restartTickLoop();
  } else if (id === 'sl-haptic') {
    Sim.settings.hapticLevel = v;
  } else if (id === 'sl-xdr') {
    Sim.settings.turboXdrStrength = v;
    /* sl-xdr 量程 1.0-8.0，但消费方 sl-hdr-desired 的上限是 potentialHeadroom(3.6)；
       不钳制会把越界值写进去，使 fill 宽度 >100% */
    const dv = clamp(v, 0, Sim.hdrLab.potentialHeadroom);
    Sim.hdrLab.desired = dv;                   /* 与 HDR 实验室滑条双向同款 */
    applyEffectsVisuals();
    if ($('#overlay-hdr').classList.contains('active')) syncHdrDesiredSlider(dv);
  } else if (id === 'sl-float-refresh') {
    Sim.floatWindow.refreshMs = v;
    restartFloatBallTimer();                   /* 让「刷新频率」真正驱动浮球，而非只改数据 */
    const modeEl = $('#float-refresh-mode');
    modeEl && (modeEl.textContent = v >= 1000 ? `${t('float_refresh_status')}: ${t('float_refresh_powersave')}` : `${t('float_refresh_status')}: ${t('float_refresh_realtime')}`);
  } else if (id === 'sl-float-ts') {
    Sim.floatWindow.textSize = v; applyFloatPreviewStyle();
  } else if (id === 'sl-float-alpha') {
    Sim.floatWindow.alpha = v; applyFloatPreviewStyle();
  } else if (id === 'sl-hdr-desired') {
    Sim.hdrLab.desired = v; updateHdrDiag();
  } else if (id === 'sl-hdr-turbo') {
    Sim.settings.turboXdrStrength = v; Sim.settings.turboXdr = true; applyEffectsVisuals();
  }
}

/* force 省略时取反（旧调用点）；makeSwitch 传目标绝对值时按绝对值写入。
   force === 当前值时仍会走完整同步链路，保证重建界面后 UI 与数据一致。 */
function toggleSetting(el, force) {
  const key = el.dataset.toggle;
  const on = force === undefined ? !Sim.settings[key] : !!force;
  Sim.settings[key] = on;
  el.classList.toggle('on', on);
  const names = { globalLight: t('settings_globallight'), hapticsOn: t('settings_haptic'), turboXdr: 'TurboXDR', nightBar: t('settings_nightlight_bar') };
  const onText = t('settings_haptic_on'), offText = t('settings_haptic_off');
  toast(`${names[key]}：${on ? t('web_sim_on') : t('web_sim_off')}`);
  applyEffectsVisuals();
  /* 状态行实时同步（与 SettingsScreen 各 *SettingsCard 的 已开启/已关闭 文本一致） */
  const stMap = { globalLight: 'st-globallight', turboXdr: 'st-turboxdr', nightBar: 'st-nightbar', hapticsOn: 'st-haptic' };
  const st = stMap[key] && $('#' + stMap[key]);
  if (st) st.textContent = on ? onText : offText;
  if (key === 'hapticsOn') {
    const blk = $('#haptic-intensity-block');
    blk && (blk.style.display = Sim.settings.hapticsOn ? '' : 'none');
  }
  if (key === 'turboXdr') {
    const off = $('#xdr-intensity-off');
    off && (off.style.display = Sim.settings.turboXdr ? 'none' : '');
    const offHdr = $('#hdr-turbo-off');
    offHdr && (offHdr.style.display = Sim.settings.turboXdr ? 'none' : '');
    const stHdr = $('#st-turboxdr-hdr');
    stHdr && (stHdr.textContent = Sim.settings.turboXdr ? onText : offText);
  }
  /* 触觉反馈拟真：短促发光脉冲 */
  if (Sim.settings.hapticsOn) el.animate(
    [{ boxShadow: '0 0 0 rgba(124,58,237,0)' }, { boxShadow: '0 0 18px rgba(124,58,237,.8)' }, { boxShadow: '0 0 0 rgba(124,58,237,0)' }],
    { duration: [120, 180, 240][Sim.settings.hapticLevel] || 180 });
}

/* 开关联动效果真实作用于页面 */
function applyEffectsVisuals() {
  const phone = $('#phone'), S = Sim.settings;
  $('#night-bar').classList.toggle('show', S.nightBar);
  phone.classList.toggle('glow-on', S.globalLight);
  phone.classList.toggle('xdr-on', S.turboXdr && S.turboXdrStrength > 1);
  /* TurboXDR 强度 1.0×..8.0× → 0..1，驱动局部 HDR 增亮倍率（1.0× 恰 SDR 白 = 关闭） */
  phone.style.setProperty('--xdr', Math.max(0, Math.min(1, (S.turboXdrStrength - 1) / 7)).toFixed(3));
}

/* 语言选择 Dialog */
/* ── 语言切换：真正生效 ────────────────────────────────────────────
 * 源码 LocaleManager + Activity recreate 的等价物：setLang() 写 localStorage
 * 并触发 onLangChange 回调 → applyLang() 重建所有已渲染的 DOM。
 * 仅改数据不重渲染 = 「切了但界面不变」，这正是此前被判定为失效的原因。
 * ------------------------------------------------------------------ */
function applyLang() {
  /* 1. Tab 文本：.tab-item 的文案是裸文本节点，直接改 lastChild.nodeValue，
        不重建 DOM、不触碰 icon（重建会丢掉 active 态与点击绑定） */
  $$('.tab-item').forEach(b => {
    const k = b.dataset.tabKey;
    if (k && b.lastChild) b.lastChild.nodeValue = t(k);
  });

  /* 2. 当前屏：rerenderScreen 会摘掉 renderedOnce 短路记录，强制重建 innerHTML */
  rerenderScreen(activeTab);

  /* 3. 打开中的覆盖层：整体重建内容（各 open* 都是幂等的 innerHTML 赋值） */
  const ov = $('.overlay.active');
  if (ov) {
    if (ov.id === 'overlay-settings') openSettings();
    else if (ov.id === 'overlay-float') openFloatConfig();
    else if (ov.id === 'overlay-sensor') openSensorDetail(+(ov.dataset.idx || 0));
    else if (ov.id === 'overlay-hdr') openHdrLab();
  }

  /* 4. 悬浮球行文案 */
  if (typeof floatBallTick === 'function') floatBallTick();

  /* 5. 设置页「语言」行回显（Sim.settings.langLabel 仅作回显镜像，真正判据是 savedLang()） */
  Sim.settings.langLabel = langLabelOf(savedLang());
  const lc = $('#lang-cur');
  if (lc) lc.textContent = `${Sim.settings.langLabel} ›`;
}

/* 语言变更 → 全量重渲染（i18n.js 的 setLang 会调用这些回调） */
if (typeof onLangChange === 'function') onLangChange(applyLang);

function openLangDialog(scope) {
  const mask = document.createElement('div');
  mask.className = 'lang-dialog-mask';
  /* 与 LocaleManager.SUPPORTED_LANGUAGES 一致（LocaleManager.kt:56-65） */
  const cur = savedLang();
  mask.innerHTML = `
    <div class="lang-dialog">
      <div class="lang-dialog-head">${esc(t('settings_language_dialog_title'))}</div>
      ${LANGS.map(o =>
        `<div class="lang-option ${o.code === cur ? 'current' : ''}" data-lang="${o.code}">
           <span>${esc(o.label)}</span>${o.code === cur ? iconSvg('check', 17) : ''}
         </div>`).join('')}
    </div>`;
  scope.appendChild(mask);
  mask.addEventListener('click', e => {
    const opt = e.target.closest('.lang-option');
    if (opt) {
      setLang(opt.dataset.lang);        /* ← 真正切换：写盘 + 触发 applyLang 重渲染 */
      toast(t('web_sim_lang_switched', langLabelOf(opt.dataset.lang)));
    }
    mask.remove();
  });
}

function waveOriginFrom(btn) {
  const phone = $('#phone').getBoundingClientRect();
  const r = (btn || $('#btn-settings')).getBoundingClientRect();
  return { x: (r.left + r.width / 2 - phone.left) + 'px', y: (r.top + r.height / 2 - phone.top) + 'px' };
}
/* 圆心由 JS 写入 CSS 变量，半径 100% 交给 style.css（.overlay.wave / .open / .closing），
   不再写 inline clip-path——否则 CSS 里的 0%/142%/0% 三档半径全部失效且两处半径不一致 */
function showWave(ov, x, y) {
  let ox, oy;
  if (x && typeof x === 'object') { ox = x.x; oy = x.y; }
  else { ox = x || 'calc(100% - 54px)'; oy = y || '82px'; }
  ov.classList.remove('closing');
  ov.style.setProperty('--ox', ox);
  ov.style.setProperty('--oy', oy);
  ov.classList.add('active');      /* display:block —— 此时半径仍是 .overlay.wave 的 0% */
  void ov.offsetWidth;             /* 强制样式落地，确保 transition 有 before-change style */
  ov.classList.add('open');        /* 0% → 142%，由 CSS transition 播放 */
}
function closeWave(ov) {
  ov.classList.remove('open');
  ov.classList.add('closing');
  /* style.css:620 transition 为 .5s —— 必须 ≥500ms 再摘 active，否则圆圈收缩被硬截断在 ~64% */
  setTimeout(() => ov.classList.remove('active', 'closing'), 520);
}

/* ============================================================
 * 覆盖层 ② — 悬浮窗设置 FloatConfigScreen（水波纹同款）
 * ============================================================ */
/* 总开关副作用（FloatingWindowScreen.kt:144-161 onCheckedChange） */
function setFloatEnabled(v) {
  const FW = Sim.floatWindow;
  FW.enabled = !!v;
  const sw = $('#float-enable-sw');
  sw && sw.classList.toggle('on', FW.enabled);
  $('#float-ball').classList.toggle('show', FW.enabled);
  floatBallTick();
  toast(FW.enabled ? t('float_starting') : t('float_stopped'));
  /* 与源码一致: 仅启用时才渲染 刷新频率/实时指标/外观 三块 → 开关切换后重建页面。
     传 null：overlay 内 click 的 currentTarget 是 #overlay-float（全屏），传下去会让圆心算成屏幕中心 */
  openFloatConfig(null);
}

function openFloatConfig(e) {
  const ov = $('#overlay-float');
  const FW = Sim.floatWindow;
  /* T3 —— CheckItem 是**裸 Row**：padding(vertical = 4.dp) + Checkbox + Text(15.sp)，
     不套 Card（FloatingWindowScreen.kt:263-273）；此前 9 行被包在一张 Card 里并带 .section-title。 */
  const metricChecks = FloatConfig.ALL_METRICS.map(m => `
    <label class="settings-row" style="cursor:pointer;padding:4px 0">
      <span style="font-size:15px;color:var(--text-primary)">${m.label}</span>
      <input type="checkbox" data-float-check="${m.id}" ${FW.visibleMetrics.includes(m.id) ? 'checked' : ''}
        style="accent-color:#7C3AED;width:17px;height:17px"/>
    </label>`).join('');
  const colors = [
    ['#A05CFF', t('float_color_purple')], ['#00D4FF', t('float_color_cyan')], ['#F43F5E', t('float_color_magenta')],
    ['#34C759', t('float_color_green')], ['#FFAB00', t('float_color_amber')], ['#FFFFFF', t('float_color_white')], ['#3D70B8', t('float_color_steel')],
  ];
  /* 背景色板逐字对齐 FloatingWindowScreen.kt bgColorPresets */
  const bgColors = [['#DC0A0A0F', t('float_bg_dark')], ['#E6000000', t('float_bg_black')], ['#DC1E1035', t('float_bg_deep_purple')],
                    ['#DC0A1A2E', t('float_bg_dark_blue')], ['#DC0A241A', t('float_bg_dark_green')], ['#DC2E0A0A', t('float_bg_dark_red')]];

  /* 悬浮球行格式（FloatingWindowService float_*_format 逐字） */

  /* bgColorPresets 是 #AARRGGBB（data.js / FloatingWindowScreen.kt）→ RGB 取末 6 位，
     不能用 slice(0,7)（会把 alpha 字节当成红色通道，6 个色板全部偏红） */
  const fwRgb = hex => '#' + String(hex).replace('#', '').slice(-6);
  const fwBg = fwRgb(FW.bgColor);
  /* T3 —— 顺序对齐 FloatingWindowScreen.kt:132-185：
       启用 Card → [RefreshIntervalCard + Spacer(6.dp) + float_section_realtime 15sp SemiBold 裸标题
                    → 9× CheckItem 裸行 → AppearanceCard] → Spacer(16.dp) + float_overlay_hint 12sp
     外层 Column spacedBy(4.dp)（:126）—— 本屏独有，故用行内 margin 覆盖 .cyber-card 的 18px。 */
  ov.innerHTML = overlayWaveBody(`
    <div class="cyber-card card-enter" style="margin-bottom:4px">
      <div class="settings-row" style="padding:16px 0">
        <span style="font-size:16px;color:var(--text-primary)">${t('float_enable')}</span>
        <div class="switch ${FW.enabled ? 'on' : ''}" id="float-enable-sw">${joySwitchHtml()}</div>
      </div>
    </div>
    ${FW.enabled ? `
    <div class="cyber-card card-enter" style="margin-bottom:10px">
      <div class="section-title">${t('float_refresh_interval')}</div>
      <div style="display:flex;align-items:center">
        ${slider('sl-float-refresh', 500, 5000, 500, FW.refreshMs, 'ms', '500,1000,2000,3000,5000')}
      </div>
      <div style="text-align:center;margin-top:6px;font-size:11.5px" id="float-refresh-mode"></div>
      <div style="display:flex;justify-content:space-between;font-size:9px;color:var(--neon-steel,#7C8DB5);margin-top:4px;padding:0 6px">
        <span>0.5s</span><span>1s</span><span>2s</span><span>3s</span><span>5s</span>
      </div>
    </div>
    <!-- Spacer(6.dp) + float_section_realtime（:257-259）15.sp SemiBold —— 裸标题，不进 Card -->
    <div style="font-size:15px;font-weight:600;color:var(--text-primary);margin-bottom:4px">${t('float_section_realtime')}</div>
    ${metricChecks}
    <div class="cyber-card card-enter" style="margin-bottom:20px">
      <div class="section-title">${t('float_section_style')}</div>
      <div style="border-radius:12px;background:${fwBg};opacity:${FW.alpha};padding:8px 0;text-align:center;color:${FW.textColor};font-size:${FW.textSize}px">GPU 45%   CPU 38°C</div>
      <div style="display:flex;align-items:center;margin-top:12px">
        <span style="font-size:12px;color:var(--text-secondary);width:64px">${t('float_text_size')}</span>
        ${slider('sl-float-ts', 9, 22, 1, FW.textSize, 'sp')}
      </div>
      <div style="display:flex;align-items:center;margin-top:6px">
        <span style="font-size:12px;color:var(--text-secondary);width:64px">${t('float_window_alpha')}</span>
        ${slider('sl-float-alpha', 0.2, 1.0, 0.05, FW.alpha, 'pct')}
      </div>
      <div class="settings-divider" style="margin:12px 0"></div>
      <div style="font-size:12.5px;color:var(--text-secondary);margin-bottom:8px">${t('float_text_color')}</div>
      <div style="display:flex;gap:10px;flex-wrap:wrap" id="float-color-row">
        ${colors.map(([c, n]) => `<div data-float-color="${c}" title="${n}" style="width:28px;height:28px;border-radius:50%;background:${c};border:2px solid ${FW.textColor.toLowerCase() === c.toLowerCase() ? '#fff' : 'transparent'};box-shadow:0 0 8px rgba(124,58,237,.4);cursor:pointer"></div>`).join('')}
      </div>
      <div class="settings-divider" style="margin:12px 0"></div>
      <div style="font-size:12.5px;color:var(--text-secondary);margin-bottom:8px">${t('float_bg_color')}</div>
      <div style="display:flex;gap:10px;flex-wrap:wrap" id="float-bg-row">
        ${bgColors.map(([c, n]) => `<div data-float-bg="${c}" title="${n}" style="width:28px;height:28px;border-radius:50%;background:${fwRgb(c)};border:2px solid ${FW.bgColor === c ? '#fff' : 'transparent'};cursor:pointer"></div>`).join('')}
      </div>
    </div>` : ''}
    <!-- Spacer(16.dp) + float_overlay_hint（:182-185）12.sp onSurfaceVariant @ .6 -->
    <div style="font-size:12px;color:var(--text-secondary);opacity:.6;padding:0 4px 26px;line-height:1.6">
${t('float_permission_hint')}
    </div>
  `, t('float_title'));
  /* 悬浮窗 overlay 的水波纹圆心恒为标题栏悬浮窗按钮：
     overlay 内 click 的 currentTarget 是 #overlay-float（全屏）→ 圆心会跑到屏幕中心 */
  const originBtn = (e && e.currentTarget && e.currentTarget.id === 'btn-float') ? e.currentTarget : $('#btn-float');
  showWave(ov, waveOriginFrom(originBtn));
  if (!ov.dataset.bound) {
    ov.dataset.bound = '1';
    ov.addEventListener('click', e => {
      const col = e.target.closest('[data-float-color]');
      if (col) {
        FW.textColor = col.dataset.floatColor;
        $$('#float-color-row [data-float-color]').forEach(d => d.style.borderColor = 'transparent');
        col.style.borderColor = '#FFF';
        applyFloatPreviewStyle();
      }
      const bg = e.target.closest('[data-float-bg]');
      if (bg) {
        FW.bgColor = bg.dataset.floatBg;
        $$('#float-bg-row [data-float-bg]').forEach(d => d.style.borderColor = 'transparent');
        bg.style.borderColor = '#FFF';
        applyFloatPreviewStyle();
      }
    });
    ov.addEventListener('change', e => {
      const chk = e.target.closest('[data-float-check]');
      if (chk) {
        const id = chk.dataset.floatCheck;
        if (chk.checked) { if (!FW.visibleMetrics.includes(id)) FW.visibleMetrics.push(id); }
        else FW.visibleMetrics = FW.visibleMetrics.filter(x => x !== id);
      }
    });
  }
  /* T8 —— 总开关走 JoySwitch（FloatingWindowScreen.kt:142 CyberJoystickSwitch），
     不再用 click 委托里手写的 toggle；重建页面后重新 makeSwitch（元素是新的）。 */
  makeSwitch($('#float-enable-sw'), () => FW.enabled, v => setFloatEnabled(v));
  makeSliderInteractive(ov);
  const modeEl = $('#float-refresh-mode');
  /* float_refresh_status + powersave/realtime: "状态: 节能" / "状态: 实时"（无 emoji） */
  modeEl && (modeEl.textContent = FW.refreshMs >= 1000 ? `${t('float_refresh_status')}: ${t('float_refresh_powersave')}` : `${t('float_refresh_status')}: ${t('float_refresh_realtime')}`);
  applyFloatPreviewStyle();
}

/* 悬浮球（FloatingWindowService 替身） */
function applyFloatPreviewStyle() {
  const ball = $('#float-ball'), FW = Sim.floatWindow;
  const fwRgb = hex => '#' + String(hex).replace('#', '').slice(-6);
  ball.style.color = FW.textColor;
  ball.style.background = fwRgb(FW.bgColor) + (Math.round(FW.alpha * 255)).toString(16).padStart(2, '0');
  ball.style.opacity = FW.alpha;
}
/* 浮球刷新由 refreshMs 独立驱动 —— 此前只挂在 2s tick 上，滑条改了数据却不改行为 */
let _ballTimer = null;
function restartFloatBallTimer() {
  if (_ballTimer) { clearInterval(_ballTimer); _ballTimer = null; }
  const ms = +Sim.floatWindow.refreshMs || 500;
  _ballTimer = setInterval(() => { if (Sim.floatWindow.enabled) floatBallTick(); }, ms);
}
function floatBallTick() {
  const ball = $('#float-ball');
  if (!ball.classList.contains('show')) return;
  const B = Sim.battery, vis = Sim.floatWindow.visibleMetrics, lines = [];
  const v = id => vis.includes(id);
  if (v('gpu_usage')) lines.push(`GPU: ${Sim.gpu.realLoad}%`);
  if (v('cpu_temp')) lines.push(`CPU: ${Sim.cpu.temp.last().toFixed(0)}°C`);
  if (v('gpu_temp')) lines.push(`GPU: ${Sim.gpu.temp.last().toFixed(0)}°C`);
  /* cpu_freq: 逐核多行 "C{i}: {MHz}MHz"（FloatingWindowService.kt:397-401, float_core_freq_format="C%1$d: %2$dMHz"），
     数据缺失时兜底单行 "频率: --MHz"（float_cpu_freq_initial） */
  if (v('cpu_freq')) {
    const coreLines = Sim.cpu.freqs
      .map((f, i) => ({ i, mhz: Math.round(f.history.last()) }))
      .filter(c => Number.isFinite(c.mhz) && c.mhz > 0)
      .map(c => `C${c.i}: ${c.mhz}MHz`);
    if (coreLines.length) lines.push(...coreLines);
    else lines.push(t('float_cpu_freq_initial'));
  }
  if (v('ram')) lines.push(t('web_sim_float_ram_format', Math.round(Sim.memory.usedMB / Device.memTotalMB * 100) + '%', Math.round(Sim.memory.usedMB), Device.memTotalMB));
  /* float_battery_temp_format = "电池: %1$d°C"（strings.xml %1$d → 整数，无小数） */
  if (v('battery_temp')) lines.push(t('float_battery_temp_format', B.tempC.last().toFixed(0)));
  if (v('battery_cur')) lines.push(B.currentMA >= 0 ? t('float_charging_format', Math.abs(B.currentMA)) : t('float_discharging_format', Math.abs(B.currentMA)));
  if (v('battery_pow')) lines.push(t('float_power_format', (B.voltageMV / 1000 * Math.abs(B.currentMA) / 1000).toFixed(2)));
  if (v('fps')) lines.push(`FPS: ${59 + (Sim.tickCount % 3)}`);
  ball.innerHTML = lines.map(l => `<div class="float-line" style="color:${Sim.floatWindow.textColor};font-size:${Sim.floatWindow.textSize}px">${esc(l)}</div>`).join('');
}

/* 悬浮球拖拽（以手机框为坐标系） */
(function floatDrag() {
  document.addEventListener('DOMContentLoaded', () => {
    const ball = $('#float-ball'), phone = $('#phone');
    let sx, sy, ox, oy, down = false;
    ball.addEventListener('pointerdown', e => {
      down = true; sx = e.clientX; sy = e.clientY;
      const r = ball.getBoundingClientRect();
      ox = r.left; oy = r.top;
      ball.setPointerCapture(e.pointerId);
      e.stopPropagation();                       /* 不触发翻页手势 */
    });
    ball.addEventListener('pointermove', e => {
      if (!down) return;
      const pr = phone.getBoundingClientRect();
      ball.style.left = clamp(ox + e.clientX - sx - pr.left, 0, Math.max(0, pr.width - 130)) + 'px';
      ball.style.top = clamp(oy + e.clientY - sy - pr.top, 0, Math.max(0, pr.height - 70)) + 'px';
    });
    ball.addEventListener('pointerup', () => down = false);
  });
})();

/* ============================================================
 * 覆盖层 ③ — HDR 实验室（中心缩放转场）
 * ============================================================ */
function openHdrLab() {
  const ov = $('#overlay-hdr');
  const H = Sim.hdrLab;
  /* 与 HdrLabScreen.canControl 一致：setDesiredHdrHeadroom 需 API 35+（Android 16） */
  const canControl = Device.sdkInt >= 35;
  ov.innerHTML = `
    <div class="overlay-bg"></div>
    ${/* T9 —— 同上，ov.innerHTML 会抹掉 index.html 的静态 GlowBackButton */ ''}
    ${glowBackBtnHtml()}
    <div class="overlay-body"><div class="overlay-content">
      <div style="font-size:18px;font-weight:700;margin-bottom:12px;padding-left:44px">${esc(t('hdr_lab_title'))}</div>

      <div class="cyber-card hdr-highlight card-enter">
        <div style="display:flex;justify-content:space-between;font-size:15px;font-weight:600">
          <span>${t('hdr_lab_potential_headroom')}</span><span style="color:var(--neon-purple-bright)">${
            /* HdrLabScreen.kt:188 —— potentialHeadroom > 1f 才显示数值，否则 "—" */
            H.potentialHeadroom > 1 ? H.potentialHeadroom.toFixed(1) : '—'
          }</span>
        </div>
        ${/* HdrLabScreen.kt:192-197 —— <= 1f 时补 unavailable 说明行 (11.sp TextSecondary) */
          H.potentialHeadroom <= 1
            ? '<div style="font-size:11px;color:var(--text-secondary);margin-top:4px">' + esc(t('hdr_lab_headroom_unavailable')) + '</div>'
            : ''}
        <div style="display:flex;gap:12px;margin-top:16px">
          <div style="flex:1">
            <div style="height:140px;border-radius:16px;background:#FFFFFF"></div>
            <div style="text-align:center;font-size:13px;font-weight:600;margin-top:6px">SDR</div>
          </div>
          <div style="flex:1">
            <div style="height:140px;border-radius:16px;position:relative;overflow:hidden;background:rgba(23,20,23,.6)">
              <div class="fs-hdr-pad" style="animation:none;filter:brightness(${H.pqActive ? 1.45 : 1})"></div>
            </div>
            <div style="text-align:center;font-size:13px;font-weight:600;margin-top:6px">
              HDR <span style="display:inline-block;width:6px;height:6px;border-radius:3px;background:${H.pqActive ? 'var(--success-neon)' : 'var(--warning-neon)'}"></span>
              <span style="font-size:10px;color:${H.pqActive ? 'var(--success-neon)' : 'var(--warning-neon)'}">${H.pqActive ? 'PQ' : '8-bit'}</span>
            </div>
          </div>
        </div>
        <div style="display:flex;align-items:center;margin-top:14px">
          <span style="opacity:.5">🌙</span>
          ${slider('sl-hdr-desired', 0, H.potentialHeadroom, 0.1, H.desired, 'x')}
          <span>☀️</span>
        </div>
        <div style="text-align:center;font-size:13px;color:var(--neon-cyan);margin-top:4px" id="hdr-brightness-txt">--</div>
        <div style="text-align:center;font-size:11px;color:var(--text-secondary);margin-top:3px">${esc(t('hdr_lab_headroom_scale_hint'))}</div>
        ${canControl ? '' : `<div style="text-align:center;font-size:11px;color:var(--warning-neon);margin-top:6px">${t('hdr_lab_needs_api35')}</div>`}
      </div>

      <div class="cyber-card hdr-highlight card-enter">
        <div style="font-size:14px;font-weight:600;color:var(--neon-purple)">${t('hdr_lab_diag_title')}</div>
        <div style="margin-top:8px">
          ${kvRow(t('hdr_lab_actual_ratio'), '<span id="diag-ratio">--</span>', 'mono')}
          ${kvRow(t('hdr_lab_layer_state'), '<span id="diag-layer">--</span>')}
          ${kvRow('PQ surface', '<span id="diag-pq">--</span>')}
          ${kvRow('EGL', '<span id="diag-egl">--</span>', 'mono')}
          ${kvRow('SDK', String(Device.sdkInt), 'mono')}
        </div>
        <div style="margin-top:12px;text-align:center;border-radius:14px;background:rgba(124,58,237,.14);padding:12px;cursor:pointer" id="hdr-fs-btn">
          <span style="font-size:14px;font-weight:600;color:var(--neon-purple-bright)">${esc(t('hdr_lab_fullscreen_btn'))}</span>
        </div>
        <div style="font-size:11px;color:var(--text-secondary);margin-top:8px;line-height:1.6">
          ${esc(t('hdr_lab_coverage_note'))}
        </div>
      </div>

      <div class="cyber-card hdr-highlight card-enter">
        <div style="display:flex;align-items:flex-start;justify-content:space-between">
          <div style="display:flex;align-items:center">
            <span style="color:var(--neon-purple-bright);display:inline-flex">${iconSvg('light', 20)}</span>
            <div style="margin-left:8px">
              <div style="font-size:15px;font-weight:600;color:var(--text-primary)">${'CyberNightlight TurboXDR'}</div>
              <div style="font-size:11px;color:var(--text-secondary)">${t('settings_turboxdr_desc')}</div>
              <div style="font-size:11px;color:var(--text-secondary)" id="st-turboxdr-hdr">${Sim.settings.turboXdr ? t('settings_haptic_on') : t('settings_haptic_off')}</div>
            </div>
          </div>
          <div class="switch pink ${Sim.settings.turboXdr ? 'on' : ''}" data-toggle="turboXdr">${joySwitchHtml()}</div>
        </div>
        <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-top:12px">
          <div><div style="font-size:14px;color:var(--text-primary)">${t('device_hdr_intensity')}</div>
            <div style="font-size:11px;color:var(--text-secondary)">${t('device_hdr_intensity_hint')}</div></div>
          <span style="font-size:14px;color:var(--neon-purple-bright);font-weight:600" id="sl-hdr-turbo-bigval"></span>
        </div>
        <div style="margin-top:4px">${slider('sl-hdr-turbo', 1.0, 8.0, 0.1, Sim.settings.turboXdrStrength, 'x')}</div>
        <div style="display:flex;justify-content:space-between;font-size:10px;color:rgba(61,112,184,.7);margin-top:2px">
          <span>1.0×</span><span>8.0×</span>
        </div>
        <div id="hdr-turbo-off" style="font-size:11px;color:rgba(61,112,184,.7);margin-top:4px;display:${Sim.settings.turboXdr ? 'none' : ''}">${t('device_hdr_intensity_off')}</div>
      </div>
      <div style="font-size:11px;color:var(--text-secondary);padding:0 2px 26px;line-height:1.6">
        ${esc(t('hdr_lab_hint_screenshot'))}
      </div>
    </div></div>`;
  ov.classList.add('active', 'scrim-scale');
  setPaneDocked('hdr', true);          // adaptive 下停靠为右侧面板，pager 同步让位
  makeSliderInteractive(ov);
  bindSwitches(ov);          // T8 —— .switch[data-toggle="turboXdr"]
  updateHdrDiag();

  if (!ov.dataset.bound) {
    ov.dataset.bound = '1';
    ov.addEventListener('click', e => {
      if (e.target.closest('.overlay-bg')) { closeOverlay('#overlay-hdr'); return; }
      if (e.target.closest('#hdr-fs-btn')) openFullscreenHdr();
    });
  }
}

function syncHdrDesiredSlider(v) {
  const sl = $('#sl-hdr-desired');
  if (!sl) return;
  /* 入参可能来自 sl-xdr（上限 8.0），先钳进本滑条量程，否则 fill 宽度会 >100% */
  const cv = clamp(v, +sl.dataset.min, +sl.dataset.max);
  sl.dataset.value = cv;
  const txt = $('#sl-hdr-desired-txt');
  const p = clamp((cv - (+sl.dataset.min)) / ((+sl.dataset.max) - (+sl.dataset.min)), 0, 1);
  $('.fill', sl).style.width = p * 100 + '%';
  $('.thumb', sl).style.left = p * 100 + '%';
  txt && (txt.textContent = fmtSliderText(sl, cv));
  updateHdrDiag();
}

/* T4 —— HdrLabScreen.kt:126 ratioAvailable = SDK_INT>=34 && display!=null && display.isHdrSdrRatioAvailable。
   data.js 无该字段且本轮禁止改 data.js → 在 app.js 侧由 SDK 版本 + actualRatio 有限性派生（Device.sdkInt=36 恒为 true）。 */
function hdrRatioAvailable() {
  return Device.sdkInt >= 34 && Number.isFinite(Sim.hdrLab.actualRatio);
}

function updateHdrDiag() {
  const H = Sim.hdrLab;
  const r = $('#diag-ratio'); if (!r) return;
  const ratioAvailable = hdrRatioAvailable();
  /* HdrLabScreen.kt:354 —— if (ratioAvailable) "%.2f".format(actualRatio) else "—" */
  r.textContent = ratioAvailable ? H.actualRatio.toFixed(2) : '—';
  const lit = ratioAvailable && H.actualRatio > 1.01;   // :359 —— ratioAvailable && actualRatio > 1.01f
  const layer = $('#diag-layer');
  layer.textContent = lit ? t('hdr_lab_ratio_lit') : t('hdr_lab_ratio_unlit');
  layer.className = 'kv-value ' + (lit ? 'success' : '');
  const pq = $('#diag-pq');
  pq.textContent = H.pqActive ? t('hdr_lab_pq_active') : t('hdr_lab_pq_fallback');
  pq.className = 'kv-value ' + (H.pqActive ? 'success' : 'warning');
  $('#diag-egl').textContent = H.eglSummary;
  const bt = $('#hdr-brightness-txt');
  bt && (bt.textContent = H.desired <= 0.001 ? t('hdr_lab_headroom_auto') : t('web_sim_hdr_brightness', H.desired.toFixed(1)));
}

function openFullscreenHdr() {
  Sim.hdrLab.fullscreen = true;
  const fs = $('#fullscreen-hdr');
  fs.classList.add('show');
  fs.innerHTML = `
    <div class="fs-hdr-pad"></div>
    <div style="position:relative;z-index:2;display:flex;flex-direction:column;flex:1;padding:16px">
      <div style="font-size:16px;font-weight:700">${esc(t('hdr_lab_fullscreen_hint_title'))}</div>
      <div style="flex:1"></div>
      <div id="fs-hdr-result" style="font-size:13.5px;font-weight:600;text-align:center;padding:10px;border-radius:12px;background:rgba(0,0,0,.45)"></div>
      <div style="text-align:center;font-size:11px;color:rgba(255,255,255,.6);margin-top:8px">${esc(t('hdr_lab_fullscreen_hint'))}</div>
      <button id="fs-hdr-exit" style="margin:14px auto 6px;padding:10px 36px;border-radius:999px;border:1px solid rgba(167,139,250,.5);background:rgba(124,58,237,.25);color:#E2E8F0;font-family:inherit;font-size:14px;font-weight:600;cursor:pointer">${t('hdr_lab_fullscreen_exit')}</button>
    </div>`;
  $('#fs-hdr-exit').addEventListener('click', closeFullscreenHdr);
}
function fsHdrTick() {
  const fs = $('#fullscreen-hdr');
  if (!fs.classList.contains('show')) return;
  const H = Sim.hdrLab;
  const ratioAvailable = hdrRatioAvailable();
  const lit = ratioAvailable && H.actualRatio > 1.01;
  fs.classList.toggle('lit', lit);
  const res = $('#fs-hdr-result');
  res.textContent = lit
    ? t('hdr_lab_fullscreen_lit', H.actualRatio.toFixed(2))
    : ratioAvailable ? t('hdr_lab_fullscreen_notlit')
                     : t('web_sim_ratio_unavailable');
  res.style.color = lit ? 'var(--success-neon)' : 'var(--warning-neon)';
}
function closeFullscreenHdr() {
  $('#fullscreen-hdr').classList.remove('show', 'lit');
  Sim.hdrLab.fullscreen = false;
}

/* T9 —— GlowBackButton 统一关闭。4 个覆盖层的模板都会输出 [data-close="1"]，
   这里做一次 document 级委托：wave 类（设置/悬浮窗）走 closeWave，scrim 类走 closeOverlay。
   注：拖拽形变由 bindGlowBackDrag 接管，松手且拖距 < 40dp 时才 click() 走到这里。 */
document.addEventListener('click', e => {
  const btn = e.target.closest && e.target.closest('[data-close="1"]');
  if (!btn) return;
  const ov = btn.closest('.overlay');
  if (!ov || !ov.classList.contains('active')) return;
  if (ov.classList.contains('wave')) closeWave(ov);
  else closeOverlay('#' + ov.id);
});

/* 返回键拖拽形变自动绑定 —— 覆盖层会整体重写 innerHTML（overlayWaveBody /
   openSensorDetail / openHdrLab），静态按钮会被冲掉，故用 MutationObserver 兜住
   所有注入时机，避免逐个调用点手工补绑。 */
(function watchGlowBack() {
  const bind = () => document.querySelectorAll('.glow-back-btn').forEach(bindGlowBackDrag);
  bind();
  if (typeof MutationObserver === 'undefined') return;
  const mo = new MutationObserver(muts => {
    for (const m of muts) {
      for (const n of m.addedNodes) {
        if (n.nodeType !== 1) continue;
        if (n.classList && n.classList.contains('glow-back-btn')) bindGlowBackDrag(n);
        else if (n.querySelectorAll) n.querySelectorAll('.glow-back-btn').forEach(bindGlowBackDrag);
      }
    }
  });
  mo.observe(document.body, { childList: true, subtree: true });
})();

function closeOverlay(sel) {
  const ov = $(sel);
  animateScrim(ov, 0);         /* scrim 先解除 → 容器后收起 */
  ov.classList.add('closing');
  if (sel === '#overlay-sensor') {
    liveChartLoop.stop();
    setPaneDocked('sensor', false);
  } else if (sel === '#overlay-hdr') {
    setPaneDocked('hdr', false);
  }
  setTimeout(() => ov.classList.remove('active', 'scrim-scale', 'closing'), 320);
}

/* ============================================================
 * cardRipple 直译：Material3 原生水波纹（按压扩散 + 松开回缩）
 * ============================================================ */
(function rippleDelegate() {
  const SEL = '.cyber-card, .quick-card, .sensor-card, .filter-chip, .glass-circle-btn, #hdr-entry, #hdr-fs-btn, .cluster-card, .sat-card';
  document.addEventListener('pointerdown', e => {
    const host = e.target.closest(SEL);
    if (!host || e.target.closest('input, .fancy-slider, .switch')) return;
    const rect = host.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height) * 2.2;
    const rip = document.createElement('span');
    rip.className = 'rip';
    rip.style.cssText = `width:${size}px;height:${size}px;left:${e.clientX - rect.left - size / 2}px;top:${e.clientY - rect.top - size / 2}px;`;
    host.classList.add('has-ripple');
    host.appendChild(rip);
    setTimeout(() => rip.remove(), 580);
  }, { passive: true });
})();

/* ============================================================
 * Toast / Tick 循环 / 键盘
 * ============================================================ */
let toastTimer = null;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 1600);
}

let tickTimer = null;
function restartTickLoop() {
  clearInterval(tickTimer);
  const iv = clamp(Math.min(...Object.values(Sim.settings.intervals)), 250, 5000);
  tickTimer = setInterval(() => {
    simTick();
    updateScreen(activeTab);
    /* 浮球不再由此驱动 —— 改由 restartFloatBallTimer() 按 refreshMs 独立刷新，
       否则 refreshMs=5000（节能）时仍会被 2s tick 拉回每 2 秒一更，语义被架空 */
    fsHdrTick();
    if ($('#overlay-hdr').classList.contains('active')) updateHdrDiag();
  }, iv);
}

document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    if ($('#fullscreen-hdr').classList.contains('show')) return closeFullscreenHdr();
    for (const sel of ['#overlay-sensor', '#overlay-hdr']) {
      const o = $(sel); if (o && o.classList.contains('active')) return closeOverlay(sel);
    }
    for (const sel of ['#overlay-settings', '#overlay-float']) {
      const o = $(sel); if (o && o.classList.contains('active')) return closeWave(o);
    }
  }
  if (/^(ArrowLeft|ArrowRight)$/.test(e.key) && e.target.tagName !== 'INPUT') {
    if ($('.overlay.active')) return;
    switchTab(clamp(activeTab + (e.key === 'ArrowRight' ? 1 : -1), 0, 8));
  }
});

/* 入口 */
document.addEventListener('DOMContentLoaded', () => {
  buildSkeleton();
  applyLayoutMode();         /* 先定形态：portrait（默认）/ adaptive，决定 CSS 多栏是否生效 */
  applySizeClass();          /* M3 窗口尺寸分级（须早于首屏渲染） */
  renderScreen(0);
  applyPager();
  setupPagerDrag();
  setupGlobalLight();
  simTick();
  restartTickLoop();
  applyEffectsVisuals();
  restartFloatBallTimer();          /* 浮球按 refreshMs 独立刷新，不再只跟 2s tick */
  window.addEventListener('resize', () => { applySizeClass(); applyPager(); renderedOnce.forEach(i => updateScreen(i)); });
});
