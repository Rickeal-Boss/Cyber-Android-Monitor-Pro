# CAM-P 知识库（机构记忆）

> 由主理人祁研深统一维护。任何分析/开发/审查前先检索本库；与当前代码冲突时以代码为准并回写。

## 0. 项目基本信息
- 仓库：github.com/Rickeal-Boss/Cyber-Android-Monitor-Pro；6.0.6 为 GitHub 默认分支，当前开发分支 `6.0.6.3`（基于 6.0.6 @ ef77a4d，2026-08-22 起传感器/海拔修复线）。
- 6.0.606 线（2026-08-23 起，设备详情页系统步数卡片功能）：GitHub 默认分支 `6.0.606.0.9`（基于 6.0.606.0 @ b17c47e；`6.0.606.0.2` 效果不满意已架空/不采用）。Tag 序列 `v6.0.606.0.9-debug-pre1` 起。见 §12。
- 技术栈：Kotlin + Jetpack Compose；局部 HDR / TurboXDR（PQ 贴片）在原生 `GLSurfaceView` 上合成。
- Git author（强制保留）：`Rickeal-Boss (private) <rickealobossdayone@agentmail.com>`。

## 1. CI 约定（重要）
- 触发：GitHub Actions 由 **Tag** 触发（非分支 push）。workflow `.github/workflows/android-build.yml` 匹配 `v*-debug-pre*` → `Build Signed Release APK`（`./gradlew assembleRelease`）。
- 轮询脚本：`C:\Users\Z\Downloads\newW2zhuan\ci_poll.sh <40位 head_sha> [timeout=1800]`；读 `$PAT` env，`curl --ssl-no-revoke`，失败用 `actions/jobs/{id}/logs` 端点 + 302 匿名跟随拉日志到 `ci_logs_<run>.txt`。脚本内部依赖 `python3`。
- **PAT 注入**：必须 `export PAT=...` 后在同一命令用 `$PAT` 展开；不可 inline `VAR=val cmd`（父 shell 不展开 → 401）。
- **git push 凭据助手语法（pre7 踩坑）**：`-c credential.helper='!f() { printf "username=x-access-token\npassword=%s\n" "$PAT"; }; f'`。`!` 后的 shell 片段必须**定义后调用** `f`，否则 git 把追加的 `get` 当命令 → "syntax error near unexpected token" → 回退交互式用户名提示、最终 `could not read Username`。
- **本机 CI 必须由主线程持有后台任务**：子 agent 结束后其后台 bash 会被回收，推送不落地。主理人直接 `run_in_background` 跑 push+poll 才可靠。

## 2. 覆盖层动画机制（MainActivity.kt）
- 主时钟（容器）：`sensorProgress` / `hdrProgress` 为 `Animatable<Float>`，**驱动 scale + 内容**；容器背景(CyberCardStart ×0.92)由 *Scrim 驱动（与 scale 解耦——见 pre11 修正）；`PredictiveBackHandler` snapTo 跟手、取消回弹 1f。
- scrim 独立时钟：`sensorScrim` / `hdrScrim`（`Animatable<Float>`，各自 0=无 1=满），**与容器进度解耦**，单独驱动 scrim alpha `(s²)*0.22`。
- **转场排序（pre10 / round6 定稿）**：scrim 与容器解耦后顺序编排 ——
  - 打开：先 `animateTo` 容器展开（progress→1）→ **然后** 才 `animateTo` scrim 到位（主界面被完全隔绝）。
  - 关闭：先 `animateTo` scrim 解除（scrim→0）→ **最后** 才 `animateTo` 容器收起（progress→0）。
  - 预测返回（关闭拖拽）：容器跟手 `* t`；scrim 领先解除 `* t * t`；完成两者归 0，取消两者回弹 1。
