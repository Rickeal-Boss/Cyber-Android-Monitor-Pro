/* ============================================================
 * data.js — 模拟数据层（DeviceRepository 浏览端替身）
 * 所有数值均为 JS 模拟，周期 tick 推进；字段命名对齐 Kotlin 数据类
 * ============================================================ */
'use strict';

/* ── 工具 ── */
const rnd = (min, max) => min + Math.random() * (max - min);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;

/** 折线历史 buffer：模拟 LineChart 的 values: List<Float> */
class History {
  constructor(size = 40, init = 0) {
    this.size = size;
    this.data = new Array(size).fill(init);
  }
  push(v) { this.data.push(v); if (this.data.length > this.size) this.data.shift(); }
  last() { return this.data[this.data.length - 1]; }
  series() { return this.data.slice(); }
}

/** 平滑随机游走：让模拟曲线像真实传感器一样缓变 */
function walker(h /*History*/, lo, hi, step) {
  const cur = h.last();
  const next = clamp(cur + rnd(-step, step), lo, hi);
  h.push(next);
  return next;
}

/* ============================================================
 * 全局设备档案（静态部分）
 * 模拟一台 8 核 arm64 设备
 * ============================================================ */
const Device = {
  model: 'Cyber X7 Pro',
  manufacturer: 'Rickeal',
  brand: 'rickeal',
  board: 'cyb-x7',
  arch: 'AArch64',
  coreCount: 8,
  abi: 'arm64-v8a',
  sdkInt: 36,
  androidVersion: '16',
  kernel: '6.1.75-android14-11-g4c2fd0a1b2 #1 SMP PREEMPT',
  buildId: 'AP3A.240905.015.A1',
  patch: '2026-08-05',
  uptimeStart: Date.now() - 26.4 * 3600e3,
  deepSleepPct: 62,
  /* buildUptimeString 同款格式所需的深度待机累计秒数 (≈16h22m) */
  deepSleepSeconds: 58920,
  /* 设备详情页 (DeviceScreen) 静态档案 */
  platformCodename: 'cyb_x7',
  cpuBigCores: 4, cpuLittleCores: 4,

  socName: 'Snapdragon 8 Gen4 (simulated)',
  gpuModel: 'Adreno 840',
  cpuFreqMax: 3300,
  memTotalMB: 16384,
  swapTotalMB: 8192,
  batteryDesignmAh: 5400,
  batteryRatedMah: 5500,
  screenW: 1440, screenH: 3120,
  refreshRate: 120,
  dpi: '560dpi · XXHDPI',

  clusters: [
    /* coreType —— 源码语义是 **CPU 微架构核心名**，不是"簇的显示名"。
       依据 CpuCache.kt:829/846：coreType = chip.clusters[i].coreName（ClusterSpec 第一参数，
       即 "Cortex-X4 Prime" / "Cortex-A720" / "Cortex-A520" 这类真实核心名，见 CpuCache.kt:256-285）。
       两个消费点：① CpuScreen.kt:243,249 簇卡副标题追加 " · ${coreType}"；
                  ② CpuScreen.kt:308-310 Per-core 第三行（if coreType.isNotEmpty()）。
       此前全为 null → ②③的行恒不渲染，属数据缺失，按 SM8650(骁龙8 Gen3) 三簇布局补真值。 */
    { name: 'Prime',      cores: [0],        maxMHz: 3300, coreType: 'Cortex-X4 Prime' },
    { name: 'Performance',cores: [1,2,3],    maxMHz: 2500, coreType: 'Cortex-A720' },
    { name: 'Efficiency', cores: [4,5,6,7],  maxMHz: 1800, coreType: 'Cortex-A520' },
  ],

  abis: ['arm64-v8a', 'armeabi-v7a', 'armeabi'],
  caches: { L1I: '64 KB x8', L1D: '64 KB x8', L2: '1024 KB x4', L3: '6144 KB (shared)', L1Ibig: '64 KB', L1Dbig: '64 KB', L1Ilittle: '32 KB', L1Dlittle: '32 KB' },

  cstates: [
    /* 名称/描述逐字取自 cpu_cstate_*_short 与 cpu_cstate_* 长文案；
       latencyUs / usage(进入次数) 为模拟值；timeUs 为**停留时长累计**（µs）。
       ★ 占比不再硬编码 pct，改由 CpuScreen.kt:145-147 的语义在运行期现算：
           totalTime = cStates.sumOf { it.timeUs }
           pct       = if (totalTime > 0) (timeUs / totalTime * 100).coerceIn(0, 100) else 0
         注意分母只累加 cStates 自身 → 四条占比合计为 100%（与"深度睡眠总占比"是两条独立口径）。
       ★ usage 是**进入次数**（源码 :158 只用于副标题文本），绝不能当 timeUs 用。 */
    { name: 'WFI 等待中断', level: 1, desc: '等待中断(轻量睡眠)', timeUs: 2960000, latencyUs: 2, usage: 1520 },
    { name: 'C2 停用时钟', level: 2, desc: '停用核心时钟·唤醒<2µs', timeUs: 3980000, latencyUs: 2, usage: 983 },
    { name: 'C3 关闭PLL', level: 2, desc: '关闭PLL·缓存保持·唤醒<50µs', timeUs: 2100000, latencyUs: 50, usage: 417 },
    { name: 'Retention', level: 2, desc: '核心下电·状态保持(Retention)', timeUs: 980000, latencyUs: 120, usage: 156 },
  ],
};

