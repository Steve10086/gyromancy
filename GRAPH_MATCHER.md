# 图匹配引擎 — 技术路线文档

## 概述

本文档记录了 Gyromancy 符号识别系统中**图拓扑匹配**方向的全部探索。图匹配的核心理念是将符号的骨架结构建模为图（节点 + 边），利用图的拓扑不变性（连通性、度序列、循环数、成对距离分布）进行旋转/缩放/平移不变的形状识别。

所有实验基于 32×32 二值空心线条图像，Zhang-Suen 细化至 1px 宽骨架后构建图。

---

## 1. 三次图匹配演进

### 1.1 角点图 — 成对距离分布 (cornerGraph, 权重 0.06)

**动机**: 已有的七描述符组合算法缺少对角点空间关系的利用。角点的 `(x, y)` 坐标是天然的空间位置，成对欧氏距离对旋转/平移不变。

**节点**: CSS (Curvature Scale Space) 检测的角点，来自细化骨架轮廓。σ=1/2/4/8 四尺度 + 跨尺度投票 (≥2 存活)。

**使用的特征**:

| 特征 | 提取 | 匹配 | 为什么用它 |
|------|------|------|-----------|
| 成对欧氏距离分布 | 所有 N(N-1)/2 个角点间距离，排序，除以最大归一化 | KS 统计量 `max\|CDF_d - CDF_t\|` | 旋转/平移/缩放天然不变；排序破坏角点索引顺序，对轮廓遍历方向免疫 |
| 角点数量比 | `min(nD,nT)/max(nD,nT)` | 直接比值 | 拓扑相似度——星形 5 角 vs 方形 4 角 |

**评分**: `0.6 × (1-KS) + 0.4 × countRatio`

**为什么 KS 而不是 DTW 或 L2**: 角点数量不同（手绘 1-3 个 vs 模板 3-6 个），KS 统计量比较经验 CDF——天然处理不同长度序列。DTW/L2 需要等长或重采样，重采样会从 1-2 个距离中插值出 15 个点导致失真。

**结果**: 有效但信号弱——权重 0.06，贡献 ~0.02-0.03 的净自匹配提升。

**核心局限**:

| 问题 | 原因 |
|------|------|
| 角点数量不足 | 手绘符号在 32×32 下 CSS 仅检测 0-3 个角点。N<3 时成对距离仅有 0-3 个值，分布无统计意义 |
| 丢失连通性 | 成对距离是无序集合——不知道角点 A 和角点 B 在骨架上是否相邻 |
| 丢失角度关系 | 只知道距离，不知道角点之间的方向。一个拉伸的正方形和一个正常的正方形成对距离分布相同 |

---

### 1.2 骨架图 v1 — 分支长度 + 拓扑过滤

**动机**: 角点图以轮廓上的曲率峰为节点，丢失了骨架的连通性和端点结构。直接从 Zhang-Suen 骨架构建图，以端点和分支点为节点，以骨架线段为边。

**预处理链**: `32×32 → upscale(4×) → 128×128 → thin() → prune(8%) → buildGraph`

**节点类型** (2 种):

| 类型 | 检测条件 | 物理含义 |
|------|---------|---------|
| ENDPOINT | 8 邻域骨架像素数 = 1 | 线段的终点 |
| JUNCTION | 8 邻域骨架像素数 ≥ 3 | 分支点、交叉点 |

**使用的特征**:

| 特征 | 提取 | 匹配 | 为什么用它 |
|------|------|------|-----------|
| 分支长度分布 | 所有边的像素长度，排序，除以最大归一化 | KS 统计量 | 捕获骨架各分支的相对长度模式 |
| 端点数量比 | `endpointCount()` 比值 | 直接比值 | 端点多寡反映形状复杂度 |
| 节点数比 | `nodeCount()` 比值 | 直接比值 | 图的规模一致性 |
| 度序列 | 所有节点度数的排序列表 | 逐一比较 (±1 容差) | 拓扑指纹——方形的度序列 [1,1,1,1,3] 不同于星形 [1,1,1,1,1,3] |

**拓扑过滤器** (Phase 1):

```
if (|nodeCount_diff| > 2) → reject
if (|endpoint_diff| > 2) → reject
degree sequence 逐一比较，允许 ±1 容差
```

