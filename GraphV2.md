# GraphV2 — 骨架提取与预处理 Pipeline

## 概述

将任意尺寸的符文/符号二值图转换为紧凑的 1px 宽骨架，为后续图匹配做准备。

## Pipeline

```
PNG (任意尺寸)
  │
  ├─ fillSmallHoles           ■ 4-连通封闭孔洞填充 (阈值 = min(5, max(w,h)/10))
  │
  ├─ upscaleConnectivityPreserving(≥128)
  │                           ■ K×K 块放大，对角邻接块间桥接像素保持 8-连通
  │                           ■ K = ⌈128 / min(w,h)⌉
  │                           ■ 32×32 → 129×129, 64×64 → 129×129, 24×24 → 145×145
  │
  ├─ gaussianSmoothBinary(σ=K/3)
  │                           ■ 可分离高斯模糊 + 0.5 阈值二值化
  │                           ■ 消除块放大引入的阶梯锯齿，平滑对角线边缘
  │
  ├─ skeletonize              ■ Guo & Hall (1989) 两子迭代 LUT 细化
  │                           ■ 与 skimage.morphology.thin() 逐像素完全一致
  │
  ├─ cropToForeground         ■ 8-连通 BFS 寻找最大连通域
  │                           ■ 取边界框裁剪至紧凑尺寸
  │                           ■ 剔除孤立的背景噪点
  │
  └─ 输出
      ├─ {name}_smooth.png    ■ 上采样+平滑+裁剪后的二值图
      └─ {name}_skel.png      ■ 裁剪后的 1px 宽骨架
```

## 算法详情

### 1. fillSmallHoles — 封闭孔洞填充

| 项目 | 值 |
|------|-----|
| 连通方式 | 4-连通 |
| 孔洞定义 | 不与图像边界相连的背景区域 |
| 阈值 | `min(5, max(w,h) / 10)`（32px 图≈3, 64px 图≈5） |
| 用途 | 消除手绘线条交叉处产生的小空格 |

### 2. upscaleConnectivityPreserving — 连通性保持的上采样

| 项目 | 值 |
|------|-----|
| 最小输出尺寸 | 128×128 |
| 块大小 | `K = ⌈128 / min(w,h)⌉` |
| 块形状 | K×K 的实心块 |
| 对角桥接 | 每个对角相邻的前景块之间填充 2 像素桥（SE+SW 方向各一对） |
| 输出尺寸 | `w·K + 1 × h·K + 1` |

**桥接原理**：无桥接时，对角邻接块仅在角点接触（8-连通意义下仍连通，但 Zhang-Suen/Guo-Hall 细化时对角接触产生一个 junction 节点）。桥接使角点接触变成边接触，细化后无多余节点。

### 3. gaussianSmoothBinary — 高斯平滑二值化

| 项目 | 值 |
|------|-----|
| 实现 | 可分离高斯（先 X 后 Y） |
| σ | `K / 3.0`（自适应：K=8→2.67, K=4→1.33, K=2→0.67） |
| 核半径 | `⌈3σ⌉` |
| 阈值 | 0.5 |
| 边界处理 | clamp（复制边缘像素） |

### 4. skeletonize — Guo & Hall 细化

| 项目 | 值 |
|------|-----|
| 算法 | Guo & Hall (1989) 平行细化 |
| LUT | `G123_LUT[256]` + `G123P_LUT[256]`，运行时生成，与 skimage 源码位一致 |
| Mask | `[[8,4,2],[16,0,1],[32,64,128]]`（E=1, NE=2, N=4, NW=8, W=16, SW=32, S=64, SE=128） |
| 迭代 | 两子迭代交替，每子迭代: snapshot→决策→write→swap |
| 终止 | 连续一个子迭代无像素被删除 |
| padding | 1px 零值边界（匹配 Cython `thin()` 的 `ndi.correlate(mode='constant')`） |

**LUT 条件**：
- **G1**：恰好一个 0→1 跳变（连通性保持）
- **G2**：min(n1, n2) ∈ {2, 3}（端点保护）
- **G3**：`!( (NE|N|!E) & S )`（SE 边界删除，子迭代 1）
- **G3'**：`!( (SW|S|!W) & N )`（NW 边界删除，子迭代 2）

**验证**：与 `skimage.morphology.thin()` 在所有 55 张测试图上逐像素完全一致（0 差异像素）。

### 5. cropToForeground — 紧凑裁剪

| 项目 | 值 |
|------|-----|
| 连通方式 | 8-连通 BFS |
| 策略 | 找最大连通域，取其边界框直接截取 |
| 保留噪点 | 边界框内的其它连通域像素也会被保留（实测不影响，因为骨架本身就是单一组件） |

## 输出文件清单

### 完整尺寸（`build/skeleton_viz/`）

| 文件 | 内容 |
|------|------|
| `{name}_smooth.png` | 上采样+平滑后的完整二值图（≥128×128） |
| `{name}_skel.png` | Guo-Hall 细化后的完整骨架 |

### 裁剪尺寸（`build/skeleton_viz/cropped/`）

| 文件 | 内容 |
|------|------|
| `{name}_smooth.png` | 裁剪到最大连通域边界框的平滑图 |
| `{name}_skel.png` | 裁剪到最大连通域边界框的骨架 |

## 关键文件

| 代码位置 | 内容 |
|------|------|
| `GeometryUtils.fillSmallHoles()` | 孔洞填充 |
| `GeometryUtils.upscaleConnectivityPreserving()` | 连通保持上采样 |
| `GeometryUtils.gaussianSmoothBinary()` | 高斯平滑 |
| `GeometryUtils.skeletonize()` | Guo-Hall 细化 |
| `GeometryUtils.cropToForeground()` | 紧凑裁剪 |
| `SkeletonExportTest.java` | 测试驱动，生成所有输出 |

## 输入规模

- 55 张测试图（53 张 symbol + 2 张 rune）
- 原始尺寸: 16×16, 24×24, 32×32, 64×64
- 处理时间: <5s（含 PNG 读写）