/* ── 详情页 (DeviceScreen) 静态明细 — 行序照源码 section 拆分 ── */
const DeviceDetail = {
  socManufacturer: 'Qualcomm',
  cache: { L1I: '64 KB x8', L1D: '64 KB x8', L2: '1024 KB x4', L3: '6144 KB (shared)', source: 'sysfs /sys/devices/system/cpu/cpu0/cache' },
  gpuVendor: 'Qualcomm', glEsVersion: '3.2', gpuDriver: 'Adreno 840 · 1.3.290 (build 27)',
  glExtensions: 'GL_ARB_texture_storage · GL_EXT_texture_format_BGRA8888 · GL_KHR_debug · GL_OES_EGL_image · GL_AMD_compressed_ATC_texture +6',
  vulkan: { version: '1.3.290', level: 'Vulkan 1.3 conformance', gpuName: 'Adreno 840', devCount: 1, extTotal: 42, extEnabled: 5, keyExts: ['ray_tracing_pipeline', 'acceleration_structure', 'timeline_semaphore', 'dynamic_rendering', 'synchronization2'], rayTracing: true, rayTracingSource: 'vulkan-ext' },
  display: { physicalSize: '6.7"', panelTech: 'OLED (LTPO)', colorDepth: '10-bit', colorGamut: 'DCI-P3 · sRGB', hdr: 'HDR10+, HLG, Dolby Vision', peakBrightness: '2600', touch: '多点触控' },
  memory: { type: 'LPDDR5X', speedMHz: 4800, source: 'device_tree' },
  storage: { type: 'UFS 4.0', protocol: 'SCSI', source: 'device_tree' },
  camera: [
    { facing: '后置摄像头', resolution: '50 MP', aperture: 'f/1.6', focal: '23 mm', pixel: '1.0 µm', features: 'OIS · EIS · 闪光灯' },
    { facing: '前置摄像头', resolution: '32 MP', aperture: 'f/2.2', focal: '26 mm', pixel: '0.8 µm', features: 'EIS' },
  ],
  audio: { stereo: true, sampleRate: '384 kHz', hiRes: true, formats: 'AAC, FLAC, MP3, Opus, PCM' },
  sim: { operator: '中国移动 CMCC', mccMnc: '46000', network: 'NR (NSA)', dualSim: true },
  connect: { bt: '蓝牙 5.4 · BLE · LE Audio', btName: 'Cyber X7 Pro', btAddr: 'A2:F3:B4:C5:D6:E8', wifi: '802.11ax · 6GHz', nfc: true, usb: 'USB · Type-C · Host', ir: true, uwb: true, wirelessCharging: true },
  health: { todaySteps: 6482, totalSteps: 1284096, bootSteps: 6482 },
  codecs: { video: 'H.264, H.265, VP9, AV1, MPEG-4 (+3)', audio: 'AAC, MP3, Opus, FLAC, Vorbis (+4)', hw: 'H.264, H.265, VP9, AV1' },
  thermal: { zones: 34, types: 'cpu-0-0, cpu-1-0, gpu-0, battery, modem, usb-port, skin, charger' },
  drm: { widevine: 'L1', schemes: 'Widevine, PlayReady, ClearKey' },
  security: { tee: true, secureBoot: true, fileEncryption: 'enc_fbe', selinux: true },
  identifiers: { fingerprint: 'rickeal/cyb_x7/CYBX7:16/AP3A.240905.015.A1/user/release-keys', androidId: '7f3a91c2e04b58d6', serial: 'CYBX7A16Q00812' },
  bootloader: { version: 'cyb-x7-boot-1.2.4', unlocked: false, verifyBoot: true },
  runtime: { java: 'Java 21 (ART)', openssl: 'OpenSSL 3.1.4', buildTime: '2026-08-05 14:32 UTC' },
  oem: { osName: 'CyberOS', oem: 'Rickeal', osVersion: 'CyberOS 16.0', perfGameMode: true, perfMode: '平衡', subsystems: [['AI 引擎', 'CyberAI 3.0 (NPU 45 TOPS)'], ['内存扩展', 'Memory Fusion +8GB'], ['散热方案', '双 VC 均热板'], ['存储加速', 'UFS TurboWrite']], props: 3 },
};

/* ── 内存分布五色图例（memory_category_* 逐字；free=空闲） ── */
const MemLegend = [
  { key: 'app',    label: '应用',   css: '#C084FC' },
  { key: 'cached', label: '缓存',   css: '#818CF8' },
  { key: 'system', label: '系统',   css: '#00D4FF' },
  { key: 'free',   label: '空闲',   css: '#34C759' },
  { key: 'other',  label: '其他',   css: '#94A3B8' },
];

/* ── 快速访问卡：QuickMeta 直译（DashboardScreen.kt:464-471）──
 * 图标/颜色/tap 目标 tab 与源码一致；mem 卡 desc 为动态 "used / total" */
const QuickCards = [
  { id: 'cpu',    iconKey: 'play_arrow', title: 'CPU 处理器', desc: '频率 · 核心 · 温度',   color: '#7C3AED', tab: 1 },
  { id: 'gpu',    iconKey: 'info',       title: 'GPU 图形',   desc: '负载 · 频率 · 温度',   color: '#A78BFA', tab: 2 },
  { id: 'mem',    iconKey: 'star',       title: '内存信息',   desc: '',                     color: '#7C3AED', tab: 3 },
  { id: 'net',    iconKey: 'share',      title: '网络状态',   desc: 'WiFi · 信号',          color: '#A78BFA', tab: 5 },
  { id: 'gps',    iconKey: 'play_arrow', title: 'GPS 定位',   desc: '卫星 · 坐标',          color: '#F43F5E', tab: 6 },
  { id: 'device', iconKey: 'search',     title: '系统详情',   desc: 'OEM · 性能模式',       color: '#34C759', tab: 8 },
  { id: 'battery',iconKey: 'battery',    title: '电池',       desc: '电压 · 温度 · 健康',   color: '#7C3AED', tab: 4 },
  { id: 'sensor', iconKey: 'sensors',    title: '传感器',     desc: '加速度 · 陀螺 · 光感', color: '#A78BFA', tab: 7 },
];

/* ── 电池页 19 卡有序列表 (BATTERY_CARD_IDS) ── */
const BatteryCardIds = [
  'current_multiplier','power_save','soh','design_capacity','rated_capacity',
  'cycle_count','protocol','power_source','wattage','internal_r',
  'level_chart','power','current','realtime_power','voltage',
  'charge_counter','temperature','health_status','dual_cell'
];

/* ============================================================
 * 传感器类型元数据 TYPE_META —— 逐条照抄 SensorTypeMeta
 * 源码: app/src/main/java/com/rb/cybermonitorpro/data/model/SensorItemInfo.kt:68-198
 * ------------------------------------------------------------
 * key = 源码 typeId。各字段来源（严格、可回溯）：
 *   typeId / unit / valueCount / axisLabels → SensorItemInfo.kt:75-109（枚举构造参数）
 *   nameZh                                 → values-zh-rCN/strings.xml 的 sensor_type_* 系列
 *   icon                                   → SensorDetailScreen.kt:300-333 (when(meta){...} 码点)
 *   fmtSingle (valueCount==1 时的格式化小数位) → SensorDetailScreen.kt:345-362
 *   fmtMulti  (valueCount>1 时的格式化小数位)  → SensorDetailScreen.kt:480-492
 *
 * 铁律（根因 P0-1）：名称 / 单位 / 轴数 / 轴标签 / 图标 / 数值格式化 / 卡片组合
 *   一律以 type 为唯一键反查本表，禁止在 SensorDefs 里硬编码。
 * ─────────────────────────────────────────────────────────────
 * 图标码点备忘（SensorDetailScreen.kt:300-333）：
 *   ☀U+2600 LIGHT / ↔U+2194 PROXIMITY / ↻U+21BB GYRO 系 / ↕U+2195 ACCEL 系
 *   ⬇U+2B07 GRAVITY / ⌖U+2316 ORIENTATION（专用）/ ⟳U+27F3 旋转矢量系
 *   ▼U+25BC PRESSURE / ≈U+2248 HUMIDITY / °U+00B0 TEMPERATURE 系
 *   ⇅U+21C5 STEP 系 / ♥U+2665 HEART 系 / ∠U+2220 HINGE_ANGLE / ↑U+2191 HEADING
 *   ⚡U+26A1 SIGNIFICANT_MOTION · MOTION_DETECT / ■U+25A0 STATIONARY_DETECT
 *   ◉U+25C9 else 默认分支（MAGNETIC_FIELD / MAGNETIC_FIELD_UNCALIBRATED / OFFBODY_DETECT 走此分支）
 * ============================================================ */
