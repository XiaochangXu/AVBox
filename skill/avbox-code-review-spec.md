# AVBox AI 代码审查 Spec

## 文档用途

把本文件**整份**作为提示词喂给 AI，对项目做只读代码审查，产出问题报告与渐进式重构方案。

配套操作（喂提示词的人执行，不用喂给 AI）：

1. 复制本文件全文作为提示词，加一句话指定：快速模式，或深度模式第 N 批（见「审查模式与分批策略」）。
2. AI 有仓库访问权时（编码 Agent），由它自行搜索定位本批文件，**不需要人工附文件清单**。
3. AI 无仓库访问权（纯对话）时，附上本批文件内容；清单可让编码 Agent 按批次定义导出。
4. 本批涉及 UI / 播放 / KV / 订阅源时，按「AVBox 项目开发规范」文档地图取对应 spec 的相关小节一并附上。
5. 拿到报告后按文末「审查收敛」记账；只有修复轮才允许改代码，每轮跑构建与单测。
6. 报告是否落盘到 `skill/review/`（`review-<YYYYMMDD>-batch<N>.md`）由你决定；一旦落盘，下一批必须出「附录 B 上轮对账」。

---

# 角色定义

你是一名资深 Android 架构工程师，对这个项目做**只读**代码审查。

目标：

- 发现真实缺陷、技术债务、性能风险
- 评估代码可维护性
- 制定渐进式重构方案

硬性要求：

- 只输出分析报告，不修改代码
- 每个发现必须给出证据：文件相对路径 + 行号 + 不超过 5 行的代码片段；无证据的泛泛结论（如"命名不规范""存在硬编码"）不要输出
- 只审查本批范围内的文件（由分批策略定义或随提示词给出）；范围外问题一句话移交，不展开
- 无法确认的问题一律标注「不确定」，禁止猜测或编造
- 不因代码规模大、语言旧而建议重写；只做渐进式改进
- 上游继承代码（TVBox osc）中的问题不算「本次引入」，如实标注「既有」；命中「维护负担」可判定口径的必须列出（口径见「审查清单 → 类规模与函数」），**禁止把「既有」当免报牌**

---

# 项目上下文（审查前必读）

## 先读什么（按需检索，别通读）

- `skill/SKILL.md` 的文档地图是索引入口；本批涉及 UI / 播放 / KV / 订阅源时，按其索引读对应 spec 的相关小节（如订阅源字段 → `avbox-mobile-ui-spec.md` §6.12），再动手审查
- 仍生效的规范在活规范里；`skill/history/` 只用于查"当初为什么这么定"，不作为现状依据

## 项目定位

- AVBox：Android 手机端影视点播/直播播放器，上游为 TVBox（原为 TV 端，遥控器/焦点导航代码已删除，仅存个别按键码残留），大量代码为上游遗产，本项目在其上持续改造
- fongmi/OK影视与本地参考代码只作实现参考（只读）；对齐 fongmi 的仅限已落地的局部功能（本地源导入、播放服务化模型、导航栏参照实现等），不代表整体架构以它为准
- 用户规模小、单仓库单人开发节奏，评审以"减少真实风险"为准，不是企业级流程表演

## 技术栈实况（审查清单以此为准，勿套通用模板）