- HDR/Sensor 覆盖层三段式：① scrim（**pre12 起**：透明 `Box`+`drawBehind{drawRect(Color.Black, alpha=s²·0.22)}`，不再用 `background(Color.Black)+graphicsLayer{alpha}`——后者把实心黑填进离屏 alpha 层，首帧离屏缓冲被清成不透明黑 → "scrim 下出现全黑闪层"；drawBehind 直接以目标 alpha 合成、不建离屏层，根除黑闪。跟 *Scrim）；② 容器（`graphicsLayer{scale 0.3→1.0 + transformOrigin}` 跟 *Progress，`drawBehind{drawRect(CyberCardStart ×0.92)}` 跟 *Scrim — **scale 与 bg 解耦**，pre11 起；bg alpha 改 drawBehind 移出层属性，pre12 起）；③ 内容（`graphicsLayer{alpha 阈值 + 上移}`，跟 *Progress，根透明无实心填充、无黑闪风险）。根 `Box` `pointerInput` 消费点击隔绝误触。
- **pre7 定稿**：锚点 `TransformOrigin(0.5f,0.5f)`（屏幕中心）；scrim alpha `(p²)*0.22`、容器 bg alpha `(p²).coerceIn(0,1)`（二次曲线，展开早期透明、主界面可见，消除"先全黑再展开"）。
- **pre8 修正（覆盖层全黑 + 缩放不够小）**：容器 bg 引入 `((x-0.6f)/0.4f).coerceIn(0,1)` 收尾淡入（**pre8~pre10 x=progress，pre11 起 x=scrim**——见 pre11）；scale 起点 `0.42→0.3`——起点更小、叠加无黑底，过渡更顺。HDR 与传感器两容器同步改。
- `fallbackOrigin`（右上角按钮区）仍用于设置/悬浮窗揭示；`sensorRevealOrigin` 自 pre7 起仅写不读（死代码，待清理）。

## 3. 卡片描边 / 水波纹（关键语义坑）
- **修饰符顺序决定裁剪**：`Modifier.a().b()` 中 `a`=OUTER（先画、包在外），`b`=INNER（后画、靠近内容）；绘制顺序 inner 先、outer 后包裹。
- `cardRipple` 用 `drawWithContent` + `clipPath(inset 4dp)` 裁其内层内容；`cardGradientBorder` 用 `drawWithCache` 在 `drawContent()` 后画描边。
- **结论**：渐变描边必须在 OUTER，即 `.cardGradientBorder(...).cardRipple(...)`；若 `cardRipple` 在 OUTER，其 clip 会裁掉描边（round2 改反、pre7 修正）。

## 4. TurboXDR / PQ 透明合成（已知大坑）
- 渲染管线路径：`CyberNightlightSwitch.enabled`（总开关，默认 false）→ `HdrPatchHost`/`CyberNightlightHost` 挂载全屏透明 PQ `GLSurfaceView`（`RGBA_1010102`）、`HdrPatchSurfaceView`/`HdrLumeSurfaceView`。
- **★ pre8 误诊（pre9 真机验证后推翻）**：pre8 把 pre7 的 `setZOrderOnTop(true)` 改成 `setZOrderMediaOverlay(true)`，理由据称是"onTop 透明区透出桌面、mediaOverlay 透出本窗口 SDR 内容且 HDR 描边仍压其上"。**但 pre9 用户真机验证结论相反**：① mediaOverlay 把 PQ 表面压到不透明 SDR 卡片之下 → 卡片内部贴片（TEXT_GLYPH/CHART_LINE/CHART_GRID，无"卡内填充"类型）被盖住，只剩顶部透明区 CARD_BORDER 描边可见（现象："TurboXDR 只剩 HDR 描边"）；② mediaOverlay 透明区仍穿透到窗口背后的桌面（背景透明可见桌面，正是 pre8 声称修好的老 bug 复现）。
- **pre9 修复 + 关键认知**：`HdrPatchSurfaceView` **revert 回 `setZOrderOnTop(true)`**。`onTop` 透明区是否漏桌面，取决于**窗口本身是否透明**——CAM-P 窗口背景是**不透明** `windowBackground=#0A0A0F`（themes.xml），故 onTop 透明区只压在这层不透明窗口背景之上，**桌面不穿透**。pre8"onTop 漏桌面"的前提是基于透明窗口，对 CAM-P 不成立。
- **z-order 最终定论**：`HdrPatchSurfaceView`=onTop；`HdrLumeSurfaceView`（夜光条）=保持 `setZOrderMediaOverlay(true)`。**全局仅一个 onTop 10-bit 表面**（两个 onTop 会争抢 HDR overlay 平面致 SF crash，pre13 教训仍有效）。pre8"两个 PQ surface 均不可用 onTop"过于绝对——patch 表面回归 onTop 是安全的，只要 Lume 仍是 mediaOverlay 即可保证唯一 onTop。
- HDR 卡片描边经 `hdrCardBorderPatch` 上报 PQ 表面；`hdrSurfacesVisible` 门控保留（避免描边突跳）。

