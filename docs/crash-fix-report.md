# 启动闪退修复报告

## 修复了 3 个可能导致闪退的问题

### 1. TabTextAppearance 样式不兼容 (themes.xml)
- **问题**: `TabTextAppearance` 父样式 `TextAppearance.Design.Tab` 来自 Design Support Library，在 `Theme.Material3.Dark.NoActionBar` 主题链中可能不存在
- **崩溃类型**: `Resources$NotFoundException`
- **修复**: 父样式 → `TextAppearance.Material3.LabelLarge` (Material 3 内置样式)

### 2. CpuFragment 废弃 API 空指针风险
- **问题**: `getBackground().setTint()` — `getBackground()` 可能返回 null 导致 NPE；`Drawable.setTint()` 在新 API 上已废弃
- **崩溃类型**: `NullPointerException`
- **修复**: 替换为 `ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color))` — 空安全 + 非废弃

### 3. fitsSystemWindows 与 setDecorFitsSystemWindows 冲突
- **问题**: CoordinatorLayout 设置 `fitsSystemWindows=true`，但 `MainActivity.onCreate()` 调用 `getWindow().setDecorFitsSystemWindows(false)`，两者互斥
- **崩溃类型**: 布局计算异常 → `RuntimeException`
- **修复**: 移除 XML 中的 `fitsSystemWindows`，统一由代码管理 insets

### 额外修复
- `tabRippleColor="@null"` → `@android:color/transparent` (部分版本 null 不兼容)