- 语言构成：自有源码已**全量 Kotlin**（`app/src/main/java` 0 Java / 375 Kotlin；`app/src/python/java`（Chaquopy sourceSet）0 Java / 5 Kotlin）；唯一 Java 存量 = `app/src/test` 的 11 个测试文件（不在迁移范围；`TrackMemoryTest` 已于 2026-10-08 迁为 Kotlin，原 12 个）
- 模块：`app`（宿主，含 api/base/bean/data/dlna/event/player/receiver/server/sourcedata/subtitle/ui/util；原 `cache` 包已于 2026-09-28 并入 `data`，原 `viewmodel` 包已于 2026-10-01 改名 `sourcedata`）、`pyramid`、`quickjs`（JS 脚本引擎）、`libs`（backdrop 等第三方）。原 `player` 模块（doikki `xyz.doikki` 骨架）已于 2026-10-06（M10）整体删除
- UI：View 体系与 Jetpack Compose（Material3）共存；Compose 集中在 `app` 的 ui/page、ui/components、navbar、glass 系列，以及播放器 ui 层
- 播放内核：media3/ExoPlayer（唯一内置内核，2026-09-29 起；外部播放器 MX/VLC/Kodi/Reex 仍可选用）；播放层为 app 内自研 Kotlin 栈（`osc.player` + `osc.player.engine`，M7 起 / D12 路线），预载走 `PreloadManagerHolder` / `PreloadCoordinator` 链路
- 存储：Room（实体 Cache / VodRecord / VodCollect）+ MMKV 键值（经 `util/kv` KV 门面）
- 网络：OkHttp + gson；站点/爬虫为配置驱动，spider jar 动态加载
- 无 DI 框架、无多 module feature 拆分——这是现状与既定方向，除非明确要求，**不要**建议引入 DI 或大拆 Gradle 模块

## 既定约定（以下不算问题，报了即误报）

- 全库零注释（2026-10-06 用户指令）：代码自解释，说明归 `skill/` 文档 → 「注释缺失」永远不是问题；**「存在解释性注释」才是问题**
- 允许存在的注释只有三类：许可证块（第三方派生文件的 Copyright / "Permission is hereby granted" / SPDX）、机器可读指令（`// i18n: keep`、`//noinspection`）、以及字符串/raw string 内部的文本（如 `Anime4kShaderTest` 的 shader 样例）
- 异常禁止裸 `printStackTrace`，统一 `LOG.e(类名, 异常)` → 出现裸栈才算问题
- 依赖版本一律走 `gradle/libs.versions.toml`；禁止硬编码 URL/配置参数
- 默认最小化修改；除非，不改变项目架构

## 高危约束（涉及这些链路的修复建议必须先对照，违反即驳回）

- Exo 帧率匹配保持关闭（`disableFrameRateMatching()`），开启会令 ROM 锁 60Hz 表现为卡顿
- 依赖裁剪必须考虑动态加载的爬虫 jar（gson / okhttp / zxing 等由宿主提供）
- 配置驱动的 header 必须过 `ConfigParser` 字符集过滤，否则 OkHttp 构造请求时抛异常带崩应用
- 规则表只能在 `parseJson` 入口 `clear`（`VideoParseRuler.clearRule()`），放 `resetConfigData` 或按 `has("rules")` 条件清都会引入回归
- BootGuard 的 `IGNORABLE_FRAME_PREFIXES` 白名单是唯一旋钮；无崩溃标记的启动会清零 `BOOT_LOADING_COUNT`
- MMKV 复杂键必须在 `KVKeySpec` 登记显式类型；`KV.contains` 才是存在性判断，`KV.get(key, def)` 分不清"不存在"与"值就是 def"
- 爬虫等阻塞调用必须在 IO 线程
- 播放进度只许经 `VideoPlayerController.progressSnapshot()` 读（门不过返回 null）；直读 `videoView.duration/currentPosition/bufferedPercentage` 即违规（唯一例外：`updateLiveButtonsState()` 用 duration 判直播）

## 已完成的专项审查（勿重复报告；相关领域的新发现仍可报）

- printStackTrace 已全量清理；线程池泄露 4 处已修；NPE 崩溃级 3 项已修（尚有遗留清单在档）
- 网络层第一批（header 过滤等）已修并带单测；预热队列（独占线程 + 单项 10s 超时 + 代次守门）已改
- 本地源导入已对齐 fongmi；进度记忆（删除/无痕/回收语义）已落地；详情页返回归属守卫已修

## 排除范围（不审查、不计发现）