const TYPE_META = {
  // typeId: { nameZh, unit, valueCount, axisLabels, icon, fmtSingle, fmtMulti }   // 源码枚举名 / 行号
  1:  { nameZh: '加速度传感器',              unit: 'm/s²',  valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2195', fmtSingle: 2, fmtMulti: 2 }, // ACCELEROMETER                            L75
  2:  { nameZh: '磁力计',                    unit: 'μT',    valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u25C9', fmtSingle: 2, fmtMulti: 2 }, // MAGNETIC_FIELD            (else 分支 ◉) L76
  3:  { nameZh: '方向传感器',                unit: '°',     valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2316', fmtSingle: 2, fmtMulti: 4 }, // ORIENTATION                              L77
  4:  { nameZh: '陀螺仪',                    unit: 'rad/s', valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u21BB', fmtSingle: 2, fmtMulti: 4 }, // GYROSCOPE                                L78
  5:  { nameZh: '光线传感器',                unit: 'lx',    valueCount: 1, axisLabels: ['照度'],       icon: '\u2600', fmtSingle: 0, fmtMulti: 2 }, // LIGHT                                    L79
  6:  { nameZh: '压力传感器',                unit: 'hPa',   valueCount: 1, axisLabels: ['压力'],       icon: '\u25BC', fmtSingle: 1, fmtMulti: 2 }, // PRESSURE                                 L80
  7:  { nameZh: '温度传感器',                unit: '°C',    valueCount: 1, axisLabels: ['温度'],       icon: '\u00B0', fmtSingle: 1, fmtMulti: 2 }, // TEMPERATURE                              L95
  8:  { nameZh: '距离传感器',                unit: 'cm',    valueCount: 1, axisLabels: ['距离'],       icon: '\u2194', fmtSingle: 1, fmtMulti: 2 }, // PROXIMITY                                L83
  9:  { nameZh: '重力传感器',                unit: 'm/s²',  valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2B07', fmtSingle: 2, fmtMulti: 2 }, // GRAVITY                                  L84
  10: { nameZh: '线性加速度传感器',          unit: 'm/s²',  valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2195', fmtSingle: 2, fmtMulti: 2 }, // LINEAR_ACCELERATION                      L85
  11: { nameZh: '旋转矢量传感器',            unit: '',      valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u27F3', fmtSingle: 2, fmtMulti: 6 }, // ROTATION_VECTOR                          L86
  12: { nameZh: '湿度传感器',                unit: '%',     valueCount: 1, axisLabels: ['湿度'],       icon: '\u2248', fmtSingle: 1, fmtMulti: 2 }, // HUMIDITY                                 L87
  13: { nameZh: '环境温度传感器',            unit: '°C',    valueCount: 1, axisLabels: ['温度'],       icon: '\u00B0', fmtSingle: 1, fmtMulti: 2 }, // AMBIENT_TEMPERATURE                      L88
  14: { nameZh: '磁场传感器(未校准)',        unit: 'μT',    valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u25C9', fmtSingle: 2, fmtMulti: 2 }, // MAGNETIC_FIELD_UNCALIBRATED (else 分支 ◉) L89
  15: { nameZh: '游戏旋转矢量传感器',        unit: '',      valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u27F3', fmtSingle: 2, fmtMulti: 6 }, // GAME_ROTATION_VECTOR                     L90
  16: { nameZh: '陀螺仪(未校准)',            unit: 'rad/s', valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u21BB', fmtSingle: 2, fmtMulti: 4 }, // GYROSCOPE_UNCALIBRATED                   L91
  17: { nameZh: '显著运动检测',              unit: '',      valueCount: 1, axisLabels: ['事件'],       icon: '\u26A1', fmtSingle: 0, fmtMulti: 2 }, // SIGNIFICANT_MOTION                       L96
  18: { nameZh: '步数检测器',                unit: 'steps', valueCount: 1, axisLabels: ['步数'],       icon: '\u21C5', fmtSingle: 0, fmtMulti: 2 }, // STEP_DETECTOR                            L81
  19: { nameZh: '步数计数器',                unit: 'steps', valueCount: 1, axisLabels: ['步数'],       icon: '\u21C5', fmtSingle: 0, fmtMulti: 2 }, // STEP_COUNTER                             L82
  20: { nameZh: '地磁旋转矢量传感器',        unit: '',      valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u27F3', fmtSingle: 2, fmtMulti: 6 }, // GEOMAGNETIC_ROTATION_VECTOR              L92
  21: { nameZh: '心率传感器',                unit: 'bpm',   valueCount: 1, axisLabels: ['心率'],       icon: '\u2665', fmtSingle: 0, fmtMulti: 2 }, // HEART_RATE                               L97
  28: { nameZh: '6DOF 姿态传感器',           unit: '',      valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u27F3', fmtSingle: 2, fmtMulti: 6 }, // POSE_6DOF                                L98
  29: { nameZh: '静止检测',                  unit: '',      valueCount: 1, axisLabels: ['状态'],       icon: '\u25A0', fmtSingle: 0, fmtMulti: 2 }, // STATIONARY_DETECT                        L99
  30: { nameZh: '运动检测',                  unit: '',      valueCount: 1, axisLabels: ['状态'],       icon: '\u26A1', fmtSingle: 0, fmtMulti: 2 }, // MOTION_DETECT                            L100
  31: { nameZh: '心跳传感器',                unit: '',      valueCount: 1, axisLabels: ['置信度'],     icon: '\u2665', fmtSingle: 2, fmtMulti: 2 }, // HEART_BEAT                               L101
  34: { nameZh: '离身检测传感器',            unit: '',      valueCount: 1, axisLabels: ['状态'],       icon: '\u25C9', fmtSingle: 0, fmtMulti: 2 }, // LOW_LATENCY_OFFBODY_DETECT  (else 分支 ◉) L102
  35: { nameZh: '加速度传感器(未校准)',      unit: 'm/s²',  valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2195', fmtSingle: 2, fmtMulti: 2 }, // ACCELEROMETER_UNCALIBRATED               L93
  36: { nameZh: '铰链角度传感器',            unit: '°',     valueCount: 1, axisLabels: ['角度'],       icon: '\u2220', fmtSingle: 1, fmtMulti: 2 }, // HINGE_ANGLE                              L103
  37: { nameZh: '头部追踪传感器',            unit: '',      valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u27F3', fmtSingle: 2, fmtMulti: 6 }, // HEAD_TRACKER                             L104
  38: { nameZh: '加速度传感器(受限轴)',      unit: 'm/s²',  valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2195', fmtSingle: 2, fmtMulti: 2 }, // ACCELEROMETER_LIMITED_AXES               L105
  39: { nameZh: '陀螺仪(受限轴)',            unit: 'rad/s', valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u21BB', fmtSingle: 2, fmtMulti: 4 }, // GYROSCOPE_LIMITED_AXES                   L106
  40: { nameZh: '加速度传感器(受限轴,未校准)', unit: 'm/s²', valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u2195', fmtSingle: 2, fmtMulti: 2 }, // ACCELEROMETER_LIMITED_AXES_UNCALIBRATED  L107
  41: { nameZh: '陀螺仪(受限轴,未校准)',     unit: 'rad/s', valueCount: 3, axisLabels: ['X', 'Y', 'Z'], icon: '\u21BB', fmtSingle: 2, fmtMulti: 4 }, // GYROSCOPE_LIMITED_AXES_UNCALIBRATED      L108
  42: { nameZh: '航向角传感器',              unit: '°',     valueCount: 1, axisLabels: ['航向'],       icon: '\u2191', fmtSingle: 1, fmtMulti: 2 }, // HEADING                                  L109
};

/**
 * "com.vendor.sensor.motion_recognition" → "Motion Recognition"
 * 逐字照抄 SensorItemInfo.kt:139-146 (humanizeStringType)
 */
function humanizeStringType(stringType) {
  if (!stringType) return '';
  const lastPart = String(stringType).split('.').pop();   // substringAfterLast('.')
  if (!lastPart) return '';
  return lastPart.split('_').map(word =>
    word ? word.charAt(0).toUpperCase() + word.slice(1) : word
  ).join(' ');
}

/**
 * 传感器显示名解析链 —— 逐字照抄 SensorItemInfo.kt:128-136
 *   1) 标准类型 (TYPE_META 命中) → strings.xml 的 sensor_type_* 中文名
 *   2) OEM 私有 (无标准 typeId)   → humanizeStringType(sensor.stringType)
 *   3) 兜底                       → "传感器 {type}"（strings.xml:1429 sensor_info_type_fallback）
 * 调用点对齐源码: SensorsScreen.kt:398 / SensorDetailScreen.kt:144 (标题) / :899 (信息卡「名称」行)
 */
function sensorDisplayName(s) {
  const meta = TYPE_META[s.type];
  if (meta) return meta.nameZh;
  const humanized = humanizeStringType(s.stringType);
  if (humanized) return humanized;
  return `传感器 ${s.type}`;
}

/* ── 传感器清单（typical Android device, simulated） ──
 * type 一律为源码 SensorTypeMeta.typeId（SensorItemInfo.kt:75-109）。
 * 名称 / 单位 / 轴数 / 轴标签 / 图标 / 格式化 全部由 TYPE_META 反查，此处不再硬编码。 */
const SensorDefs = [
  // ── 原 20 条按源码 typeId 校正（括号内为网页原 type → 源码正确 typeId）──
  { type: 35, name: 'Acceleration (uncalibrated)',   vendor: 'QTI',            maxRange: 156.91,     wake: false, dynamic: false }, // -1 → 35
  { type: 1,  name: 'Accelerometer',                 vendor: 'QTI',            maxRange: 78.45,      wake: false, dynamic: false }, // 正确不变
  { type: 15, name: 'Game Rotation Vector',          vendor: 'QTI',            maxRange: 1.0,        wake: false, dynamic: false }, // 正确不变
  { type: 20, name: 'Geomagnetic Rotation Vector',   vendor: 'QTI',            maxRange: 1.0,        wake: false, dynamic: false }, // 正确不变
  { type: 9,  name: 'Gravity',                       vendor: 'QTI',            maxRange: 78.45,      wake: false, dynamic: false }, // 正确不变
  { type: 4,  name: 'Gyroscope',                     vendor: 'QTI',            maxRange: 34.91,      wake: false, dynamic: false }, // 正确不变
  { type: 16, name: 'Gyroscope (uncalibrated)',      vendor: 'QTI',            maxRange: 34.91,      wake: false, dynamic: false }, // 正确不变
  { type: 21, name: 'Heart Rate',                    vendor: 'QTI',            maxRange: 255,        wake: true,  dynamic: false }, // 18 → 21（18 是 STEP_DETECTOR）
  { type: 5,  name: 'Light Illuminance',             vendor: 'QTI',            maxRange: 60000,      wake: false, dynamic: false }, //  8 → 5 （8  是 PROXIMITY）
  { type: 2,  name: 'Magnetic Field',                vendor: 'AKM',            maxRange: 4912,       wake: false, dynamic: false }, // 10 → 2 （10 是 LINEAR_ACCELERATION）
  { type: 14, name: 'Magnetic Field (uncalibrated)', vendor: 'AKM',            maxRange: 4912,       wake: false, dynamic: false }, // 17 → 14（17 是 SIGNIFICANT_MOTION）
  { type: 3,  name: 'Orientation',                   vendor: 'QTI',            maxRange: 360,        wake: false, dynamic: false }, // 13 → 3 （13 是 AMBIENT_TEMPERATURE）
  /* 'Pick up gesture' 已移除：源码 SensorItemInfo.kt:75-109 无对应 typeId，
     且 app/src/main 全量 grep pickup|pick_up|PICK_UP 零命中 → 无 stringType 可回退，
     按「绝不臆造」原则删除（原网页 type 21 实为 HEART_RATE，语义冲突）。 */
  { type: 6,  name: 'Pressure',                      vendor: 'STMicro',        maxRange: 1100,       wake: false, dynamic: false }, // 正确不变
  { type: 8,  name: 'Proximity',                     vendor: 'QTI',            maxRange: 5,          wake: true,  dynamic: false }, // 12 → 8 （12 是 HUMIDITY）
  { type: 17, name: 'Significant Motion',            vendor: 'QTI',            maxRange: 1,          wake: true,  dynamic: false }, // 11 → 17（11 是 ROTATION_VECTOR）
  { type: 29, name: 'Stationary detect',             vendor: 'QTI',            maxRange: 1,          wake: false, dynamic: false }, // 19 → 29（19 是 STEP_COUNTER）
  { type: 19, name: 'Step Counter',                  vendor: 'QTI',            maxRange: 2000000000, wake: false, dynamic: false }, //  5 → 19（5  是 LIGHT）
  { type: 37, name: 'Head Tracker',                  vendor: 'DynamicSensor',  maxRange: 1,          wake: false, dynamic: true  }, // 23 → 37
  { type: 30, name: 'Motion Detect',                 vendor: 'QTI',            maxRange: 1,          wake: true,  dynamic: false }, // 25 → 30
  // ── 补齐源码已定义而网页缺失的标准类型（SensorTypeMeta 均已定义）──
  { type: 10, name: 'Linear Acceleration',           vendor: 'QTI',            maxRange: 78.45,      wake: false, dynamic: false }, // L85
  { type: 11, name: 'Rotation Vector',               vendor: 'QTI',            maxRange: 1.0,        wake: false, dynamic: false }, // L86
  { type: 12, name: 'Humidity',                      vendor: 'Sensirion',      maxRange: 100,        wake: false, dynamic: false }, // L87
  { type: 13, name: 'Ambient Temperature',           vendor: 'QTI',            maxRange: 125,        wake: false, dynamic: false }, // L88
  { type: 18, name: 'Step Detector',                 vendor: 'QTI',            maxRange: 1,          wake: false, dynamic: false }, // L81
];

/** ON_CHANGE(1) 上报模式的 typeId（其余为 CONTINUOUS(0)）。
 *  即原清单里的 显著运动 / 静止检测 / 运动检测 三条，type 校正后对应 17 / 29 / 30。 */
const SENSOR_ON_CHANGE_TYPES = [17 /* SIGNIFICANT_MOTION */, 29 /* STATIONARY_DETECT */, 30 /* MOTION_DETECT */];

/* ── 三语搜索别名的 英文 / 繁中 标题 ──
 * 仅用于 buildSearchAliases 搜索匹配（SensorItemInfo.kt:156-182），**不参与显示**（显示恒用 nameZh）。
 * 英文: values/strings.xml:268-281, 625-644, 1453-1455
 * 繁中: values-zh-rTW/strings.xml:299-330, 906-907, 1451-1453
 * 注意：取 SensorTypeMeta 实际引用的资源名（如 L85 用 sensor_type_linear_accel，
 *       而非同义的 sensor_type_linear_acceleration），与 TYPE_META.nameZh 的取法保持一致。 */
const TYPE_NAME_EN = {
  1: 'Accelerometer', 2: 'Magnetometer', 3: 'Orientation', 4: 'Gyroscope',
  5: 'Light Sensor', 6: 'Pressure Sensor', 7: 'Temperature', 8: 'Proximity Sensor',
  9: 'Gravity Sensor', 10: 'Linear Acceleration', 11: 'Rotation Vector', 12: 'Humidity Sensor',
  13: 'Ambient Temperature', 14: 'Magnetometer (Uncalibrated)', 15: 'Game Rotation Vector',
  16: 'Gyroscope (Uncalibrated)', 17: 'Significant Motion', 18: 'Step Detector', 19: 'Step Counter',
  20: 'Geomagnetic Rotation Vector', 21: 'Heart Rate', 28: 'Pose 6DOF', 29: 'Stationary Detect',
  30: 'Motion Detect', 31: 'Heart Beat', 34: 'Off-body Detect', 35: 'Accelerometer (Uncalibrated)',
  36: 'Hinge Angle', 37: 'Head Tracker', 38: 'Accelerometer (Limited Axes)', 39: 'Gyroscope (Limited Axes)',
  40: 'Accelerometer (Limited, Uncalibrated)', 41: 'Gyroscope (Limited, Uncalibrated)', 42: 'Heading',
};
const TYPE_NAME_ZHTW = {
  1: '加速度感測器', 2: '磁力計', 3: '方向感測器', 4: '陀螺儀',
  5: '光線感測器', 6: '壓力感測器', 7: '溫度感測器', 8: '距離感測器',
  9: '重力感測器', 10: '線性加速度感測器', 11: '旋轉向量感測器', 12: '濕度感測器',
  13: '環境溫度感測器', 14: '磁場感測器(未校準)', 15: '遊戲旋轉向量感測器',
  16: '陀螺儀(未校準)', 17: '顯著運動偵測', 18: '步數偵測器', 19: '步數計數器',
  20: '地磁旋轉向量感測器', 21: '心率感測器', 28: '6DOF 姿態感測器', 29: '靜止偵測',
  30: '運動偵測', 31: '心跳感測器', 34: '離身偵測感測器', 35: '加速度感測器(未校準)',
  36: '鉸鏈角度感測器', 37: '頭部追蹤感測器', 38: '加速度感測器(受限軸)', 39: '陀螺儀(受限軸)',
  40: '加速度感測器(受限軸,未校準)', 41: '陀螺儀(受限軸,未校準)', 42: '航向角感測器',
};

/**
 * 构建搜索别名 —— 逐字照抄 SensorTypeMeta.buildSearchAliases (SensorItemInfo.kt:156-182)
 * 组成顺序（源码 L163-181）：
 *   1) 当前语言标题（= 与列表显示一致的 getDisplayName）
 *   2) 三语标题 EN / zh-CN / zh-TW —— **仅标准类型**（L167 `if (meta != null)`）；
 *      OEM 私有 (type ≥ 65536, fromTypeId == null) 跳过此循环，与源码 L166 注释一致
 *   3) 硬件名（非空）
 *   4) 厂商（非空）
 *   5) distinct()
 * 三语 locale 顺序 = SEARCH_ALIAS_LOCALES (L185-186): ENGLISH → SIMPLIFIED_CHINESE → TRADITIONAL_CHINESE
 */
function buildSearchAliases(type, stringType, hardwareName, vendor) {
  const aliases = [];
  aliases.push(sensorDisplayName({ type, stringType }));      // L165 当前语言标题（无条件入列）
  const meta = TYPE_META[type];
  if (meta) {                                                 // L167-178 仅标准类型解析三语资源
    for (const name of [TYPE_NAME_EN[type], meta.nameZh, TYPE_NAME_ZHTW[type]]) {
      if (name && String(name).trim() !== '') aliases.push(name);   // L174 localized.isNotBlank()
    }
  }
  if (hardwareName && String(hardwareName).trim() !== '') aliases.push(hardwareName);  // L179
  if (vendor && String(vendor).trim() !== '') aliases.push(vendor);                    // L180
  return [...new Set(aliases)];                               // L181 distinct()
}

/* ── 卫星星座种子（prn 区段: GPS 1-32 / GLONASS 65-96 / BDS 201-240 / GAL 301-336） ── */
const GNSS_CONSTS = [
  { sys: 'GPS',   prefix: 'G', count: 9, prnBase: 1 },
  { sys: 'GLONASS', prefix: 'R', count: 7, prnBase: 65 },
  { sys: 'BDS',   prefix: 'C', count: 10, prnBase: 201 },
  { sys: 'Galileo', prefix: 'E', count: 6, prnBase: 301 },
];

/* ── 悬浮窗配置域 (FloatingWindowConfig) ── */
const FloatConfig = {
  ALL_METRICS: [
    { id: 'gpu_usage',    label: 'GPU 利用率' },
    { id: 'cpu_temp',     label: 'CPU 温度' },
    { id: 'gpu_temp',     label: 'GPU 温度' },
    { id: 'cpu_freq',     label: 'CPU 频率 (最高核心)' },
    { id: 'ram',          label: '运行内存' },
    { id: 'battery_temp', label: '电池温度' },
    { id: 'battery_cur',  label: '电池电流' },
    { id: 'battery_pow',  label: '电池功率 (V×I)' },
    { id: 'fps',          label: '实时 FPS' },
  ],
  DEFAULT_VISIBLE: ['gpu_usage', 'cpu_temp', 'ram'],
  defaultRefreshMs: 500,
  textSize: 11, textColor: '#00D4FF', alpha: 0.85, bgColor: '#DC0A0A0F',
};

/* ============================================================
 * 实时状态（tick 推进）
 * ============================================================ */
const Sim = {
  tickCount: 0,

  // 数据源健康 (12源)
  sources: [
    { id: 'CPU', ok: true }, { id: 'GPU', ok: true }, { id: 'BAT', ok: true },
    { id: 'RAM', ok: true }, { id: 'IO',  warn: false }, { id: 'WiFi', ok: true },
    { id: '4G', ok: true }, { id: 'IF', ok: true }, { id: 'SYS', ok: true },
    { id: 'SNS', ok: true }, { id: 'DEV', ok: true }, { id: 'OEM', ok: true },
  ].map(s => ({ id: s.id, state: 'ok' })),

  overview: {
    cpuTemp: new History(40, 38),
    memUsage: new History(40, 54),
    batLevel: new History(40, 76),
    gpuLoad: new History(40, 22),
  },

  cpu: {
    temp: new History(40, 38),
    freqs: Array.from({ length: 8 }, (_, i) => ({ history: new History(30, 800 + i * 300), base: 800 + i * 300 })),
    load: new History(40, 18),
    deepSleep: new History(40, 75.8),   /* C-States 深度睡眠折线数据 */
    perCoreMode: 'cluster', // 'cluster' | 'core'
    governor: 'walt',
    /* Per-core 状态（CpuScreen.kt:295-314 CoreItem 两处消费）:
       - online:false → 核心名行右端显示 "OFF"（strings.xml:1020，11sp NeonMagenta）
       - 否则 usagePercent 非 NaN → 显示 "%.0f%%"（11sp NeonCyan）
       模拟 big.LITTLE hotplug：低负载时小核簇尾部核心会 offline。 */
    cores: Array.from({ length: 8 }, () => ({ online: true, usagePercent: 20 })),
  },

  gpu: {
    load: new History(40, 22),
    realLoad: 0,
    freqMHz: 480,
    freqMax: 1100,
    /* GPU 频率历史（GpuScreen.kt:69 / :124 —— gpu.freq.chart 的 LineChart 数据源） */
    freqHist: new History(40, 480),
    temp: new History(40, 39),
    throttled: false,
    governor: 'msm-adreno-tz',
    /* availableGovernors —— GpuScreen.kt:139 Governor 卡副标题。
       源码取值路径 GpuDataSource.kt:594-595 读 /sys/class/kgsl/kgsl-3d0/devfreq/available_governors，
       且 `avail.replace('\n',' ').trim()` → **空格分隔**的候选列表（KGSL / Adreno 真实取值）。 */
    availableGovernors: 'msm-adreno-tz msm-adreno-tz-simple bw_hwmon powersave performance userspace',
    renderer: 'Adreno (TM) 840',
    vulkanDriver: '1.3.290 developer build (build 27)',
    vulkanIntegrated: 'integrated',
  },

  memory: {
    usedMB: 8800, totalMB: 16384, availMB: 7584,
    swapUsedMB: 700, swapTotalMB: 8192,
    zramUsedMB: 1400, zramTotalMB: 4096,
    dist: { app: 0.28, cached: 0.17, system: 0.09, free: 0.46, other: 0.00 },
    distHists: null, // filled below
    availableHist: new History(40, 7584),
    usedHist: new History(40, 8800),
    topProcs: [
      'system_server            2180 MB',
      'com.rb.cybermonitorpro     212 MB',
      'com.android.launcher        186 MB',
      'com.tencent.mm              342 MB',
      'surfaceflinger              154 MB',
    ],
  },

  battery: {
    level: 76, charging: false, plugged: false, full: false,
    statusKey: 'discharging', // charging/discharging/full/not_charging
    voltageMV: 4320, currentMA: -312, powerMW: 1348,
    currentSource: 'power_supply',   /* currentNowSource（BatteryScreen current 卡副标题段一） */
    tempC: new History(40, 31.2),
    sohPct: 97, cycleCount: 214, protocol: 'PD3.0 / QC5',
    powerSource: 'ps_battery', wattage: 0,
    internalROhms: 42,
    chargeCounterUAh: 4102000,
    designCap: 5400, nowCap: 5238, ratedCap: 5500,
    capSource: 'power_supply', cycleSource: 'power_supply',
    healthStatus: 'battery_health_good', tech: 'Li-poly', chargerType: 'AC',
    dualCell: false, currentMultiplier: 1.0, multiplierEnabled: false, powerSave: false,
    levelHist: new History(40, 76),
  },

  network: {
    wifiConnected: true, ssid: 'CyberNet-5G', linkSpeedMbps: 1200, networkType: 'Wi-Fi 6E',
    rxMbps: new History(40, 2.4), txMbps: new History(40, .6),
    wifiFreqMHz: 6180, wifiStandard: '802.11ax', channelWidth: '160 MHz',
    chipVendor: 'Qualcomm WCN7850', powerSaveOn: true,
    ip: '192.168.31.127', gateway: '192.168.31.1', dns: '192.168.31.1',
    mac: 'A2:F3:B4:C5:D6:E7', subnet: '255.255.255.0', bssid: 'F4:C2:A1:9B:33:D8',
    netType: 'NR (NSA)', operator: '中国移动 CMCC',
    nrRSRP: -89, lteRSRP: -101,
    cellBand: 'n78', dlBandwidth: '100 MHz', ulActive: false,
    nearbyAps: [
      { ssid: 'Office-2.4G', rssi: -58 }, { ssid: 'ChinaNet-x9f', rssi: -71 },
      { ssid: 'TP-LINK_5G', rssi: -77 }, { ssid: 'Xiaomi_Guest', rssi: -83 },
    ],
  },

  gps: {
    enabled: true, state: 'fixed', // fixed/searching/waiting/disabled
    lat: 23.129100, lng: 113.264400,
    accuracyM: 4.2, speedKmh: 0, speedMs: 0,
    satellites: [], // generated below
    skyHistory: [],
  },

  sensors: {
    list: SensorDefs.map((d, i) => {
      // 静态信息字段（对齐 SensorItemInfo）：SensorDetailScreen.SensorInfoCard 逐行展示
      const stringType = 'android.sensor.' +
        d.name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
      // 检测/触发类传感器为 ON_CHANGE(1)；其余 CONTINUOUS(0)
      // （原网页按 19/21/11/25 判定，type 校正后对应 SIGNIFICANT_MOTION 17 / STATIONARY_DETECT 29 / MOTION_DETECT 30）
      const reportingMode = SENSOR_ON_CHANGE_TYPES.includes(d.type) ? 1 : 0;
      const meta = TYPE_META[d.type];
      return {
        ...d,
        id: i + 1,
        sensorId: i + 1,
        stringType,
        // 可监控标识 ▶：源码 SensorsScreen.kt:404 `if (meta != null)` → 即 type 命中 SensorTypeMeta
        monitorable: !!meta,
        // 单位 / 轴数 / 轴标签 —— 由 TYPE_META 反查（源码 SensorDetailScreen.kt:276-279）
        unit: meta ? meta.unit : '',
        valueCount: meta ? meta.valueCount : 3,
        axisLabels: meta ? meta.axisLabels : ['X', 'Y', 'Z'],
        // 搜索别名预计算（对齐源码 SensorDataSource.getAllSensors 预填充 SensorItemInfo.searchAliases,
        // SensorItemInfo.kt:24-26；消费方 SensorsScreen.kt:287）
        searchAliases: buildSearchAliases(d.type, stringType, d.name, d.vendor),
        version: d.dynamic ? 1 : 3,
        resolution: +(d.maxRange / 32768).toFixed(6),
        powerMa: +(0.12 + (i % 5) * 0.45).toFixed(3),
        minDelay: reportingMode === 1 ? 0 : (5000 + (i % 4) * 5000),
        reportingMode,
        live: { x: 0, y: 9.81, z: .4, accuracy: 3 },  // 实时值(详情页用)
      };
    }),
    /* ── 搜索状态 —— 逐条对齐 SensorsScreen.kt:111-126 的 remember 状态 ──
     * 注意: 源码搜索**不过滤列表**, 仅做「单步定位 + 脉冲高亮」(L137-163),
     *       列表始终全量渲染 (L252), 计数恒为未过滤总数 (L209)。 */
    query: '',            // L111  输入框实时值 (onQueryChange)
    submittedQuery: '',   // L113  提交值 (onCommit 后生效), 匹配只用它, 不用实时值
    searchTrigger: 0,     // L116  提交计数; LaunchedEffect 的 key; ==0 时不触发定位 (L138)
    searchStep: 0,        // L118  单步定位游标, 多匹配时自上而下推进、到末张后环绕 (L147-148)
    pulseTick: 0,         // L121  独立脉冲计数, 与 highlightedIdx 同批自增 (L149-151)
    highlightedIdx: -1,   // L122  当前高亮卡索引
    listRootTopPx: 0,     // L124  滚动列表容器顶部在根坐标系的 Y
    cardTops: {},         // L126  各卡片顶部在根坐标系的 Y (由 onCardPositioned 采集, L267)
  },

  settings: {
    langLabel: '跟随系统 / Follow System',
    intervals: { cpu: 2000, gpu: 2000, mem: 2000, battery: 2000 },
    globalLight: true, hapticsOn: true, hapticLevel: 1,
    /* settings_haptic_light/standard/heavy 逐字 */
    hapticLabels: ['轻柔 · 触感微弱', '标准 · 清晰反馈', '强烈 · 饱满震感'],
    /* 源码 AppSettings.kt:67-81 默认值：turboXdr=false「用户明确要求默认关」、
       intensity=1.0f「避免用户意外开到很亮烧屏」、nightBar=false；globalLight=true（:60） */
    turboXdr: false, turboXdrStrength: 1.0, nightBar: false,
  },

  hdrLab: {
    potentialHeadroom: 3.6, desired: 2.0, actualRatio: 1.0,
    pqActive: false, eglSummary: 'pending', fullscreen: false,
  },

  floatWindow: {
    enabled: false, visibleMetrics: [...FloatConfig.DEFAULT_VISIBLE],
    refreshMs: FloatConfig.defaultRefreshMs,
    textSize: FloatConfig.textSize, textColor: FloatConfig.textColor,
    alpha: FloatConfig.alpha, bgColor: FloatConfig.bgColor,
  },
};

/* 卫星初始化 */
(function initSats() {
  let n = 0;
  const sats = [];
  for (const c of GNSS_CONSTS) {
    for (let i = 1; i <= c.count; i++) {
      sats.push({
        id: `${c.prefix}${String(i).padStart(2, '0')}`,
        prn: c.prnBase + i - 1,
        sys: c.sys,
        azDeg: rnd(0, 360), elDeg: rnd(5, 88),
        snr: rnd(10, 44), usedInFix: Math.random() > .35,
      });
      n++;
    }
  }
  Sim.gps.satellites = sats;
})();

/* 内存分布历史（每段一条） */
Sim.memory.distHists = Object.fromEntries(
  Object.keys(Sim.memory.dist).map(k => [k, new History(30, Sim.memory.dist[k])])
);

/* ============================================================
 * 每 tick 推进（≈1s 一个仿真节拍）
 * ============================================================ */
function simTick() {
  const S = Sim;
  S.tickCount++;

  /* ── CPU：温度慢漂 + 各核频率游走 ── */
  const t = walker(S.overview.cpuTemp, 33, 52, 0.55);
  S.cpu.temp.push(t);
  /* C-States 深度睡眠总占比缓变（合计锚定 ~75.8%） */
  walker(S.cpu.deepSleep, 70, 82, .3);
  /* C-State 停留时长累计推进 —— 占比由 CpuScreen.kt:145-147 的运行期归一算出（非硬编码），
     故这里只推 timeUs 原始计数，不预存百分比。 */
  Device.cstates.forEach(c => {
    c.timeUs = clamp(Math.round(c.timeUs + rnd(-90000, 90000)), 150000, 12000000);
  });
  /* Per-core hotplug + 使用率（CpuScreen.kt:300-305 的两分支数据源） */
  const lowLoad = S.cpu.load.last() < 18;
  S.cpu.cores.forEach((c, i) => {
    if (!c.online) { if (Math.random() < .22) c.online = true; }
    else if (i >= 6 && lowLoad && Math.random() < .05) { c.online = false; }
    /* offline 时 usagePercent 置 NaN：源码 else-if 分支只在 online 时求值，NaN 即"无可显示" */
    c.usagePercent = c.online
      ? clamp(c.usagePercent + rnd(-7, 7), 2, 100)
      : NaN;
  });
  S.cpu.freqs.forEach((c, i) => {
    /* offline 核心频率为 0（真机 offline 后 cpufreq 读不到 / 归零） */
    if (!S.cpu.cores[i].online) { c.history.push(0); return; }
    const burst = Math.random() < .06 ? rnd(.5, 1.0) : .08;   // 偶发脉冲
    c.history.push(clamp(c.history.last() + (burst * (Device.cpuFreqMax - c.base)) * rnd(-.25, .25)
      + Math.sin((S.tickCount + i * 7) / 17) * 60, 608, i < 4 ? [3300,2500][i] || 3300 : 1800));
  });

  /* ── GPU：负载波动、频率联动 DVFS ── */
  const gload = walker(S.overview.gpuLoad, 3, 96, 7);
  S.gpu.load.push(gload);
  S.gpu.realLoad = Math.round(gload);
  S.gpu.throttled = gload > 82 && Math.random() < .5;
  S.gpu.freqMHz = Math.round(lerp(240, S.gpu.freqMax, gload / 100 + rnd(-.05, .05)));
  S.gpu.freqHist.push(S.gpu.freqMHz);   /* gpu.freq.chart 历史（GpuScreen.kt:69） */
  S.gpu.temp.push(walker(S.gpu.temp, 33, 61, .6));

  /* ── 内存 ── */
  const mu = walker(S.overview.memUsage, 38, 79, 1.4);
  const used = Math.round(Device.memTotalMB * mu / 100);
  S.memory.usedMB = used;
  S.memory.availMB = Device.memTotalMB - used;
  S.memory.usedHist.push(used); S.memory.availableHist.push(S.memory.availMB);
  let d = S.memory.dist;
  d.app = clamp(walker(d.app === undefined ? S.memory.distHists.app : S.memory.distHists.app, .18, .42, .02), .18, .42);
  d.cached = clamp(walker(S.memory.distHists.cached, .1, .26, .018), .1, .26);
  d.system = clamp(walker(S.memory.distHists.system, .07, .14, .01), .07, .14);
  d.free = clamp(1 - d.app - d.cached - d.system - d.other, .15, .6);
  S.memory.swapUsedMB = clamp(Math.round(used - Device.memTotalMB * .48), 0, Device.swapTotalMB);
  S.memory.zramUsedMB = clamp(Math.round(S.memory.zramUsedMB + rnd(-90, 90)), 900, 3600);

  /* ── 电池 ── */
  const B = S.battery;
  if (!B.charging) {
    B.level = clamp(B.level - (Math.random() < .045 ? 1 : 0), 5, 100);
    B.currentMA = Math.round(B.currentMA + rnd(-24, 22));
    B.currentMA = clamp(B.currentMA, -1850, -95);
  } else {
    B.level = clamp(B.level + (Math.random() < .06 ? 1 : 0), 5, 100);
    B.currentMA = Math.round(clamp(B.currentMA + rnd(-160, 160), 320, 3350));
    if (B.full && B.level >= 100) { B.statusKey = 'full'; }
  }
  B.powerMW = Math.round(4.32 * Math.abs(B.currentMA) / 100) * 100 / 100;
  B.wattage = +(B.voltageMV / 1000 * Math.abs(B.currentMA) / 1000).toFixed(2);
  B.chargeCounterUAh += B.charging ? rnd(0, 4200) : -rnd(0, 2600);
  B.chargeCounterUAh = clamp(B.chargeCounterUAh, 2.4e6, Device.batteryDesignmAh * 1e3);
  B.voltageMV = Math.round(lerp(3880, 4380, B.level / 100) + rnd(-14, 14));
  B.levelHist.push(B.level);
  B.tempC.push(walker(B.tempC, B.charging ? 29.5 : 28.5, B.charging ? 46 : 42, .22));

  /* ── 网络 ── */
  S.network.rxMbps.push(clamp(walker(S.network.rxMbps, .05, 220, 6), .05, 240));
  S.network.txMbps.push(clamp(walker(S.network.txMbps, .02, 65, 2.2), .02, 70));
  S.network.nrRSRP = Math.round(clamp(S.network.nrRSRP + rnd(-3, 3), -112, -72));

  /* ── GPS：卫星微动 ── */
  for (const sat of S.gps.satellites) {
    sat.azDeg = (sat.azDeg + rnd(-.35, .35) + 360) % 360;
    sat.elDeg = clamp(sat.elDeg + rnd(-.25, .25), 2, 89);
    sat.snr = clamp(sat.snr + rnd(-2.2, 2.2), 0, 47);
    if (Math.random() < .01) sat.usedInFix = !sat.usedInFix;
  }
  const fixing = S.gps.satellites.filter(s => s.usedInFix).length;
  S.gps.speedKmh = S.gps.state === 'fixed' ? clamp(rnd(-1.2, 6), 0, 60) : 0;
  S.gps.speedMs = +(S.gps.speedKmh / 3.6).toFixed(2);

  /* ── HDR 实验室 500ms 轮询语义 ── */
  const H = S.hdrLab;
  if (S.tickCount % 2 === 0) {
    H.actualRatio = H.desired > 1.01 ? clamp(H.desired * .92 + rnd(-.1, .1), 1.0, 9.9) : 1.0;
    H.pqActive = H.desired > 1.01;
    H.eglSummary = H.pqActive ? 'EGL_SUCCESS · PQ 10-bit' : 'EGL_SUCCESS · RGB888';
  }

  /* ── 数据源健康：IO 源偶发抖动（error/warn 双态演示 HealthTracker 显示路径）── */
  const io = S.sources.find(s => s.id === 'IO');
  if (io.state === 'ok' && Math.random() < 0.012) io.state = Math.random() < .5 ? 'warn' : 'error';
  else if (io.state !== 'ok' && Math.random() < 0.28) io.state = 'ok';

  /* ── 悬浮窗文案刷新由渲染层读取即时值 ── */

  return S.tickCount;
}

/* 格式化 helpers（与 Kotlin 字符串模板一致） */
const Fmt = {
  uptime() {
    const s = Math.floor((Date.now() - Device.uptimeStart) / 1000);
    return `${this.uptimeStr(s)}`;
  },
  /* buildUptimeString 同款: days>0 "Xd Xh Xm" / hours>0 "Xh Xm" / else "Xm" */
  uptimeStr(seconds) {
    if (seconds <= 0) return '';
    const d = Math.floor(seconds / 86400), h = Math.floor((seconds % 86400) / 3600), m = Math.floor((seconds % 3600) / 60);
    return d > 0 ? `${d}d ${h}h ${m}m` : h > 0 ? `${h}h ${m}m` : `${m}m`;
  },
  /* "%.1f°C" — 无空格（源码 Kotlin %.1f°C 逐字对齐） */
  temp(v) { return `${v.toFixed(1)}°C`; },
  mhz(v) { return `${Math.round(v)} MHz`; },
  ghz(v) { return `${(v / 1000).toFixed(2)} GHz`; },
  /* 逐字照抄 FormatUtils.kt:23-29 formatBytes(bytes: Long) —— **入参为字节数**：
       四档阈值、Locale.US（小数点为 "."）、1024 进制。
     与旧实现 `v>=1024?(v/1024).toFixed(2)+' GB':Math.round(v)+' MB'` 的三处差异：
       ① 亚 GB 档源码是 **1 位小数** 的 MB（旧版取整，丢失精度）；
       ② 新增 KB 档（1 MB 以下不再裸显示 "0 MB"）；
       ③ 0 / 负值源码返回 "0 B"（旧版返回 "0 MB"，且 KB 以下无档位）。 */
  mb(bytes) {
    if (bytes <= 0) return '0 B';                                             // L24 bytes <= 0
    if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(2)} GB`;  // L25 >= 1 GiB
    if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`;        // L26 >= 1 MiB
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;              // L27 >= 1 KiB
    return `${Math.trunc(bytes)} B`;                                          // L28 %d B
  },
  /* 适配器（唯一换算点）：模拟数据层统一以 **MB** 计量 → ×1048576 转字节后走上面四档。
     写在 Fmt 里而不是散落到 12 个调用点，避免各处重复 *1048576 而出错。 */
  mbMB(v) { return this.mb(v * 1048576); },
};