## 5. sharedElement / 共享转场（round4 大坑，勿再误用）
- **pre8 移除**：列表传感器卡（`SensorsScreen.SensorItemCard`）与详情标题（`SensorDetailScreen`）曾用同名 key `sensor_<id>` 的 `sharedElement`：列表包**整张 Card**（大 bounds）、详情只包**标题 Box**（小 bounds），且双方 `AnimatedVisibility(visible=true)` 永不退场 → 形变副本卡在 overlay 顶层，出现"某张卡浮顶盖住其它卡、其余不可见"。
- **规则**：本仓库列表→详情是 MainActivity 全屏覆盖层 + `sensorProgress` 缩放转场，**不走 sharedElement**。若将来要加 shared transition，必须保证：双方 bounds 语义一致（同元素）、`AnimatedVisibilityScope` 正常退场、key 唯一且范围对称，否则直接复用覆盖层转场即可。

## 6. Compose graphicsLayer 编译坑（pre5/pre6）
- `LocalDensity.current` / `LocalConfiguration.current` 是 @Composable，**不能**在 `graphicsLayer { }` 这类非 Composable lambda 内调用 → 编译失败。必须外提到 graphicsLayer 前的普通 `val`。
- lambda 内定义的局部变量（如 `p`）在 lambda 外不可见 → "Unresolved reference"。同样外提。

## 6b. Compose 修饰符 import 解析坑（pre9 踩坑）
- **`weight` 切勿显式 `import androidx.compose.foundation.layout.weight`**：该包内同时存在公开的顶层 `Modifier.weight()` 函数与 **internal 的 `RowColumnParentData.weight` 属性**，`import ...layout.weight` 按简单名导入时会绑定到 internal 属性 → 报 `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file`。仓库内 `MainActivity` 用 `import androidx.compose.foundation.layout.*` 通配、`DashboardScreen`/`SettingsScreen` 等**从不按名 import weight**，全靠 Row/Column 作用域内 `RowScope`/`ColumnScope.weight` 自动解析（`Spacer(Modifier.weight(1f))` 在 Column 内即合法）。
- **`fillMaxSize` 等无同名 internal 属性，可安全显式 import**（pre9 首次引入 `.fillMaxSize()` 时即这样加，正常）。
- **审查清单必加一项**：新增/修改 Compose 修饰符后，逐项核对每个新调用的修饰符是否已在作用域可见（import 或 scope 扩展），否则 CI 才暴露 `Unresolved reference`/`internal` 错误（本地无 JDK 无法预编译）。