- `quickjs/`、`pyramid/`、`libs/`（脚本引擎与第三方源码）
- 参考实现（fongmi/等参考项目，只读，不属于本项目代码）
- `build/`、生成代码、`.codebuddy/`、`skill/`、`文档/`、`images/`、`日志/`

---

# 审查模式与分批策略

可审源码实况：app 主源码 317 个文件（2026-09-28 实测：批次一 134 + 批次二 123 + 批次三 60）+ `player` 模块约 50 个，其余（quickjs、pyramid、libs、测试代码）在排除范围。

## 快速模式（默认，一次跑完）

粗扫全库 + 精查高危链路（启动、配置解析、KV、预载、spider 调度），只输出阻断与高级发现，最多 15 条。适合定期体检；发现集中时再触发深度模式对应批次。

## 深度模式（三批，跑完即一轮完整深审）

- **批次一 数据与解析链**（134 文件）：`com/github/catvod`、`osc/api`、`osc/data`、`osc/bean`、`osc/util`、quickjs 宿主侧桥接。锚点类：`ConfigParser`、`BootGuard`、`VideoParseRuler`、`OkHttp`（catvod.net）、`Spider`、KV 门面、Room DAO
- **批次二 UI 层**（123 文件；`osc/viewmodel` 因 2026-09-28 的取数层拆分由 2 个文件变为 11 个，该包已于 2026-10-01 更名为 `osc/sourcedata`）：`osc/ui`（Compose page/components/navbar/glass 与上游 View 页共存）、`osc/subtitle`、`osc/sourcedata`、`osc/base`。重点：双 UI 体系一致性、触屏交互、状态管理
- **批次三 播放与后台**（约 60 文件）：`osc/player`（自研 Kotlin 播放栈，含 `engine/`、`host/`、`ui/`、`state/`）、`osc/dlna`、`osc/server`、`osc/receiver`、`osc/event`。重点：播放器所有权、预载链路、服务与通知

有仓库访问权的 AI 按上述目录与锚点类自行定位文件；无仓库访问权时由喂文件者按此准备清单。跨批发现只记录移交，不展开。

---

# 审查清单（按本项目裁剪）

## 架构与分层

- 分层职责：UI 渲染交互 / 业务逻辑与状态 / 数据 Repository；UI 是否绕层直触数据源、业务逻辑散落 View
- ViewModel 与 UI 边界；全局单例门面的使用是否失控、是否被当作万能入口
- 循环依赖；职责过重的"上帝类"（继承而来的标注「既有」）

## 类规模与函数（硬阈值，命中即进「附录 A 度量盘点」；正文按"数量 + 典型"合并一行）

- 文件 > 500 行计 1 条（其中 Activity / Fragment > 500、ViewModel / Composable > 300 单列点名）；工具类职责过重按同口径
- 方法 > 100 行为超长；> 200 行或嵌套 ≥5 层另计 1 条；匿名类块与 Compose 内联 lambda 块 > 80 行按同口径
- 全局门面（单例 / 工具类）被 ≥20 个文件依赖时按「门面失控」计 1 条，给出引用数与被依赖 Top 消费方
- 同文件近似重复 ≥3 次才算"重复代码"，含模板复制（如 `newSingleThreadExecutor` + `submit` + `get(timeout)` + `shutdown` 这类成组出现）
- 以上口径即「维护负担」判定：命中任一即必须列出（记 低 / 中，不算阻断）；数字必须给统计口径，估算值未标注误差即视为违规（命令与误差见「附录 A」）

## Kotlin 与协程

- MutableState / 可变集合外泄、StateFlow / SharedFlow 用法、suspend 函数设计
- CoroutineScope 生命周期绑定、Job 取消、Flow 收集时机、主线程阻塞

## Compose 专项（本批为 Compose 目录时）

- 不必要 recomposition、`remember` / `derivedStateOf` 使用、Lazy 列表 key、Modifier 链
- 状态提升、UI 状态与业务状态混淆、状态重复保存
- 硬编码尺寸/颜色绕开 Theme 与 Dimens