通过过滤则进入几何比较；不通过则直接返回 0.1（硬拒绝）。

**结果**: 纯骨架图 5/12 通过。拓扑过滤器过于严格——手绘产生的伪分支导致度序列与模板不一致。

**失败分析**:

| 问题 | 具体表现 | 原因 |
|------|---------|------|
| 4× 上采样放大锯齿边 | square_1/2 的 45° 斜边产生 5-8 个端点 | 32×32 像素阶梯经过 4× 放大变成锯齿，细化后每个锯齿成为独立分支 |
| 拓扑过滤器过严 | 手绘符号的度序列常与模板差 1-2 个节点 | Zhang-Suen 对手绘线宽不均匀敏感 |
| 边信息单一 | 边仅携带长度，无曲率信息 | 直线边和弯曲边在长度分布中无区分 |

---

### 1.3 骨架图 v2 — 32×32 原生 + 边曲率 + 循环过滤

**动机**: v1 的 4× 上采样是伪分支的主要来源。v2 在 32×32 原生分辨率上运行 thin→prune→graph，同时引入边曲率分析和 Euler 循环过滤。

**四大改动**:

#### 改动 1: 去除上采样

```
v1:  32×32 → upscale(4×) → 128×128 thin
v2:  32×32 → thin() directly
```

剪枝阈值从总长 8% 降至 4%（总像素从 ~100-300 降至 ~10-30）。

#### 改动 2: 边曲率图 + 拐点节点

**边新增字段**:

```java
GraphEdge {
    double[] curvatureProfile;   // 16 采样曲率序列
    double totalCurvature;       // Σ|κ| 边上的总弯曲量
    double curvatureStd;         // κ 的标准差 — "波浪 vs 直线"
    int inflectionCount;         // 曲率符号变化的次数 — 拐点计数
}
```

**新增节点类型**: CORNER — 边上曲率峰超过阈值处插入节点，分隔边。

拐点检测: 沿边逐像素计算方向变化（前→当前→后的 3 点窗口切向角差分）。`|curvature| > 0.35 rad` 且为局部极大值的点 → 插入 CORNER 节点 → 所在边分裂为两个子边。

#### 改动 3: 模板细节权重

```java
// 预计算，缓存在 SymbolTemplate 中
edgeWeight = 
    length > 25% total → 1.0   (长边 — 结构骨架)
    10% < length ≤ 25% → 0.6   (中边 — 辅助)
    length ≤ 10% total → 0.2   (短边 — 可能是噪声)
```

匹配时使用模板权重做加权求和——长边的匹配质量更重要。

#### 改动 4: 循环数拓扑过滤

```java
cycles = edges - nodes + 1   // Euler 特征 — 封闭区域数
```

| 符号 | 封闭区域 | cycles |
|------|---------|--------|
| circle/squre/star | 1 个环 | 1 |
| water_symbol | 1 个环 + 1 个尾巴 | 1 |
| fire_symbol | 2 个瓣 → 2 个环 | **2** ★ |
| figure_8 | 2 个连着的环 | 2 |
| noise_1 | 随机涂鸦 | 0 或 ≥3 |

**过滤规则**:

```
|Δcycles| > 1 → 硬拒绝 0.05
|Δcycles| = 0 → 满分 1.0 (拓扑完全一致)
|Δcycles| = 1 → 0.5 (接近但不完美)
```

**当前骨架图评分公式**:

```
skeletonGraphScore = 0.25 × cycleScore
                   + 0.20 × epsScore        (端点比)
                   + 0.30 × edgeLengthKS    (边长度 KS)
                   + 0.15 × nodeCountRatio
                   + 0.10 × edgeCountRatio
```

**纯骨架图结果**: 5→3/12（v1→v2.1 纯图模式下有所下降，因为 32×32 原生骨架边数减少，但循环过滤有效）。在组合算法中权重 0.09，贡献稳定的 ~0.02-0.03 净提升。

---

## 2. 三大图匹配器的当前并行结构