## 7. Round 历史
- pre1 success / pre3 failure / pre4 success / pre5 failure / pre6 success / pre7 success（run #32333367929，`e53aa745`）/ pre8 success（run #32343534924，`8a454d0`，round4）/ pre9（big-fix2，`757a4f1`，CI 经 2 次编译失败修复后成功：run #32354937648 失败 fillMaxSize 缺 import → #32355525518 失败 weight 显式 import 冲突 → 修后成功）/ pre10（big-fix2，**不彻底**——仅解耦 0.22 scrim，0.92 容器 bg 仍跟 progress，真机视觉无变化）/ pre11（big-fix2，**pre10 状态派生修正：容器 0.92 bg 改跟 *Scrim**）/ pre12（big-fix2，**黑闪修复：scrim/容器背景改 drawBehind 取代 background+graphicsLayer{alpha}，根除离屏层黑闪**）。
- **pre8 = round4 回归修复**：① 撤 TurboXdrCompat 守卫（修 pre7 导致的 TurboXDR/夜光条全失效）；② 移除传感器 sharedElement（修"传感器32"卡浮顶盖住其它卡）；③ 覆盖层 bg 收尾淡入 + scale 起点 0.3（修动画全黑/过渡突变）。锚点修复（pre7 中心锚点）保留未动。
- **pre9 = round5（big-fix2）回归修复（推翻 pre8 的 PQ z-order 误诊）**：① `HdrPatchSurfaceView` revert 回 `setZOrderOnTop(true)`（修 pre8 引入的 TurboXDR 内部 HDR 贴片消失 + 背景透明穿透桌面）；`HdrLumeSurfaceView` 保持 mediaOverlay（全局仅一个 onTop 10-bit 表面，避开 pre13 SF crash）；② HDR/Sensor 覆盖层容器 bg 由全不透明改为 ×0.92（半透明，比设置/浮窗 0.85 更不透）；③ 设置覆盖层 `fillMaxSize()`+`Spacer(weight(1f))` 铺满屏幕（与悬浮窗对齐）。验证前提：onTop 安全依赖不透明 `windowBackground=#0A0A0F` 兜底，仍需真机复测桌面不穿透（CI 仅编译不跑设备）。
- **pre10 = round6（big-fix2）转场排序重构（**不彻底**）**：将 HDR/Sensor 覆盖层的 **0.22 scrim 层**从单一主时钟 *Progress 解耦为独立 *Scrim，open/close animateTo 顺序正确反转。但 **0.92 容器背景(CyberCardStart)** 仍误绑在 *Progress，导致用户感知的"完全隔绝/黑色隔板"（实际是 0.92 bg，不是 0.22 scrim）与容器 scale 同相位——真机上看似转场顺序未变，仅时长变慢（1.1s）。预测返回拖拽期 scrim 用 `* t * t` 领先解除、完成归零、取消回弹。
- **pre11 = round7（big-fix2）pre10 状态派生修正**：HDR/Sensor 覆盖层 0.92 容器背景从 *Progress 改跟 *Scrim 驱动（curve 保留 `((s-0.6)/0.4).coerceIn(0,1)*0.92`）。scale（*Progress）与 bg（*Scrim）真正解耦——打开 = 容器 scale 0.3→1.0（背景透明可见主界面）→ scrim→1 + bg→0.92（主界面被完全隔绝）；关闭 = scrim→0 + bg→0（隔绝解除）→ progress→0（容器收起）。预测返回拖拽：bg 跟 scrim *t² 同步（s<0.6 即 0），完成归零、取消回弹。视觉验证仍需真机（CI 仅编译不跑设备）。
- **pre12 = round8（big-fix2）黑闪修复**：用户真机发现"scrim 下出现真正全黑层（开始时全黑→渐变到正常背景）"。根因 = `background(Color.Black)+graphicsLayer{alpha}` 把实心黑填进离屏 alpha 层，首帧离屏缓冲被清成不透明黑（OEM GPU 常见黑闪）。**修复**：scrim 改透明 `Box`+`drawBehind{drawRect(Black, alpha=s²·0.22)}`；容器 bg 同步改 `drawBehind`（scale 仍留 graphicsLayer）；内容层③根透明无实心填充、低风险保留。顺序逻辑(pre11)不变。CI 仅编译不跑设备，黑闪是否消失须真机复测。