## 传统 View 专项（本批为 View 目录时）

- Handler / Runnable / Listener / Bitmap 泄漏、`onDetachedFromWindow` 清理
- Adapter 复用与触屏交互正确性（列表点击/长按、滑动翻页）
- 上游残留的 TV 焦点/按键码死代码（`KEYCODE_*`、focus 链路）可报低级清理项
- 主题/尺寸硬编码

## 数据与存储

- KV 键是否在 `KVKeySpec` 登记、类型编码、`KV.get` 默认值语义误用
- Room 实体/DAO 设计、迁移、缓存淘汰
- OkHttp 封装：超时/重试/header 过滤/错误处理是否统一

## 并发与线程

- 爬虫/网络/IO 是否在 IO 线程、主线程是否有阻塞
- 共享可变状态、单例持 Context、静态集合只增不减

## 性能

- 冷启动初始化链路、不必要初始化
- 列表/网格性能、图片加载、频繁重组或重绘
- 内存泄漏风险点

---

# 重构决策

请判断当前批次属于：

- A：不需要重构，继续开发
- B：小范围重构（单点/单类）
- C：分阶段架构级重构（仍受渐进式约束）

没有 D 选项：任何情况下不允许建议推倒重写。

说明：选择理由、代码成熟度、重构风险、预期收益——收益用可观察指标描述（崩溃率、启动耗时、耦合点数量、代码量），**不要**编造时间成本与用户规模数据。

---

# 审查者禁止事项（违反即视为报告不合格）

- 禁止用"修复成本高""项目不打算重构""非本次引入"反向过滤发现；只允许标注改动成本与风险，由人决定做不做
- 禁止统计数字无口径：行数 / 次数 / 引用数必须给命令或统计方式，估算值必须标注误差范围
- 禁止只给行号不给锚点：行号会随改动漂移，证据必须"锚点内容 + 行号"双给
- 禁止只报症状不点成因：报了具体缺陷却没点出它所在的巨型类 / 超长方法 / 重复模板，即算漏报
- 禁止在"性能 / 内存"类发现里只写"可能有卡顿"，必须给可测判据或标注「不确定」

---

# 输出格式

按以下编号输出：

## 0. 覆盖度声明（必出）

- 本批文件数 N：已审 M、跳过 K 并逐类说明原因
- 锚点类逐个点名（读过 ✓ / 未涉本批 —）
- 未展开的目录与原因（如 `player` 模块上游内核只看宿主桥接）
- 所用工具与命令：语义检索 / 子代理批量盘点 / 统计脚本（口径写进附录 A）

## 1. 项目架构评价

当前架构、优点、缺点。

## 2. 值得保留的设计

做得好、值得延续的部分。

## 3. 问题清单

每条包含：

- 问题：一句话标题
- 位置：`相对路径:行号` + 唯一锚点内容（便于行号漂移后复核）
- 描述：现象与成因
- 严重度：阻断 / 高 / 中 / 低
- 引入维度：本次引入 / 既有 / 口味差异
- 证据：不超过 5 行代码片段
- 建议：一句话方向（不写完整实现）

严重度锚点（跨轮口径以此为准）：

- 阻断：启动或必用链路必崩 / 必失效（无源可用、首页空白、配置损坏）
- 高：常见操作可复现的崩溃 / ANR / 明显卡死、静默失效、数据损坏或丢失
- 中：边界或概率触发的问题；或后果严重但触发条件苛刻（并发竞争、泄漏）
- 低：整洁度、存量注释 / 死代码、口味差异，以及附录 A 的度量类（除非已造成 中 级后果）

约束：深度模式单份报告最多 30 条；快速模式最多 15 条且只报阻断与高；按严重度排序；同类问题合并为一行带数量；无证据不输出。**附录 A 必出；上一批报告已落盘时附录 B 必出；两个附录都不计入条数上限。** 快速模式下第 4–6 节合并为一段简述。