```
match(drawn, template)
  │
  ├── cornerSeqScore  (权重 0.06)  ── 角点弧长索引 1D 对齐
  ├── cornerGraphScore (权重 0.06) ── 角点成对距离 KS
  └── skeletonGraphScore (权重 0.09) ── 骨架图: 循环 + 端点 + 边长度

总置信度中图相关占比: ~21%
序列描述符 (curvDTW + cdfL2 + tfL2): ~70%
其他 (skew + pixelRatio): ~9%
```

---

## 3. 各图匹配器使用的特征全表

### 3.1 cornerSeqScore — 角点 1D 对齐

| 特征 | 物理含义 | 匹配方式 | 不变性 |
|------|---------|---------|--------|
| 角点弧长索引 `corner.idx` | 角点在轮廓遍历中的归一化位置 | 循环平移 + 窗口容差 ±3 | 旋转不变（平移=旋转）；缩放不变（弧长归一化） |

**丢失的信息**: 角点的 (x,y) 坐标、凹凸方向、相邻关系、成对空间关系

---

### 3.2 cornerGraphScore — 角点成对距离 KS

| 特征 | 物理含义 | 匹配方式 | 不变性 |
|------|---------|---------|--------|
| 排序归一化成对欧氏距离 | 角点之间的空间紧凑度/散布度 | KS 统计量 | 旋转/平移/缩放不变 |
| 角点数量比 | 复杂度一致性 | 直接比值 | — |

**丢失的信息**: 连通性 (谁连谁)、相对角度、凹凸性

---

### 3.3 skeletonGraphScore — 骨架图拓扑

| 特征 | 物理含义 | 匹配方式 | 不变性 |
|------|---------|---------|--------|
| **循环数** (Euler) | 骨架中封闭区域的数量 | 差值判断: >1→拒绝, 0→满分 | 拓扑不变量 |
| **端点数** | 骨架的自由末端数量 | 差值: >2→拒绝, 否则比值 | 拓扑不变量 |
| **边长度分布** | 骨架各分支的相对长度 | KS on 排序归一化长度 | 缩放 + 旋转不变 |
| **节点数比** | 图规模的相对大小 | 直接比值 | — |
| **边数比** | 边的数量一致性 | 直接比值 | — |
| 模板边权重 | 各边的重要性 (基于长度) | 当前未激活 | — |

**已提取但当前未激活的特征**:

| 特征 | 存储位置 | 未激活原因 |
|------|---------|-----------|
| `curvatureProfile[16]` | 每条边的 16 采样曲率 | 32×32 下手绘边每个采样点随机摇摆 ≠ 有意义弯曲 |
| `totalCurvature` | 边总弯曲量 Σ\|κ\| | 手绘弯曲量与模板弯曲量不可比 |
| `curvatureStd` | 曲率标准差 | 32×32 短边上统计量无区分力 |
| `inflectionCount` | 拐点数量 | 16 采样中拐点位置不稳定 |

---

## 4. 当前的问题与瓶颈

### 4.1 分辨率天花板

32×32 是核心限制。骨架总像素仅 10-30，边数 2-6，边长度 3-15 像素。在这个尺度下：

- 两条不同形状的边长度分布重叠严重
- 边曲率在 3-15 像素的路径上无法形成统计学显著的 profile
- CSS 角点数量在 0-3 之间，成对距离仅 0-3 个值

### 4.2 单一循环数陷阱

10/18 个模板共享 cycles=1（circle、square、star、water、triangle、rune_earth 等）。循环过滤无法在它们之间区分——而 water_2 和 noise_1 恰好落在这个区域。

### 4.3 连通性信息被浪费

三个图匹配器的共同特征：**全部使用节点/边的集合统计量**（排序距离、长度、数量比），没有一个使用节点之间的连通关系（edge 的 fromId→toId）。`cycleCount` 从 Euler 公式间接推断连通性，但从不显式匹配"哪条边连接哪两个节点"。

当前存储了完整的连通性信息（每条边记录 `fromId` 和 `toId`），但在匹配阶段被忽略。

### 4.4 手绘与模板的节点类型不对应

手绘符号的拐角可能在 CSS 中被检测为 0 个角点（拐角被画圆），而模板有 1 个 CORNER 节点。同样，手绘的某个端点可能在细化后消失（线条宽度不均匀导致骨架末端提前截断）。