## 8. 协作/工具
- 频率限制：hy3/reasoning 模型易 429；成员沉默/限流时主理人直接兜底。
- 团队实例跨会话会丢失，需重建。
- 子代理 shell stdout 捕获故障（2026-08-22）：Bash/PowerShell 命令执行成功（exit 0）但 stdout 不回显 → 用「输出重定向落盘 + Read 读回」兜底，结论仍来自文件原文。

## 9. 传感器/海拔/搜索框 round（6.0.6.3，2026-08-22，commit 7d29e54 + tag v6.0.6.3-debug-pre1）
- **B1 枚举策略**：SensorTypeMeta 补类型时**只追加不重排**——fromTypeId 用 `entries.find` 与声明顺序无关，全量重排纯 churn（19 行全动、diff 难审、易错位）。现 34 条覆盖 type 1–42；OEM 私有（type≥65536）靠 `Sensor.getStringType()` 末段 humanize 回退（"com.vendor.sensor.motion_recognition" → "Motion Recognition"），采集时 try-catch 防 OEM 异常。
- **AOSP getDefaultSensor wake-up 白名单坑**：STEP_DETECTOR(18)/PRESSURE(6) 不在白名单 → 仅暴露 wake-up 版本的设备上 `getDefaultSensor` 返回 null 但 getSensorList 里有。标准解法：三级降级链 `getDefaultSensor(type) ?: getDefaultSensor(type, true) ?: getSensorList(type).firstOrNull()`（均 API 21+，minSdk 21 可直用）。
- **速率类计算模式**（气压海拔 A1 教训）：速率基准点绝不能每样本刷新（阈值永不满足→速率恒 null→UI 恒 "---"）；改独立基准每秒一算 + EMA(α=0.3) 平滑 + 间隔内复用平滑值防闪烁；时间戳必须用事件单调时钟（`SensorEvent.timestamp/1e6`，elapsedRealtime 域）防 NTP 墙钟跳变；校准/设参考点后必须重置基准并清 EMA（防假爬升尖峰）。GPS 校准守卫：fixAcquired + accuracy≤20m（NaN 拒绝）。
- **搜索框跳转高亮**（SensorsScreen 方案Y：不过滤列表，输入即定位+脉冲高亮）：
  - 布局拆分：固定头（标题 Row + 搜索框 + 计数）+ 滚动列表 Column(weight(1f)+verticalScroll)。**拆分后外层 Column 必须补 verticalArrangement=spacedBy**；滚动内容底边距要放 `.verticalScroll()` **之后**的 `.padding(bottom=16.dp)`（=内容 padding，放外面则滚到底贴边）。
  - 滚动目标公式：`target = cardTop(boundsInRoot) + scrollState.value - listRootTopPx - margin`（boundsInRoot 已含滚动偏移，加回 value 得内容坐标）。
  - **同尺寸 drawBehind 辉光会被不透明卡片完全遮住**——halo 必须外扩（inflate 8dp：topLeft 负偏移 + size 加倍 + cornerRadius 同步加大），Compose 默认不裁剪越界绘制。
  - query 必须 trim：纯空格查询会因 haystack 含空格恒命中第一张卡（跳顶+脉冲）。
  - 脉冲高亮：外层 Box `graphicsLayer(scale)` + `drawBehind(glow)`，Card 修饰符链（cardGradientBorder 外 / cardRipple 内）原样不动。
- **本机编译环境**（备选，用户允许超时即跳过）：`JAVA_HOME=C:/Users/W2/.jdks/temurin-21.0.12`、`GRADLE_USER_HOME=C:/Users/W2/.gradle`、`./gradlew :app:compileDebugKotlin --offline` 可 BUILD SUCCESSFUL（首次 ~2min / 增量 ~6s）；仅 DeviceDetailDataSource.kt 预存警告。
- **三语 strings 插入锚点行号各不相同**（values≈L610 / zh-rCN≈L310 / zh-rTW≈L315 的 `sensor_type_accelerometer_uncalibrated` 之后），必须按文本锚点定位、勿硬编码行号。
- 本轮 CI：tag `v6.0.6.3-debug-pre1` 触发 run 32577515048（结果见每日记忆 2026-08-22）。

