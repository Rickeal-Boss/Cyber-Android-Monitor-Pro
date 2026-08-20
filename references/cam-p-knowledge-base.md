# CAM-P 知识库（机构记忆）

> 由主理人祁研深统一维护。任何分析/开发/审查前先检索本库；与当前代码冲突时以代码为准并回写。

## 0. 项目基本信息
- 仓库：github.com/Rickeal-Boss/Cyber-Android-Monitor-Pro，开发分支 `big-fix`。
- 技术栈：Kotlin + Jetpack Compose；局部 HDR / TurboXDR（PQ 贴片）在原生 `GLSurfaceView` 上合成。
- Git author（强制保留）：`Rickeal-Boss (private) <rickealobossdayone@agentmail.com>`。

## 1. CI 约定（重要）
- 触发：GitHub Actions 由 **Tag** 触发（非分支 push）。workflow `.github/workflows/android-build.yml` 匹配 `v*-debug-pre*` → `Build Signed Release APK`（`./gradlew assembleRelease`）。
- 轮询脚本：`C:\Users\W2\Videos\newW2zhuan\ci_poll.sh <40位 head_sha> [timeout=1800]`；读 `$PAT` env，`curl --ssl-no-revoke`，失败用 `actions/jobs/{id}/logs` 端点 + 302 匿名跟随拉日志到 `ci_logs_<run>.txt`。脚本内部依赖 `python3`。
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
- HDR/Sensor 覆盖层三段式：① scrim（`Box`+`Color.Black`+`graphicsLayer{alpha = s²·0.22}`，跟 *Scrim）；② 容器（`graphicsLayer{scale 0.3→1.0 + transformOrigin}` 跟 *Progress，`background(CyberCardStart ×0.92)` 跟 *Scrim — **scale 与 bg 解耦**，pre11 起）；③ 内容（`graphicsLayer{alpha 阈值 + 上移}`，跟 *Progress）。根 `Box` `pointerInput` 消费点击隔绝误触。
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
- pre1 success / pre3 failure / pre4 success / pre5 failure / pre6 success / pre7 success（run #32333367929，`e53aa745`）/ pre8 success（run #32343534924，`8a454d0`，round4）/ pre9（big-fix2，`757a4f1`，CI 经 2 次编译失败修复后成功：run #32354937648 失败 fillMaxSize 缺 import → #32355525518 失败 weight 显式 import 冲突 → 修后成功）/ pre10（big-fix2，**不彻底**——仅解耦 0.22 scrim，0.92 容器 bg 仍跟 progress，真机视觉无变化）/ pre11（big-fix2，**pre10 状态派生修正：容器 0.92 bg 改跟 *Scrim**）。
- **pre8 = round4 回归修复**：① 撤 TurboXdrCompat 守卫（修 pre7 导致的 TurboXDR/夜光条全失效）；② 移除传感器 sharedElement（修"传感器32"卡浮顶盖住其它卡）；③ 覆盖层 bg 收尾淡入 + scale 起点 0.3（修动画全黑/过渡突变）。锚点修复（pre7 中心锚点）保留未动。
- **pre9 = round5（big-fix2）回归修复（推翻 pre8 的 PQ z-order 误诊）**：① `HdrPatchSurfaceView` revert 回 `setZOrderOnTop(true)`（修 pre8 引入的 TurboXDR 内部 HDR 贴片消失 + 背景透明穿透桌面）；`HdrLumeSurfaceView` 保持 mediaOverlay（全局仅一个 onTop 10-bit 表面，避开 pre13 SF crash）；② HDR/Sensor 覆盖层容器 bg 由全不透明改为 ×0.92（半透明，比设置/浮窗 0.85 更不透）；③ 设置覆盖层 `fillMaxSize()`+`Spacer(weight(1f))` 铺满屏幕（与悬浮窗对齐）。验证前提：onTop 安全依赖不透明 `windowBackground=#0A0A0F` 兜底，仍需真机复测桌面不穿透（CI 仅编译不跑设备）。
- **pre10 = round6（big-fix2）转场排序重构（**不彻底**）**：将 HDR/Sensor 覆盖层的 **0.22 scrim 层**从单一主时钟 *Progress 解耦为独立 *Scrim，open/close animateTo 顺序正确反转。但 **0.92 容器背景(CyberCardStart)** 仍误绑在 *Progress，导致用户感知的"完全隔绝/黑色隔板"（实际是 0.92 bg，不是 0.22 scrim）与容器 scale 同相位——真机上看似转场顺序未变，仅时长变慢（1.1s）。预测返回拖拽期 scrim 用 `* t * t` 领先解除、完成归零、取消回弹。
- **pre11 = round7（big-fix2）pre10 状态派生修正**：HDR/Sensor 覆盖层 0.92 容器背景从 *Progress 改跟 *Scrim 驱动（curve 保留 `((s-0.6)/0.4).coerceIn(0,1)*0.92`）。scale（*Progress）与 bg（*Scrim）真正解耦——打开 = 容器 scale 0.3→1.0（背景透明可见主界面）→ scrim→1 + bg→0.92（主界面被完全隔绝）；关闭 = scrim→0 + bg→0（隔绝解除）→ progress→0（容器收起）。预测返回拖拽：bg 跟 scrim *t² 同步（s<0.6 即 0），完成归零、取消回弹。视觉验证仍需真机（CI 仅编译不跑设备）。

## 8. 协作/工具
- 频率限制：hy3/reasoning 模型易 429；成员沉默/限流时主理人直接兜底。
- 团队实例跨会话会丢失，需重建。