这些节点级别的差异导致集合统计量（节点数、边数、端点比）偏离——即使两个图在拓扑上是同构的。

---

## 5. 已实现的基础设施清单

### GeometryUtils.java

```java
// 数据记录
record GraphNode(int id, int x, int y, int degree, NodeType type)
record GraphEdge(int fromId, int toId, int pathLength, List<int[]> path,
                 double[] curvatureProfile, double totalCurvature,
                 double curvatureStd, int inflectionCount)
record SkeletonGraph(List<GraphNode> nodes, List<GraphEdge> edges, int totalLength)
enum NodeType { ENDPOINT, JUNCTION, CORNER }

// 核心方法
int[][] thin(int[][])                          // Zhang-Suen 细化
int[][] upscale(int[][], int)                  // 最近邻上采样 (public)
int[][] pruneSkeleton(int[][], double)         // 剪枝短分支
SkeletonGraph buildSkeletonGraph(int[][])      // 提取骨架图
SkeletonGraph insertCornerNodes(SkeletonGraph, int[][]) // 插入拐点 + 计算边曲率
double[] cornerDistanceSignature(List<Corner>, int) // 角点成对距离签名
double[] cornerAngleSignature(List<Corner>, int)    // 角点曲率角签名
double[] emphasizeCurvature(double[])          // 曲率 x²·sign 强调
double[] decimate(double[], int)               // 线性插值降采样
double l2Norm(double[], double[])              // L2 欧氏距离
```

### GeometricMatcher.java

```java
// 图匹配器
float computeCornerGraphScore(List<Corner>, List<Corner>)       // 角点成对距离 KS
float computeSkeletonGraphScore(SkeletonGraph, SkeletonGraph, float[]) // 骨架图拓扑
SkeletonGraph buildDrawnGraph(int[][])                          // 手绘 → 骨架图

// 辅助方法
float computeSkewness(double[])    // CDF 偏度
int countPixels(int[][])           // 前景像素计数
double ksMax(double[], double[])   // KS 统计量
```

### SymbolTemplate.java

```java
// 模板缓存 (lazy, 第一次访问时计算并缓存)
record TemplateDescriptors(..., SkeletonGraph graph, float[] edgeWeights)

SkeletonGraph skeletonGraph()   // 获取模板骨架图
float[] edgeWeights()            // 获取模板边权重
```

---

## 6. 未尝试的方向

| 方向 | 潜在价值 | 挑战 |
|------|---------|------|
| **显式子图同构匹配** — 匹配骨架图的连通性 | 直接利用 fromId→toId 关系，不被集合统计局限 | 手绘骨架可能缺少节点，需要子图同构（NP-hard），需启发式 |
| **角点凹凸性区分** — 正曲率=凸角，负曲率=凹角 | 区分星形 (交替凹凸) 和方形 (全是凸) | CSS 角点的 `curvature` 和 `angle` 字段已携带此信息但未使用 |
| **节点度数的加权匹配** — 高 degree 分支点权重大于端点 | 交叉/分支点比端点更具结构信息量 | 需要定义度数→权重的映射 |
| **边的相对角度** — 在分支点处，出站边的夹角 | 对 branch 结构 (rune) 区分力极强 | 手绘分叉角度可能大幅偏离模板 |
| **边曲率 profile DTW 在高分辨率下** — 仅当边 >20px 时激活 | 边长度足够时 DTW 可区分"直线边"和"弧线边" | 32×32 下边太短，提升到 64×64 或 128×128 后才会有效 |

---

## 7. 版本时间线

| 版本 | 日期 | 方法 | 纯图通过 | 组合算法通过 | 关键发现 |
|------|------|------|---------|------------|---------|
| cornerGraph v1 | 前期 | 角点成对距离 KS | — | 10/12 | 角点太稀疏，N<3 时退化为简单比值 |
| skeletonGraph v1 | 前期 | 128×128 骨架 + 拓扑过滤 + 分支长度 | 5/12 | — | 上采样产生伪分支；拓扑过滤过严 |
| skeletonGraph v2 | 当前 | 32×32 骨架 + 边曲率 + 循环过滤 + 边权重 | 3/12 | 10/12 | 循环过滤有效 (rune 交叉降至 0.24)，但边曲率在 32×32 下无区分力 |