## 10. 文档深度审计 + pre2 round（6.0.6.3，2026-08-22，commit 8c4964a + tag v6.0.6.3-debug-pre2）
- **官方文档审计裁定**：34=LOW_LATENCY_OFFBODY_DETECT 映射正确（三源核对）；32=DYNAMIC_SENSOR_META 系统专用、33=ADDITIONAL_INFO 仅 HAL 层不暴露——跳过正确。SIGNIFICANT_MOTION(17) 是 one-shot（须 requestTriggerSensor，registerListener 无事件）→ 留档不修（UI 模型不匹配）。HEAD_TRACKER(37) 系统专用。HEART_RATE(21) 无 BODY_SENSORS 时传感器直接不返回（HAL requiredPermission 过滤）。采样率 <<200Hz 无需 HIGH_SAMPLING_RATE_SENSORS。getStringType() 实为 **API 20+**。
- **ACTIVITY_RECOGNITION（官方仅要求 STEP_COUNTER/STEP_DETECTOR，SIGNIFICANT_MOTION 不需要）**：未授权时步数传感器直接从 getSensorList 消失（wake-up 降级链无法兜底）——B2 的第二根因。pre2 修复：Manifest 声明 + 传感器页用户触发"授权"按钮（照 DeviceScreen 蓝牙先例）+ 授权后 refreshSensors() 重采。
- **★ 公开 SDK 常量名 ≠ AOSP 源码名（重要教训）**：`CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`（公开 SDK，API 21，**无 _MODES 后缀**；..._MODES 是 AOSP 内部名）；`CONTROL_AVAILABLE_PREVIEW_STABILIZATION_MODES` Key 不存在——预览防抖以 `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES` 列出 `CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION` 枚举值表示（API 33）。**规格/审查引用 SDK 常量必须 javap 对实际 android.jar 实证**——本轮主理人规格写错常量名，由柯码成 javap 实证纠正（成员实证 > 主理人断言）。OIS/EIS 反射改直接常量引用，**双数据源（DeviceDetailDataSource + OemDataSource 同款拷贝）必须同修**。
- **OIS 不在公开 Sensor API**（属相机子系统，官方通道 Camera2）——用户"传感器列表找不到 OIS"属架构使然；CAM-P 相机卡特性行（DeviceScreen CameraRow features）已展示 OIS/EIS。

## 11. Live Updates 方案存档（2026-08-22 完成调研+方案设计，**用户决定不实施**）
- 关键事实（供未来重启）：API 36 `ProgressStyle` 无双栏文本能力（Segment 只有 length+color、Point 只有 position+color，禁 RemoteViews）；真双栏模板 `MetricStyle`（≤3 指标并排）属 **Android 17（API 37）**；完整提升体验需 **36.1 QPR1**（SDK_INT 恒 36，须 `Build.VERSION.SDK_INT_FULL`/`BuildCompat.isAtLeastB_1()` 判断；compat 路径 `NotificationCompat.setRequestPromotedOngoing` 需 core≥1.17）。Eligibility：`POST_PROMOTED_NOTIFICATIONS`（normal 装时授予）+ setOngoing + contentTitle + 禁 customContentView/group summary/colorized + channel≠IMPORTANCE_MIN。官方 1 秒内多次更新会被丢弃→变更阈值节流；用户划掉不得自动重发（setDeleteIntent）。国产 ROM 分裂：小米（miui.focus.param 私有+邮件申请+年度续期）/vivo（superx 私有，islandData 原生左右双栏但 scene 白名单无硬件监控）/OPPO ColorOS 15（私有流体云）；ColorOS 16/OriginOS 6/Nothing OS 4/三星 One UI 8 Now Bar 消费原生 API。
- CAM-P 冲突评估结论：唯一 FGS（FloatingWindowService）通知 id=1001/通道 floating_window(LOW) 不可复用（互踩）；**三权限缺口：POST_NOTIFICATIONS/READ_PHONE_STATE/ACCESS_FINE_LOCATION 全声明全无运行时申请**（Android 13+ 新装通知默认关）；**采集循环归 MainActivity（DisposableEffect）——activity 回收即全部 SharedFlow 停发（悬浮窗既有隐患）**，任何常驻通知须服务自持采集；电池温度现成 `repo.batteryFlow`（sticky intent 官方唯一路径，无 BatteryManager 属性）；SS-RSRP 现有 parseNr 轮询路径（`tm.signalStrength` API 28+，<28 无守卫已顺手修）。
- 完整方案 α（独立 LiveUpdatesService + 通知 id 1002 + live_updates 通道 + 温度仪表条 + 双权限申请 + 节流器，8 文件拆解）存档于每日记忆 2026-08-22，重启时可直接派工。