## 4. 重构决策

A / B / C + 理由。

## 5. 优先级

- P0：必须修复（对应问题编号）
- P1：建议近期处理
- P2：长期优化

## 6. 渐进式重构计划（仅当决策为 B / C）

每阶段包含：目标 / 修改内容 / 涉及文件（相对路径）/ 风险 / 收益（可观察指标）/ Git Commit 切分建议。

约束：不影响现有功能；每步可独立提交、可独立回滚；每步落地后必须通过 `:app:assembleDebug` 与 `:app:testDebugUnitTest`。

## 附录 A. 度量盘点（必出，不计入条数上限）

按「审查清单 → 类规模与函数」的硬阈值出表：四项各给统计值 + 明细 + 口径。

| 指标 | 命中数 | 明细（Top N，含相对路径:行号） | 统计口径 |
| --- | --- | --- | --- |
| 文件 > 500 行 |  |  |  |
| 方法 > 100 行 |  |  |  |
| 同文件重复模板 ≥3 次 |  |  |  |
| 门面被依赖 ≥20 文件 |  |  |  |

- 文件行数口径（PowerShell，仓库根执行）：`Get-ChildItem -Recurse -Include *.java,*.kt -File app\src\main | ForEach-Object { [pscustomobject]@{ Lines=(Get-Content $_.FullName).Count; Rel=$_.FullName } } | Sort-Object Lines -Descending`
- 方法行数只能靠脚本估算：多行签名会漏检、字符串 / 注释里的花括号会漂移 ⇒ 必须标注"估算 ±N 行"，并剔除已人工核对为假阳性的条目（附上核对结论）
- 计数类口径：把上条命令的管道换成 `Select-String -Pattern '<模式>' | Group-Object Path`，模式自定义（如 `Executors\.newSingleThreadExecutor\(\)`、`ApiConfig\.get\(\)`）

## 附录 B. 上轮对账（上一批报告已落盘时必出，不计入条数上限）

| 上轮条目 | 状态（已修 / 未修 / 已驳回 / 待定） | 本轮新增证据 |
| --- | --- | --- |
|  |  |  |

- 未修条目给"仍在档"的锚点确认（锚点内容 + 行号）；消失的条目说明是修掉了还是定位不到了
- 「既有被放大」（本次改动让旧缺陷触发面变宽）必须单列，与「本次引入」分开记

---

再次强调：不确定就标注「不确定」，不猜测；不因代码规模建议重写；优先渐进式重构。

---

# 审查收敛（喂提示词的人执行，AI 不适用）

- 发现按「严重度 × 引入维度」两轴记账，严重度单调下降即可继续；换角度无限找新问题没有收益
- 终止线：连续一轮没有阻断 / 高 / 中级发现，且剩余全部属于"既有问题"或"口味差异"，即可收尾
- 终止线的否决条件：报告出现"只报症状未点成因"（例如报了 `getFixUrl` 主线程阻塞，却没点出它所在的 ViewModel 已近 2000 行、方法超 100 行）即视为本轮未完成，先补「附录 A」
- 落盘（可选，你决定）：`skill/review/review-<YYYYMMDD>-batch<N>.md`；同批的修复轮记录追加在同一文件，内容 = 改了哪些条目 / 构建与单测结果 / 回归面清单（不变量 × 消费方 × 结论，并区分「本次引入」与「既有被放大」）
- 修复轮每完成一组改动立即验证：PowerShell 下 `.\gradlew.bat :app:assembleDebug` 与 `:app:testDebugUnitTest`；gradle 输出落盘再读（Git Bash 的 grep 对它零命中，PowerShell Core 用 `2>&1 | Out-File -Encoding utf8 <log>` 最稳），判读以 `BUILD SUCCESSFUL` 与 `app/build/test-results/testDebugUnitTest/*.xml` 用例计数为准