## 12. 设备详情页系统步数卡片（6.0.606.0.9，2026-08-23）
- **功能**：DeviceScreen 新增「运动健康（步数）」卡片，依赖 STEP_COUNTER 传感器（TYPE_STEP_COUNTER=19）+ ACTIVITY_RECOGNITION 运行时权限（API 29+）。架构链路：`DeviceRepository.hasStepCounter()`/`enableStepCounter(onReading)`（三级降级链取传感器）→ `StepCounterStore` 账本对账（`offset + (raw - bootBaseline)`，`dayStamp` 取今日步数）→ `DeviceViewModel._stepUi: MutableLiveData<StepUiState>`（todaySteps/totalSteps/stepsSinceBoot，60s 速率窗口）→ `DeviceScreen` 用 `val stepUi by viewModel.stepUi.observeAsState()` 渲染；未授权时显示「点击授权」`RowItemClickable`（照蓝牙先例 `activityPermLauncher.launch(ACTIVITY_RECOGNITION)`）。派生指标常量 `AVG_STRIDE_M=0.762f`/`KCAL_PER_STEP=0.04f`/`STEPS_PER_MIN=100f` 在 `SensorDetailViewModel` 提为 `internal const val` 单一来源，跨页共享（消除字面量重复）。
- 三语 strings 各 +5 条：`device_section_health` / `device_step_total_label` / `device_step_boot_label` / `device_step_permission_label` / `device_step_permission_action`。
- **★ CI 编译失败教训（括号失衡逃逸静态审查）**：首次提交 `913bfe8` 因插入的步数卡外层 `if (hasStepCounter || needActivityPerm) {` 缺闭合 `}`（只闭合了内层 `Box`），把文件级 `private fun SectionCard/RowItem/RowItemClickable` 重新嵌套为 `DeviceScreen` 的**局部函数**（局部函数须先声明后使用）→ 报 `Unresolved reference 'SectionCard'/'RowItem'` 级联 + "@Composable invocations can only happen from a @Composable function"。**根因是结构性括号失衡，code-quality-reviewer 的 import/scope 静态审查给 PASS 无法发现**（函数定义本身语法合法，只是被错误嵌套）。修复 = 在外层 if 末尾补 `}`（L440 闭 Box、L442 闭 if）。
- **★ 审查清单追加项（本地无 JDK 不可编译，括号类错误只能靠 CI 暴露）**：Compose 文件改完后必须①肉眼/grep 核对顶层 `private fun`（尤其 `SectionCard/RowItem/RowItemClickable`）仍在**文件作用域**（`grep '^private fun'`）；②整体括号配平，尤其新插入的 `if` / `Box` / `SectionCard` 三层嵌套要逐层数；③确认新调用的修饰符/导入在作用域可见（延续 §6b）。任一缺失只能等 CI 编译失败才暴露。
- Round：`v6.0.606.0.9-debug-pre1` 首次 run #32627541620 编译失败（括号失衡）→ 补 `}` 后同 tag 名 `-f` 强制推送重触发。CI 仅编译不跑设备，步数真机行为（传感器可用性/授权后重采）须用户真机复测。
