---
name: AVBox Java→Kotlin 迁移转换规范（活规范）
description: M0 立规产出——迁移流程与禁止项、Kotlin 静态/字段/构造器转换细则、Gson×data class 规范、并发不变量、等价性卡口与字节码卡口脚本用法、M0 实测基线
---

# 0. 适用范围与上游

- **上游计划**：`skill/review/refactor-plan-20261005.md`（M 系里程碑、D 系决策、“目标终态”验收清单）。本文是该计划 §7.1 / §8 的**落地细则**，冲突时以计划文档的决策为准、执行细则以本文为准。
- **本规范在以下场景必读**：任何 Java→Kotlin 迁移步；改动 `com.github.catvod.**` 或 `player` 模块（`xyz.doikki.videoplayer.**`）；改动被动态 jar / 第三方 AAR 按名字访问的宿主静态面（D9 Tier A/B）。
- **最高优先事项**：迁移的目标是**签名不变**。判据不是“用了某个注解”，而是 `javap -p -s` 输出等价。

# 1. M0 实测基线（2026-10-05，迁移起点）

| 项 | 实测值 | 命令 / 备注 |
| --- | --- | --- |
| `:app:assembleDebug` | BUILD SUCCESSFUL | `.\gradlew.bat :app:assembleDebug` |
| `:app:testDebugUnitTest` | **501 用例 / 0 失败 / 0 错误 / 0 跳过（65 个 suite）** | 计数取自 `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests` 求和 |
| **M1 后复测（2026-10-05）** | `:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **516 用例 / 0 失败**；`app/src/main/java` **183 Java / 167 Kotlin** | `bean/` 21 个已全量迁 Kotlin（提交 `5d0a35e` 族 A + `1098119` 族 B + 复审修复 `7cad57f`）；用例数 = 基线 501 + 新增 15 例反序列化/等价性回归 |
| **M2 后复测（2026-10-05）** | `:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **516 用例 / 0 失败**；`app/src/main/java` **174 Java / 179 Kotlin**；`app/schemas` 逐字未变 | `data/` 建 3 个 Repository（`History`/`Collect`/`Cache`）+ `AppGraph` + `CurrentSubscription`；DAO/entity 7 个迁 Kotlin；`RoomDataManger`/`CacheManager` 两个 Java 门面删除（逻辑并入 Kotlin 实现）；提交 `cb14e6b`/`d608631`/`619a4fc`/`094e301`；`javap` 差异见 §7.4 |
| **M3 后复测（2026-10-05）** | `:app:assembleDebug` 绿；`:app:testDebugUnitTest` **523 用例 / 0 失败**（516 基线 + 7 例新增）；`app/src/main/java` **166 Java / 187 Kotlin**（本里程碑 -8 j / +8 kt） | 8 个叶子类迁 Kotlin：`util/{RegexUtils,EpisodeMatcher,MD5,StringUtils}` + `sourcedata/{SourceHelper,PushUrlParser,PushDetailResolver,SourceResultParser}`；7 笔迁移提交 `a342923`/`d2db4eb`/`b6f4a86`/`99e73ac`/`e06a079`/`300163b`/`27bbd3e` + 1 笔告警清理 `716de19` + 1 笔审查轮修复 `303c603`；实测规则、保留告警与审查账目见 §7.5 |
| **M4a 后复测（2026-10-05，含审查轮）** | `:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **534 用例 / 0 失败**（523 基线 + 11 例新增）；源文件计数不变（无语言迁移）；Tier B 11 符号未变 | 7 个通道换 `sourcedata/SourceChannel`（Flow 主面 + LiveData 兼容面），5 个 Loader 的输出参数、`SourceResultParser`/`PushDetailResolver` 参数、`SourceViewModel` 7 个字段随之改；3 个页面 VM 改收 `flow`；`LiveDataFlow.kt` 已删；实测规则与语义差异见 §7.6 |
| **M4b 后复测（2026-10-05，含审查轮）** | `:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **536 用例 / 0 失败**（534 基线 + 2 新增）；`app/src/main/java` **159 Java / 194 Kotlin**（-7 j / +7 kt）；Tier B 11 符号未变、`app/schemas` 未动 | 5 个 Loader + `SourceViewModel` + `SourceRuntimeState` 迁 Kotlin（纯语言迁移，生产面仍 `postValue`/`setValue`）；既有 534 用例（含 `SourceViewModelWiringTest`/`SourceRuntimeStateTest`/`PlayLoaderSeqTest`）**零改动全绿**；实测规则见 §7.7 |
| **M5 后复测（2026-10-05，含审查轮）** | `:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **536 用例 / 0 失败 / 0 错误 / 0 跳过（70 suite）**（与 M4b 基线持平，纯语言迁移无新增用例；另 `--no-build-cache` 与 `--rerun-tasks` 各跑一次确认）；`app/src/main/java` **151 Java / 202 Kotlin**（-8 j / +8 kt，`api/` 包 Java 清零）；Tier B 11 符号未变、`app/schemas` 未动 | `api/` 全包 8 个类迁 Kotlin（门面 `ApiConfig` + 7 个职责文件）；`javap -p -s` debug/release 两侧差异一致、**零公开成员消失或改名**（`ApiConfig.get()` 等静态入口与两个回调接口逐成员守恒）；`ApiConfig.get()` 调用点实测 **102 处 / 36 文件**（M0 记 104/37，差额是 M1–M4b 删/改文件造成的自然漂移，非本里程碑改动）；实测规则、Kotlin 侧改写清单与登记差异见 §7.8 |
| `app/src/main/java` 语言构成 | **204 Java / 146 Kotlin** | M0 起点快照（口径 = 仓库自有 main 源码）；后续切片计数见上表各行 |
| `player` 模块 | **27 Java / 0 Kotlin** | `player/src/main/java/xyz/doikki/videoplayer/**` |
| `app/src/python/java`（Chaquopy sourceSet） | **5 Java** | `com/github/catvod/crawler/pyLoader.java` + `com/undcover/freedom/pyramid/{PyLog,PythonLoader,PythonSpider,PyToast}.java`；**计划 §2 与 M11 口径未覆盖**，已登记入计划 |
| `app/src/test` | ~~12 Java / 53 Kotlin~~ → **0 Java / 66 Kotlin** | **【已迁，2026-10-10】**原"不在迁移范围（§3.1 口径 = `main`）"仅指当时不做；当日按用户要求把最后 11 个 Java 测试全量迁 Kotlin（1241 行，纯语言迁移逻辑零改动），`app/src/test` 内 **Java 清零**。新增 `UriUtilTest`（13 例，先写后迁，见 §7.24）。实测规则与踩坑见 §7.23 |
| `libs/backdrop` 子模块 | 31 Kotlin / 0 Java | 无 Java |
| `ApiConfig.get()` 依赖面 | **104 命中 / 37 文件** | 计划 §2 原写 106/37，以本行为准 |
| `observeForever` 面 | 2 处 | `player/PlaybackFetch.java`、`player/PreloadCoordinator.java` |
| DAO / Manager 直连面 | 75 命中 / 16 文件 | `AppDataManager.get()` / `RoomDataManger` / `CacheManager` |

**行尾归一化说明**：仓库 `core.autocrlf=input`，索引里全部是 LF。工作区有一批既有 `.kt` 文件落盘为 CRLF（`git ls-files --eol` = `i/lf w/crlf`），提交时会被归一化，**属既有状态、不顺手批量转换**；本次改动的文件一律保证落盘 LF。**实测清单（2026-10-06，见 §7.16 登记项⑧）**：`ui/activity` 12 个、`ui/music` 4、`ui/components` 3、`ui/page` 2、`player/effect` 4、`player/ui` 1、`osc/util` 11（本行旧口径"12 个既有 `.kt`（`osc/util/` 下）"作废）。

# 2. 每个切片的四步与卡口

沿用计划结论摘要的四步：① Kotlin 定义 Repository 接口 + 薄委托 → ② 消费方改依赖接口 → ③ 背后 Java 实现迁 Kotlin → ④ 删旧入口。每步独立 commit、独立回滚。

**每切片收尾必跑**（不攒到最后）：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

判据 = `BUILD SUCCESSFUL` + 用例数 ≥ 基线 501（迁移步只许持平或增加）。

**构建口径（2026-10-05 用户指令，硬规则）**：`:app:assembleRelease` **未经用户明确许可一律不跑** —— 单次 4–5 分钟，只为 R8/keep 规则、资源混淆、契约层 release 侧 `javap` 比对这三类事服务；日常切片验证只跑 `:app:assembleDebug` + `:app:testDebugUnitTest`。本文件各里程碑历史行里的「`assembleRelease` 绿」是当时的事实记录，**不代表后续切片照跑**；需要 release 产物时先问用户。

# 3. Kotlin 转换规范

## 3.1 通用（全库适用）

1. **禁止只靠 IDE 自动转换就提交**（D4）。自动产物常见 `!!`、冗余判空、把可空语义改成非空；必须逐行过等价性 + 手工收敛为 Kotlin 惯用写法。
2. **迁移 ≠ 重构**（§7 硬约束 5）：不改逻辑、不改命名、不收紧可见性（`private`→`public` 需在 commit 里登记理由）。要改逻辑单独立项。
3. **Kotlin 零值属性不生成 `putfield`**（§7 硬约束 6）：凡 `initView()` 之后才使用的协作对象，一律 `lateinit` 且**在 `initView()` 内构造**，别在属性初始化器里建（会造成虚调用时序变化）。
4. **行尾 LF**：改完立刻用卡口脚本 `-Action lf` 核查本次改动文件（工具倾向写 CRLF）。
5. **UI 层不新增注释**；既有 KDoc 只做最小事实同步（推翻的旧决策措辞要清掉。M4a 先例：`sourcedata/LiveDataFlow.kt` 里“不重写 Java→Kotlin”那句随该文件删除；`HomeViewModel`/`PartitionListViewModel`/`DetailViewModel` 里“`observeForever` 有主线程断言 / 桥接器的 `awaitClose` 摘观察者”这两句改成事实口径）。

## 3.2 契约层（`catvod` + `player` 模块）逐条清单

计划 §7.1 的 a–j 是硬规则，本节给可执行的判定：

| Java 形态 | 必须写成 | 写错的后果（产物差异） |
| --- | --- | --- |
| 类被子类继承/覆盖（`Spider`、`BaseVideoController`、`VideoView`…） | `open class` + 被覆盖成员 `open` | Kotlin 默认 `final` → jar/AAR 子类 **类加载期** `VerifyError: overrides final method` |
| `static` 方法 | `companion object { @JvmStatic fun m() }` | 只有 `companion object` → 叫 `X.INSTANCE.m()`；jar 按 `X.m()` 调 → `NoSuchMethodError` |
| `static` 可写字段 | `companion object { @JvmField var f }` | 缺 `@JvmField` → 变静态 getter/private field → `NoSuchFieldError` |
| `static final` 基本类型/String 常量 | `const val`（内联，等价）或 `@JvmField val` | 普通 `val` → 静态 getter，字段消失 |
| `public` 实例字段 | `@JvmField var f` | 普通属性 = private field + getter/setter → Java 子类/同包直接读字段编译失败或运行时 NoSuchField |
| `protected` 实例字段 | `@JvmField protected var f` | 同上（`VideoView.mVideoController`、`BaseVideoController.mControlWrapper` 是已知必中项） |
| 自定义 View / Java 侧按固定参数调用的类 | `@JvmOverloads constructor`，保 `(Context)` / `(Context, AttributeSet)` / `(Context, AttributeSet, Int)` 三重载 | Kotlin 默认参数只生成主构造器 + `$default` 合成方法 |
| 顶层函数 / 扩展函数 / `object` 单例 | **禁止**（契约层） | 生成 `XxxKt.m()` 或 `X.INSTANCE.m()` |
| `internal` 可见性 | **禁止** | 会给成员加 `$module` 后缀，签名被改 |
| 默认参数替代重载、`@JvmName` | **禁止** | 多出 `xxx$default` 合成方法或改掉描述符 |
| 包名/类名/方法名/字段名 | **一字不改**（含 `JarLoader` 里的 FQCN 字符串、`JsLoader` 的 `com.github.catvod.js.Function`/`Method`） | 名字本身就是契约 |

**`object` 的边界**：纯静态工具类（无实例语义、且**没有**被 jar/AAR/JS 按类名静态访问）允许 `object`；一旦在 D9 Tier A/B 清单里，一律 `companion object` + `@JvmStatic`/`@JvmField`。

## 3.3 非契约层的静态面

`sourcedata/SourceHelper.SPIDER_POOL`/`PREPARE_POOL` 这类**只被 Kotlin 调用方**使用的静态成员：迁 `object` 或 `companion object` 都行，但**必须同步改全部调用点**（同一 commit 内成对完成），并在 commit message 里点出调用点数量。风格先例 = `util/BoundedCall.kt`。

# 4. Gson × data class 规范

## 4.1 序列化契约不变

- **属性名 = 旧 Java 字段名**：Gson 走字段反射，Kotlin 属性名即 backing field 名。重命名必须用 `@SerializedName("旧名")` 固定，否则字段改名 = 序列化契约变更。
- **`@SerializedName` 原样保留**，不增不减不换值。
- **bean 属性必须有 backing field**：不要给 bean 写自定义 getter/setter 或纯计算属性（Gson 看不到，字段会从 JSON 里消失）。
- 静态字段、`@Expose`/`transient`/`@Transient` 的语义保持原样。

## 4.2 无参构造与默认值

Gson 用 `Unsafe` 直接分配实例、**不走构造器**，所以“没有无参构造”本身不会让 Gson 反序列化失败。但保底仍要做：

- 迁移时**给所有属性默认值**（`var x: T? = null`），这样 Kotlin 会生成无参构造，兼容其他反射路径（Kotlin 反射、`copy()`、未来换序列化器）。
- 不要引入 no-arg 插件（换栈/加插件都超出本 spec 范围）。

## 4.3 null 语义

- Java 字段没有 null 标注 ⇒ 一律按**可空**迁（`var x: T? = null`）。**不要**把 Java 的可空字段写成 Kotlin 非空类型：Gson 用 `Unsafe` 赋值会绕过 Kotlin 的 null 检查，缺字段的 JSON 会得到一个“非空类型但值是 null”的对象，之后第一次访问由编译器插桩抛 NPE——崩溃点从“解析时”挪到“使用时的任意位置”。
- 只有旧 Java 代码本来就必然崩/本来就有兜底的字段，才允许保持非空，并在 commit 里登记。

## 4.4 data class 的隐式语义变化（M1 必查项）

`data class` 会额外生成 `equals`/`hashCode`/`toString`/`copy`/`componentN`，而旧 Java POJO 默认是 **identity equals**。若该 bean 参与以下任意场景，行为会变：

- 进 `HashSet`/`HashMap`/`Set`/`distinct`/`contains`/`indexOf`/`==` 比较；
- 作为 `MutableState`/`remember` key 或 `diff` 依据；
- 被当作缓存 key。

**做法**：迁移前用 `search_content` 核查该 bean 的消费点；命中且语义敏感就改普通 `class` + 只补必要成员，或把“为什么结构相等是安全的”写进 commit。`copy()` 属新增 public API，对 Tier A 面要按“新增方法”处理。

## 4.5 反序列化回归（逐字段）

每个 bean 迁移时留一份**旧 Java bean 的 JSON 快照**（真实接口回包片段优先，其次手写全字段样例），比对三件事：① 反序列化后逐字段值一致（含 null / 缺字段 / 多余字段三种输入）；② 再序列化输出的字段集合与旧实现一致；③ 数值/日期/集合泛型的类型形态一致。回归放单测里，别靠人眼。

# 5. 并发与线程不变量（迁移不得改动，D6）

- `SourceHelper.SPIDER_POOL` / `PREPARE_POOL` 是**共享池**，不得变成每请求新建；迁 Kotlin 后仍指向同一实例。
- QuickJS 线程亲和：超时不能 cancel 掉正在跑脚本的线程。
- `allowMainThreadQueries()` 主线程查库是显式设计；进度写入走 `vod-progress-writer`（读须 `awaitWrites`）。
- `synchronized (sortCache)` 的 access-order 加锁语义原样保留。
- Repository 内出现 `withContext(` / `flowOn(` 即驳回（除 M0 白名单）——会改 D6 线程语义与读序。

# 6. 唯一授权的结构变更

仅 M2/M4 的“建 Repository 接口 + 消费方改注入点 + 实现藏到接口后”。其余（上帝类拆分、包改名、线程模型变更、DI 引入）一律单独立项。

Repository 约定（D2/D7/D10/D11）：接口与 Room 域/内容域实现统一落 `com.github.tvbox.osc.data`（`sourcedata/` 是否保留为实现子包在 M5 定）；输出二分——一次性查询 `suspend fun`、持续观察 `Flow`；构造参数只接窄接口（DAO 接口/网络 API 接口），装配只走 `AppGraph`（Kotlin `object`），**实现内禁止直取 `AppDataManager.get()`/`ApiConfig.get()`**（项目无 Mockito/Robolectric，直取静态入口 = 无法构造替身 = 单测验收落不了地）。

# 7. 卡口

## 7.1 等价性 / 字节码 / 构建

| 卡口 | 用在哪 | 判据 |
| --- | --- | --- |
| 归一化多重集比对 | 同语言搬迁（移动、包改名、原样搬） | **新旧任一侧有增删即违规**（原样搬迁不该有任何内容变化，「新增了 2 行」也必须解释） |
| 跨语言 token 多重集 | Java→Kotlin 迁移 | **旧有新无 = 0**（标识符/字符串/数字字面量丢失 = 漏迁）；新有旧无 = 参考项 |
| 方法级存在性 | 纯搬迁步的粗网 | 旧侧**声明**（方法/构造函数名，不认调用点）在新文件全部命中 |
| `javap -p -s` 逐类描述符 | 契约层（M9/M10）与公开签名变更 | 与基线逐类一致；新增/消失/描述符变化即驳回；debug 与 release 各跑一次（**release 侧需用户许可**） |
| 构建 + 单测 | 每切片 | `:app:assembleDebug` `BUILD SUCCESSFUL` + 用例数 ≥ 501（**`:app:assembleRelease` 不在常规卡口内，未经许可不跑**，见 §2 构建口径） |

**关于 `javap` 的一侧可比性**：迁移后 Java 产物消失，所以**在动契约层之前**先把基线快照导出；要 debug/release 两侧就分别构建后各导一次（release 构建前先取得用户许可）。

**快照前必须重新构建对应变体**（`assembleDebug` / `assembleRelease`）：产物目录会残留已删除源码的陈旧 `.class`（2026-10-05 实测：`app/build/intermediates/javac/release/**` 里还留着 3 个已删类的 `ProtectedInitJar*`）。脚本遇到同名类出现在多个产物目录时会告警——出现告警就先重跑构建，否则可能拿到陈旧产物造成假通过。

## 7.2 脚本用法（`skill/scripts/verify-migration.ps1`）

```powershell
# 同语言搬迁：与 git 基线逐行去空白多重集比对
pwsh skill/scripts/verify-migration.ps1 -Action multiset -Path <旧路径>

# Java→Kotlin：标识符/字面量多重集（只看“旧有新无”）
pwsh skill/scripts/verify-migration.ps1 -Action tokens -Path <旧路径> -NewPath <新路径>

# 纯搬迁粗网：方法名存在性
pwsh skill/scripts/verify-migration.ps1 -Action methods -Path <旧路径> -NewPath <新路径>

# 契约层字节码基线（Java 产物；release 侧需先跑 assembleRelease——该构建须先取得用户许可——再指定 -ClassPath）
pwsh skill/scripts/verify-migration.ps1 -Action javap -Snapshot -Package com.github.catvod -Out <基线文件>
pwsh skill/scripts/verify-migration.ps1 -Action javap -Baseline <基线文件> -Package com.github.catvod

# 行尾 LF 核查
pwsh skill/scripts/verify-migration.ps1 -Action lf -Path <本次改动的文件/目录>

# D9 Tier B 清单：catvod → osc.* 逆向引用
pwsh skill/scripts/verify-migration.ps1 -Action tierb
```

- 退出码：`0` 通过 / `1` 有差异；`javap` 默认在 `app` + `player` 的 debug 产物目录里找 class，跨模块或 release 用 `-ClassPath` 显式指定。
- 匿名类与 lambda 合成类（`X$1`、`X$foo$1`）默认跳过——两侧命名规则本来就不同，比对无意义；命名内部类用 `-IncludeInner`。
- **`tokens` 的已知盲区（写清楚，别当它是全量保证）**：Java/Kotlin 关键字两侧一律剔除，因此与关键字同名的标识符（`in`/`out`/`it`/`data`/`open` 等）的丢失不被它覆盖 —— 这类遗漏由编译错误（未解析引用）、`methods`、`javap`、单测兜。反之 `tokens` 的「新有旧无」只是参考项（Kotlin 惯用写法会新增标识符），不要当违规计数。
- **`methods` 只认声明形态**：以 `return`/`if`/`for`/`Log.e(...)` 之类的语句行开头的一律不算成员，避免把调用点当成方法名（否则卡口会退化成「两个文件都有 `for` 就算通过」）。
- 四个子命令都做过正/反向自测（同文件对照 = 通过；与不相干文件或改动后基线对照 = 报差异且退出码 1），改脚本后请重跑这组对照。
- **Tier B 实测初值（2026-10-05，11 个符号）**：`osc.server.ControlManager`、`osc.server.RemoteServer`、`osc.util.AppContextHolder`、`osc.util.FileUtils`、`osc.util.KV`、`osc.util.LanguageManager`、`osc.util.LOG`、`osc.util.MD5`、`osc.util.OkGoHelper`、`osc.util.SSL.SSLSocketFactoryCompat`、`osc.util.StringUtils`。

## 7.3 M1 实测登记（2026-10-05，`bean/` 全量 21 个）

**结论**：`bean/` 21 个 Java 全部迁 Kotlin，**`javap -p -s` 逐类比对（含内部类，debug/release 两侧差异完全一致）无任何公开成员消失或改名**；新增 `app/src/test/.../bean/BeanSerializationRegressionTest.kt`（13 例）钉住 XStream/Gson 契约。

**`tokens` 卡口对 Java→Kotlin 不可当硬卡口（实测，待并入 M0 卡口修缮）**：`bean/` 21 个文件跑 `-Action tokens`，`旧有新无` 命中量 8–51 项，逐条核对后**没有一项是逻辑缺失**，来源只有四类：
1. **注释里的示例字符串** —— 脚本先抽全文字符串字面量、再剥注释，所以 `// : "20"` 这类注释里的示例值（`AbsJson.java` 30+ 处）被当成"丢失的字面量"；
2. **访问器名** —— `getXxx/setXxx` 在 Kotlin 里折成属性，`getName`/`setUrl` 之类 token 消失（描述符其实一字未改，这正是 `javap` 该管的事）；
3. **JDK 类型 / 导入拼写** —— `java` / `util` / `List` / `ArrayList` / `String` / `Integer`；
4. **Java 专有调用形态** —— `charAt`→`[]`、`length`→`.size`、`getAsJsonObject`→`.asJsonObject`、`isEmpty`→`isNullOrEmpty`、`i` 计数循环→`in 0 until`。

→ 建议给 `tokens` 加 `-Lang java2kotlin` 预处理（先剥注释再取字面量；`get[A-Z]`/`set[A-Z]` 归一成属性名；JDK 类型同义表 + `charAt/length/isEmpty` 映射），否则该子命令在 M2–M11 只能当"参考打印"，不能当"退出码即判据"。（本次以 `javap` + 行为回归作为硬门。）

**Kotlin 产物差异目录（M1 全量实测、已逐类审查通过）**：
- `public class` → `public final class`（方法同理补 `final`）—— bean 无子类，安全；
- 新增 `public static final int $stable` 与 `<clinit>`（Compose 编译器产物）；`VodInfo` 少了 `$assertionsDisabled`（Kotlin `assert` 不用该字段）；
- 类的静态成员改由伴生对象承载：新增 `Companion` 静态字段 + `X$Companion` 类（`Danmu`/`Depot`/`ProxyRule`），原 `private static` 助手移入伴生对象；
- 私有/包私有字段名跟随 Kotlin 属性名（`liveChannelItems`→`liveChannels`、`url`→`urlValue`、`name/url`→`rawName/rawUrl`、`itemSelected`→`isItemSelected`）；
- 包私有放宽为 public（`Epginfo.timeFormat`、`LivePlayerManager` 两个配置字段、`Depot.string`）——Kotlin 无包私有，按"只许放宽"取值。

**Java→Kotlin 语义陷阱（M1 实际踩到，后续里程碑逐条照查）**：
1. **`String.split(String)` 语义不同**：Java 按**正则**、Kotlin 按**字面量**。`"a$$$b".split("\\$\\$\\$")` 在 Kotlin 里切不开（静默退化成单元素）。必须写 `split(Regex("\\$\\$\\$"))`。（M1 靠新增回归用例抓到，属高危静默缺陷。）
2. **Java 公开字段被 Java 侧按字段语法读写时，Kotlin 必须 `@JvmField`**（否则 Java 侧编译不过，Gson 字段名也会变）。
3. **Kotlin 属性名与 Java 字段名不一致时，Gson/XStream 走的是属性对应的 backing field**：既要保字段名又要自定义 getter（`ParseBean.url` 走 `checkReplaceProxy`、`LiveChannelGroup.liveChannelItems` 的 getter 叫 `getLiveChannels`）时，只能把 backing field 换名并登记（本次 4 处）。
4. `getX()`/`setX()` 与 Kotlin 默认命名不同时（`getIsZip`/`setIsZip`、`getIsNew`/`setIsNew`），用 `@get:JvmName` / `@set:JvmName` 才能保住。
5. **XStream 不走构造器**：`Sun14ReflectionProvider` 分配实例时不执行字段初始化器（Kotlin 属性初始化器同理），所以 Java 的 `= -1` / `= new ArrayList<>()` 在反序列化结果里**本来就是 JVM 默认值**——别把它当迁移回归（生产 `SourceResultParser` 里 `if (sort.filters == null) sort.filters = new ArrayList<>()` 就是这个兜底）。
6. **XStream 1.4 在 JVM 单测里默认拒绝未放行类型**（`ForbiddenClassException`），生产 `SourceResultParser` 没有 `addPermission` 而是 try/catch 吞掉；单测里验证 XStream 映射必须显式 `addPermission(AnyTypePermission.ANY)`（属验证手段，不是改生产逻辑）。
7. 非静态内部类（`AbsJson.AbsJsonVod`、`AbsSortJson.AbsJsonClass`）必须 `inner class` 才能保住 `this$0` 与 `(Outer)V` 构造器；Gson 对这类类走 `Unsafe`，行为不变（`SourceResultParserRoutingTest` 已覆盖）。
8. Kotlin 非空属性被 Gson `Unsafe` 赋 null 时，Java 里那句显式判空（`if (filterSelect == null) return 0`）可能被编译期优化掉——语义敏感的兜底要用"可空类型的局部变量"承接，别直接对非空属性判空。
9. `@SerializedName(alternate = ...)` 的 `alternate` 是 `String[]`，Kotlin 要写 `["id"]`。
10. **Java 集合里的 null 元素（M1 复审实测，真踩到）**：`ArrayList<T>` 这类 **Java 具体集合**的元素在 Kotlin 侧是平台类型，`for (x in list)` 会插 `checkNotNull(next(...))`；而 Kotlin 自己的 `MutableList<T>` 不会插。所以 Java 版「跳过 null 元素」的判空（`if (x == null) continue`）在 Kotlin 侧有两种失效方式：被编译期判成**恒假/恒真**（`w: Condition is always 'false'/'true'`）而不再生成，或压根轮不到就已经在迭代处 NPE。保法有二：把**元素类型**写成可空（`ArrayList<T?>`，字段描述符与泛型签名都不变、Java 调用方无感），或用可空局部量承接（`val item: T? = x`）。实测两处：`AbsSortJson.classes`（需改元素类型，因为它用的是 `ArrayList`）、`ProxyRule.init` 的 hosts（用 `MutableList`，只需可空局部量）。反之 Java 本来就 NPE 的路径（如 `AbsJson.list` 的 `list：[null]`）不用动。
11. **复审手段（M1 靠它抓出上一条）**：`.\gradlew.bat :app:compileDebugKotlin --rerun-tasks` 后过滤 `^w:` 里本次改动文件 —— **`Condition is always 'true'/'false'` 这类告警就是"Java 的防御性判空在 Kotlin 侧可能不再执行"的定位器**，`Java type mismatch: inferred type is 'T?', but 'T' was expected` 则是"把可空值喂给了 Java 形参"（多数是 NPE 等价，逐条确认即可）。

# 7.4 M2 实测登记（2026-10-05，`data/` Room 域 Repository + DAO/entity 迁 Kotlin）

**结论**：四步走完（4 笔 commit），`app/schemas` 逐字未变（Room schema 兼容）、`javap -p -s` debug 与 release 差异完全一致、单测 516 持平。以下条目是 M2 现场核实出的规则，M3 起照查。

1. **Room 3 + KSP 生成的是 Kotlin 实现类（最重要）**：产物里可见 `kotlin.Lazy`、`kotlin.coroutines.Continuation`、`$stable` —— `VodRecordDao_Impl`/`AppDataBase_Impl` 是 Kotlin 写的。因此 **DAO 接口的 `String` 入参若写成非空**，生成实现会在 override 上插 `checkNotNullParameter`，把 Java 原平台类型下的「传 null ⇒ SQL 匹配不到行」变成崩溃。判据：原 Java 未标注的 `String` 入参一律迁成 `String?`（描述符不变、schema 不变）。
2. **实体字段要 `@JvmField`**：Java 侧按字段语法读写（`cache.key = ...`、`record.cid = ...`）时必须保留可见字段；属性名即字段名（Gson 与 Room 都按它走）。
3. **冗余 `@NonNull` 可以删**：Kotlin 非空类型已承载该语义，删注解既不进 `javap -p -s` 也不改 schema —— 判据是 `git status app/schemas` 干净。
4. **`@JvmStatic` 加在 `object` 的 `val` 上只保留静态 getter**（实例级 getter 消失，描述符相同只差 `static`）：Kotlin 调用方仍按属性访问，Java 因此可写 `AppGraph.getCacheRepository()`。
5. **Gson 的显式类型实参**：`fromJson(record.dataJson, type)` 推不出 `T`，必须 `fromJson<VodInfo>(...)`；推断结果保持平台类型，于是「解析出 null → 下一行 NPE → 被同一个 catch 吞掉并落日志」的 Java 语义原样成立。**不要**用 `!!` 补，也不要改成静默丢。
6. **Java 集合的防御判空**：`for (Cache row : rows) if (row == null || row.data == null)` 里的 `row == null` 在 Kotlin 侧（`List<Cache>` 元素非空）恒假、不可达，按不可达处理并登记；`row.data == null` 是真实分支必须保留（与 §7.3 陷阱 10 同源，区别是这里元素类型非空 ⇒ 现状真的不可达，故不改元素类型）。
7. **行尾**：`write_to_file` 写出的新文件是 **CRLF**，`replace_in_file` 保留原行尾。新建文件后必须转 LF 再过 `-Action lf`。（`util/HistoryMerge.kt` 属既有 `i/lf w/crlf` 的 12 个文件之一；本次改动后已落盘 LF。）
8. **`-ClassPath` 传数组**：脚本以原生进程调用时 `-ClassPath a,b` 会被当成一条路径，须写 `-ClassPath @('a','b')`，即从 PowerShell 里用 `& ./skill/scripts/verify-migration.ps1 ...` 调用。
9. **`AppGraph` 传 provider 而不是 DAO 实例**：`AppDataManager.backup/restore` 会 close 并重建 DB 实例，缓存 DAO = 恢复之后所有读写落到已关闭实例。provider 只在装配点取 DAO，满足 D11「实现内不直取静态入口」。
10. **M2 与 D10 的取舍已登记**（计划 §5 M2 偏差 ②）：Repository 方法一律阻塞式，未上 suspend/Flow —— D6（定案）「不改线程语义」优先于 D10（待拍板）；改 suspend 必须同时把消费方协程化（`DetailViewModel.toggleCollect`、`CollectPage` 的 `currentCid()` 都在主线程同步取值）。

11. **新增的非空入参 = 新增崩溃边界（M2 审查轮实测，最容易被漏）**：Java 平台类型入参在 Kotlin 侧写成非空后，实现的入口会插 `checkNotNullParameter`（`javap -c` 可见），null 从「旧实现的正常处理」变成 NPE。`CacheRepository` 三入口就栽在这里：`get`/`delete` 的 `key` 由 `MD5.string2MD5(...)` 提供、空串入参返回 null，旧门面对 null 是「查不到 / 空操作」，非空声明后成了新 NPE。**判据**：逐个入参问「旧实现遇到 null 做什么」——① 旧实现正常处理（null 查询 / 空操作 / 返回 null）⇒ 必须可空；② 旧实现本来就抛（如空主键写 SQLite）⇒ 保留非空，别顺手改成静默不落库（那属于行为变更，且违反「不静默吞错」）。落库类实体的主键**不能**为可空：`Cache.key` 声明 `String?` 时 Room 直接报 `Primary keys cannot be nullable`，且 KSP 会改写 `app/schemas/*.json`（试过，已回滚）。
12. **Java 平台值 → Kotlin 非空返回也会自动插 `checkNotNull`**：`CurrentSubscription.cid()` 的 `return apiUrl`（来自 `KV.get(key, "")`）带 `Intrinsics.checkNotNull`；本例不可达（`KVCodec.decode` 对非空默认值恒不返回 null），登记为「低/既有」，不改写。

# 7.5 M3 实测登记（2026-10-05，`util` 4 个 + `sourcedata` 4 个叶子类）

**结论**：8 个类全量迁 Kotlin（7 笔 commit + 审查轮修复 `303c603`），`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **523 用例 / 0 失败**（516 基线 + `StringUtilsTest` 6 例 + `absXml` 切分 1 例）；新增/删除的源文件全部 LF；`tokens` 卡口的"旧有新无"逐条核对后仍全是已知盲区（`get`/`put`→下标、`Map` 类型、`toLowerCase`/`length` 等 Java 专有形态），**不作为判据**（同 §7.3）。审查轮结论与账目见本节末尾。

**本里程碑现场核实出的规则（后续照查）**：

1. **`\f` 不是 Kotlin 字符串转义**：Java 的 `'\f'`（form feed, 0x0C）在 Kotlin 里编译不过（`Unsupported escape sequence`），要写 `'\u000C'`。Kotlin 支持的转义只有 `\t \b \n \r \' \" \\ \$ \uXXXX`。（`StringUtils.trimBlanks` 两处。）
2. **`import java.lang.reflect.Array` 会遮蔽 `kotlin.Array`**：`StringUtils.isEmpty(Object)` 要 `java.lang.reflect.Array.getLength(...)`，一旦 import 了它，同文件的 `Array<String?>` 参数类型就会解析错 → 用**全限定名**，不 import。
3. **`ThreadLocal<T>.get()` 在 Kotlin 侧是 `T?`**（不是平台类型）：`sortXStream.get()` 直接 `.fromXML(...)` 会报 `Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver`。加 `!!` 后告警消失、且不产生新告警（javap 确认：加 `!!` 只多一个 `checkNotNull`，旧实现在 null 时同样是 NPE）。**别照抄成 `.get()`**。
4. **Kotlin 自己的非空类型属性上的判空对比不会被告警折掉**（重要）：`MovieSort.SortData.filters` 是 `@JvmField var filters: ArrayList<...> = ArrayList()`（非空类型），但 XStream 不走字段初始化器 ⇒ 解析出来真的是 null，Java 侧那句 `if (sort.filters == null) sort.filters = ArrayList()` 是**载荷**的。迁 Kotlin 后该行报 `Condition is always 'false'` 警告，但 **javap -c 实证 `ifnonnull` + `new ArrayList()` 仍然生成**（读 `@JvmField` 只是 `getfield`，比较没有被折叠）。结论：这类"非空类型但运行时可能为 null"的兜底**保留**，只登记告警；判据 = `javap -c` 看到分支，不是"有没有告警"。
5. **`!!` 与智能转换的相互作用**：Kotlin 2.x 里 `if (!x!!.isEmpty())` 之后的 `x` 已被智能转换 ⇒ 里面再写 `x!!` 会报 `Unnecessary non-null assertion`。本次删了 3 处（`SourceHelper` 1 + `SourceResultParser` 2），另外 2 处 `xstream.get()!!` 是**必需**的（见第 3 条）。
6. **Java 的 `if (x != null)` 迁移三分类（逐个判，不一律保留）**：
   - 元素/字段类型在 Kotlin 侧就是可空（如 `urlinfo.beanList: MutableList<...>?`）⇒ 检查是真分支，保留；
   - 元素类型非空、且集合只由 Kotlin 代码填（`VodInfo.seriesFlags`、`Movie.Video.videoList`、`Movie.Video.UrlBean.UrlInfo.beanList`）⇒ 旧检查不可达，删掉并在 commit 里登记；
   - 非空**类型**但运行时真可能为 null（XStream/Gson 绕过初始化器，如 `SortData.filters`）⇒ **必须保留**（第 4 条）。
7. **`split` 只有一种写法等价 Java（本条在 M3 审查轮被推翻重写，§7.3 陷阱 1 与本文旧版说法都不完整）**：
   - Java `String.split(regex)` = `Pattern.compile(regex).split(input, 0)` —— **尾部空串被丢掉**。
   - Kotlin `split("字面量")` = 字面量切分且**保留**尾部空串 ✗。
   - Kotlin `split(Regex(p))` / `split(p.toRegex())` = 走 Kotlin 自己的 `findAll` 逐段实现，**limit=0 时同样保留尾部空串** ✗ —— **只有正整数 limit 时与 Java 逐段一致**（`split(Regex(p), 2)` ✓；实测 `"a$b$c"` / `"a$"` / `"ab"` 三种输入两侧完全一致）。
   - 要逐字等价 Java 的 limit=0：**只能用 Java 的 `Pattern.split`** → `RegexUtils.getPattern(p).split(s)`（返回 `Array<String>`，对应 Java 的 `String[]`）。
   - ⚠️ 别用 `split(Regex(p)).dropLastWhile { it.isEmpty() }` 代替：空输入时 Java 的 `Pattern.split("")` 返回 `[""]`，`dropLastWhile` 会把它削成 `[]` ✗。
   - 实测证据（M3 审查轮回溯）：`"第1集$url#"` 用 `split(Regex("#"))` 得 **2** 段（多一条空集 ⇒ 多一个空剧集条目），用 `RegexUtils.getPattern("#").split(...)` 得 **1** 段 = Java。`StringUtils.getBaseUrl` 的 `split("/")[0]` 同理：退化输入 `"/"` 下 Java 抛 AIOOBE、Kotlin 的 `Regex.split` 返回 `""`。
   - `replaceAll(regex, repl)` → `replace(Regex(regex), repl)` ✓（Kotlin 的 `Regex.replace` 与 Java 的 `replaceAll` 同样把 replacement 里的 `$1`/`\` 当引用解释）；`String.replace(a, b)` 两侧都是字面量 ✓ 不需要改。
   - M1 的两处 `AbsJson` 用的也是 limit=0 的 `split(Regex("\\$\\$\\$"))`（同样保留尾部空串），但紧随其后有 `if (playFlags[i].trim().isEmpty() || playUrls[i].trim().isEmpty()) continue` 守卫 ⇒ 结果与 Java 一致，**登记不改**；`app/src/main` 其它 Kotlin 的 `split('\n')`/`split(':')`/`split(":", limit = 2)` 属 M1 之前的既有 Kotlin 代码（非迁移引入），不在迁移范围。
8. **`TextUtils.isEmpty(...)` 原样保留，不要顺手换成 `isNullOrEmpty()`**：单测环境 `unitTests.isReturnDefaultValues=true` 让 `TextUtils.isEmpty` 恒返 false，既有 Java 逻辑的分支走向被这个 quirk 影响过（`SourceHelper.getFixUrl`/`isHomeSource`、`PushUrlParser`、`PushDetailResolver`）。保留调用点 = 迁移前后**连单测环境的行为**都一致。（`sourceKey == x` 取代 `sourceKey.equals(x)` 这类改写仅在本处成立，且差异只在单测环境不可达路径上。）
9. **`android.util.Log.e(tag, msg)` 的 msg 是 `@NonNull`**：`Log.e(tag, e.message)`（可空）编译不过 → 用 `"${e.message}"`（null 落成字符串 "null"）。Java 传 null 会在 native 层抛 `println needs a message`，属不可达分支；登记为可空收紧。
10. **Kotlin 没有包私有**：4 个 `sourcedata` 包私有类（`SourceHelper`/`PushUrlParser`/`PushDetailResolver`/`SourceResultParser`）放宽为 `public`（同 M1 先例）。**不可用 `internal`**：`internal` 类的成员会被加 `$module` 后缀做名字修饰，同包 Java 调用方（8 个 Loader）与 `SourceResultParserRoutingTest` 会全部编译失败。
11. **静态面按"调用形态"选载体**：Java 按**字段**读的（`SourceHelper.SPIDER_POOL`/`PREPARE_POOL`、`PushUrlParser.PUSH_*`、`PushUrl.inner.url`）→ `@JvmField`；按**方法**调的（`siteGet`/`absXml`/`isPushFallback`/`createPushPlayResult`…）→ `@JvmStatic`；`static final String` 常量 → `const val`（保留 Java 的 ConstantValue 内联语义）。`object` + `@JvmStatic` 同时服务 Java 与 Kotlin 调用点，**本次 0 处调用点改动**（除 MD5 的可空收敛，见第 12 条）。
12. **Java 平台类型 → Kotlin 可空返回会连带改 Kotlin 调用点**：`MD5.string2MD5/encode/encrypt` 迁 Kotlin 后必须声明 `String?`（`CacheRepository` 依赖"空串→null"的既有语义，见 §7.4-11），于是 4 个 Kotlin 调用点（`LocalConfigHelper`×2、`AppBootstrap`、`WatchProgressStore`）补 `!!` = 原来平台类型下编译器插的那次隐式断言；Java 调用点（`SpiderLoader`/`JsLoader`/`FileUtils`…）完全无感。**迁移前先 `search_content 'MD5\.'` 数一遍 Kotlin 调用点**，别等编译报错。
13. **`Matcher.group(int)` 在 Kotlin 侧被判为可空**：`Integer.parseInt(matcher.group(1))` 报 `Java type mismatch: inferred type is 'String?', but 'String' was expected`（警告级，不是错误）。两条出口（null/非数字）都落进同一个 `catch (ignored: Exception)` → 都返回 -1，行为等价，保留。
14. **保留的 4 条告警（已逐条判定为无害，别再"顺手修"）**：① `URLDecoder.decode(String)` deprecated（Java 侧同样 deprecated；非弃用的 `decode(String, Charset)` 要 API 33）；② OkHttp `Response.body` 非空 ⇒ `PushDetailResolver` 里 `body != null` 报 `Condition is always 'true'`（Java 那句本来就恒真）；③ 第 3 条之外的 `SortData.filters` 恒假告警（有意保留）；④ 第 13 条。
15. **新增叶子回归测试**：`app/src/test/.../util/StringUtilsTest.kt`（6 例）——锁重载解析（`CharSequence` / `Object` 两个重载都被 Java 调过）、`trim`/`trimBlanks` 的空白字符集（含全角空格 U+3000 与 `\u000C`）、`getBaseUrl` 的 regex 切分、以及 `listToString`/`arrayToString`/`trimBlanks` 三个**旧实现能返回 null** 的出口（防止以后被插上非空断言）。`EpisodeMatcher` 有既有 `EpisodeMatcherTest`（Java，45 处断言）复跑通过；`MD5` 的非空契约由 `PySourcePackTest`/`SourceHelperExtendTest` 间接覆盖。

16. **`String.trim()` 的空白集不同**：Java `String.trim()` 只去 `<= 0x20`（`\t\n\u000B\u000C\r` + 空格），Kotlin `String.trim()` 去的是 Unicode 空白（多出 U+00A0、U+2000–200A、U+2028/29、U+3000…）。逐字等价要写 `s.trim { it <= ' ' }`（有 String 重载、直接返回 String，**不要再加 `.toString()`** —— 否则报 `Redundant call of conversion method`）。本次两处：`SourceHelper.tryMinifyJson`、`SourceResultParser.json` 的空体判定。

**M3 审查轮（2026-10-05，结论 = 可收尾）**：逐类对账 Java 原文（`git show 847265a:`）+ 全量扫"迁移陷阱面"（`split`/`replace`/`trim`/`lowercase`/`getBytes`/`remove`/`===`/`containsKey`/`substring`/`Array` 遮蔽/未使用 import），并用 `tokens` 卡口 8/8 比对、缺失项逐条核对（全部落在已知盲区：访问器折成属性、类型推断、正则形态转换、`remove(i)`→`removeAt`）。记账（严重度 × 本次引入/既有/口味）：

- **中（本次引入，已修 `303c603`）**：`split("#")` 语义偏差 —— `SourceHelper.absXml`、`PushDetailResolver.list` 两处 limit=0 切分写成 `split("#")`/`split(Regex("#"))`，都保留尾部空串 ⇒ 播放地址以 `#` 结尾时会多出一条空剧集；`StringUtils.getBaseUrl` 的 `split(Regex("/"))[0]` 在退化输入 `"/"` 下也不再与 Java 一致（AIOOBE → `""`）。三处统一改 `RegexUtils.getPattern(p).split(s)`。
- **低（本次引入，已修）**：Kotlin `trim()` 空白集大于 Java（2 处，见第 16 条）；4 个未使用 import（`PushDetailResolver` 的 `Spider`/`SourceBean`、`SourceResultParser` 的 `JsonArray`/`JsonElement`）。
- **低（本次引入，登记接受，不改）**：`util/RegexUtils` 由 `class`（隐式 public 无参构造器）变 `object`（构造器消失、多 `INSTANCE` 字段）。静态方法经 `@JvmStatic` 全保留；仓库内零调用点、也不在 D9 Tier B 清单（Tier B 的 `MD5`/`StringUtils` 用的是 `class` + `companion`，构造器形态守恒）。要完全保守可改回 `class` + `companion`。
- **低（既有，登记不改）**：`AbsJson` 两处 limit=0 `split(Regex(...))` 保留尾部空串（被 `trim().isEmpty()` 守卫兜住，结果等价）；`PushUrlParser`（org.json）与 `PushDetailResolver`（OkGo/迅雷/App）在 `unitTests.isReturnDefaultValues = true` 下无法做 JVM 单测（既有结构限制，靠真机走查）。
- **无 阻断 / 高 级发现**，剩余全部为低 / 既有 / 口味 ⇒ 按收敛终止线判定**可收尾**。
- 复核结果：`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **523 用例 / 0 失败**（新增 1 例覆盖 `#` 切分）、改动文件 LF、编译告警仍只有下面第 14 条的 4 条。新增回归测试做了**正/反向自测**：退回 `split(Regex("#"))` 时报 `expected:<1> but was:<2>`，改回 `Pattern.split` 后通过。
- 审查覆盖面的诚实标注：`PushUrlParser`/`PushDetailResolver` 的 JSON/网络分支没有自动化覆盖（见上）；`SourceHelper`/`SourceResultParser` 有既有单测 + 新增 1 例。

**未验证面（诚实标注）**：真机走查未做 —— 首页分类/推荐（`sortXml`+filters 兜底）、详情起播（push:// 解析、迅雷改写）、搜索面板、换源与 extend（`getFixUrl` 本地/网络/超时三出口）、推送直链带 `@Headers=`、`#` 结尾的播放地址（多集/多线路）。

# 7.6 M4a 实测登记（2026-10-05，LiveData→Flow 收口）

**结论**：新增 `sourcedata/SourceChannel<T>`（**Flow 主面** + **LiveData 过渡兼容面**），`SourceViewModel` 的 7 个通道、5 个 Loader 的输出面、`SourceResultParser`/`PushDetailResolver` 的参数全部换到该类型；3 个页面 VM 由 `observeAsFlow()` 改成 `channel.flow`；`sourcedata/LiveDataFlow.kt` 零调用点后删除。`:app:assembleDebug` + `:app:assembleRelease` 绿、`:app:testDebugUnitTest` **534 用例 / 0 失败**（523 + `SourceChannelTest` 6 + `SourceResultParserRoutingTest` 1 + `SourceViewModelWiringTest` 4）、新增/删除文件全部 LF、无语言迁移、Tier B 11 符号未变。四处偏差、4 项审查发现与语义差异见 `refactor-plan-20261005.md` §5 M4a。

**本里程碑现场核实出的规则（M7 照查；M4b 已按这些规则执行，实测登记见 §7.7）**：

1. **LiveData 在 JVM 单测里不能投递**（最重要）：单测环境 `Looper.getMainLooper()` 为 null，`MutableLiveData.setValue` 的主线程断言直接 `NPE: Cannot invoke "android.os.Looper.getThread()"`，`postValue` 也落进 stub 的 `Handler` 而静默 no-op。所以"兼容面投递走向"不能用真实 `MutableLiveData` 断言 —— 通道把 LiveData 投递抽成 `protected open fun dispatchToLiveData(value, sync)`，单测用替身记录（**先例**：`SourceResultParserRoutingTest` 里覆盖 `postValue` 的 `RecordingChannel` 就是同一手法）。想直接断言得引入 `androidx.arch.core:core-testing`，而 §6 禁新增依赖，故**登记为环境限制**（同 §7.5 第 14 条对 `PushUrlParser` 的处理）。
2. **`MutableSharedFlow(replay = 1, extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST)` 是"LiveData 等价面"的最小配方**：`replay = 1` 复刻 LiveData 粘性（新收集者立刻拿到最近一次的值 —— 页面 VM 因此不会空等已发生的结果）；`DROP_OLDEST` 让 `tryEmit` **永不失败也不挂起**，任意线程（OkGo 回调、`SPIDER_POOL`/`PREPARE_POOL`、main handler）都能安全投递。（要求 `replay > 0 || extraBufferCapacity > 0` 才允许非 SUSPEND 的溢出策略，本配方满足。）
3. **`postValue` 的合并语义是唯一已知语义差**：`MutableLiveData.postValue` 在同一主线程 tick 内多次调用只投**最后一次**（`mPendingData` + 一个 runnable），`MutableSharedFlow` 逐条投递。现有 5 条页面通道每次请求只投一次结果 ⇒ 等价；仅同通道**并发**多次投递时新实现会多投一条，由消费方守卫（`detailToken` 代次、`pending` 槽、`sourceKey` 比对）变成 no-op。**取舍理由**：合并的故障模式是丢结果（`PartitionLoader` 的 `pending` 永不回调 ⇒ 分区永停 Loading），多投一条的故障模式是可被守卫吸收的重复；故选"不合并"。要重新引入合并就把 `extraBufferCapacity` 降到 0（容量 = replay = 1，即 conflated）。
4. **通道对象必须同实例贯穿**：`SourceResultParser` 靠**身份**分投（`searchResult === result`、`result === detailResult`），所以 `SourceViewModel` 里每条通道只有一个实例、原样传给 Loader 与 Parser。Kotlin 泛型可空（`SourceChannel<AbsXml?>`）与 Java 侧实参（`SourceChannel<AbsXml>`）在 JVM 签名上同一，不影响 Java 调用方，也不影响 `===`。
5. **泛型参数取可空形态**：null 是合法载荷（取数失败/无结果都投 null），所以通道声明为 `SourceChannel<AbsXml?>`/`SourceChannel<JSONObject?>`；`postValue(null)` 必须作为一次投递送达（`SourceChannelTest.nullPayloadIsDeliveredNotSwallowed` 锁住）。
6. **`setValue` 的主线程约束原样保留**：Loader 里唯一的 `setValue` 在 `PlayLoader.postPlayResult`（`mainHandler.post` 内），兼容面仍按 LiveData 规则（`setValue` 主线程同步 / `postValue` 任意线程）。`flow` 面则无此约束 —— 收集者跑在自己的 dispatcher（页面 VM 全是 `viewModelScope` / `Main.immediate`），与旧 `observeAsFlow` 的"必须有主线程 dispatcher"断言相比是**放宽**（旧注释里"`Main.immediate` 是因为 `observeForever` 有主线程断言"已随 M4a 更新为"让回包仍在主线程处理"）。

7. **门面构造器在纯 JVM 单测里可实例化（本轮实测推翻旧注释）**：`new SourceViewModel()` 能跑通 —— 那 7 个 `MutableLiveData`/`MutableSharedFlow` 只是字段装配，**不碰 Looper**。历史注释（`SourceRuntimeStateTest`、`history/features.md:2648`）把"单测挂掉"归因于 `MutableLiveData` 初始化器是误记：真因是**消费方**的 `observeForever`（`LiveData.assertMainThread` → `Looper.getMainLooper()` 为 null 再解引用）。因此"能不能在单测里测某个 VM"看的是它的**消费面**：`DetailViewModel` 至今仍不可实例化，真因是 `init` 里 `viewModelScope.launch` → `Dispatchers.Main` 抛 `The main looper is not available`（M4a 换掉 `observeForever` 只消掉了旧阻塞中的一条）。判据改用 `Dispatchers.Main`/`observeForever` 是否在构造路径上，别再看 `MutableLiveData`。
8. **转换后要找出"转换本身新承重的不变量"并补测试**：M4a 的分投完全依赖 `SourceResultParser` 的**身份判定**（`result === detailResult` / `searchResult === result`），通道实例一旦被复制一份就静默走错分支（表现为"详情不解析 `push://`""搜索面板收不到结果"，编译与静态检查都看不出来）。补 `SourceViewModelWiringTest`（反射读私有字段：门面 → Parser/Resolver → 5 个 Loader 的通道同实例、7 通道互异、共享 `SourceRuntimeState` 的两张缓存），并做**正/反向自测**（把通道换成 `new SourceChannel<>()` ⇒ 必须报失败）。这类"接线型"不变量对反射读字段是可测的，不要因为字段是 private 就放弃。

**未验证面（诚实标注）**：真机走查未做 —— 首页分类/推荐、详情回包与换源 fallback（代次链路）、搜索面板、`action` 消息、起播取流与下一集预载（`playResult`/`preloadResult` 的 LiveData 兼容面）。

# 7.7 M4b 实测登记（2026-10-05，`sourcedata` 取数侧迁 Kotlin）

**结论**：7 个类迁 Kotlin（5 个 Loader + `SourceViewModel` + `SourceRuntimeState`）+ 审查轮修复 1 笔；`:app:assembleDebug` + `:app:assembleRelease` 绿、`:app:testDebugUnitTest` **536 用例 / 0 失败**（534 基线 + `SortLoaderActionVideoTest` 2 例）、改动/新增文件全 LF、Tier B 11 符号未变、`app/schemas` 未动。**既有 3 个测试文件（`SourceViewModelWiringTest`/`SourceRuntimeStateTest`/`PlayLoaderSeqTest`）一行未改且全绿** —— 它们是本里程碑的等价性主门（通道接线、access-order 缓存、双通道序号）。交付、偏差与未验证面见 `refactor-plan-20261005.md` §5 M4b。

**本里程碑现场核实出的规则（M5/M6 照查）**：

1. **可见性与静态面按 Kotlin 现实放宽/换载（只许放宽，逐条登记）**：5 个 Loader 由包私有 `final class` 变 `public class`；`ListLoader.HomeRecCallback` 由包私有嵌套接口变 public；`SourceRuntimeState.sortCache`/`extendCache` 由包私有 static 变 `public static final`（`@JvmField`）；`PlayLoader.isStaleResult` 由包私有 static 变 `public static final`（`companion object` + `@JvmStatic`，单测按 `PlayLoader.isStaleResult(...)` 调用）。**不可用 `internal`**（成员会被加 `$module` 后缀，同 §7.5 第 10 条）。`SourceViewModel` 的 7 个通道字段保持 `@JvmField`（Java 播放层走 `playResult.getLiveData()` 的字段读法），但由非 final 变 `public final`（Java 侧不能再整体替换通道实例 —— 零调用点，登记接受）。
2. **`SourceRuntimeState` 由 `final class` + 私有构造器 → `object`**：产物多 `INSTANCE` 与 `$stable`（原构造器本就 private，零调用点）；两个缓存经 `@JvmField` 保字段读法、`clearRuntimeCache()` 经 `@JvmStatic` 保静态调用，Java/Kotlin 调用点均无须改。`javap -p` 实证三个成员全在；**access-order 语义（上限 5 + `removeEldestEntry`）由 `SourceRuntimeStateTest` 3 例锁定**（含"清空不换新实例"）。
3. **`sortCache` 的 value 类型声明为可空（`MutableMap<String, AbsSortXml?>`）**：Java 的 `Map<String, AbsSortXml>` 允许 null 值（`cacheSort` 的入参本就是可能为 null 的 `sortXml`）；Kotlin 若写成非空 V，`sortCache[key] = sortXml` 需要 `!!` = **新增崩溃边界**（判据 §7.4 第 11 条）。泛型擦除与 Signature 都不变（javap 显示 `Map<String, AbsSortXml>`）。
4. **"Java 集合里可能为 null 的元素"在**入参**上同样按可空迁（§7.3 第 10 条的补充）**：`ListLoader.getHomeRecList(ids: ArrayList<String?>?)` —— Java 原文是 `ArrayList<String> ids`，收的是 `vod.id`（bean 里是 `String?`，XStream 可绕过初始化器），Java 会静默放进 null 元素；声明非空元素就得在 `ids.add(vod.id)` 处写 `!!`。**形参描述符 `java.util.ArrayList` 与 Java 逐字相同，只有元素可空性变（字节码不可见）**。同源还有 `SortLoader.hasActionVideo(videos: List<Movie.Video?>?)`（元素判空是真分支，写成非空元素会被编译器折掉 ⇒ 载荷含 null 元素时 NPE；已补 `SortLoaderActionVideoTest` 锁住）。
5. **保留非空的判据在本里程碑出现了两个方向的样本**：`DetailLoader.getDetail` 的 `urlid` 保留非空（Java 第一个动作就是 `urlid.startsWith("push://")`，null 即 NPE；调用点全传非空）；`SortLoader.cacheSort` 的 `sourceKey` 也声明非空（`MutableMap.set` 要求非空 key，且调用链上 `getSort` 已在 `sourceKey == null` 处早退）。其余引用型入参（`sourceKey`/`wd`/`playFlag`/`progressKey`/`subtitleKey`/`sortData`）一律可空 —— 旧实现在 null 上走的是正常分支。
6. **私有 static 助手 → 私有实例方法（只有 `isStaleResult` 保静态）**：Java 的 `private static`（`SortLoader` 的 5 个判定助手、`DetailLoader.createEmptyDetail`、`PlayLoader` 的 `shouldDirectPlay`/`normalizePlayerResult`/`mergeSiteHeaders`）落成 Kotlin 私有实例方法 —— 私有成员无外部契约，`javap` 差异只在此；Java 侧本来也有 `access$xxx` 合成访问器（Kotlin 用 lambda + `access$<方法名>`，命名不同、冲突面为零）。
7. **`assert`、SAM 与 lambda 提前退出的写法**：POST 分支保留 `assert(body != null)`（Kotlin `assert` 不吃 `$assertionsDisabled`，断言关闭时同为 no-op）；`SourceHelper.*_POOL.execute { … }`、`mainHandler.post { … }`、`RemoteTVBox.post(…, object : okhttp3.Callback { … })` 分属 Java 接口的 SAM 与 Kotlin 接口的 `object :`；提前退出写 `return@execute` / `return@post` / `return@Callable`。
8. **三处 `Charsets` 逐字等价改写（Kotlin 的 `String` 没有 `getBytes(String)` 重载，编译不过）**：`new String(bytes, "UTF-8")` → `String(bytes, Charsets.UTF_8)`；`s.getBytes("UTF-8")` → `s.toByteArray(Charsets.UTF_8)`；`"{}".getBytes()` → `"{}".toByteArray(Charset.defaultCharset())`（**Java 的 `getBytes()` 用平台默认字符集，别顺手写成 `toByteArray()` 的默认 UTF-8**）。`catch (UnsupportedEncodingException)` 原样保留（Java 侧也只是检查异常声明）。同 M3 `PushDetailResolver` 先例。
9. **`trim` 的空白集差异在取数侧也会踩到**：`PlayLoader.playFromApi` 的 `sourceBean.getPlayerUrl().trim()` 必须写 `trim { it <= ' ' }`（§7.5 第 16 条）。**逐类扫 `trim(` 是必需动作**（本处由审查轮抓到）。
10. **分支重排只允许"短路语义逐条等价"的形态**：Java 的 `if (withRec && sortXml != null && sortXml.list != null && …) … else if (sortXml != null && sortXml.classes != null) … else postSortFailure()`，因 `var` 被 lambda 捕获后不能智能转换，改成 `val sortXml: AbsSortXml? = if (…) … else null` + `if (sortXml != null) { … } else postSortFailure()`：**`else` 出口与 `classes == null` 的落点必须逐条对齐**（对错就是把"解析成功但没推荐"误判成失败或反之，属静默回归）。同类：`recVideoList` 提前取出替代 `sortXml.list.videoList` 的三层判空（顺序无副作用）。
11. **`tokens` 卡口在 Java→Kotlin 仍是参考打印（同 §7.3）**：7 个文件的"旧有新无"逐条核对后全是已知盲区 —— 访问器折成属性（`getKey`/`getExt`/`getHeader`/`getPlayerUrl`/`getMessage`）、`Map.Entry` 循环折成 `for ((k, v) in map)`（`Entry`/`entrySet`/`getKey`/`getValue` 消失）、`Runnable`/`Callable`/`AsyncCallback` 匿名类变 lambda/object 表达式、`GetRequest` 显式类型变推断、`Integer` → `Int?`、字符串拼接变模板（`STR:"…: "` → `STR:"…:$x"`）、`x.put(k, v)` → `x[k] = v`、`getBytes("UTF-8")` → `Charsets.UTF_8`。**旧文件已删除时必须显式给 `-Ref <迁移前那一笔 commit>`**（脚本缺省取 `HEAD`，而 `HEAD` 上 `.java` 已不存在 ⇒ 直接抛异常）。
12. **`isReturnDefaultValues = true` 下 `org.json` 同样是桩** ⇒ `PlayLoader.normalizePlayerResult`/`mergeSiteHeaders`、`DetailLoader.createPushDetail` 这些**依赖 JSONObject 的私有助手做不了 JVM 单测**（与 §7.5 对 `PushUrlParser`/`PushDetailResolver` 的登记同源）。能测的只有不碰 Android/JSON 的纯判定 —— 本里程碑的 `SortLoaderActionVideoTest`（反射调 `hasActionVideo`，元素判空守卫）即属此类，并做了正/反向自测（撤掉判空 ⇒ 用例 NPE 失败）。

13. **保留的编译告警（逐条判定无害，勿"顺手修"）**：① `URLDecoder.decode(String)` / `URLEncoder.encode(String)` deprecated（Java 侧同样 deprecated；非弃用重载要 API 33）；② OkHttp 4 的 `Response.body` 是非空类型 ⇒ `if (body != null) … else throw …` 报 `Condition is always 'true'`（8 处；Java 那句本来就恒真，**删掉 else 出口才是重构**）；③ `SortData.filterSelect` 的两条恒真/恒假（第 4 条 / §7.5 第 4 条，分支已 `javap -c` 实证保留）；④ `PlayLoader` 的 `Java type mismatch: inferred type is 'String?'`（`JSONObject(json)` 的可空入参 —— 与 Java 的 NPE/JSONException 落在同一个 `catch`，行为等价，同 §7.5 第 13 条）。

**未验证面（诚实标注）**：真机走查未做 —— 首页分类/推荐、详情回包与换源 fallback（`detailToken` 代次链路）、搜索面板、`action` 消息、起播取流与下一集预载（`playResult`/`preloadResult` 的 LiveData 兼容面）、t4 源 extend 的 GET/POST 双出口、推送直链与迅雷改写。

# 7.8 M5 实测登记（2026-10-05，`api/` 全包 8 个类）

**结论**：`api/` 全包迁 Kotlin（门面 `ApiConfig` + `ConfigParser`/`ConfigApplier`/`ConfigLoader`/`SpiderLoader`/`WarmQueue`/`ProxyEntry`/`DanmakuApi`），8 笔迁移 commit + 审查轮 1 笔；`:app:assembleDebug` + `:app:assembleRelease` 绿、`:app:testDebugUnitTest` **536 用例 / 0 失败**（与 M4b 基线持平）、`app/src/main/java` 159 j / 194 kt → **151 j / 202 kt**、`api/` 包 Java 清零。**冻结口径达成**：`javap -p -s` debug/release 两侧差异一致，逐成员配对后**零公开成员消失、零描述符变化**（未配对的 55 条=51 个 `DanmakuApi` 私有助手迁入 Companion + 3 个 Java lambda 合成改名 + `$assertionsDisabled`）。交付、偏差与未验证面见 `refactor-plan-20261005.md` §5 M5。

**本里程碑现场核实出的规则（M6/M6b 照查；M7/M9/M10 也适用）**：

1. **迁移顺序受"可见性闭包"约束**：Kotlin 没有包私有类，包私有 Java 类一旦被 public Kotlin 类的**签名**引用就编译不过（`'public' function exposes its 'public/*package*/' parameter type`）。例：`WarmQueue(ApiConfig, SpiderLoader)` 里的 `SpiderLoader` 必须先迁。**动作**：动手前先画「参数类型 → 是否包私有」的依赖图，把包私有 Java 类排在被引用者之前；纯内部使用的类（`ConfigParser`/`ConfigApplier`）不受此限。
2. **`object` / `class` + `companion` 的选择**：`@JvmStatic` **只能**写在 `companion object` 或 `object` 成员上（写在普通类体里是编译错误 `@JvmStatic annotation is not applicable`）；纯静态工具类用 `object`（`ConfigParser`/`ConfigApplier`），有实例语义或需要保住隐式公开构造器的用 `class` + `companion`（`ApiConfig` 私有构造器、`DanmakuApi` 公开构造器、`SpiderLoader`）。
3. **冻结 Java 名时，Kotlin 属性与显式 `getX()` 函数不能共存**（同一个 JVM 签名）。若既有 Kotlin 调用点两种写法都有，只能二选一：本里程碑按**多数派**选形态 —— 已用属性语法的 4 个成员（`channelGroupList`/`parseBeanList`/`liveSettingGroupList`/`liveConnectTimeoutSeconds`）落成 Kotlin 属性，其余（含 `getHomeSourceBean`：显式 4 处 vs 属性 2 处）落成显式函数，少数派调用点改 1–2 行。**清点命令**：`Select-String 'ApiConfig\.get\(\)\.[A-Za-z_]+' *.kt | Group-Object`。
4. **只有 setter 的 Java 字段**（`SpiderLoader.liveSpider`/`jarCache`）：`private var x` + 显式 `fun setX(v)` **不冲突**（私有属性不生成访问器），这样能保住"零新增成员"；反之写成公开属性会平白多一个 getter。getter-only 的字段用 `var x; private set`（Kotlin 会省掉未用的私有 setter）。
5. **Kotlin 接口默认方法是真 JVM default**（Kotlin 2.4 默认 `-jvm-default=enable`），但会**额外**生成 `X$DefaultImpls` 与 `access$m$jd` 桥 ⇒ `javap` 会报"新增类"，登记即可；Java 实现方（`PlaybackFetch` 只覆写 `onFound`/`onNotFound` 中的部分）不受影响。判据用 `javap -p` 看 `public default void m();`。
6. **接口参数的可空性必须与既有覆写逐字一致**：Java 接口是平台类型，Kotlin 覆写既能写 `String` 也能写 `String?`；一旦接口迁 Kotlin，两种覆写不能共存。**只能放宽为可空**（收紧会把"传 null"变成新崩溃边界），故改声明显式 `String` 的那几处（本次 `LivePlayActivity`/`LivePlayViewModel` 4 行），并连带处理 `host.toast(msg)` → `msg ?: ""`。**动作**：迁接口前先 `grep 'override fun <方法名>\('` 清点两侧写法。
7. **`const val` 在 companion 里仍落在外层类的静态字段**（`WARM_ITEM_TIMEOUT_MS` 经 `javap` 实证两字节码一致）；但**非编译期常量**（`TimeUnit.SECONDS.toMillis(20)`）只能 `val`，字段会移到 `X$Companion`（私有，登记）。
8. **`new String(byte[])` 是 M4b 规则 8 的镜像坑**：Kotlin 把 `String(bytes)` 编译成 **UTF-8** 构造器，而 Java 的 `new String(byte[])`/`String.getBytes()` 用**平台默认字符集** ⇒ 逐字等价必须写 `String(bytes, Charset.defaultCharset())`。本次 `ApiConfig.FindResult` 两处由审查轮抓出并修复。
9. **Java 字段初始化早于构造器体，Kotlin 按声明顺序与 `init` 交错**：带初值的字段必须声明在 `init {}` **之前**，否则构造期间会出现"字段是 null 而 Java 是空集合"的空窗（`ApiConfig.liveSettingGroupList` 由审查轮抓出并前移）。**动作**：迁类时把 Java 的字段声明位置逐个对照（Java 里"声明在方法之间"的字段，初始化仍然最早）。
10. **`javap` 逐成员配对脚本口径**（本次自写）：去掉 `public/private/protected/final/static/abstract/…` 修饰符与 `throws …`，**跨类全局**配对 `-`/`+` 行。这样能把"私有方法搬进 `Companion`（同名同描述符）"与"真丢失"分开 —— 前者在逐类比对里会假报为删除（本次 51 条）。
11. **`!!` 的判据仍是"复刻 Java 平台类型的隐式解引用"**（§7.7 第 5 条）：本次 29 处逐条核对后全部落在同一条路径上；特别注意 `!TextUtils.isEmpty(x)` 守卫后的解引用 —— 单测桩（`isReturnDefaultValues=true`）下 Java 也在同一行 NPE，故 `!!` 连单测环境的行为都一致。
12. **私有方法的 `throws` 随 Kotlin 消失是预期项**（`Exceptions` 属性不属描述符，`javap -s` 看不出；`javap -p` 会少一段）。有 Java 调用点的 public 方法若要保留受检异常声明，必须 `@Throws`。
13. **保留的编译告警（逐条判定无害，勿"顺手修"）**：`response.body == null` 恒假 ×9（`DanmakuApi` 8 + `SpiderLoader` 1，OkHttp 4 的 `Response.body` 非空；`ConfigLoader` 1 处同源）—— Java 那句本来就恒真，与 §7.7 第 13 条同源。本里程碑**无**未使用 import / 冗余 `!!` 告警。

**登记的产物差异（debug 与 release 一致）**：`class` → `public final class`；包私有类/成员 → `public`；`private static` 助手 → `private` 实例/伴生方法（`DanmakuApi` 的 51 个搬进 `DanmakuApi$Companion`）；新增 `Companion`/`$stable`/`access$*` 桥；`ApiConfig` 私有构造器旁新增 `DefaultConstructorMarker` 合成构造器、`instance` 私有静态字段移入 `Companion`；3 个私有方法丢 `throws`；`ConfigParser$ConfigUrl` 与 `DanmakuApi$EpisodeList|EpisodeMatch` 字段/构造器放宽为 `public final`（类私有/包内）；`SearchCallback` 新增 `DefaultImpls` 与 `access$onNotFound$jd`。

**调用点改写清单（Kotlin 源，字节码零影响；共 12 行，其中 1 行含新增 `!!`）**：`sourcedata/ListLoader.kt`・`SourceHelper.kt` 的 `homeSourceBean` → `getHomeSourceBean()`（2 行，属性语法→显式 getter，字节码同）；`ui/page/AppBootstrap.kt` 的 `getSpider()` → `getSpider()!!`（1 行，复刻 Java 平台解引用）；`ui/activity/LivePlayActivity.kt`・`LivePlayViewModel.kt` 的 `error`/`notice` 覆写 `String` → `String?`（4 行）+ `host.toast(msg)` → `msg ?: ""`（2 行，接口可空性，见规则 6）；`api/ConfigLoader.kt` 的 `activity: Activity` → `Activity?`（1 行，`AppBootstrap` 传 `null`）；`api/ConfigParser.kt` 的 `parseLiveChannelName` 形参 `ArrayList<String?>` → `ArrayList<String>` 并删不可达元素判空（2 行，§7.7 规则 6 口径 —— Java 形参本就声明 `ArrayList<String>`，唯一生产者是 `Pattern.split`）。

**审查轮（2026-10-05，结论 = 可收尾）**：3 个只读子代理独立逐方法复核（`ApiConfig` / `DanmakuApi`+`SpiderLoader` / 其余 5 类 + 全包陷阱扫描），**均判语义等价（high confidence）、0 条阻断/高/中**；本机复跑 `tokens` 8/8 + `lf` + `javap` 双变体 + 逐成员配对 + `--no-build-cache`/`--rerun-tasks` 全量单测。审查轮修复 4 条低级项（规则 8/9 + `parseLiveConfigContent(String, File)` 恢复"先关流再解析" + 删 3 个未使用 import）；登记不改的项：`ConfigParser.parseLiveChannelName` 的不可达判空删除（恢复可空元素需在 `loadLives` 强转或复制列表，更差）、29 处 `!!`、`LivePlayActivity`/`LivePlayViewModel` 的"null → 空 toast/空串"放宽（生产不可达）、`SpiderLoader.getCSP` 系列非空返回（加载器失败出口全返回 `SpiderNull`）。

**未验证面（诚实标注）**：真机走查未做 —— 换源成功/失败（多仓分流、`;pk;` 密钥、`clan://`/`file://`/局域网地址）、本地源不可读/已删除两条报错路径、快照回落与 TTL、广告拦截与 rules 不真空、doh、解析器列表与「超级解析」、直播配置三条入口（JSON/文本/多仓）、预热队列不回归、jar 下载/重试/`img+`/`jarCache`、js/py 源加载、/proxy 四级兜底路由、弹幕搜索（内置 API + 占位符/自定义 API + retry 代次）、DLNA/局域网服务地址。

# 7.9 M6 实测登记（2026-10-05，`subtitle/` 22 个类）

**结论**：`subtitle/` 22 个 Java 全量迁 Kotlin（1 笔 commit `dabd7ff`），`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **536 用例 / 0 失败 / 0 错误 / 0 跳过**（与 M4b/M5 基线持平，纯语言迁移未新增用例）、`lf` 卡口 22/22、`tokens` 卡口抽查 5 个代表文件（`Time`/`SubtitleLoader`/`FormatSTL`/`SimpleSubtitleView`/`TimedTextObject`，缺失项全落在 §7.3 的已知盲区）。M6 计划里 **`util/` 45 个 Java（含 D9 Tier B 的 `LOG`/`KV`/`FileUtils`/`OkGoHelper`/`AppContextHolder`/`SSLSocketFactoryCompat`）仍未开始**，续做时按本节规则 + §7.8 规则 1（可见性闭包）排序。

**本里程碑现场核实出的规则（M6 续做 / M6b / M7 照查）**：

1. **`AppGraph` 的 repository 是 Kotlin 属性，不是 getter（本次构建失败的头号原因）**：`AppGraph` 是 `object`，三个 repository 声明为 `@JvmStatic val`。Java 侧写 `AppGraph.getCacheRepository()`（`@JvmStatic` 生成静态 getter），**Kotlin 侧必须写 `AppGraph.cacheRepository`**；写 `getCacheRepository()` 会 `Unresolved reference`。同类还有 `AppGraph.historyRepository` / `collectRepository`（既有 Kotlin 调用点已是属性写法）。
2. **禁止 `import java.util.Iterator` / `java.util.Collection`**：① 触发 deprecation 警告；② `java.util.Iterator<T>` 无变型，`MutableIterator<T>` 赋不进去 ⇒ 报 `Initializer type mismatch: expected 'Iterator<Subtitle>', actual 'MutableIterator<Subtitle>'`。要么用 Kotlin 默认导入的 `Iterator`/`Collection`，要么干脆省掉显式类型标注（`val c = tto.captions!!.values`）。**子代理产出的 Java 风格显式类型标注要在复核时清掉。**
3. **`TimedTextFileFormat` 是接口**（Java `Object toFile(TimedTextObject)` ⇒ Kotlin `Any?`）。四个 `Format*.toFile` 都有 `if (!tto.built) return null` ⇒ Kotlin 返回类型必须可空（`Array<String>?` / `ByteArray?`），连带 `TimedTextObject.toSRT/toASS/toSTL/toSCC/toTTML` 一起放宽为可空 —— 先 `grep` 确认全库零调用点再放宽（本次确认零调用点）。
4. **自定义 View 的三构造器不能合并成 `@JvmOverloads`**：`SimpleSubtitleView` 的背景描边 `TextView` 要逐个镜像 Java 的 `TextView(context)` / `TextView(context, attrs)` / `TextView(context, attrs, defStyleAttr)`；用 `@JvmOverloads` + 单构造器会改变描边层的默认样式解析，属**观感变化**（本项目红线）。同时 `@JvmField` 保住 `isInternal`/`hasInternal` 两个被 Java 按字段读写的公开字段。
5. **父类构造期回调的守卫用 `lateinit` + `isInitialized`**：`onTextChanged` 可能被 `TextView` 构造器触发，此时 `backGroundText` 还是 null（Java 用 `!= null` 守卫）。Kotlin 写 `private lateinit var backGroundText: TextView` + `if (this::backGroundText.isInitialized)`，行为等价；会产生一条 `'lateinit' is unnecessary: definitely initialized in constructors` 警告，**判定无害**。
6. **Java 里没写 `@Override` 的接口实现，迁 Kotlin 必须补 `override`**：`SimpleSubtitleView.setPlaySubtitleCacheKey` 在 Java 中未标注，Kotlin 报 `hides member of supertype 'SubtitleEngine' and needs an 'override' modifier`。
7. **冗余 `!!` 只删被点名的那些**：编译器报 `Unnecessary non-null assertion` 的才删；同一段里**前一处 `!!` 建立的智能转换可能正是后一处的依赖**（`FormatSTL` 的 `currentCaption`：`if` 分支与 `else` 分支各自需要一次 `!!`，分支内的后续行才不需要）。
8. **静态持有类 → `object` + `@JvmStatic`（沿用 M3 `RegexUtils` 先例）**：`SubtitleFinder`、`SubtitleLoader`、`AppTaskExecutor` 都是「私有构造器（`SubtitleLoader`/`SubtitleFinder` 的构造器还 `throw AssertionError`）+ 全静态成员」形态 ⇒ 落成 `object`，静态成员 `@JvmStatic`（`SubtitleLoader` 另有一个**实例**方法 `loadSubtitle(String)`，在 `object` 里不加 `@JvmStatic` 即保住实例形态）。登记产物差异：新增 `INSTANCE`、丢失不可达的抛异常构造器。
9. **接口的平台类型入参一律放宽为可空**（同 §7.8 规则 6）：`SubtitleEngine` 的 `path`/`milliseconds`/`cacheKey`/`listener`/`mediaPlayer` 全部 `?`，`getPlaySubtitleCacheKey(): String?`；实现侧该 `!!` 的地方 `!!`（如 `DefaultSubtitleEngine` 的 `val p = path!!`，复刻 `TextUtils.isEmpty` 在单测桩下恒 false 的既有 quirk）。
10. **`List<Subtitle>` 的模型字段用 `@JvmField var ... : MutableList<Subtitle>? = null`**：`Subtitle.lines` 被 Java/Kotlin 双方按字段读写（`previous.lines = ArrayList()`），`@JvmField` + 可空保住字段形态与 Java 的「未赋值即 null」语义；`Subtitle.start`/`end`/`style` 同理可空，消费侧 `!!` 复刻 Java 的隐式解引用（`SubtitleFinder`/`buildSubtitles`）。
11. **本批次其它登记项**：`TimedTextObject.cleanUnusedStyles` 引入局部 `val style = current.style`（语义等价，`current` 的 token 计数因此下降）；`SimpleSubtitleView.setSubtitleDelay` 的形参由 `mseconds` 改名 `milliseconds` 以对齐 Kotlin 接口（形参名不进描述符）；`SubtitleLoader` 的 `loadAndParse`/`openBomAwareStream` 形参沿用 Java 的 `is`（Kotlin 用反引号 `` `is` ``）；`String.toLowerCase()` → `lowercase()`（与 §7.5 先例一致，仅土耳其语等少数 locale 有差异，本项目无实际影响）。

**保留的编译告警（逐条判定无害，勿"顺手修"）**：`URLDecoder.decode(String)` deprecated ×2、commons-io `ReaderInputStream(Reader, Charset)` deprecated ×2、`Html.fromHtml(String)` deprecated ×2（三者在 Java 侧同样 deprecated）、第 5 条的 `lateinit` 提示。

**M6 subtitle 审查轮（2026-10-05，结论 = 可收尾）**：4 个只读子代理独立逐方法逐语句对账（分工：model/exception/runtime/根小类 13 个；`SubtitleLoader`+`DefaultSubtitleEngine`+`SimpleSubtitleView`；`TimedTextFileFormat`+`FormatSRT`+`FormatSCC`；`FormatASS`+`FormatTTML`+`FormatSTL`），**0 条 阻断/高/中**。本机机械卡口：`lf` 22/22、`methods` 20/22、`tokens` 22/22、`prune_imports` 22/22 为 0、`--rerun-tasks` 全量构建 + 单测 536/0/0/0。报告落盘 `skill/review/review-20261005-m6-subtitle.md`。记账：**低（本次引入，不可达，登记不改）** 2 条 —— `DefaultSubtitleEngine.setSubtitleDelay` 的 `milliseconds!!` 落点后移（Java 在 `Integer == 0` 解引用，Kotlin 在 `!!`；唯一调用方传 `int`）、`SubtitleLoader` 的 `Charset.forName` 异常类型与 `UnsupportedEncodingException` 不同（都被外层 `catch (Exception)` 吞）；**低（本次引入，口味差异）** 2 条 —— 可空化放宽、`lowercase()` 的 locale；**低（既有，登记不改）** 6 条 —— 两处 DCL 缺 volatile/二次判空、`SubtitleFinder` 死分支、`Style` 的 `magenta`/`cyan` 尾随空格、`FormatASS.parseStyleForASS` 的 `var warnings = warnings` 导致警告被丢弃（Java 同样）、`TimedTextObject` 的 `style.iD!!`。**卡口误报（写清楚，别当违规）**：① `methods` 会把 Java 匿名类（`new Executor(){…}`/`new Handler.Callback(){…}`）的方法名（`execute`/`handleMessage`）报成"新文件中不存在"，Kotlin 落成 SAM/lambda 后源文件无声明形态；② `tokens` 对 `when` 分支的 `92 ->` / `10 ->` 这类**数字后紧跟 `->`** 的写法判成"新侧 0"（实证字面量存在），且多行字符串/注释会被折成 `STR:"…` 碎片条目。**附录 A（既有维护负担，非本次引入）**：文件 >500 行 4 个（`FormatSCC.kt` 990 / `FormatSTL.kt` 633 / `FormatASS.kt` 574 / `FormatTTML.kt` 547，Java 侧同样 >500）；方法 >100 行 12 个（估算 ±5 行，含 `FormatSCC.parseFile` 353、`Time.getTime` 143）；同文件重复模板 1 组（`SubtitleLoader` 的三个 `loadFromXxxAsync`）。

**未验证面（诚实标注）**：真机走查未做 —— 外挂字幕加载（本地/`data:`/远端三条入口）、BOM 与编码探测、`content-disposition` 文件名解析、srt/ass/stl/ttml/scc 五种格式解析与 `#` 结尾地址、歌词模式（`lines` 合并与 `lyricCurrent` 高亮）、字幕延时、字幕缓存读写与清理、字幕刷新循环不回归。

# 7.10 M6 util 批次实测登记（2026-10-05，`util/` 45 个类，含 6 个 D9 Tier B）

**结论**：`util/` 45 个 Java 全量迁 Kotlin（8348 行），原 `.java` 全删、`util` 包 Java 清零。`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **536 用例 / 0 失败 / 0 错误 / 0 跳过（70 suite）**（与 M5/M6-subtitle 基线持平，纯语言迁移无新增用例）、**12 个 util 相关既有测试文件一行未改**、`methods` 卡口 **45/45 全部命中**、`lf` 45/45、`prune_imports` 清 4 个未使用 import。交付、记账与未验证面见 `skill/review/review-20261005-m6-util.md`。**修掉 1 条阻断级新回归（见规则 6）。**

**本里程碑现场核实出的规则（M6b / M7 / M9 / M10 照查）**：

1. **`AppContextHolder.context()` 的可空性会传导一整批文件**：Java 里它是平台类型，Kotlin 声明 `Context?` 后，`LOG`/`EpgUtil`/`ImgUtil`/`FileUtils`/`OkGoHelper`/`Jianpian`/`Thunder`/`UA` 等 20+ 处 `AppContextHolder.context().xxx` 全部要补 `!!`（复刻 Java 隐式解引用）。**动作**：迁一个"返回可空 Context 的门面"时，先 grep 它的全部调用点，别等编译报错逐个补。
2. **`String.equalsIgnoreCase` 在 Kotlin 不存在** ⇒ `equals(x, ignoreCase = true)`。`LocalIPAddress` 2 处、`RemoteTVBox` 4 处、`TxtSubscribe` 1 处、`OkProxySelector` 1 处。
3. **`HashMap.keySet()` 在 Kotlin 侧不可用**：`java.util.HashMap` 被映射成 Kotlin 的 `MutableMap`，只暴露 `keys` 属性 ⇒ 写 `checked.keys`。报错形态很迷惑（同时报 `Unresolved reference 'keySet'` 与 `Method 'iterator()' is ambiguous`，并列出 `Map.iterator()` 等一堆候选）。
4. **Kotlin 没有 `Int + String`**（`None of the following candidates is applicable`，候选只列 `plus(Byte/Short/Int/Long/Float/Double)`）⇒ 必须 `x.toString() + "…"`。`LocalIPAddress.intToIp`、`TrackMemory.videoFingerprint`、`FileUtils.formatCacheSize`（`Math.max(1L, …).toString() + "KB"`）。
5. **`VideoView<P>.setPlayerFactory(PlayerFactory<P>)` 的泛型捕获**：`VideoView<*>` 的捕获类型喂不进 `PlayerFactory<CapturedType(*)`> ⇒ 只能 `@Suppress("UNCHECKED_CAST") (videoView as VideoView<ExoPlayer>).setPlayerFactory(playerFactory)`。**注意是 app 侧的 `com.github.tvbox.osc.player.ExoPlayer`**（`ExoMediaPlayerFactory extends PlayerFactory<ExoPlayer>`），不是 doikki 的 `xyz.doikki.videoplayer.exo.ExoMediaPlayer` —— 用错会报 `actual type is 'ExoMediaPlayerFactory!', but 'PlayerFactory<ExoMediaPlayer!>!' was expected`。参数类型仍保持 `VideoView<*>?`（Java 调用方传的是 raw `MyVideoView extends VideoView`，Kotlin 视作 `VideoView<*>`）。
6. **⚠️ `KV.get(key, defaultValue)` 不能带 `T : Any` 上界（本批次唯一阻断级回归的根因）**：`fun <T : Any> get(key, defaultValue: T?): T` 的 `as T` 会被编译器插入 `Intrinsics.checkNotNull` ⇒ `KV.get(key, null)`（Java 原文 `RemoteTVBox.getAvalible()` 就这么写）从"返回 null"变成**抛 NPE**，打断 `getExistPlayerTypes()`→`getPlayersExistInfo()`→`getAvalible()` 这条链上的"单击播放器按钮 / 打开播放器参数面板 / 打开投屏面板 / 投屏播放"四条常用路径。**正解**：去掉上界写 `fun <T> get(key: String, defaultValue: T?): T` —— 上界变 `Any?` 后 unchecked cast 不再插空检查，非空默认值的调用点仍推断出非空 `T`（既有 Kotlin 调用点零改动），JVM 描述符 `<T:Ljava/lang/Object;>(Ljava/lang/String;TT;)TT;` 与 Java 逐字相同。**判据**：这类"返回非空但要能返回 null"的桥接重载，一律 `javap -p -c` 看 `getInternal` 之后有没有 `checkNotNull`。
7. **`AES.CBC/ECB` 诚实地声明 `String?` 会传导到 `ApiConfig.FindResult`**：Java 里它返回平台 `String`、解密失败时 `json = null` 并 `return null`。Kotlin 落成 `FindResult(...): String?`（内部用局部 `out`），调用点 `ConfigLoader.kt` 补 `!!` —— 因为下游 `ConfigParser.clanContentFix`/`fixContentPath` 的 `content` 是非空形参，Java 在传 null 时也走 `checkNotNullParameter` NPE → 外层 `catch (th: Throwable)` → `error`，`!!` 复刻的就是这条路径。
8. **`String` 的 `trim`/`split`/字符集在 45 个文件里逐处核对是必需动作**（本批次结果：`.trim()` 0 残留、`replaceAll(` 0、`charAt(` 0、`toLowerCase/UpperCase(` 0、`equalsIgnoreCase` 0、`new String(`/`.getBytes()` 0 无 charset、`TextUtils.isEmpty` 计数逐文件与 Java 完全一致）。Java 无 limit 的 `split(regex)` 丢尾部空串 ⇒ `RegexUtils.getPattern(p).split(s)`；Java `split(x, -1)` ⇒ Kotlin `split("字面量")`（Kotlin limit=0 保留尾部空串）。**同一文件里两种方向会并存**（`M3u8` 11 处里 9 处走 `RegexUtils`、2 处走 `split("\n")`），必须逐处判。
9. **依赖版本坑（okhttp 5.5.0）**：`HttpUrl.get(s)` / `OkHttpClient.dispatcher()` / `Dispatcher.setMaxRequestsPerHost(n)` 都是 **ERROR 级**弃用 ⇒ `s.toHttpUrl()`（`import okhttp3.HttpUrl.Companion.toHttpUrl`）/ `.dispatcher` / `.dispatcher.maxRequestsPerHost = 10`。`Headers.of(Map)` 同源（`JsonParallel` 改用 `Headers.Builder().apply{ forEach{add(k,v)} }.build()`）。另：`Response.body` 在 Kotlin 侧是**非空属性**，写 `response.body`（不带括号）。
10. **依赖版本坑（coil 3.6.3）**：`OkHttpNetworkFetcher.factory(...)` 在 Kotlin 侧是**顶层函数** `coil3.network.okhttp.OkHttpNetworkFetcherFactory`（`@JvmName("factory")`），且**只接受函数类型** `() -> Call.Factory` —— 写成 `Function0<Call.Factory>` 会报 `None of the following candidates is applicable`。`coil3.Image_androidKt.asDrawable` ⇒ `import coil3.asDrawable` + `image.asDrawable(resources)`；`ImageLoader.getMemoryCache()` ⇒ `.memoryCache`（可空，`!!` 复刻 Java 隐式解引用）。
11. **`SSLSocketFactoryCompat` 的 `static {}` 落 companion `init {}` 会被编入外层类 `<clinit>`**（`javap` 实证），与 Java `static {}` 执行时机一致 —— **静态初始化块放 companion 的 `init` 是安全的**。
12. **`@Synchronized` 与 Java `static synchronized` 的锁对象不同**（INSTANCE vs `Class`），但本批次 5 个同步方法彼此仍共用同一把锁；**前提是全仓没有外部 `synchronized(OkGoHelper.class)`**（grep 确认）。迁"静态同步方法"前先 grep 有没有外部按 Class 加锁。
13. **`@JvmField` + `@Volatile` 可以并用**（`OkGoHelper.dnsOverHttps`/`dnsHttpsList`/`myHosts` 实证编译通过且 `javap` 显示 `public static volatile`）。
14. **`static ArrayList<Integer> hisNumArray = {30,50,100}` 这类"基本类型装箱数组"**：Kotlin `arrayOf(30, 50, 100)` 编译出 `java.lang.Integer[]`（`javap` 已证），与 Java `Integer[]` 一致 —— 不要画蛇添足写 `intArrayOf`。
15. **`assert` 一律保留**（`Proxy` 3 处、`OkGoHelper.CustomDns` 1 处）；但 `assert x != null` 在形参已非空时会报"恒真"告警，登记即可。反之 Java 里"**判空出现在解引用之后**"的死判空（`AES.rightPadding` 的 `if (key != null && …)`）可以删，登记。
16. **`java.lang.String.valueOf` / `java.lang.Long.parseLong` / `java.lang.Double.parseDouble` 必须写全限定名**（Kotlin 的 `String`/`Long`/`Double` 是映射类型，没有这些静态方法）。
17. **`PlayerHelper.runExternalPlayer(6 参)` 的无限递归是既有缺陷，原样保留**（迁移 ≠ 重构，全仓 0 调用方）。同类：`PlayerHelper.getPlayerExist` 与两个 `runExternalPlayer` 的返回类型由 `java.lang.Boolean` 变原生 `boolean`（描述符变化、无调用方受影响，登记）。
18. **`Thunder.ParseTask.run()` 的 `switch` fallthrough**（Java `case 2:` 无 break ⇒ 落到 `case 3: break outerLoop`）：Kotlin `when` 没有 fallthrough，但 `when` 对 `break` 是透明的 ⇒ 在 `2 -> {}` 分支的 try/catch **之后**补一句 `break@outerLoop` 即可精确复刻（try 内命中时另有一处 `break@outerLoop`）。**迁移前逐 `switch` 判有没有漏 break。**
19. **`object` 化的纯静态类里，Java 的 `private static` 助手落成私有实例方法即可**（`private` 无外部契约）；但**包私有**成员要逐个 grep：有外部调用方就放宽为 `public`（`DefaultConfig.pickByCategories`、`PlayerHelper.isExoDecodeApplied`、`Proxy.resolveRedirectLocation`/`joinUrl`、`BootGuard.*`、`OkGoHelper.indexOfDohUrl` 都是同包测试在用），没有就收紧为 `private` 并登记（`OkGoHelper` 的 5 个、`Thunder` 的 6 个、`KVDecoder.parse`/`assignable`）。
20. **既有 Java 测试用"实例引用调静态方法"的写法会挡住 `@JvmStatic`**：`KVDecoderTest.java` 的 `decoder.coerceNumber(int.class, …)` 要求 `coerceNumber` 在 Kotlin 侧是**实例方法**（`@JvmStatic` 只生成外层类静态方法 + companion 实例方法，不会给外层类生成实例方法）⇒ 落成 public 实例方法，Java 测试一行不改仍可编译。**动作**：迁类前先 grep 测试里的 `<实例>.<方法>(` 形态。
21. **`@Throws` 只在本类有 Java 调用方时需要**（`UnicodeReader` 的 5 个构造器/`close`/`init`/`read`、`Proxy.itv`/`removeBOMFromM3U8`/`getRedirectedUrl`/`getM3U8Content`、`Utils.fixJsonVodHeader`/`jsonParse`、`SSLSocketFactoryCompat` 的 `createSocket` 系列）；纯 Kotlin 调用方的受检异常声明可以丢（登记）。

**调用点改写清单（非 util 文件，共 4 处）**：`api/ApiConfig.kt` 的 `FindResult(...)` 返回类型改 `String?` + 内部局部 `out`（1 处）；`api/ConfigLoader.kt:393` 的 `ApiConfig.FindResult(...)!!`（1 处，复刻 Java 在下游非空形参处的 NPE→外层 catch→error）；`bean/ParseBean.kt:18` 的 `DefaultConfig.checkReplaceProxy(urlValue!!)`（1 处，Java 同样 NPE）；`util/SubtitleHelper.kt` 的 `getTextSize`/`getSubtitleTextAutoSize` 形参改 `Activity?` + 内部 `ScreenUtils.getSqrt(activity!!)`（适配既有 Kotlin 调用方传 `findActivityOrNull()`）。

**审查轮（2026-10-05，结论 = 修 1 条阻断后可收尾）**：5 个只读子代理独立逐方法逐语句对账（分工：Tier B+kv 栈 8 个；`FileUtils`+`OkGoHelper`+`M3u8`；`Proxy`+thunder+live+parser 7 个；播放/图片/网络/配置 12 个；叶子小类+net/SSL 15 个），**0 条高 / 0 条中（除已修的阻断项）**；本机复跑 `lf` 45/45 + `methods` 45/45 + `tokens` 45/45 + `prune_imports` + 定向陷阱面扫描 + `javap -p -c` 双变体实证。报告落盘 `skill/review/review-20261005-m6-util.md`。记账：**阻断/本次引入 1 条（已修）** = `KV.get(key, null)` 的 NPE；**中/本次引入 1 条** = `PlayerHelper` 的 `Boolean`→`boolean` 描述符变化（0 调用方）；**低/本次引入 7 条** = `DefaultConfig.pickByCategories` 的 `==` 取代 `equals`（更宽容）、`KVDecoder.coerceNumber` static→实例 + public、`KVKeySpec` 两常量/构造器放宽、`OkGoHelper`/`Thunder` 若干包私有收紧、`UnicodeReader` final 化、`Proxy` 的日志/异常类型差异、`VideoParseRuler` 丢 `assert`；**低/既有 2 条** = `KV` KDoc 与实现本就不一致、`PlayerHelper` 无限递归；**口味 2 条** = `lowercase()` 的 locale、`SubtitleHelper` 形参放宽。

**未验证面（诚实标注）**：真机走查未做 —— 换源成功/失败与 `clan://` 解密、播放器参数面板与内核切换、投屏与 DLNA、本地/在线字幕加载与轨道记忆、m3u8 去广告全链路与 `/proxy` 四级路由、迅雷/荐片下载、启动看门狗与黑名单、原生库修复与清除缓存、KV 全链路（集合与嵌套泛型还原、doh 合并去重）。详见审查报告第四节。

# 7.11 M6 追加批次实测登记（2026-10-05，`sourcedata/SubtitleViewModel` + `data/AppDataManager`）

**结论**：M6 收尾的 2 个自有 Java 全量迁 Kotlin（共 **409 行 Java**，产出 297 行 Kotlin），原 `.java` 全删 ⇒ **`data/` 与 `sourcedata/` 两包 Java 清零**；`app/src/main/java` 由 **84 j / 269 kt → 82 j / 271 kt**。`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **536 用例 / 0 失败 / 0 错误 / 0 跳过（70 suite）**（与 M5 / M6-subtitle / M6-util 基线持平，纯语言迁移无新增用例）、**4 处调用点一行未改**、`methods` 卡口 **2/2 全部命中**（5 + 18 个成员）、`lf` 2/2、`tokens` 2/2（缺失项逐条判为「类型推断 / 属性语法 / import 精简 / 注释」四类，见下）、`prune_imports` 无需处理。交付、记账与未验证面见 `skill/review/review-20261005-m6-tail.md`。**本批次免 `javap` 卡口**（非 D9 Tier A/B；`AppDataManager.get()` 调用点全在仓内、由构建覆盖）—— 但因 M2 已留 `javap-data-*` 基线，仍跑了一次作信息性核对，见本节末尾。

**本批次现场核实出的规则（M6b / M7 照查）**：

1. **Kotlin 允许「属性与函数同名」**：Java 的 `public MutableLiveData<SubtitleData> searchResult;` + `public void searchResult(String, int)` 在 Kotlin 里可以原样并存（属性 getter = `getSearchResult()`，方法 = `searchResult(String,int)`，JVM 无冲突）。**迁移前不必为了改名而改调用点** —— 已用 kotlinc 2.4.20 实测通过。
2. **⚠️ Kotlin 接口（非 `fun interface`）不支持 Kotlin 侧的 SAM 转换**：`SubtitleLoader` 若落成普通 `interface`，`SubtitleSheets.kt` 的 `viewModel.getSubtitleUrl(item) { subtitle -> … }` 会编译失败 ⇒ 必须 `fun interface`。**Java 侧向 Kotlin 接口传 lambda 不受语言影响**（两种写法 Java 都能用）。
3. **接口形参是否放宽为可空，判据是「调用点是否可能传 null」而不是「实现方是否判空」**：`SubtitleLoader.loadSubtitle` 的 Java 声明是平台类型，`PlayContainer.java` 的实现里写了 `subtitle == null`（防御性死分支），但**全部调用点都传非空** ⇒ 保持非空更安全 —— 抽象接口方法不生成 `checkNotNullParameter`（没有方法体），保持非空既无新崩溃边界，也让 Kotlin 实现方（`SubtitleSheets.kt` 的 lambda）不加 `?.`/`!!`；改成可空反而要在调用点补 `!!`。
4. **`jsoup` 的 `Elements.last()` 在 Kotlin 侧是 `Element?`**（jsoup 有 `@Nullable`）⇒ `pages.last()!!.text()`。同理 `selectFirst()` 返回 `Element?`（本文件已按 null 分支处理）。
5. **`String.split(delimiter, limit)` 的第二位置参数是 `ignoreCase: Boolean`**，写 `split("/", 2)` 会报类型不符 ⇒ 必须具名 `split("/", limit = 2)`。Java `split(regex, 2)` 的正 limit 语义与 Kotlin `split("字面量", limit = 2)` 一致（§7.5 规则 7）。
6. **`object` 里引用自身类型、并用 `this` 赋值是合法的**：`object O { private var m: O? = null; fun init() { …; m = this } }`。这是 Java「`private static AppDataManager manager` + DCL `new AppDataManager()`」这类**纯旗标实例**的自然落法（`manager` 在本类内从不使用，只作「是否已 init」的判据）。
7. **⚠️ `@Synchronized` 不等于 Java 的 `static synchronized`**：前者锁 `INSTANCE`，后者锁 `Class`。`AppDataManager` 的 `init()` 用 `synchronized(AppDataManager.class)`、`get()` 是 `static synchronized`，两者在 Java 里共用同一把 Class 锁 ⇒ Kotlin 侧**两侧都写成显式 `synchronized(AppDataManager::class.java)`**（与 §7.10 规则 12 同源）。
8. **`object` 的私有属性编译出来仍是 `private static` 字段**（`javap` 实证：`manager`/`dbInstance`/`DB_FILE_VERSION`/`DB_NAME` 与 Java 逐字相同），不必担心"变成实例字段"带来的行为差异。
9. **`File.getParentFile()` 在 Kotlin 侧是 `File?`**，直接 `.exists()` 只报 **warning**（不报 error）⇒ 沿用 `io/FileUtils.kt:451-452` 的既有处理，保留告警登记即可，不要"顺手"改成 `!!`。
10. **`text.split` / `toLowerCase(Locale)` / `Integer.valueOf(x.trim())`** 的 Kotlin 形态：`split("/", limit = 2)` / `lowercase(Locale.ROOT)` / `x.trim { it <= ' ' }.toInt()`（前两条同 §7.5 规则 16 / §7.10 规则 8）。
11. **`OkGo` 的 `params(key, value)` 在 Kotlin 侧是平台类型参数**，传 `String?` 不需要 `!!`（`SearchLoader.kt` 已实证可传 null）；`params(key, int)` 有独立重载 ⇒ `.params("page", page)` 可直接写。
12. **`builder.readTimeout(15, TimeUnit.SECONDS)` 的整数字面量会按 Long 推断**（`RemoteTVBox.kt:143` 先例），不必写 `15L`。

**`tokens` 卡口缺失项逐条判定**（判据：只看「旧有新无」）：

| 文件 | 缺失项 | 判定 |
| --- | --- | --- |
| `AppDataManager` | `dbInstance` 10→8 | `if (dbInstance != null) dbInstance.close()` → `dbInstance?.close()`（等价；`?.` 只读一次字段，**收掉了 check-then-act 的竞态窗口**） |
| | `AppDataManager` 5→4 | 类声明 + 私有构造器 → `object` 单声明 |
| | `File`/`String` 各减 | Kotlin 类型推断去掉显式类型（`val db = …`、`val DB_NAME = "tvbox"`） |
| | `getParentFile` 2→0 | 属性语法 `db.parentFile` |
| `SubtitleViewModel` | `Document`/`Element`/`Elements`/`OkHttpClient`/`Request`/`Builder` 减 | 同上：类型推断 + import 精简（`org.jsoup.nodes.*`、`java.util.regex.Matcher`、`java.util.{ArrayList,List}` 均未使用） |
| | `getUrl`/`setUrl` 各 1→0 | Kotlin 属性语法 `subtitle.url` |
| | `IOException` 3→2 | Kotlin 不写 `throws` |
| | `SubtitleViewModel` 2→1 | 无显式构造器 |
| | `java`/`org`/`jsoup`/`regex`/`util` 减 | import 精简 |
| | 若干巨大 `STR:` 项 | **脚本噪音**：PowerShell 5.1 取 `git show` 输出按 GBK 解码，中文注释乱码导致字符串字面量正则误匹配（标识符级比对不受影响） |

**信息性 `javap` 核对（非卡口）**：`javap-data-debug-baseline.txt` 是 **M0 期快照**（仍含 M2 已删的 `CacheManager`/`RoomDataManger`、尚无 `CurrentSubscription`），故 diff 里绝大部分是 M0→今的累计差异（M2 已登记）。**本批次真正的新增差异只有 `AppDataManager` 一类**：`public class` → `public final class`；4 个静态方法加 `final`；`get()` 的 `synchronized` 修饰符从签名消失（改为方法内 `synchronized(Class)` 块，锁对象不变）；`static String dbPath()`（包私有）→ `private final String dbPath()`；新增 `public static final INSTANCE` / `$stable` / `static {}`；私有构造器与 4 个私有静态字段（`manager`/`dbInstance`/`DB_FILE_VERSION`/`DB_NAME`）**逐字不变**。4 个公开入口的**名字与描述符完全未变**（`init()V`、`get()Lcom/…/AppDataBase;`、`backup(Ljava/io/File;)Z throws IOException`、`restore(Ljava/io/File;)Z throws IOException`）。

**审查轮 1（2026-10-05，本机逐方法逐语句对账 2 个文件）**：**0 条 阻断 / 高 / 中**；记账 —— **低 / 本次引入 5 条**（`manager` 旗标实例 → `this`、`dbPath()` 包私有 → private、`dbInstance?.close()` 的读次数、局部 `url` → `downloadUrl` 2 处、`SubtitleLoader` 形参保持非空）、**低 / 既有 1 条**（`URLDecoder.decode` 弃用告警）、**口味 1 条**（`File?` 告警沿用 `FileUtils` 处理）。

**审查轮 2（2026-10-05，3 个只读子代理 + 本机复核，结论 = 达到收敛终止线）**：同样 **0 条 阻断 / 高 / 中**，且 **0 条「既有被放大」**。新增条目全部低危 —— **低 / 本次引入 2 条**（`AppContextHolder.context()!!` ×3 的 NPE 抛出点前移，同 §7.10 规则 1；`AppDataManager` 变 `final` + 新增 `INSTANCE`，`object` 化固有形态）、**低 / 既有 3 条**（302 直链 `Response` 从不 `close()` 且每次新建 `OkHttpClient` 不复用；`pagesTotal` 只在 `page == 1` 重置、换片名可能沿用旧总页数；`manager`/`dbInstance` 非 volatile 的 DCL）、**口味 1 条**（`object` 的 `INSTANCE` 与 final）。子代理实证补充：`javap -c` 确认 `init()` 与 `get()` 用的是**同一个 Class 常量**（非 INSTANCE）；`object` 的私有属性仍编译为 `private static` 字段；`TextUtils.isEmpty` 出现次数 Kotlin 9 / Java 9 逐行一致；10 处 `!!` 逐处判为复刻 Java 隐式解引用。**附录 A/B（度量盘点 + 上轮对账，含 M6-util 阻断项已修确认）落盘 `skill/review/review-20261005-m6-tail.md` 第 5 节。**

**未验证面（诚实标注）**：真机走查未做（用户红线：需授权）—— 在线字幕搜索（assrt 搜索 / zip 展开 / 分页）、记忆还原路径 `pickEpisodeSubtitle`（集号/变体/同名/单文件四条规则）、字幕直链 302 解析与 `onFailed` 回落、DB `backup`/`restore`（全库 0 调用方，仅由构建覆盖）。

# 7.12 M6b 实测登记（2026-10-05，`base`/`server`/`dlna`/`event`/`receiver` + `com.p2p` + `app/src/python/java` 共 23 个类）

**结论**：23 个 Java 类全量迁 Kotlin（6 笔 commit `4e448d5`/`89a7c3d`/`cbbd7d4`/`ce231af`/`3d3cc4e`/`7fddf70` + 11 个调用点适配），原 `.java` 全删。`:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **536 用例 / 0 失败 / 0 错误 / 0 跳过（70 suite）**（与 M5/M6 基线持平，纯语言迁移无新增用例）；`app/src/main/java` **64 Java / 289 Kotlin**（本批 -18 j / +18 kt），`app/src/python/java` **0 Java / 5 Kotlin**（**Chaquopy sourceSet Java 清零**）；`methods` 卡口 23/23、`tokens` 23/23（缺失项全为已知盲区 + 模板折串/注解串）、`lf` 34/34、`prune_imports` 清 2 个未使用 import、`tierb` 11 符号未变；**`javap -p -s` debug 与 release 双变体逐类比对 + 跨类归一化逐成员配对**（未配对仅 20 条，逐条为"私有 helper 迁 Companion / lambda 改名 / throws 消失 / 星投影 / 私有字段类型"五类，零公开成员消失）。交付、记账与未验证面见 `skill/review/review-20261005-m6b.md`。

**本里程碑现场核实出的规则（M7 / M9 / M10 照查）**：

1. **自定义 java srcDir 会被 KGP 纳入 Kotlin 编译（实测）**：`app/build.gradle.kts` 的 `sourceSets.main.java.directories += "src/python/java"` 之后，`kotlin.sourceSets.getByName("main").kotlin.srcDirs` 实测为 `[src/main/kotlin, src/main/java, src/python/java]` ⇒ **无需改构建**（计划为"若不纳入则单独一笔 commit"的预案未触发）。验证手段：Gradle init script 打印两个 sourceSet 的 srcDirs。
2. **无主构造器 + 继承 Java 类：类头写 `: Spider`（不带括号）并由某个次级构造器 `: super()` 初始化父类**。写成 `: Spider()` 会报 `Supertype initialization is impossible without a primary constructor`，并连带产生 `error_constructor` 的"Conflicting overloads"假错误（`PythonSpider` 实测）。
3. **Java 里的"隐式覆写"必须补 `override`**：`PythonSpider.init(Context, String)` 在 Java 无 `@Override` 但确实覆写 `Spider.init(Context, String)`（同名同描述符）⇒ Kotlin 必须 `override`，否则分派改变；三参 `init(Context, String, String)` 是新重载（不覆写）。
4. **Java 接口常量迁 Kotlin 后落在 `Companion`**（`RequestProcess.KEY_ACTION_*` → `RequestProcess$Companion` 的静态字段），不再挂接口本身；本批零调用点、登记接受。要在接口上保字段只能放弃 `const val`。
5. **静态同步的锁对象**：`synchronized(PyLog::class.java)` 才是 Java `static synchronized` 的 Class 锁（`@Synchronized` 锁 INSTANCE）；但 `@Synchronized` 加在**实例**方法上锁 `this`，与 Java 方法级 `synchronized` 等价（`pyLoader.clear/getSpider`）。
6. **排序 API 对照**：`Arrays.sort(arr, comparator)` → `arr.sortWith { … }`（都稳定）；`Comparator.comparing(f, nullsLast(naturalOrder()))` → `compareBy(nullsLast<String>()) { … }`（null 键两侧都"排末尾"；Java 遇 null 键会 NPE，Kotlin 更宽容）。
7. **okhttp 5 API 对照（本批实测）**：`MediaType.parse(s)` → `s.toMediaTypeOrNull()`（非法串同返 null）；`RequestBody.create(mt, bytes)` → `bytes.toRequestBody(mt)`；`HttpUrl.parse(url)` → `url.toHttpUrlOrNull()`；但 **`Request.Builder.url(String)` 可以原样保留**（它内部保留 `ws://`→`http://` 静默替换与非法 URL 抛 IAE，改 `toHttpUrl()` 会丢 ws 替换）。`Response.body` 是**非空 Kotlin 属性**，保留 Java 的 `if (body != null)` 会得恒真告警（Java 那句本就恒真）。
8. **私有 static 助手迁 companion 后，`javap` 逐类比对会把它们报成"消失"**：必须做**跨类归一化配对**（去修饰符 + 去 `throws` 后全局配对）才能把"搬进 `Companion`"与"真丢失"分开（本批 15 条属此类：`DLNACastManager.str`、`PyLog` 的 7 个助手、`SocketHttpStreamServer.ISO_8859_1`、`PythonLoader` 的两个 lambda 等）。
9. **`private val devices = ConcurrentHashMap<...>()`** 会把私有字段类型由 Java 的 `Map` 变 `ConcurrentHashMap`（描述符变化、私有、零外部影响）；**并注意**该字段的 null key 语义：Java 底层就是 `ConcurrentHashMap` ⇒ `put(null, v)` 抛 NPE，Kotlin 的 `devices[device.id!!]` 与 Java 同址同因，**不是**新崩溃边界（审查时勿只据 javap 的声明类型误判为"HashMap 容忍 null"）。
10. **Java 的 `public static` 字段一律 `@JvmField var`**：`App.burl`、`P2PClass.port`、`RemoteServer.serverPort`、`ControlManager.mContext`、`CustomWebReceiver` 的 5 个、`PyLog$TagConstant` 的 8 个；**只读字段若 Java 侧非 final 也必须 `var`**（`val` 会把字段 final 化，Java 侧重新赋值编译失败；`CustomWebReceiver.callback` 是审查轮抓到的实例）。
11. **JNI 包装类迁 Kotlin**：`private external fun` 保名保描述符（`P2PClass` 26/26 与 Java 逐字一致，`javap` 实证）；`static {}` 的 `loadLibrary` 放 companion `init` 落外层 `<clinit>`；内部类调用 private native 时会生成 `access$xxx` 合成桥（不影响 JNI 按名解析）。native 方法名/类名/包名一律禁改。
12. **`app/src/python/java` 里的类混着 `Spider` 继承与 Tier B 静态调用**（`PythonSpider extends Spider`、`LOG`/`App`），迁移口径与主包一致；`PythonLoader.getInstance().pyApp` 这类**同包按字段访问**的成员只能放宽为 `public @JvmField`（Kotlin 无包私有、`internal` 会加 `$module` 后缀）。
13. **契约面有嵌套类要 `javap -IncludeInner` 才覆盖**（`PyLog$TagConstant`、`P2PClass$init`）：本批以"编译期调用点（`PyLog.TagConstant.TAG_APP = …`）+ 手工 `javap`"补证；建议后续里程碑把 `-IncludeInner` 纳入默认流程。
14. **`String.getBytes()`/`new String(byte[])` 的平台默认字符集**本批 5 处逐一对齐（`toByteArray(Charset.defaultCharset())`；`String(bytes, Charsets.UTF_8)` 只用于显式 UTF-8 的 `/dash/` 解码）。
15. **`split` 的等价写法本批 3 处**：`RegexUtils.getPattern("\\.").split(hostname)`（Java `split("\\.")` 丢尾部空串）、`getPattern("base64,").split(content)[1]`、`split(" ", limit = 3)`（正 limit 与 Java 一致）。
16. **可空性判据仍按 §7.4 规则 11**：入参非空=新崩溃边界，本批非空入参（`ControlManager.mContext!!`、构造器参数、`setApplication/setConfig/getUrlByApi/getSpider(key)`、`CastVideo.url`）逐条核为"调用点全部传非空/已有守卫"，登记为低；`!!` 约 40 处全部复刻 Java 隐式解引用。

**登记的产物差异（debug 与 release 一致）**：`class` → `public final class`（需要被匿名子类继承的 `DLNAServiceConfiguration` / `OkHttpStreamClient.Configuration` 保留 `open`）；包私有类/成员放宽为 `public` 或收紧为 `private`（清单见审查报告 §2）；私有 static 助手迁 `Companion`；新增 `Companion`/`$stable`/`access$*` 桥/`DefaultConstructorMarker`；`Object` 桥接方法的 `protected`→`public`（泛型覆盖产物）；`throws` 从 4 处方法消失（无 Java 调用方）；泛型签名由裸类型变星投影（`StreamClient<*>`/`StreamServer<*>`/`Map<*,*>`）。

**审查轮（2026-10-05，结论 = 可收尾）**：6 个只读子代理独立逐方法复核 + 本机 `git show 9010c69:` 逐条闭环（子代理无 shell，其"待核对"项全部由本机结论），**0 条 阻断 / 高 / 中**；修复 2 条低级本次引入项（`RemoteServer.getCurrentEpisodeIndex` 恢复 `current != null` 防御、`CustomWebReceiver.callback` 改回非 final 字段）；登记项 = 9 条低×本次引入 + 5 条低×既有 + 4 条口味差异（逐条见 `skill/review/review-20261005-m6b.md` §2）。**卡口口径补充**：`methods` 的 5 条"未命中"全部是属性化 getter/匿名类变 lambda（`CastDevice`/`CastVideo` 的 3+3 个 getter、`RemoteServer.compare`、`BaseActivity` 的 4 个）——写清楚，别当违规。

**未验证面（诚实标注）**：真机走查未做 —— 启动、局域网服务地址/端口与五条路由、DLNA 投屏、广播接收、P2P 原生库、py 源加载（详见审查报告 §6）。

# 7.13 M7a 实测登记（2026-10-05，播放栈自研替换：M7-0 + 新内核适配层 `osc.player.engine`）

**结论**：M7-0 删 `player/build.gradle.kts` 的 `api(libs.dkplayer.ui)`（死依赖，全库源码零 `xyz.doikki.videocontroller` 类引用；toml 项留 M10 删）；M7a 新建 **`com.github.tvbox.osc.player.engine`** 包 9 个 Kotlin 文件 = 移植 4 件（`OkHttpDataSource`/`HlsErrorHandlingPolicy`/`MediaSources`/共享缓存委派 `PlayerCache`）+ 装配 2 件（`EngineRenderersFactory`/`PlayerEngine`）+ 策略 3 件（`SourcePolicy`/`CodecPreferences`/`NetworkSpeed`），另 4 个单测文件。`:app:assembleDebug` + `:app:assembleRelease` 绿（**该次 release 为许可前套跑，此后一律按 §2 构建口径：未经用户许可不跑**）；`:app:testDebugUnitTest` **569 用例 / 0 失败 / 0 错误 / 0 跳过（74 suite）**（536 基线 + 33 新增）。**M7a 不接 UI/不接调用方（双栈并存，doikki 仍是回退面）**，新旧共享状态收口 2 处：旧 `ExoPlayer.setPreferSoftwareDecode/isPreferSoftwareDecode` 改读写 `CodecPreferences`（选择器同源）、app 侧 `PlayerCache` 反向委派旧 `ExoMediaSourceHelper`（模块依赖方向 app→player）。独立子代理逐类对照复核（18 条结论）：**1 阻断 + 1 高（同根因）+ 6 中低全部已修**，2 条登记（私改公为单测、`usesExoSelector` 日志恒真）。

**本切片现场核实出的规则（M7b–M7f 照查）**：

1. **Kotlin override Java 方法的参数必须声明为非空**（平台类型不许写 `?`）：`DefaultRenderersFactory.buildVideoRenderers/buildTextRenderers` 的 `Handler`/`VideoRendererEventListener`/`TextOutput`/`Looper` 写可空会直接报 `NOTHING_TO_OVERRIDE`；`javap -c` 实证三个 override 方法入口均插了 `Intrinsics.checkNotNullParameter`，但 media3 上游实参恒非空（`ExoPlayerImpl` 传 `new Handler(builder.looper)` 与 `componentListener`，`buildTextRenderers` 的 looper = `eventHandler.getLooper()`）⇒ **不可达、不构成崩溃面**（升级 media3 时按本条复核上游传值）。
2. **Kotlin 不能直接访问"继承来的 Java 静态常量"**：`MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY` 必须写 `DefaultRenderersFactory.XXX`。
3. **`val x = f().also { … 读 x … }` 陷阱（本轮抓到阻断级真缺陷）**：`also` 块先于赋值执行 ⇒ 块内 `internalPlayer` 仍是 null，`applyPlaybackParameters()`/`disableFrameRateMatching()`/`applyFrameRateTracking()` 三处全部空转（隧道/AAC 偏好/帧率匹配静默失效）。**正解 = 先 `val exo = createPlayer(); internalPlayer = exo;` 再逐项下发**（旧 doikki `super.initPlayer()` 先建实例的顺序）。
4. **`C.LENGTH_UNSET` 是 Int**：Kotlin 里与 `Long` 比较/赋值要显式 `.toLong()`（`dataSpec.length != C.LENGTH_UNSET.toLong()`），Java 的隐式提升不再成立。
5. **`InvocationHandler.invoke` 的 `args` 是 `Array<out Any>?`**（不可写）：要改元素需 `@Suppress("UNCHECKED_CAST") (args as Array<Any>).clone()`；`method.invoke(renderer)` 与 `method.invoke(renderer, *invokeArgs)` 必须分两支（Kotlin 不能把 null 数组展开成"无参调用"）。
6. **JVM 单测（`isReturnDefaultValues`）里 `Uri`/`Bundle`/`TextUtils` 全是桩**：依赖 `Uri.parse` 的路径判定（`/live.php`、`/live/`）、`TextUtils.isEmpty` 的空键过滤、Bundle 序列化（`buildMediaItem`/`getHeadersFrom`）**都不可 JVM 测**；`PlaybackException(message, cause, code, Bundle())`（4 参）可构造（3 参构造走 `Bundle.EMPTY` 桩 null 会被内部 `checkNotNull` 拒绝）；`LoadErrorInfo` 链上要 `DataSpec`/`Uri` ⇒ HLS 策略的重试延迟/fallback 分支同样不可 JVM 测（只测 `isChunkError` 与重试次数）。
7. **跨模块依赖方向**：`player` 模块不能引用 app 的类（`com.github.tvbox.osc.*`）。共享缓存的实现只能留在 `ExoMediaSourceHelper`，app 侧 `PlayerCache` 做反向委派；**M10 拆除 player 模块时把实现整体搬进 `PlayerCache`（唯一改动点）**。
8. **`setDisplay` 的自动补发是承重行为**：旧 `ExoPlayer.setDisplay` 在 `super.setDisplay` 后按 `holder.getSurfaceFrame()` 补发 `MSG_SET_VIDEO_OUTPUT_RESOLUTION`（SurfaceView 路径唯一补发点，漏发 = 效果管线黑屏）；新层必须照做，且 `reset()` 会把 `playWhenReady` 停到 false —— 复用内核起播须补 `setOptions()`（旧链路 `reset → setOptions → prepare`）。
9. **配置默认值要对齐"旧缺键口径"而非 media3 默认**：`bufferTimes` 缺省 = `HawkConfig.BUFFER_TIMES_DEFAULT`（3），不是 1（写 1 会让 M7c 漏传时缓冲缩到 1/3）。
10. **新层遗漏项已补**：`getTcpSpeed()`（旧 `PlayerUtils.getNetSpeed`，OSD 网速）→ `NetworkSpeed`（TrafficStats 差值法，脱离 doikki 依赖）；`MediaSources` 构造即归一 `applicationContext`（旧单例的防泄漏语义）；client 未注入时回落 `OkGoHelper.getItvClient()`（旧栈全局注入，避免静默走裸 client 丢 DoH/hosts/代理/SSL）。

**M7a 未承接 / M7b–M7f 待接线清单（登记，防丢；② 之后为第二轮审查补录 2026-10-05）**：① 渲染宿主（Surface/Texture 双模式、`setVideoSurface`/`setDisplay` 调用、尺寸/比例/挖孔、音频焦点、进度保存）；② 状态机与事件面（`onPrepared`/`RENDERING_START`/`BUFFERING_*`/completion/error、`videoSizeListener` 与 `ErrorListener` 是现成钩子）；③ 接线面 = `PlayerEngineConfig` 全字段 + `setDataSource(..., isLive = KV<PLAYER_IS_LIVE>)` + `setContentKey`/`setUseDiskCache`/`setStartPosition`（旧 `MyVideoView` 注入路径）；④ `PictureEffects` 接线（`onPrepare`/`onPlayerReleased`，参数类型待在 M7c 改为新引擎类型；`PictureEffects` 现仍面向旧 `ExoPlayer`）；⑤ `OkGoHelper.initExoOkHttpClient` 的 `setOkClient` 注入点（M7c/M7f 改指向 `MediaSources`）；⑥ `FileUtils` 清缓存目录名与共享缓存实现搬迁（M10）；⑦ **旋转事件**：旧 `MEDIA_INFO_VIDEO_ROTATION_CHANGED`（`ExoMediaPlayer` 发、`VideoView` 驱动 `RenderView.setVideoRotation`）的等价物 = `videoSizeListener` 第 3 参（rotation 度），**M7b 桥接必须回发 onInfo(10001)**，漏了则旋转源静默不转；⑧ **`keepRenderViewOnReset` 必须为 true**（旧 `ExoMediaPlayer` 覆写；宿主 `VideoView.replay` 据此选复用分支，M7b 桥接固定返回 true）；⑨ **调用点改写清单（4 处 `instanceof/as ExoPlayer`，漏改即静默降级）**：`ui/player/TrackSelectorDelegate`（选轨菜单）、`ui/player/PlayContainer`（OSD/参数面板）、`player/controller/ComposeVideoController.kt`（解码名/帧率/丢帧/重缓冲/隧道）、`player/PlaybackRetryDelegate`（重试阶梯按 `lastErrorKind` 1/2 分支）；⑩ M7b 须**新增 `AbstractPlayer` 子类桥**（`VideoView.mMediaPlayer` 类型为 `AbstractPlayer`，新引擎不是它）；⑪ `setDataSource(AssetFileDescriptor)`（旧为空实现）无对应，登记即可；⑫ 注入面收窄：旧 `setTrackSelector/setRenderersFactory/setLoadControl` 中 **TrackSelector 不再可注入**（新层内部建 `DefaultTrackSelector`）；⑬ `OkHttpDataSource` 便捷构造由 3 个 public 收窄为 `Factory`（仓内零调用方，产出契约收窄）；⑭ 预载侧旧 helper 三 API 的切换点（`PreloadManagerHolder` 的 `buildPreloadMediaItem`/`createDataSourceFactory`/`getHeadersFrom`，与 ⑤ 同批）；⑮ 单测缺口登记：`getRetryDelayMsFor`(500ms)/`getFallbackSelectionFor`(不 fallback) 承重分支与 `retriedAsHls` 单次重试在 JVM 测不可达（`LoadErrorInfo` 链依赖 `Uri`），入 M7b 真机走查清单（后者可抽纯函数补测）。

**审查轮（第二轮，2026-10-05，结论 = 可收尾）**：两个独立只读子代理（A = 逐项复审首轮 10 条修复；B = 独立收尾判据审查：公开契约面/语义/单测质量/代码卫生）+ 本机 `javap` 闭环。**0 阻断 / 0 高**：首轮 10 条修复 9 条确证落地、1 条（`@Volatile` 清单）本轮补齐；新层 9 文件逐方法对读未发现功能性偏差。本轮修订（全部为低风险口径对齐，无行为变更）：① `@Volatile` 齐平旧栈 10 个 volatile 字段（补 `frameRateWindowStartMs`，补旧 `AbstractPlayer` 的 `startPositionMs`/`startPositionApplied`）；② 删恒假判空 `tracks == null`（非空形参）；③ `MediaSources` KDoc 与 client 回落链实现对齐；④ 删 `PlayerCache` 两个无引用死常量（容量/目录名真值源保留在旧实现，避免多处声明）；⑤ `isLocalProxyUrl` 三处重复实现收敛 —— `PlayerHelper.isLocalProxyUrl` 改为委派 `SourcePolicy`（旧调用点零行为变化），`PreloadCoordinator` 的私有实现登记留 M7f；⑥ `PlayerEngine` 6 处 `lowercase()/uppercase()` 对齐旧 Java 默认 locale（§7.12 规则 14 口径，`Locale.getDefault()`）；⑦ 单测加判别力声明并钉跨层契约值（`ERROR_KIND_*` = 0/1/2；HLS 切片档 3 与 media3 默认 3 数值巧合 ⇒ 判别力只在 progressive-live=6 一条）。**登记未改**（低）：单测对 `Uri`/`Bundle` 依赖分支不可覆盖（规则 6）、`usesExoSelector` 日志恒真、`OkHttpDataSource` 整类无 JVM 单测（入 M7d 真机走查）。

**未验证面（诚实标注）**：M7a 不接 UI ⇒ 真机走查无从执行，门 = `:app:assembleDebug` 构建 + 单测 + 逐类对照复核（release 侧 R8/keep 验证留待用户许可时补跑，见 §2 构建口径）；新栈的起播/渲染/效果/字幕/轨道全部行为留待 M7b/M7c 切换后随 `avbox-playback-service-spec.md` §4 清单走查。

# 7.14 M7b 实测登记（2026-10-06，播放栈自研替换：内核桥切换 + 渲染宿主 + 状态机）

**结论**：M7b 把 app 侧内核换成"桥 → `PlayerEngine`"、渲染视图换成 app 侧双模式宿主、新增新栈状态机与音频焦点组件，并做最小接线。`:app:assembleDebug` 绿；`:app:testDebugUnitTest` **603 用例 / 0 失败 / 0 错误 / 0 跳过（77 suite）**（570 基线 + 33：`RenderMeasureTest` 13 / `PlaybackStateMachineTest` 10 / `AudioFocusActionsTest` 10）；改动文件全 LF。**切换口径 = "桥保留类名与对外方法面"**：`com.github.tvbox.osc.player.ExoPlayer` 由 Java 重写为 Kotlin（`extends AbstractPlayer` 直连 `PlayerEngine`，不再继承 doikki `ExoMediaPlayer`）⇒ 全部调用点（`MyVideoView`/`PlayContainer`/`TrackSelectorDelegate`/`ComposeVideoController`/`PlaybackRetryDelegate`/`MusicSessionDelegate`/`PictureEffects`/`PlayerHelper`）**零签名改动**，由 `:app:assembleDebug`（Java 侧匿名类、`instanceof`/强转）与单测兜底。

**交付物（新文件 9 + 改动点 7；另 3 个单测文件）**：
- 桥：`player/ExoPlayer.kt`（重写，删 `ExoPlayer.java`）——AbstractPlayer 全契约映射 + 回调映射 + app 扩展面 27 项 + 静态面（`ERROR_KIND_*` const / `setPreferSoftwareDecode`/`isPreferSoftwareDecode`）+ 嵌套 `OnCuesListener`（`@JvmSuppressWildcards` 保 Java 匿名实现）。
- 状态机：`player/state/PlayState.kt`（`PlayState` 枚举 + `PlaybackStateMachine`，StateFlow 输出；迁移表与暂停记忆逐条对齐旧 `VideoView.STATE_*`；M7b 期间无读取方）。
- 渲染宿主：`player/host/`（`RenderMeasure` / `TextureRenderHost` / `EngineSurfaceRenderView`（+Factory）/ `EngineTextureRenderView`（+Factory））。
- 音频焦点：`player/host/PlayerAudioFocus.kt`（`AudioFocusTarget` + 纯逻辑 `AudioFocusActions` + Android 壳 `PlayerAudioFocus`；**M7b 未接线**，生效实现仍是旧 `VideoView.AudioFocusHelper`）。
- 引擎侧增补（`engine/PlayerEngine.kt`）：`playbackStateListener` / `retryAsHlsListener` / `prepare()` 返回 `Boolean`。
- 接线：`PlayerHelper.kt`（渲染工厂注入换新实现）、`MyVideoView.java`（`TextureRenderHost` 判定 ×2 + `switchRenderToTexture` 换新工厂）、`PlayContainerViewBridge.java` 与 `MusicPlayerActivity.kt`（纯音频强制 Texture 换新工厂）、`PlaybackEngine.java`（`createPlayerView` 按 `HawkConfig.PLAY_RENDER` 设初始渲染宿主）、`PictureEffects.kt`（`isPlaying()`/`isPictureEffectsActive()` 改函数语法）。

**本切片现场核实出的规则（M7c–M7f 照查）**：
1. **Kotlin 桥覆写 Java `AbstractPlayer` 时 `isPlaying/getCurrentPosition/getDuration/getBufferedPercentage/getTcpSpeed/getSpeed/setSpeed` 必须写函数**（写 `override val/var` 报 "overrides nothing"）；而**自有的 app 扩展成员按调用点语法选形态**：Kotlin 调用点用属性语法（`.selectedVideoFormat`/`.isTunnelingEnabled`）就必须声明 `val`（生成 `getXxx()`/`isXxx()`，Java 调用点不受影响）；`isPictureEffectsActive`/`isPictureHdrSource` 有调用点用函数语法 ⇒ 声明 `fun`，并把 `PictureEffects` 两处属性语法改函数。
2. **`AbstractPlayer.getStartPosition()` 是 protected final + `setStartPosition` public** ⇒ 桥不覆写 setter（会被判成合成属性），改用**读合成属性** `startPosition`（与旧 `ExoMediaPlayer.prepareAsync` 的读取等价）。
3. **`@JvmSuppressWildcards` 是 Kotlin 侧 `fun interface OnCuesListener { fun onCues(cues: List<Cue>) }` 让 Java 匿名类能覆写的必要条件**（否则生成 `List<? extends Cue>`，`PlayContainer.java` 的匿名实现不构成 override）。
4. **宿主替换的兼容锚点**：`EngineTextureRenderViewFactory` 刻意 `extends TextureRenderViewFactory`（保住 `MyVideoView.factoryRenderType()`/`ensureRenderViewMatchesConfig()` 的 `instanceof` 判定）；新宿主的交面钩子改用 app 侧 `TextureRenderHost` 接口判定；`EngineSurfaceRenderView` 是 `SurfaceView` 子类（保住 `isSurfaceRenderActive()`）。
5. **引擎无页面起播必须自带渲染宿主**（`PlaybackEngine.createPlayerView` 设初始工厂）：否则落到 dooki 默认 Texture 工厂 ⇒ 新宿主的输出缓冲尺寸/交面钩子静默失效（历史上开调色黑屏的根因面）。
6. **`PlayerEngine.prepare(): Boolean`** 供桥复刻旧 doikki 的"无源不下发也不进等待 onPrepared"早退语义；`retryAsHlsListener` 在 HLS 源真正建好后触发；桥内 `pendingSpeed` 复刻旧 `mSpeedPlaybackParameters` 的"重建内核后回灌"。
7. 旧 app `render/SurfaceRenderView.java` + `SurfaceRenderViewFactory.java` 已成**零引用死代码**（回退面 = git 历史 + M10 删除清单）；实际回退 = revert 本切片。

**复核轮（2026-10-06，2 个独立只读子代理 + 本机闭环）**：A = 桥 vs 旧 `ExoPlayer`/`ExoMediaPlayer` 逐方法对照（24 个契约方法、回调映射、配置来源、扩展面 27 项、静态面、调用点扫描）；B = 渲染宿主/状态机/接线对照（测量算法逐分支、双宿主逐成员、工厂继承判定、钩子链、状态迁移表、接线副作用、旧类型残留）。**0 阻断 / 0 高**；修复 1 中 2 低：初始渲染工厂缺口（见规则 5）、`MATCH_PARENT`×旋转 spec 交换（对齐旧实现，补单测）、`prepareAsync` 早退 + `pendingSpeed` + `retryAsHlsListener` 后移。

**登记项（M7b 引入但当前无外部影响 / M7c 必办）**：
① 桥不再继承 `ExoMediaPlayer` ⇒ 丢继承面 `setPlaybackLooper`/`setTrackSelector`/`setRenderersFactory`/`setLoadControl` 与受保护字段（**全库零调用点**，构建实证）；② 配置读取时机：`BUFFER_TIMES`/`EXO_VIDEO_DYNAMIC_SCHEDULING` 由构造器改为 `initPlayer`（同实例内不会变化 ⇒ 现状等价）；③ `enableLog` 未下发（旧经 `VideoViewManager` 开关也从未生效，全库无调用）；④ 状态机 `onStartAborted` 无接线（`STATE_START_ABORT` 不可达）、`PREPARED` 不可观测（桥在同一 READY 分派里连发 `onPrepared`+`onRenderingStart`，StateFlow 合并）；⑤ 状态机**未承接副作用**（音频焦点/`setKeepScreenOn`/进度保存与清零）—— M7b 期间这些仍由旧 `VideoView` 承担，M7c 直读前必须落地（焦点组件已建待接线）；⑥ 桥 `stop()` 也投状态机 ⇒ `MyVideoView.clearVideoFrame` 的间接 stop 会让状态机瞬时翻 IDLE（M7b 无读取方，M7c 收口）；⑦ `TrackSelector` 不可注入、`setDataSource(AssetFileDescriptor)` 空实现为计划清单 ⑪⑫ 的承接（已在桥内落实）。

**§7.13 待接线清单 ①–⑮ 状态更新**：① 渲染宿主 ✅（双模式 + 尺寸/比例/旋转 + 输出尺寸钩子；**挖孔仍由控制器层承接、音频焦点组件未接线**）；② 状态机 ✅（桥驱动，读取面待 M7c）；⑦ 旋转 `onInfo(10001)` ✅；⑧ `keepRenderViewOnReset=true` ✅；⑨ 4 处 `as ExoPlayer` 调用点**无需改写**（桥保留类名与类型面，该清单作废）；⑩ `AbstractPlayer` 桥 ✅；⑪（AFD 空实现）✅；⑫（TrackSelector 不可注入）✅。其余（③ 接线面、④ `PictureEffects` 参数类型、⑤ `OkGoHelper` 注入点、⑥ 缓存实现搬迁、⑬⑭⑮）仍待 M7c。

**未验证面（诚实标注）**：真机走查未做 —— 按 `avbox-playback-service-spec.md` §4 清单从"详情页预览态"开始（起播/首帧/暂停记忆/旋转/双渲染模式/纯音频强制 Texture/输出分辨率与效果链/错误与 HLS 重试/自动软解/切集复用），再扩全屏/直播/音乐。

# 7.15 M7c 实测登记（2026-10-06，播放栈自研替换：点播全链切换 9 片）

**结论**：`player/` 包 36 Java → 只剩 2 个（`PreloadCoordinator`/`PreloadManagerHolder` 留 M7f）；app 内 `com.github.tvbox.osc.player` **82 Kotlin / 2 Java（18091 / 657 行）**，`app/src/main/java` 由 M6b 的 **64 Java / 289 Kotlin** 降到 **30 Java / 338 Kotlin**。两阶段执行：语言迁移 8 片（逻辑零改动）→ 栈收口 1 片（`observeForever` → `flow`）。每片 `:app:assembleDebug` 绿、前 8 片 `:app:testDebugUnitTest` **603 用例 / 0 失败 / 0 错误 / 0 跳过**（与 M7b 基线持平）、收口片 **604**（净 +1）；改动文件全 LF；`tokens` 卡口抽查（「旧有新无」逐条归入已知盲区）。提交清单、复核账目与未验证面见 `skill/review/refactor-plan-20261005.md` §M7 的 M7c 交付行。

**本切片现场核实出的规则（M7d–M7f 照查）**：

1. **Kotlin 的 `or`/`and` 是中缀函数，折行必须把运算符留在行末**：`A or` ⏎ `B or` ⏎ `C` 会被解析成两条语句并报 `Unresolved reference 'or'`（`PlaybackService` 组合 `PlaybackStateCompat.ACTION_*` 踩到）。
2. **Service 子类里"继承来的 Java 静态常量"要写全限定**：`Service.START_NOT_STICKY`、`Context.NOTIFICATION_SERVICE`/`POWER_SERVICE`/`WIFI_SERVICE`（与规则 §7.13-2 同源）。
3. **Java 静态面迁 `companion object` + `@JvmStatic` 时必须回查 Java 调用点**：`PlaybackService` 9 个入口逐一比对 `PlayContainer.java` 后落 `@JvmStatic`；`onEngineReleased` 由包私有放宽为 public；私有 static 助手（`setLiveFlag`/`prewarmEnabled`/`startHost`/`releaseEngine`）留 companion private（Kotlin 侧调用不受影响）。
4. **可空性放宽必须按"调用点实参"定，只放宽不收紧**：`PlaybackService.updateSession(title/subtitle/artwork)` → `String?`（来源含 `host.vod()!!.pic`）；`ProgressManager.saveProgress/getSavedProgress(url)` → `String?`（dooki 传 `mUrl`，release 路径会置 null，收紧即新增崩溃）；`PlaybackController.getCastUrl` 返回 `String?`（`CastVideo(url: String)` 调用点补 `?: url`）；`PlaybackController.initParse(playUrl, url)`、`MusicSessionDelegate.Host.playUrl(headers)`、`PlaybackViewBridge.playM3u8(headers, gen)`/`playExternalPlayer(subtitle, headers)`/`newSniffWebView()`/`buildPreloadSnapshot()` 同则。
5. **`tokens` 的 getter 属性化盲区**：`getMessage`→`message`、`getClass`→`javaClass`、`getSimpleName`→`simpleName`、`getPackageName`→`packageName`、`getSessionToken`→`sessionToken`、`getApplicationContext`→`applicationContext`、`getAction`→`action`、`Image_androidKt.toBitmap`→`toBitmap`（扩展函数）都计入「旧有新无」，不算违规；`NonNull`/`Nullable`/`SuppressLint` 等注解 import 消失同理。
6. **"Java 逐次重读不得缓存" 适用于数据读取点（不只是桥）**：`PlaybackController.play()/goPlayUrl()` 里 `vod()`、`vs.url` 的多次重读必须逐次复刻旧形态（`vod()!!.x`），**不能**收敛成入口处的局部 val —— 桥回调（`view.stopOtherPlayers()`/`releasePlayer()`/`clearVideoFrame()`）与 `Thunder.play`/`preload.consumeResult` 之间都可能重新进入会话边界。反之旧 Java 自己缓存成局部量的（`PlaybackRetryDelegate.trySoftDecodeFallback`/`tryNextLine`）照缓存。
7. **字段初始化必须早于 `init {}` 块**：Kotlin 属性初始化器与 `init` 按文本顺序执行，而 Java 的字段初始化器一律早于构造器体 —— 把 `idleRelease` 这类"构造期就可能被方法用到"的字段写在 `init` 之后，构造期会读到 null（`PlaybackEngine` 复核轮的中项）。
8. **Flow 收口的 Scope 与派发写法（本片定型）**：收集域 = 拥有者对象自身（`private var collectJob = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { flow.collect { … } }`，`release`/`destroy` 里 `cancel`）；主线程派发靠 `Dispatchers.Main.immediate`（`tryEmit` 在发射线程恢复收集者）。**"观察者已注册"这类语义要显式复刻**（`deliver` 用 `collectJob?.isActive` 守卫）。UI 侧对通道的订阅用 `lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` + 通道 `replay = 1`，等价旧 `observe(lifecycleOwner)` 的 STARTED 期订阅与回放。
9. **`SourceChannel` 现只有 `flow` + `postValue`/`setValue`**（均 `tryEmit`）：新增消费方不要再引 LiveData；`SourceChannelTest` 锁住三条语义（粘性回放 / null 合法载荷 / 活跃收集者不合并 + 溢出丢最旧 + 跨线程投递）。与 LiveData 的差异（不合并）是**有意行为**，写进走查清单。

**复核轮（2026-10-06）**：M7c-5b（`PlaybackController`）、M7c-6a（`PlaybackEngine`）、M7c-6b（`PlaybackService` 迁移）各跑一轮独立只读子代理逐类对照 —— 5b：0 阻断 / 0 高 / 2 中（`vod()`、`vs.url` 的逐次重读被缓存，按规则 6 改回）/ 10 低（`@JvmStatic`、`@Throws` 缺失已补，可空性收紧 2 条不可达）；6a：0 阻断 / 0 高 / 1 中（规则 7，已改回）/ 3 低（`!!` 复刻 Java 隐式解引用、`?:` 死分支、ProgressManager 入参放宽）；6b：0 阻断 / 0 高 / 1 中（`updateSession` 的 `context` 收紧，已按规则 4 放宽为 `Context?`）+ 3 低（`stopSession` 同项、KDoc 链接写法、`onEngineReleased` KDoc 与"不 stopSelf"矛盾，均已修）。三轮均无"新引入的功能性偏差"。

**未验证面（诚实标注）**：真机走查未做 —— 见计划档 M7c 未验证面（含本次收口引入的 3 条可观测差异）。

# 7.16 M7d 实测登记（2026-10-06，播放栈自研替换：直播/音乐/DLNA/第三方出口读取面切换 5 片）

**结论**：把「直播 / 音乐 / DLNA / 第三方出口」四链路的**状态读取面**从 doikki 基类 int（`VideoView.STATE_*`）切到新栈 `PlayState`，并接通音乐页外部播放器出口。新增读口 = `MyVideoView.playState`（读桥内状态机）+ `PlaybackViewBridge.playState()` + `PlayState.fromLegacy(int)`（仅作旧通知参数的适配，M7f 删）。**DLNA 零改动**（`dlna/**` 与 `CastSheet.kt` 对 doikki 与状态面 0 引用，仅回归核对）。提交：**`d68cd61`**（状态读口 + 调度层：`PlaybackController.isStartedPlayState/isPlaybackStarted/isIdleKernelReusable`、`PlaybackRetryDelegate`、`DanmuLoadController`、`MusicSessionDelegate`、`PlaybackEngine` 监听传参、`MyVideoView`/`PlayContainerViewBridge`/`HeadlessView` 三处读口实现 + `PlayStateTest` 3 例）；**`7e8a4e3`**（直播链：`LivePlayActivity`/`LivePlayViewModel`/`LiveScreens`/`LiveOverlayController` + `ComposeLiveController.LiveControlListener` 签名改 `PlayState`；`ExoMediaSourceHelper.HEADER_FORMAT` → `MediaSources.HEADER_FORMAT`；`PlayerUtils.safeTimeMs` → 新 `PlaybackTimes`，+3 例）；**`2e1cd36`**（音乐链 + 第三方出口 + 详情页：`MusicPlayerActivity` 状态监听/positionTick、`MusicPageBridge.playExternalPlayer` 接通、`DetailActivity.musicPlaybackDetected`）；**`b408675`**（收口：`MyVideoView.clearVideoFrame` 改走桥 `stopForFrameClear()`，不投状态机 —— 复刻旧"直调内核 stop 不改基类状态"）；**`d708461`**（复核修复：引擎建视图显式装桥工厂、点播桥 subtitle 空兜底、`PlayerHelper.runExternalPlayer` 六参自递归、`fromLegacy` 去 `@JvmStatic`、补枚举成员表断言）。每片 `:app:assembleDebug` 绿；`:app:testDebugUnitTest` 604 → 607 → 610 → **611 用例 / 0 失败 / 0 错误 / 0 跳过（79 suite）**；改动文件全 LF。

**本切片现场核实出的规则（M7e–M7f 照查）**：

1. **枚举 `when` 语句也要穷尽**：旧 int 的 `when` 作语句可不写 else，换 `PlayState` 枚举后 K2 强制穷尽 —— `MusicPlayerActivity` 状态监听补 `PlayState.IDLE, PlayState.START_ABORT -> {}`（等价旧 int 的"这两态不动作"）。
2. **通知回调内读状态机与通知参数等价（前提必须成立）**：桥对每条命令/事件都是"先投状态机、后调 listener"，且基类 `setPlayState` 只在状态迁移时广播 ⇒ 回调内 `view.playState` == `fromLegacy(回调参数)`。`fromLegacy` 只用于"把旧通知参数转枚举比较"，**读值一律走 `playState`**。
3. **直调内核命令会污染状态机**（M7b 登记⑥的收口）：`clearVideoFrame` 是"复用换集前的盖黑帧"、旧实现直调 `mMediaPlayer.stop()` 本就不改基类状态；桥 `stop()` 会投 IDLE ⇒ 专用 `ExoPlayer.stopForFrameClear()`（只停引擎）。凡新增"静默停内核"调用点必须走该口，否则 `isIdleKernelReusable`/`canReusePlayer` 会把仍有内容的内核误判成空闲。
4. **`MyVideoView.playState` 依赖"内核必为桥"**：`as? ExoPlayer` 不成立时静默退 IDLE。`PlaybackEngine.createPlayerView` 必须显式 `setPlayerFactory(ExoMediaPlayerFactory.create())`（无页面起播不经 `PlayerHelper.updateCfg`，沿用 dooki 默认工厂会拿到旧内核 ⇒ 读取面全面失真）。
5. **可空性放宽的配套兜底**：`PlaybackViewBridge.playExternalPlayer(subtitle?)` 放宽后，Java 桥直传非空 Kotlin 形参 = NPE 面（`PlayerHelper.runExternalPlayer(subtitle: String)`），补 `subtitle == null ? "" : subtitle`；新增实现（音乐页）用 `subtitle.orEmpty()`。**只放宽不收紧时，必须逐调用点核兜底**。
6. **`@JvmStatic` 只给有 Java 调用点的 companion 成员**：`PlayState.fromLegacy` 三个调用点全在 Kotlin ⇒ 不加（§7.15-3 的反向口径）。
7. **无 else 的枚举 `when` 会被新增成员静默走过**：单测补 `PlayState` 成员表断言（`enumMembersArePinned`）钉住顺序与数量。
8. **`-1` 的两种语义要分清**：`fromLegacy(-1)` = ERROR（通知参数的负一）；读值口无 -1（无播放器/无内核 → `PlayState.IDLE`）。

**复核轮（2026-10-06，2 个独立只读子代理 + 本机闭环）**：A = 状态机 vs doikki 基类逐调用点配对（14 个 `setPlayState` 调用点 × 桥投递点）+ 15 个场景逐条 + `fromLegacy`/`-1` 二义 + 漏改面；B = 改动面审查（语义等价/可空性/覆盖完整/新文件质量/规范/潜在回归/登记缺口）。**0 阻断 / 0 高**；中 3 + 低 5：① 引擎未显式装桥工厂（"内核非桥"时读取面降级为 IDLE，已修 `d708461`）；② 点播桥 subtitle 直传非空形参的 NPE 面（已修）；③ `PlayerHelper.runExternalPlayer` 六参重载自递归（**非本轮引入**、当前零调用点，已修）；低项处置：`fromLegacy` 去 `@JvmStatic`（已修）、补枚举成员表断言（已修）、`PlaybackTimes` KDoc 微调（已修）；其余保留登记（见下）。

**登记项（M7e/M7f 接续）**：
① `PlaybackViewBridge.currentPlayState()` 已是**零调用读口**（仍在用旧 int 的三处直接打在 `mVideoView`/`host.player()` 上：`PlayContainer.java:217/1159`、`TrackSelectorDelegate.java:86/97`）—— 保留至 M7e 删 `ui/player` 4 Java 时一并删接口成员与 `HeadlessView` override（删除前新增读取一律走 `playState()`）。**【已收口 2026-10-07】**接口成员、`HeadlessView`/`PlayContainerViewBridge` override 与全部读点已删（读点改 `playState()` 枚举；见 `skill/review/refactor-plan-20261007.md` 步 3/4）。
② `PlayState.fromLegacy` 是**迁移期适配**（消费点 3 处：`PlaybackEngine` 监听、`MusicPlayerActivity` 监听、`ComposeLiveController` 转发），doikki 清零（M7f）后整体删除。**【已收口 2026-10-07】**3 处消费点已清零（步 3）；映射函数（含 `toLegacy`）与 legacy 映射单测已随旧面整体废除（步 4），`PlayStateTest` 只留成员表与 `isInPlaybackState` 断言。
③ **START_ABORT 语义缺口**：状态机 `onStartAborted` 无接线；旧侧 int 8 在本仓库恒不可达（`showNetWarning()` 闸门恒 false：`VideoViewConfig` 默认 `mPlayOnMobileNetwork=true` 且 app 内 0 处 `VideoViewManager` 配置）。**开闸前必须给状态机接 `onStartAborted`**，否则三处读点分歧：`isIdleKernelReusable`（机制 IDLE→复用 / 旧 8→不复用）、`LivePlayActivity.canReusePlayer`（机制 IDLE→不复用 / 旧 8→复用）、`DanmuLoadController.isVideoReady`（同为 false）。**【已收口 2026-10-07】**`AppPlayerView.startPlay()` 的闸门分支已 `abortStart()` + 派发 `START_ABORT`，`ExoPlayer.abortStart()` 接线 `stateMachine.onStartAborted()`（步 2；闸门仍恒 false，语义等价；`skill/review/refactor-plan-20261007.md` 附录 C）。
④ **PREPARED 在读取面不可观测**（桥首次 READY 同帧连发 `onPrepared`+`onRenderingStart`）⇒ 三个读点（`LiveOverlayController` 分辨率 OSD、`DanmuLoadController.isVideoReady`、`DetailActivity.musicPlaybackDetected`）实际由 PLAYING/BUFFERED 过闸；集合保留 PREPARED 无害，真机走查覆盖"出现时机"。
⑤ **日志取证面 int → 枚举名**（`PlaybackRetryDelegate`/`MusicSessionDelegate`/`LivePlayActivity` 的 `state=$state`）—— 走查清单里按 `state=<int>` grep 的条目改枚举名；`PlaybackEngine` 的 `echo-player error` 仍为 int（未变）。
⑥ `PlaybackEngine` 状态监听体内残余的 int 比较（`VideoView.STATE_ERROR/PLAYING/BUFFERING/BUFFERED`）与 `PlaybackPreload.onPlayerState(int)` 的 int 状态面**留 M7f**（随 doikki 清零/预载改写一并收口）。**【已收口 2026-10-07】**监听体换 `PlayState` 枚举（步 3）；旧监听 API 整体删除，引擎改订阅 `AppPlayerView.playStateFlow`（步 4）；`PlaybackPreload.onPlayerState(PlayState)`。
⑦ 音乐页外部播放器的**可观测差异**（修复）：M7d 前 `MusicPageBridge` 继承 `HeadlessView.playExternalPlayer = false`，pl≥10 的源在音乐页"调用外部播放器"必提示失败且不拉起外部 App；现真正拉起（`subtitle.orEmpty()` + `isPageAlive` 守卫）—— 入走查清单。
⑧ **工作区 CRLF 存量清单**（`i/lf w/crlf`，提交自动归一化、非本轮引入；实测 2026-10-06）：`ui/activity` 12（LiveChannelNavigator/ThemeSettingsActivity/SearchViewModel/SearchScreens/SearchListScreens/ConfigManageActivity/SearchIdleScreens/SearchActivity/PreferenceSettingsActivity/LiveProxyLoader/PlaySettingsActivity/LiveEpgParser）、`ui/music` 4、`ui/components` 3、`ui/page` 2、`player/effect` 4、`player/ui` 1、`osc/util` 11 —— §1「行尾归一化说明」的旧口径（"12 个既有 `.kt`（`osc/util/` 下）"）作废，以本清单为准。

**未验证面（诚实标注）**：真机走查未做 —— 本片把直播/音乐/详情页的状态读取面与音乐页外部播放器出口翻新，走查至少覆盖：直播（起播 loading/暂停图标/自动换源超时分支/切台复用判定/分辨率 OSD 时机）、音乐（缓冲转圈/播放暂停图标/播完续播/外部播放器拉起）、详情页自动进音乐页判定、DLNA 投屏回归（零改动，仅核对），以及登记项 ③④⑦ 三条可观测差异。

# 7.19 M8 实测登记（2026-10-06，`osc` 主包收尾）

**结论**：M8 四条中**三条已达成、两条有登记项**（i18n 非 UI 侧待补标记、UI 层例外注释），**无代码改动**（纯验证/盘点里程碑）。

| 条目 | 结论 | 证据 |
| --- | --- | --- |
| 主包 Java 清零 | ✅ | `com/github/tvbox` = **0 Java / 349 Kotlin**；`app/src/main/java` 24 Java 全在 `com/github/catvod`（M9） |
| 行尾 LF | ✅ | `git ls-files --eol` 的 `i/crlf` = **0**；37 个 `i/lf w/crlf` 系本地 `core.autocrlf` 检出差异 |
| i18n 卡口 | ⚠️ 门通过（ui 0）/**非 ui 35 处待补标记** | 见下 |
| 包级环 | 已重新基线（22 组），**不做"未新增"断言** | 见下 |
| 清 KDoc | ✅ | `不重写 Java`/`LiveDataFlow`/`D2 分域` 等 **0 命中** |
| 契约层 | 事实 | `catvod` 仍 24 Java，全库零 Java 由 M11 判定 |

**i18n（`.codebuddy/tools/i18n_gate.py`）**：`ui 层（硬闸门）= 0 处 / 0 文件` ✅；`非 ui = 35 处 / 3 文件` —— `player/engine/PlayerEngine.kt` 33（`LANG_MAP` 语言归类值 18 + `matchLanguage` 关键字与返回值 14 + `"未知"` 过滤值 1）、`sourcedata/SourceHelper.kt` 1（`"豆瓣"` 探测值）、`sourcedata/SourceResultParser.kt` 1（异常文案）。**逐条甄别：35 处全属"匹配关键字/归类值/探测值/诊断文案"，无一处是待翻译的 UI 文案** ⇒ 属**缺 `i18n: keep` 标记**（规范 §1.3 R1–R11 的数据值/日志类），来源是 **M7a 新建 `PlayerEngine.kt`（`8878235`）时未标注**。门脚本按行认标记（该行或上一行），补 34 处可恢复到历史 `ui 0 / 非 ui 0`；纯注释零风险，但会在引擎文件插入约 30 行注释，**登记为待办、未擅自执行**。
- **承接给后续片的规则**：新建/移植**任何**"语言码 → 中文归类值"或"关键字匹配表"时，**当场打 `// i18n: keep(轨道语言匹配值)`** —— 否则每建一个引擎/解析类都会让 i18n 门回退一格。

**包级环**：提取口径 = 文件所在包与 import 目标包各取"根包（`com.github.tvbox` / `com.github.catvod`）之后头 3 段"，双向即成环。实测 **22 组 / 231 边 / 53 包**（231 边与旧基线数值相同，但包数 39 → 53 说明**分组口径不完全一致**）。**旧基线脚本已丢失 ⇒ 不做"是否新增"的断言**，改为重新基线；其中 5 组是 M7 拆播放栈子包的 `player.*` 家族内环（旧文档已判可接受）。**不建议清环**：22 组的较小方向导入数之和为 63，但 11 组涉及 `util` 枢纽，而旧 spec 已**实测证明挪包无效**（util 拆分实验 23 → 23 不降），只能逐类依赖反转 ⇒ 跨轮架构重构、收益（环不影响运行）与风险（动最被依赖的包）严重不匹配，计划已明确排除。
- **承接给后续片的规则**：**环数是架构指标、不是缺陷**；发现环先判"是否同族/util 枢纽"，**不要试图靠挪包批量消环**（本项目已证伪一次）。

**UI 层零新增注释**：规则（规范 §57 / 计划 §362）针对**迁移期不该顺手加注释**。本会话为两处**功能性布局修复**在 `ui/page/` 加了 10 行解释性注释（`CollectPage.kt`：标题移出海报的首页样式对齐出处；`ConfigManagePage.kt`：空态为何原不居中）⇒ **登记为"功能性改动所加"的例外**，可随时按用户意见删除。

# 7.18 M7f 实测登记（2026-10-06，2 片）

**交付**：`app/src/main/java/com/github/tvbox/osc/player/` 下最后两个 Java 迁 Kotlin —— `PreloadCoordinator`（325 行，`e46ca59`）与 `PreloadManagerHolder`（333 行，`035c4cc`），**纯语言迁移、逻辑零改动**。该目录实测 **0 Java / 30 Kotlin**；主包 Java 26 → 24。**`com/github/tvbox` 已全量 Kotlin（0 Java / 349 Kotlin）**，剩余 24 个 Java **全部在 `com/github/catvod`**（契约保持边界，计划明确保留）。

**结构性改写早已完成（本片只需语言迁移）**：计划原写「其订阅改由 `PlaybackPreload` 收集通道 `flow`」，实测 `observeForever` 全仓清零（仅注释提及），flow 订阅已在 `PlaybackPreload.kt:42`（`vm.preloadResult.flow.collect`），两个待迁类本身 `LiveData/Observer/Disposable/Flow` 引用数均为 0。

**逐文件结构卡口（零丢失）**：
| 文件 | 方法 | 字段 | 匿名类 | `kotlin.Unit` lambda | `@Override` |
| --- | --- | --- | --- | --- | --- |
| PreloadCoordinator | 19（0 丢失） | 46（0 丢失） | 0 | 0 | 0 |
| PreloadManagerHolder | 30（0 丢失） | 30（0 丢失） | 1 | 0 | 8 |

**`PreloadManagerHolder` 迁 Kotlin 的形态要点（承接给后续同类"全静态工具类"）**：
1. Java 全静态类 → Kotlin `object` + 公开函数加 `@JvmStatic`（**Java 侧调用形式不变**：仍是 `PreloadManagerHolder.enabled()`）。
2. `synchronized static` → `@Synchronized` + `@JvmStatic`。**互斥对象仍是 Class**：`@JvmStatic` 在静态桥上加 `ACC_SYNCHRONIZED`（独立复核用 `javap` 核实：全类 `monitorenter` 计数为 0），与 Java 逐位一致 —— **不是**「改为锁 INSTANCE」（我原先的注释写错了，已改）。
3. `@Volatile` 在同一批字段上逐一对齐（`sPreloadHeaders`/`sStartPosMs`/`sRangeMs`/`sReadyListener`/`sCompletedUrl`）；Kotlin `object` 的属性后备字段会提升为 static，故 `@Volatile` 保持 Java 的 static volatile 内存语义。
4. `LinkedHashMap` 匿名子类重写 `removeEldestEntry` → Kotlin 对象表达式；参数须与原版逐项一致（容量 8 / `0.75f` / `accessOrder=true` / `size() > 8`），复核已用字节码核实。
5. media3 的两个 Factory（`DataSource.Factory`/`MediaSource.Factory`）：`getSupportedTypes()` 返回 `IntArray`、`setDrmSessionManagerProvider`/`setLoadErrorHandlingPolicy` 返回 `this`；接口方法顺序不变。
6. **Python 写文件的坑（本轮踩到）**：用 `u'''...'''` 写含 `'\n'` 的 Kotlin 源码时，Python 会把 `\n` 解释成真实换行、打断语法 —— 需写 `\\n`。凡是要在产物里出现反斜杠转义，都要多一层。

**nullability 决策（逐条，均为"复刻 Java 平台类型"而非收紧）**：
- `PreloadCoordinator(sourceViewModel: SourceViewModel?)`：调用点 `PlaybackPreload.kt:36-38` 先 `ensureFetch()` 再读、**仍可能为空**，Java 靠平台类型放行、到取流时才 NPE ⇒ Kotlin 保留可空 + 在**使用点** `!!`，而不是在构造期就抛（那会把失败点提前，属行为变更）。
- `Snapshot.playFlag`/`currentKey`/`nextUrl` 可空：调用点分别传 `VodInfo.playFlag`、`scheduler.progressKey()`、`VodSeries.url`，在 Kotlin 侧都是可空；只有 `isJpUrl(url: String)` 要求非空，故仅该处用 `!!`。
- `preload(url: String?, headers: Map?)`、`isPreloadTargetUrl(url: String?, headers: Map?)`：保留 Java 的显式判空分支。

**已登记的**有意偏离**（1 条）**：`PreloadCoordinator.evaluate` 里 Java 原文 `snapshot.currentKey.equals(gaveUpKey)` 在 `currentKey` 为空时 **NPE 崩溃**；Kotlin 的 `==` 在 `null == null` 时为 true，会**静默跳过该片全部预载**。两者都不是本意 ⇒ 改为「`gaveUpKey` 非空且相等才跳过」。

**复核轮（独立只读子代理，含字节码与 media3 1.11.1 源码核证）**：**阻断 0 / 高 0 / 中 0 / 低 6，无功能回归**。除上述两条已处置外，其余 4 条低危均为 **Kotlin 强制的非空参数校验**，落在复核已证明**当前不可达**的路径上：`Snapshot` 构造 4 项、`preload` 的 `context`、ranking lambda 的 `it`、`get()` 中 `sManager` 的赋值时机（`addListener` 期间 `sManager` 尚为 null，但该窗口不可观测）。复核另核实：同步面 7 处一致、`@Volatile` 5 字段一致、LRU 参数一致、**43 条日志字符串逐字节一致**、`headersSignature` 的 `TreeMap(CASE_INSENSITIVE_ORDER)` 与 `':'`/`';'` 拼接一致、`putCache` 两轮迭代的条件顺序一致、`consumeResult` 四个出口一致、`getSupportedTypes()` 顺序一致、无 `equals/hashCode/toString` 新增、**无任何 Java 调用方**。复核遗留的「未验证 media3 版本」已闭合：`:app:dependencies` 实测 debugRuntimeClasspath = **1.11.1**。

**未验证面（诚实标注）**：真机走查未做（预载路径的可见表现是"下一集已就绪"提示与读盘命中）—— 建议随 M7a–M7d 的 §4 清单一起走查。

# 7.17 M7e 实测登记（2026-10-06，播放栈自研替换：控制器去 View + 全量去 doikki，4 片）

**结论**：**`app/src` 已零 `xyz.doikki` 导入、零代码引用**（只有「移植自 …」的示意性注释）。提交 **`a9e8fef`**（doikki 工具面移植：`util/PlayerUtils.kt` + `util/CutoutUtil.kt`）/ **`aaeb98d`**（宿主与控制器去 doikki View 层：新增 `KernelPlayer.kt`/`AppPlayerView.kt`/`host/PlayerRenderView.kt`，`ExoPlayer : KernelPlayer`，`MyVideoView : AppPlayerView` 直持 media3 `ExoPlayer`，`ComposeVideoController`/`ComposeLiveController` 去 `BaseVideoController`，删 `ExoMediaPlayerFactory`）/ **`7717458`**（`PlayerCache` 收回 `SimpleCache` 实现体、`MediaSources.getInstance` 承接单例、`App`/`OkGoHelper`/`PreloadManagerHolder` 切走）/ **`ce198db`**（手势边缘带工具公开）。`:app:assembleDebug` + `:app:assembleRelease` 绿；`:app:testDebugUnitTest` **611 用例 / 0 失败 / 0 错误 / 0 跳过（79 suite）**（与 M7d 基线持平）；改动文件全 LF。

**新旧接口对照（迁移口径 = 逐语义承接，命名的对应关系必须记住）**：

| 旧 doikki 面 | 新 app 侧面 | 备注 |
| --- | --- | --- |
| `AbstractPlayer`（内核契约） | `player/KernelPlayer.kt` | 抽象类；`mPlayerEventListener` 用 `@JvmField protected` 承接（子类 7 处直读零改动）；`setStartPosition` 内含 `max(0, pos)` 钳位；`startPosition` 是 `protected val`（与 setter 分名字段） |
| `VideoView<P>`（播放器视图） | `player/AppPlayerView.kt` | 只保留本仓库用到的面；删 XML 属性读取、`VideoViewManager`/`VideoViewConfig` 单例、`PlayerFactory`、`setFullScreen`/`setTinyScreen`（app 零调用点，全屏是 Activity 级）、静音/循环/镜像/截图 |
| `ProgressManager` | `AppPlayerView.ProgressSink` | 两个入口同名同义；`PlaybackEngine` 注入；`setProgressManager` → `setProgressSink`（5 处） |
| `AudioFocusHelper` | `host/PlayerAudioFocus`（M7b 已建 + 单测） | 只建一次；`ensureAudioFocusHelper` 等价物在 `AppPlayerView`；`pause` 放弃、`release` 无条件放弃并置空 |
| `IRenderView` / `RenderViewFactory` | `host/PlayerRenderView` / `PlayerRenderViewFactory` | `attachToPlayer(KernelPlayer)`；`doScreenShot()` 改可空 |
| `TextureRenderViewFactory` 的 `is` 判定 | `AppPlayerView.renderIsSurface`（Java 侧 `getRenderIsSurface()`） | `MyVideoView.factoryRenderType()` 改判 `EngineTextureRenderViewFactory` |
| `BaseVideoController` | `AppPlayerView.VideoControllerHost` + 控制器自持 | `setPlayState`/`setPlayerState`/`onVideoSizeChanged`/`onVideoSizeCleared`/`startProgress`；进度定时器从基类搬到 `ComposeVideoController.progressRunnable` |
| `ControlWrapper` | `AppPlayerView` 自身的查询面 | `duration`/`currentPosition`/`bufferedPercentage`/`videoSize`/`tcpSpeed`/`isPlaying`/`togglePlay`/`seekTo`/`setSpeed`/`setScreenScaleType`/`isFullScreen` |
| `VideoView.setVideoController` | `PlayerControlApi.setKernelProvider(MyVideoView?)` | 注入视图 + 自注册为状态宿主；`AppPlayerView.setVideoController` 的三件事（摘旧、追加到容器顶部、状态回灌＋`startProgress`）逐条保留；引擎侧用 `releaseController()` |
| `ExoMediaSourceHelper`（共享缓存 + 单例） | `engine/PlayerCache` + `engine/MediaSources.getInstance` | `SimpleCache`/`LRU`/`StandaloneDatabaseProvider`/`externalCacheDir ?: cacheDir`/目录名 `exo-video-cache` 逐条一致 |
| `PlayerUtils` / `CutoutUtil` / `L` / `VideoViewManager` / `VideoViewConfig` / `MeasureHelper` / `TextureRenderView` | `util/PlayerUtils` / `util/CutoutUtil` / 其余不移植 | `L`/`VideoViewManager`/`VideoViewConfig` 全零调用点、`MeasureHelper` 已由 `host/RenderMeasure` 替代、`TextureRenderView` 已由 `EngineTextureRenderView` 替代 |

**本片现场核实出的规则（后续片照查）**：

1. **Kotlin 类里 `open`/`override` 属性不能加 `@get:JvmName`**（"annotation is not applicable to this declaration"）。想让 Java 继续按 `getXxx()`/`isXxx()` 调：要么该成员保留为**函数**（另给同义属性，如 `AppPlayerView.isPlaying` + `isPlaying()`），要么在 Java 调用点改读 Kotlin 属性自动生成的 `getXxx()`。
2. **属性与其同义函数不能同名**：`val isPlaying` 与 `fun isPlaying()` 在 JVM 上撞签名（Platform declaration clash）；`val renderIsSurface` 与 `val isSurfaceRenderActive` 同被注解成 `isSurfaceRenderActive()` 也撞。二选一或改名。
3. **Java 侧读 Kotlin 属性会编译失败**：`mVideoView.currentPosition` 必须写 `getCurrentPosition()`。本片修正 `PlayContainer`/`TrackSelectorDelegate` 共 9 处。
4. **内部对内核的读数要一起改**：`mMediaPlayer?.getDuration()` → `mMediaPlayer?.duration`（`KernelPlayer` 的读数面是 Kotlin 属性）。漏一处就是 Unresolved reference。
5. **`protected val startPosition` 与 `setStartPosition(position)` 必须分名字段**，否则属性与参数同名遮蔽。
6. **`LOG.d` 等 app 侧日志是单参**（`LOG.d(msg)`），noikki `L.d(tag:msg)` 的双参写法不可照搬。
7. **`@JvmName` 在普通 `protected fun` 上可用**（如早期草稿的 `getStartPosition`），但一旦该成员被 `override` 就不行 —— 最终选择直接暴露 `protected val startPosition`。
8. **共享缓存实现体搬迁必须一案一提交完成**：`App` 容量注入 → `PlayerCache`、`OkGoHelper` client 注入 → `MediaSources`、`PreloadManagerHolder` 5 处调用点，全部切完才提交（`SimpleCache` 同目录双实例会抛）。
9. **`getInstance` 单例的 client 必须懒读**：`MediaSources.getInstance(ctx)` 不缓存 `OkGoHelper.getItvClient()`（`reloadDns()` 会重建 client）。
10. **`isEdge` 依赖"含导航栏的屏幕宽高"**：`AppPlayerView`/`PlayerUtils` 的 `getScreenWidth(ctx, true)`/`getScreenHeight(ctx, true)` 不能因为"无直接调用点"而删，`PlayerUtils.dp2px` 已公开（手势边缘带用）。
11. **【复核抓出的阻断项，务必照查】Activity 解析方向不能反**：旧 `BaseVideoController` 用**控制器上下文**解析 `mActivity`（`mActivity = PlayerUtils.scanForActivity(getContext())`，控制器由页面以 Activity 为上下文创建）；`AppPlayerView` 归引擎、由 `ContextThemeWrapper(applicationContext, …)` 创建，**对它的上下文 `scanForActivity` 恒返回 null**。首版把 `playerActivity()` 写成 `videoView?.hostActivity()` ⇒ 控制器构造期 `SubtitleHelper.getTextSize(null)`（内部 `activity!!`）**进播放页即 NPE**；连带"旋转 / 返回键 / OSD 屏参"三处静默失效。修法：`playerActivity()` 按"控制器自身上下文优先、视图兜底"解析并缓存（见 `ComposeVideoController.playerActivity()`）。**凡"旧实现从父类字段取 Activity/Context"的地方，都要先问清该字段的真实来源。**
12. **【复核抓出的阻断项，务必照查】自持定时器必须照抄"延迟 + 停表"两条**：旧 `BaseVideoController.mShowProgress` 是 `postDelayed(this, (1000 - pos % 1000) / speed)`，且**不在播时复位 `mIsStartProgress` 并停止续期**。首版写成"无 delay 的 `post` + 末尾无条件续期" ⇒ 0 延迟自旋（主线程消息队列被自身填满、`PlaybackProgress.onProgress` 最大频率触达），注释却写"与旧基类一致"。另：`onDetachedFromWindow` 里**必须同时复位计时标志** —— 只 `removeCallbacks` 会让标志停在 true，重挂后 `startProgress()` 全部早退 ⇒ 进度条/时间胶囊永久冻结。
13. **状态机里"顺手复位"的分支不能漏**：旧 `BaseVideoController.onPlayStateChanged(STATE_PLAYBACK_COMPLETED)` 会清锁定态（`mIsLocked = false`），而手势层的门禁读的就是它 ⇒ 漏清会出现"锁屏 → 自动跳下一集 → 手势全被拒，且锁图标已自动隐藏、用户无从解锁"。
14. **`AppPlayerView.showNetWarning()` 的默认值口径**：`VideoViewConfig.Builder.mPlayOnMobileNetwork` 默认 **true**，但单例字段取自 `VideoViewManager` 自己的 `mPlayOnMobileNetwork`（默认 **false**），本仓库从不 `setConfig` ⇒ 旧闸门恒为 false（从不中止起播）。移植按"恒不中止"落成钩子即等价。

**复核轮（2026-10-06，独立只读子代理逐方法对照 fork 原文）**：**2 阻断 / 2 高 / 1 中 / 5 低，已全部处置**。① 阻断 = `playerActivity()` 解析方向反了（进播放页 NPE，见规则 11）—— 已修；② 阻断 = 进度定时器 0 延迟自旋（见规则 12）—— 已修（照抄 `postDelayed` + 秒边界 + 不在播停表）；③ 高 = 旋转 / 返回键 / OSD 三处同样依赖 `playerActivity()` —— 随 ① 修；④ 高 = `onDetachedFromWindow` 未复位计时标志（重挂后进度永久冻结）—— 已修；⑤ 中 = 完播不清锁定态 —— 已修（见规则 13）；⑥ 低 = `PlayerUiState.showing` 成无写入死字段、`resume()` 删掉 Surface 未就绪的等待分支（改由渲染宿主 `surfaceCreated` 交面，属有意差异）、`playOnMobileNetwork` 注释默认值写反（已按规则 14 改写）、`MediaSources` 单例 KDoc 声称"懒读 client"而代码是"注入优先"（当前无缺陷：`OkGoHelper` 每次 `reloadDns()` 都重注入）、`setMute`/`isFullScreen`/`onBackPressed`/`getCurrentPlayerState` 保留空壳入口（app 侧零调用点）—— 均登记不作返工。**子代理另核出打包耦合**：`:player` 模块还携带 app 唯一来源的原生库（`player/src/main/jniLibs/arm64-v8a/libp2p.so` / `libxl_stat.so` / `libxl_thunder_sdk.so`，app 侧无 `jniLibs`，`P2PClass` 用 `System.loadLibrary("p2p")`）⇒ **M10 删模块前必须先把 jniLibs 迁到 `app/src/main/jniLibs`**，否则 `P2PClass` 类初始化即 `UnsatisfiedLinkError`。复核同时确认了本片自称的两条：(a) `app/src` 的 `xyz.doikki` 命中**全在 KDoc**；(b) 无任何 app 代码 `extends`/`implements`/`new` doikki 类（fork 仅因 `app/build.gradle.kts:137 implementation(project(":player"))` 被打包）。

**该项（手势）已尝试并主动回退（2026-10-06，第二轮独立复核，提交 `09de9d6`）**：本片把手势判定抽成纯状态机 `player/ui/VideoGestureHandler`（+16 例单测全绿，覆盖边缘带/未启用/非播放态/锁屏标记/预览态/横竖择优/半屏分侧/「禁用手势控制」只拦竖滑/长按与滑动互斥/点按与双击窗口/取消不提交），并写了 Compose 指针接线（`Modifier.videoGestureLayer`）。**接线层经独立复核判定「不予交付」：阻断 2 / 高 3 / 中 3 / 低 4，且两个阻断项都是接线层问题、单测 0 覆盖（假信心）**：

- **阻断 ①**：接线层**从不检查 `isConsumed`**（全文件无该判断），因此子控件（中央播放键、底栏按钮、进度条）在 Main pass 消费后，本层仍在 Final pass 同一事件上再处理一次 —— 点播放键会连带 `toggleControls()` 把控制条收掉；拖进度条会在子控件 seek 之后**再 seek 一次并覆盖落点**。旧实现之所以正确，是因为 View 分发下"子 View 消费后父容器收不到"，而 Compose 的消费只影响同 pass 的后续处理者、**不会**阻止父节点看到该事件（复核给出框架级证据：Main pass 先子后自身、Final pass 先自身后子，故 Final 里 `isConsumed` 已是子控件结果 —— 能看到却没用）。
- **阻断 ②**：单击超时等待（`withTimeoutOrNull { awaitPointerEvent(...) }`）会**把双击的第二下 DOWN 取走**并不进 `beginSession`，随后 `awaitEachGesture` 在其 block 结束时 `awaitAllPointersUp()` 把第二下剩余事件排空 ⇒ `isDoubleTap` 永假、**双击播放/暂停在生产路径不可达**，且"取到第二下"时反而不派发单击（双击彻底无反应）。
- 高 ③ 亮度/音量基准只在"史上第一次"取（旧实现每次 `onDown` 重取）⇒ 第二次滑动会跳变；若首次音量手势时系统音量为 0，此后音量恒被钳到 0。
- 高 ④ `ACTION_CANCEL` 被当成正常抬手（框架会把 CANCEL 合成为同 id 的 `changedToUp` 并三 pass 全发）⇒ 横滑中下拉通知栏/来电会**提交**这次 seek（旧实现回原位），`onSeekCancel` 成死代码。
- 高 ⑤ 长按：暂停态也提速（旧实现显式排除 `STATE_PAUSED`）；改为事件驱动后手指完全静止可能永不触发；`maxDistance` 滞后一拍。
- 中 ⑥ 屏幕几何（边缘带/半屏分侧）在 `pointerInput` 块外求值、键只有稳定的 `handler` ⇒ 旋转后不重算；中 ⑦ 边缘判定用**局部坐标**比屏幕尺寸（旧口径是 `rawX/rawY`）⇒ 预览态点预览窗顶部会误判为上边缘；中 ⑧ 锁屏短路从"一切判定之前吞事件"退成"各动作自查"⇒ 锁屏时四边 40dp 内、非播放态、长按都唤不出锁屏钮，且唤出要等双击窗口。

**为什么回退而不是继续修**：两个阻断项都在"Compose 指针语义"这一层，而本仓库**无 `androidTest`、无真机走查条件**，继续修只能靠再猜一轮框架语义（复核列出的 12 条缺失用例全部落在接线层，无法离线覆盖）。按「迁移步禁夹带高风险改写」纪律，**整片回退**：`GestureController` 原样恢复、`VideoGestureLayer.kt` 与手势接线删除，仅保留 `PlayerUtils.dp2px`/`getScreenHeight` 公开（手势边缘带与子类布局共用，属无害增强）。**下一片落地的前置** = 先补 Compose UI 测试（`androidTest`）或安排一次真机手势走查，并按 ①–⑧ + 12 条缺失用例逐条验收；纯状态机代码与 16 例单测可直接复用（其判定语义经复核确认与旧实现等价，含 seek 符号代数：`target = cur + Δx/width·240000`，右滑前进）。

**② 真机走查与迭代（2026-10-06,用户实测;提交 `2fba430`→`b0fa2bf`→`c23ce2c`→`373d2b4`）**：接线后用户连续走了四轮真机,共报 7 个问题,**全部已修**。价值最高的三条:

1. **`dragging` 是我多做的**:手势横滑时我额外设了 `dragging=true` + `seekPreviewPositionMs`,而底部进度条渲染取的正是 `seekPreviewOrPosition` ⇒ 真机表现为"进度条白球缩放、整条左移"。**旧 `GestureController` 手势滑动只出提示文字、不碰 SeekBar**。教训:迁移时"顺手对齐口径"就是引入 bug。
2. **长按必须独占会话**:长按成立后 `onMove` 须立即返回、不再选模式,否则"手指轻微移动就变成调进度/音量"。
3. **顶端带(最关键)**:系统只把**屏幕最顶端**留给"下拉通知栏"。从那一带起手时,系统会**先持续送 MOVE**,等它接管时亮度/音量**已经被改过**了 ⇒ 因此"等一个更好的 CANCEL 信号"这条路在**顺序上就不可能成立**(前两轮我都在改 CANCEL 判据,方向错了)。正解:**起手位置在画面顶部 15% 以内 ⇒ 竖滑整段不参与亮度/音量**(横滑仍照常)。中部起手的竖滑不受影响,因为系统根本不会接管它。

**验证(2026-10-06,用户操作 + `adb logcat` 抓 `echo-gesture` 痕迹,9 条样本)**：
```
fromTopBand=true |NONE   -> 3 次   ← 顶端带起手,一次都没进亮度/音量
fromTopBand=false|SEEK   -> 1 次   ← 横滑
fromTopBand=false|VOLUME -> 3 次   ← 画面中部竖滑(期望行为)
fromTopBand=false|UNDECIDED -> 2 次 ← 位移未越起判阈值
断言:顶端带出现 VOLUME/BRIGHTNESS = 0 次;VOLUME/BRIGHTNESS 全部来自非顶端带 = 3/3
```
手势层保留 `echo-gesture` 诊断行(sawMove/quietMs/mode/cancelled/fromTopBand),后续真机问题先看它,不再靠猜。

**其余各条修复**:双击失效(接线层阻塞等第二下、被 `awaitEachGesture` 收尾吃掉 ⇒ 改为抬手即返回 + 宿主定时器补发单击,并把"待定单击"的归属收回状态机使 `markSingleTapConfirmed` 幂等);长按无倍速提示(适配器漏设 `speedBoostVisible/speedBoostValue`);时间/音量基准按会话现取;`ACTION_CANCEL` 不当抬手提交 seek;竖滑起判阈值(高度的 12%,约 288px)与横滑缩放从 240000ms 收敛到 120000ms(真机反馈"太灵敏")。

**过程教训(登记)**:这四轮里我三次都是"只改了实现没同步状态/判据"(漏 `speedBoostVisible`、漏 `tapPending` 赋值、连续两轮改错 CANCEL 方向)。**凡是一个状态被两个组件持有,判断权必须归给信息更全的那个**(待定单击归状态机);**凡是系统会介入的手势区域,先确认区域边界再写判据**(顶端带)。

**② 已接线到控制器（2026-10-06,提交 `43b730a`）**：`GestureController.kt`(259 行 View 级 `GestureDetector`)**已删除**,改由:
- `ComposeVideoController` 持有 `VideoGestureHandler`(判定)+ `VideoGestureActionsImpl`(副作用);
- `PlayerOverlay` 根 `BoxWithConstraints` 挂 `Modifier.videoGestureLayer(gestureHandler)`,快照由 `gestureActions.beginSession(w,h,screenWidth)` **每次 DOWN 现算**(宽高/边缘/半屏分侧都现算);
- 半屏分侧用的屏幕宽度在**组合期**读出(`pointerInput` 的 lambda 内不能有 `@Composable` 调用 —— 编译期会报 `@Composable invocations can only happen from the context of a @Composable function`);
- `gestureActions`/`gestureHandler` 在 `init{}` 里**先于** `initComposeLayer()` 建好,故 `pointerInput(handler)` 的键稳定、无初始化顺序风险;
- 接线层不再有 `onTouchEvent` 覆写,空白区触摸由 Compose 层认领(子控件消费的照旧不认领)。

**两处接线期修正**:① 亮/音量基准**按会话现取**(旧草稿缓存过一次,跨会话串味 —— 复核高危项 ③ 的正解);② 手势横滑进入与底部进度条拖动**同一个 `dragging` 态**,否则 1Hz 进度表会与预览互相打架;此外把 `endSession` 的返回值改为显式 `EndResult`,让接线层不必猜"该不该等第二下"。

`assembleDebug` + `assembleRelease` + **638 用例 / 0 失败** 全绿。

**② 已落地实现 + 接线层用例（2026-10-06,提交 `2fba430`）**：在验证栈跑通后重建了手势层,并**用测试钉住两轮复核的阻断项**:
- **实现**:`player/ui/VideoGestureLayer.kt` = 纯状态机 `VideoGestureHandler`(判定,20 例单测)+ `Modifier.videoGestureLayer`(指针接线)。两条硬约束:① `awaitFirstDown(requireUnconsumed = true)` —— 子控件消费的触摸从不认领(阻断项 B1 正解);② 等第二下时**不消费**取到的 DOWN,交给下一轮 `awaitEachGesture` 重新起会话(双击可达)。
- **写测试时抓到 3 个真 bug(全部已修)**:① `endSession` 先把 `mode` 复位再读,导致**横滑永远不提交/不取消** seek(状态机用例当场抓到);② **首下抬手几乎总落在长按竞速窗口内**(测试里 down/up 同帧),旧写法之后会再等一个永不到来的 up ⇒ `endSession` 永不执行、**点击全部无反应**;③ 单击确认只在两条收尾路径之一可达 ⇒ 点击静默结束。为此把 `endSession` 返回值从 `Boolean` 改为显式 `EndResult{TAP_PENDING, DOUBLE_TAP, NONE}`。
- **覆盖**:状态机 20 例(单击恰好一次/双击抑制单击/超窗非双击/长按含暂停与 CANCEL 恢复/横滑提交与取消/右左半屏亮度音量/总开关只拦竖滑/预览态/锁屏/边缘带/未越 slop)+ **接线层 3 例**(子控件消费不被处理、空白区单击仍派发、横滑预览并提交)。全套件 **614 → 638 用例 / 0 失败**。
- **1 例受限(@Ignore)**:双击的**接线层**用例 —— Robolectric 虚拟时钟无法稳定表达"两次注入之间"的双击窗口时序(不推进时钟则第二下落在窗口外;推进时钟会被 300ms 超时先打断;换 `withTimeout` 则单击路径不可靠)。**双击的判定逻辑本身已由状态机用例覆盖**,缺的是指针层到达时序 ⇒ 属真机走查项。

**② 的验证栈已铺好（2026-10-06,提交 `756c098`）**：按用户指示先补测试依赖,现已落地并可跑:
- **依赖**:`gradle/libs.versions.toml` 新增 `robolectric = "4.17"`、`androidxTestExtJunit = "1.3.0"` 与 alias `robolectric` / `androidx-test-ext-junit` / `androidx-test-core` / `androidx-compose-ui-test-junit4` / `androidx-compose-ui-test-manifest`;`app/build.gradle.kts` 的 `testImplementation` 新增这 5 项(Compose 两项走 `platform(libs.androidx.compose.bom)`,解析为 **1.13.0-alpha01**)。
- **构建开关**:`testOptions.unitTests.isIncludeAndroidResources = true`(**关键**:不开的话 Robolectric 看不到合并清单/资源)。
- **三个非显然的坑(全部已解,勿回退)**:① 真实 `App` 在 `attachBaseContext` 里 `KV.init → MMKV.initialize → System.loadLibrary("mmkv")`,JVM 里必然 `UnsatisfiedLinkError`,测试根本进不到测试体 ⇒ 用测试源集的极简 `com.github.tvbox.osc.testing.TestApplication` 顶掉;② 宿主 Activity 不能用 `ui-test-manifest` 提供的 `androidx.activity.ComponentActivity`(它不在应用运行期 dex 里,`ActivityScenario` 实例化失败)⇒ 用测试源集自己的 `ComposeTestActivity`;③ 用 `createAndroidComposeRule<T>()` 显式指定宿主。
- **能力探针 `ComposeGestureHarnessTest`(3 例)**:`performClick` 能落到 Compose 节点;低层 `down/up` 能进 `pointerInput`;以及**最关键的**——父层在 `PointerEventPass.Final` 能观察到子控件已消费(`isConsumed=true`)。第三例正是第一轮复核断言"缺失"的那个前提。
- **当前状态**:探针暂时 `@Ignore`,唯一原因是本工程 **`applicationId`(com.github.avbox.osc) ≠ `namespace`(com.github.tvbox.osc)**,导致测试源集 Activity 的名字在"清单合并"(绝对名)与"Robolectric 规范化"(相对 applicationId 的 `.ui.ComposeTestActivity`)两侧对不上,`Unable to resolve activity for Intent`。**属 AGP 9 清单接线问题,与手势逻辑无关** —— 同一份代码在 `mergeDebugUnitTestManifest` 尚未重跑的那次构建里 3 例全 PASS。解锁方向(均不需改手势代码):① 把宿主 Activity 放进主源集(或主清单声明一个调试用 Activity)使两侧名字天然一致;② 改走 `androidTest`;③ 查清 AGP 9 下该场景的清单来源。全套件 **614 用例 / 0 失败 / 3 跳过**。

**② 的阻塞条件（2026-10-06 实测确认）**：本仓库**不具备离线验证 Compose 指针语义的手段** —— `app/build.gradle.kts:89-90` 只有 `testOptions { unitTests.isReturnDefaultValues = true }` + `testImplementation(libs.junit)`（JUnit4）；`gradle/libs.versions.toml` 无 `robolectric`/`ui-test-*`；`app/src` 无 `androidTest` 源集。两轮独立复核对已实现的手势层结论一致（"两个阻断项都在接线层、16 例单测 0 覆盖"），而接线层语义只能由 `androidTest` 或真机证明 ⇒ 在补上验证手段前，该项的每次实现尝试都无法被判为可交付。**解锁路径**：① 引入 `androidx.compose.ui:ui-test-junit4` + `robolectric` 并补 12 条接线层用例（清单见上）；② 真机手势走查；③ 明确接受保留 View 层 `GestureDetector` 并从 objective 摘除本项。

**未完成项（2 项，诚实标注）**：

**① `GestureController` 仍是 `GestureDetector` + View `onTouchEvent`**（未改 Compose `pointerInput`）。本片已尝试并**主动回退**（详见上一节「该项已尝试并主动回退」）。

**② `ui/player` 4 Java 已清零（0 Java / 5 Kotlin）。** 已迁（均为纯语言迁移、逐行等价，每笔跑 `:app:assembleDebug` + **611 用例 / 0 失败**）：`PlayContainerControlListener.java`(153) → `.kt`、`TrackSelectorDelegate.java`(154) → `.kt`（提交 `ccc41e4`）；`PlayContainerViewBridge.java`(330) → `.kt`（提交 `7f0c405`，47 个 override）。`app/src/main/java` Java 30 → **27**、Kotlin 343 → **346**。**只剩 `PlayContainer`(1354 行)**，实测规模 = 117 方法 / 89 字段 / 6 匿名类 / ~30 处返回 `kotlin.Unit` 的 Java lambda + 内部类 `MyWebView` + `@Subscribe` + 4 个宿主接口实现 + 泛型 `observe` 回调 ⇒ 需单独一整轮 + 完整复核。

**`PlayContainerViewBridge` 迁移中发现的两处"必须做决定、不能字面转写"（已按同族实现口径处理）**：
- `scheduler.playerCfg()` 在 Kotlin 声明为 `JSONObject?`，而 Java 侧是平台类型直传进 `PlayerHelper.updateCfg(view, JSONObject)` ⇒ 无配置时必须显式给空对象（`?: JSONObject()`，与 `MusicPlayerActivity` 同类实现同一兜底；`updateCfg` 内部按缺键回落全局设置）。
- `playM3u8(url, headers, gen)` 的 `headers` 在 Java 侧未判空、直接转调双参重载 ⇒ Kotlin 侧保持该假定（`headers!!`），**不要**擅自补 `?: HashMap()`（那会静默改变"调用方保证非空"的既有契约）。

**本片核实出的迁移规则（续 `ui/player` 用）**：
1. **同名 `.java` 仍在时不能新建 `.kt`**：本仓库 `write` 工具会报 `file no longer exists` —— 先 `Remove-Item` 掉 `.java`，再写 `.kt`（本片两笔均如此落地）。同包内 Kotlin 类被 Java 代码 `new` 时**不需要** `@JvmStatic`/`@JvmField`：Kotlin `class` 默认 public，构造函数 Java 可见（本片 `PlayContainer.java` 仍 `new PlayContainerControlListener(this)` / `new TrackSelectorDelegate(host)`，编译通过即是证明）。
2. **`internal` 不要用**在跨语言边界（Kotlin `internal` 成员会被改名 `$module`，Java 看不到）；用默认 public。
3. **Java 包私有字段/方法在 Kotlin 侧是 public**：`PlayContainer` 的 `mVideoView`/`mController`/`scheduler`/`mActivity`/`pageHost`/`exitingPreview`/`isAttached()`/`setTip()` 等 Kotlin 直接可访问（同包 + 平台类型），无需为迁移先改 Java 可见性。
4. **平台类型的可空性要按"实参口径"显式化**：`subtitle` 可空（`subtitle ?: ""`）、`vod.name` / `TrackInfoBean.name` 在 Kotlin 侧是 `String?` 而 Java 原样直传 ⇒ 迁移时用 `!!` 复刻"直接解引用"的既有约定（**不要**改成 `?: ""`，那会静默改变行为）。
5. **Kotlin 属性 vs 函数的判定按声明来源**：`TrackInfo.kt` 里 `getAudio()/getVideo()/getAudioSelected()` 是**函数**（不是属性）；`ExoPlayer.getTrackInfo()` 也是函数；`PlayerUiState.selectDialog`、`AppPlayerView.currentPlayState`/`duration` 是**属性**（Kotlin `var`/`val` 或 `@get:JvmName` 的 Java getter）。搞错就是 Unresolved reference。
6. **lambda 内要重新取的值先落局部 `val`**：`host.player()?.mediaPlayer` 在 lambda 里重取会丢 smart cast（`Only safe calls allowed on nullable receiver`）；把内核取成非空局部量后，lambda 捕获它与旧 Java"捕获局部 mediaPlayer"逐字一致。
7. **`SelectDialogState` 的回调是 `(Int) -> Unit`**：Java 的 `pos -> { …; return kotlin.Unit.INSTANCE; }` 换成 Kotlin lambda 时，**早退点必须改写成 if/else**（Kotlin lambda 内 `return` 只能是非局部 return，条件放外层）；`if (pos >= 0 && pos < size)` 与旧 `if (pos < 0 || pos >= size) return` 等价（补集）。
8. **`new MyWebView(...)`（Java 内部类实例化）→ `container.MyWebView(...)`**：Kotlin 用"外部实例.内部类"语法；返回类型是非空 `WebView`（Java 侧是平台类型，Kotlin 接口已声明非空）。
9. **`WebView?`/`HashMap?` 形参要按 Kotlin 接口声明补空**：Java 实现原先靠平台类型宽松通过，迁 Kotlin 后必须显式接受可空（`playExternalPlayer` 的 `subtitle`/`headers`、`playM3u8(headers, gen)`、`evaluateScript(webView)`、`checkDanmu(onFailed)`）。
10. **`PlayContainerViewBridge` 的两个 `startVideoPlayback` 细节不能改**：`container.mController.hidePauseRoot()` 在 Java 侧是**未判空**调用（`mController` 由 `initView` 保证非空），Kotlin 迁移若写 `?.` 是放宽而非收紧（**可以**，但要登记）；`if (headers != null) setUrl(url, headers) else setUrl(url)` 两分支必须保留（`setUrl(String)` 与 `setUrl(String, Map?)` 语义不同：后者会清 headers）。

**`PlayContainer` 迁移实测（提交 `2b259c1`；`:app:assembleDebug` + `:app:assembleRelease` + 611 用例绿）**：`ui/player` 由 **4 Java → 0 Java / 5 Kotlin**，主包 Java **30 → 26**；实测 118 个方法 / 99 个字段全部落地（机械卡口：Java 侧方法名、字段名、调用名、`receiver.member` 多重集逐项对照，零丢语句）。Kotlin 侧的三个"必须做决定"点：

1. **`pageHost`/`exitingPreview` 改名 `mPageHost`/`mExitingPreview`**：显式 `setPageHost(PageHost)`/`setExitingPreview(Boolean)` 与 Kotlin 属性自带的 setter **撞 JVM 签名**（`Platform declaration clash`）。全仓调用方（`DetailActivity.setPageHost/setExitingPreview`、两个同包兄弟类）已一并核对无遗漏；**副作用登记**：JVM 上不再有 `getPageHost()`（只剩 `getMPageHost()`），全仓 grep 无引用，但外部/反射调用需留意。
2. **`lateinit` 让 Java 的字段判空失去守卫作用（登记,不改）**：`mController`/`scheduler`/`surfaceSlot` 用 `lateinit`（Java 里从不赋 null 且被同包兄弟类裸解引用）。实测 kotlinc 语义：`lateinit` 上 `x != null` 被**常量折叠为 true**、`x?.y` 未初始化时**抛 `UninitializedPropertyAccessException`**（不返回 null）⇒ 这些判空/安全调用从"容错跳过"变成"未初始化即抛"。**当前不可观测**（所有调用点都在 `init{}` 之后；`scheduler`/`mController`/`surfaceSlot` 分别在构造体与 `initView()` 内就位），但**将来任何在 `initView()` 之前新增的调用点会崩**。若后续要恢复容错,应把这些字段改成可空类型。
3. **`String.replaceAll` 在 Kotlin 不存在**：Java 的 `name.replaceAll("[\\\\/:*?\"<>|]", "_")` 是**正则**替换；Kotlin 侧必须写 `replace(Regex(...), "_")`。**本片踩过这个坑**：把 `replaceAll(` 批量替换成 `replace(` 时变成了**字面量子串**替换（该子串永不出现 ⇒ no-op），导致文件名含 `/` 的本地字幕导入静默失败；已由复核轮抓出并修回（提交 `fe54bc0`）。

**复核轮（2026-10-06，独立只读子代理逐方法对照 Java 原文）**：**阻断 0 / 高 1 / 中 2 / 低 1，全部处置**。① 高 = 上述 `replaceAll→replace` 回归（已修）；② 中 = `TrackSelectorDelegate` 在无内核时 `?: return` 提前返回、**跳过了 Java 会弹的"无音轨/无视频轨"提示**（已修：保留 `val mediaPlayer: KernelPlayer?` 并让 `trackInfo == null` 分支照旧弹提示）；③ 中 = `applyPlayerConfigToView` 自创 `?: JSONObject()` 回落（Java 是让 callee 抛 NPE）⇒ 属行为变更而非迁移（已改回 `!!`）；④ 低 = 上述 `lateinit` 语义（登记）。复核另确认：无丢语句（调用名与 `receiver.member` 多重集一一对应）、字段初始化顺序与 Java"全部初始化器 → 构造体"等价（Kotlin 侧无任何属性声明晚于 `init{}`）、3 处带早退的 Unit-lambda 取反全部正确、`MyWebView` 内部类与 `@Subscribe refresh` 逐句一致、25 处 override 签名无宽窄变化、线程用法（`post`/`postDelayed`/`runOnUiThread`/`Thread{}`）全保留。**另报告 9 条 Java 既有缺陷（未修，仅登记）**：`videoDuration` 死字段、`playNext(isProgress)` 忽略参数、`onServiceStopped()` 留下 stale `scheduler` 与未摘的 tip listener、`reviveEngineIfReleased()` 早退不复位、`hideExoInternalSubtitle()` 把**活的** `exoCues` 列表交给 `setCues()` 且随后在其他线程继续改它、`buildPreloadSnapshot()` 把可空分量拼成字面 `"null"` 写进 key、`showCastDialog()` 对 `webPlayUrl()` 的 TOCTOU、`openSubtitleSearchSheet()` 未判 `vod()`、`onLocalSubtitlePicked()` 未判 `openInputStream()`。
**未验证面（诚实标注）**：真机走查未做 —— 走查重点见计划档 M7e 未验证面（控制器状态回灌与图标一致性、1Hz 进度刷新、音频焦点、Surface↔Texture 热切与纯音频强制 Texture、边播缓存/预载读盘命中、点播↔直播内核复用与控制器挂摘、手势手感回归）。

# 7.20 M9 实测登记（2026-10-06，`com/github/catvod` 24 个契约类）

**结论**：24 个 Java 全量迁 Kotlin（3 笔迁移提交 `5bb03e8`/`6922608`/`e9e56b4` + 1 笔复核轮修复 `04718c6`），`com/github/catvod` **0 Java / 24 Kotlin**。`:app:assembleDebug` 绿；`:app:testDebugUnitTest` **655 用例 / 0 失败 / 0 错误 / 1 跳过（82 suite）**（与 M7/M8 基线持平 —— 纯语言迁移无新增用例）；**`javap -p -s` 公开面逐成员比对：旧有新无 = 0**（零公开成员消失/改名/描述符变化）；**Tier A 实证：用设备上两个真实第三方 jar 反汇编出的 21 个成员引用逐项核对，21/21 命中且描述符逐字一致**。

## 实测方法（本次新增的两道闸门，后续契约类里程碑照用）

1. **公开面过滤版基线**：`javap -p -s` 全量快照里只保留 `public`/`protected` 成员（含类头），落成 `skill/review/javap-m9-catvod-debug-public-{baseline,current}.txt`。契约判据 = 「名字 + 描述符 + 可见性」，`private`/包私有/synthetic 成员不属契约。
2. **归一化比对**：比对前把两侧的 `static`/`final`/`synchronized` 修饰符抹掉（Kotlin 产物必然差异，见下表），再要求**旧有新无 = 0**。
3. **Tier A 实证（最强的一道）**：真 jar 不是拿来"跑一下看崩不崩"，而是**反汇编出它实际引用的宿主成员与描述符**，再逐条到新字节码里找：
   ```
   dexdump -d <jar 解出的 classes.dex> | grep -oE "Lcom/github/catvod/(net|crawler|js)/[A-Za-z0-9_$]+;\.[^ ]*"
   ```
   两个 jar 合计引用宿主 21 个成员（`Spider` 14 / `SpiderApi` 5 / `SpiderDebug` 2），**全部命中**。
4. **真 jar 从哪来**：`adb exec-out run-as com.github.avbox.osc`（只读）从 `files/csp/<md5>.jar` 取设备上正在用的两个 jar，落盘到 `日志/spider-jars/`（`日志/` 已 gitignore）。

## 设备上两个 jar 的实测形态（Tier A 面的来源）

| jar | 体量 | 关键内容 |
| --- | --- | --- |
| `0db00b04…`（1.86 MB / 3772 类） | 全功能爬虫 jar | 109 个 `com.github.catvod.spider.*`（含 `PanAli/PanQuark/PanUC/PanTianyi/PanXunlei/QuarkPan/UCPan` 等网盘源）、`spider.Init`、`spider.Proxy`、`js.Function`、`parser.{JsonBasic,JsonParallel,JsonSequence,MixDemo,MixWeb}`、`spider.Danmu` |
| `b8f0b528…`（1.0 MB / 139 类） | wexguard 加固的网盘/媒体 jar | `spider.Init`、`spider.Proxy`、**`WebDAVGuard`、`AListGuard`、`SambaGuard`、`Emby*`**、`DexNative` + `assets/{wexguard_v7.so,wexguard_v8.so,wexshinidie.guard}` |
| `源码/1.jar`（4.1 MB / 1413 类） | FishGuard 加固的全功能 jar | 96 个 `com.github.catvod.spider.*`（`AList`、`Pan115/Pan123/PanWebShare*`、`Quark`、`UC`、`XunleiPan`、`TianYi`、`Cloud`、`Bili`、`Libvio`、`XPath*`…）、`spider.Init`/`InitOrigin`/`Proxy`/`ProxyOrigin`、`js.Method`、`utils.FishNative` + `assets/FishGuard-{v7,v8}.so`；**无 `spider.Danmaku`** |

- **jar 对宿主的继承关系实测只有一条**：`Superclass: Lcom/github/catvod/crawler/Spider;`（三个 jar 共 179 个 spider 类全部如此），**没有任何 jar 类实现宿主接口、也没有别的宿主父类** ⇒ 全仓只有 `Spider` 必须保持非 final。
- **`源码/1.jar` 的 Tier A 引用面是三个 jar 的交集子集**（`Spider.<init>()V`、`Spider.init(Context,String)V`、`SpiderDebug.log(String)V`、`SpiderDebug.log(Throwable)V`），无 `Lcom/github/tvbox/osc/*` 反向引用 ⇒ 三 jar 独立复核一致。
- **jar 引用的 `Spider` 成员（14 项）**：`<init>()V`、`init(Context)V`、`init(Context,String)V`、`initApi(SpiderApi)V`、`safeDns()Lokhttp3/Dns;`（静态）、`homeContent(Z)String`、`homeVideoContent()String`、`categoryContent(String,String,Z,HashMap)String`、`detailContent(List)String`、`searchContent(String,Z)String`、`searchContent(String,Z,String)String`、`playerContent(String,String,List)String`、`action(String)String`、`destroy()V`。
- **jar 引用的 `SpiderApi`（5 项）**：`getAddress(Z)String`、`getPort()String`、`log(String)V`、`multiReq(JsonArray)String`、`webParse(String,String)String`。
- **jar 引用的 `SpiderDebug`（2 项）**：`log(String)V`、`log(Throwable)V`。
- **⚠️ 三个 jar 都没有 `com.github.catvod.spider.Danmaku`**（设备 jar A 只有 `spider.Danmu`，那是站点类不是 UI 钩子；`源码/1.jar` 只有站点自带的 `HonHonDanmu`/`DanmuApi` 内部实现，dex 里既无该类定义也无类型引用）。**这个钩子是上游 TVBox 的约定**（`示例文件/上游项目/.../crawler/JarLoader.java:137` 与本项目 `JarLoader.invokeDanmaku` 逐字相同）⇒ 只有 TVBox 血统的 jar 才可能有，fongmi 血统没有。**用户已决定不做该项验证（2026-10-06）**，为它临时造的契约桩 jar 与三个 jar 的解包产物均已删除；若日后要补验，配方见用户级 skill `android-contract-preserving-kotlin-migration`（`javac` + `d8` 打一个只含 `Danmaku.onClick/onLongClick` 的 dex-in-jar）。

## 登记的产物差异（debug 侧；全部为 Kotlin 必然产物或已证明等价的改写）

| # | 差异 | 数量 | 判定 |
| --- | --- | --- | --- |
| 1 | 类/方法多 `final`；`static` 方法变 `static final` | 全部类 | Kotlin 必然产物。**唯一必须非 final 的 `Spider` 实测为 `public class`**（无 ACC_FINAL），14 个可覆盖方法实测无 `final` |
| 2 | 每个带 companion 的类多出 `Companion` 静态字段、`$stable`、`access$*` 合成桥、`DefaultConstructorMarker` 合成构造器 | 新增 83 项 | Kotlin 必然产物（M6b 同口径） |
| 3 | `OkHttp` 4 个 `static synchronized`（`dns`/`client`/`reset`/`resetClient`）的 ACC_SYNCHRONIZED 改为方法体内 `synchronized(OkHttp::class.java)` 块 | 4 | **等价**：Java 的 `static synchronized` 锁的就是 Class 对象，锁对象与覆盖范围完全一致（Kotlin 无法表达 `static synchronized`） |
| 4 | `Connect.withTimeout`、`Connect.client` 由包私有放宽为 `public` | 2 | 包私有→public 属放宽（规则 g 允许）；`withTimeout` 另被 `ConnectTimeoutTest` 单测直接调用 |
| 5 | `OkDns` 多出 `newCall(Dns$Request)` 桥 | 1 | Kotlin 实现带默认方法的 Java 接口时的必然产物 |
| 6 | `Trans$Loader`（私有 holder 类）消失；`Async`/`FunCall`/`Trans` 的私有成员迁入 `Companion` | — | 私有、非契约（M6b 规则 8 同口径） |
| 7 | Java 里**恒真**的 `response.body() != null` 判空被删（`OkHttp.string` ×3、`JarLoader.download`、`JsLoader.loadJarInternal`） | 5 | okhttp 5 的 `body` 是非空属性，Java 那句本就恒真 |
| 8 | `JarLoader.getServerPort` 里对 `ControlManager.getAddress` 返回值的判空被删 | 1 | 该方法已是 Kotlin 非空返回，判空恒真 |
| 9 | `SpiderApi.getScreenOrientation` 里 `AppManager.currentActivity()` 的判空**保留** | 1 | 恒假告警（Java 那句本就恒假）；保留以贴近原文 |

## 本里程碑现场核实出的规则（M10/M11 与后续契约类照查）

1. **`protected` 可以放在 `companion object` 里**（实测编译通过且产物为 `protected static`）：`Spider.mContext` 用 `@JvmField protected var mContext: Context? = null` 逐形态保住了 Java 的 `protected static Context mContext` —— 计划 §7.1 规则 e 的关键一条，jar 子类可直接读写该字段。
2. **`javap -p -s` 的成员行含泛型实参**（`java.util.HashMap<java.lang.String, java.lang.String>`），而 `descriptor:` 行是擦除形态 ⇒ **契约比对必须认 `descriptor` 行**，用成员行做字符串等值比较会误报。
3. **Kotlin 里不能 `import java.util.List/HashMap/Map`**：导入后 `java.util.List` 与 `kotlin.collections.List` 在编译器眼里是**两个类型**，`ArrayList<String>()` 赋给 `List<String>` 报 `Return type mismatch`，且**会连带让子类 override 的参数类型不匹配**。契约类只写 Kotlin 的 `List`/`HashMap`/`Map`（JVM 描述符本来就是 `java.util.*`）。
4. **Kotlin 的 ASI 会把「行尾带 `//` 注释的字符串拼接」拆断**：`"a" // 注释` 换行 `+ "b"` 在 Java 合法，在 Kotlin 被解析成「语句结束 + 一元 `+`」⇒ `Unresolved reference 'unaryPlus'`。拼接的 `+` 必须放行尾，注释另起一行。
5. **`String(bytes, charsetName)` 在 Kotlin 没有对应构造器**（只有 `String(ByteArray, Charset)`）⇒ 用 `Charset.forName(name)` 等价替代（未知字符集时异常类型从 `UnsupportedEncodingException` 变 `UnsupportedCharsetException`，同为 `catch (Exception)` 兜底，登记为口味差异）。
6. **okhttp 5 的弃用是「错误级」而非告警级**：`Headers.of(map)` / `Call.dispatcher()` / `MediaType.get` / `RequestBody.create` 在 Kotlin 里**直接编译失败**（`DEPRECATION_ERROR`），必须换成 `toHeaders()` / `.dispatcher` / `toMediaTypeOrNull()` / `toRequestBody()`；而 Java 的 `@Deprecated`（`URLEncoder.encode(String)`、`Class.newInstance()`）只是告警，可原样保留。
7. **`String.trim()` 必须逐处对齐**：Java 只裁 `<= ' '`，Kotlin 的 `trim()` 按 Unicode 空白裁（含 U+00A0/U+3000）⇒ 统一写 `trim { it <= ' ' }`。**本里程碑在 6 处踩到**（两个 jar md5 字段、HTML 列表文本、JSON header 值、DNS host 拆分），由复核轮抓出并修复（`04718c6`）。
8. **`Objects.requireNonNull(x)` 在 Kotlin 里推不出非空**（`T` 会被推成 `String?`）⇒ 直接写 `x!!`（NPE 语义一致）。
9. **`Any?.toString()` 对 null 返回 `"null"` 而 Java 的 `o.toString()` 抛 NPE** ⇒ 需要 NPE 的点必须写 `o!!.toString()`（`JsSpider.getStream` 是实例）。
10. **`CharSequence.trim()` 的谓词形式是 Java `String.trim()` 的唯一等价写法**（`trim { it <= ' ' }`）；`StringUtils.trim` 是项目自定义的（额外裁 U+3000），**不能**拿来替代 Java 的 `trim()`。
11. **可空性判据（本里程碑口径）**：Java body **能容忍 null** 的入参（显式判空、字符串拼接、`isEmpty` 守卫、或本地 catch 吞掉 NPE）一律迁 `T?`；Java body **必然解引用且异常会逃出方法**的入参可迁非空（边界 NPE 与原抛点等价）；Java 的解引用**在本地 try 内被吞掉**的，写 `T?` + `!!` 放回 try 内。**实现方与接口/基类的可空性必须同批改**：`Spider` 的 `init/categoryContent/searchContent/playerContent` 改成 `T?` 时，`JsSpider`/`PythonSpider` 的 override 必须同步（Kotlin 的 override 参数类型要精确一致）。
12. **接口参数可空性要跟着唯一实现走**：`IPyLoader.getSpider(key: String, …)` 的 `key` 定非空（实现 `pyLoader.kt` 的 `key` 本就在 M6b 定成非空），代价是 `SpiderLoader` 的 5 个调用点补 `!!`（Java 版在 `ConcurrentHashMap.containsKey(null)` 处同样 NPE，等价）。
13. **Tier A 的"真实回归"应该用反汇编枚举引用面，而不是只跑一遍**：`dexdump -d` 反汇编 jar 后 `grep` 出 `Lcom/github/catvod/...;->成员` 即得**必须存在的成员清单**，可逐条到新字节码验证 —— 比"点开源看崩不崩"覆盖更全、可复现。
14. **契约层迁移的"由内向外"顺序有效**：先 `crawler/js/*`（无 jar 直连），再 `SpiderDebug/SpiderNull/IPyLoader/OkDns`，再 `Spider/SpiderApi`，最后 `JarLoader/JsLoader/OkHttp/Proxy`；每步都构建 + 单测 + `javap`，`Spider` 那一步单独核对 Tier A 清单。

## 审查轮（2026-10-06，三个独立只读子代理逐方法对账）

- **A（契约与加载器：`Spider`/`SpiderApi`/`JarLoader`/`JsLoader`）**：**阻断 0 / 高 0 / 中 0**；2 条低 + 1 条口味 —— `JarLoader`/`JsLoader` 的 md5 `trim()` 未对齐（**已修**）；`Spider` 三个非空入参（`pg`/`id`/`action`）的 `checkNotNullParameter`，调用点全传非空且 jar 覆盖后不执行基类体 ⇒ 不可达；`proxyInvoke` 的 `!!` NPE 点位差异。
- **B（JS 桥与网络栈：`JsSpider`/`Global`/`Connect`/`HtmlParser`/`Trans`/`Json`/`Req`/`Res`/`Crypto`/`Async`/`FunCall`/`local`/`OkHttp`/`Proxy`）**：**阻断 0**；1 条高 + 1 条中（都是 `trim()` 语义 —— `HtmlParser.parseDomForList` 的列表文本、`Json.safeString` 的 header 值，**已修**）+ 2 条低 + 1 条口味。B 另逐点确证等价：split 全走 `RegexUtils.getPattern(x).split(y)`、字符集/locale 全对齐、`replaceAll(regex,"$1")` ≡ `Regex.replace(...,"\$1")`、反射 vararg 全 `*args`、okhttp 5 替换的 null 边界与抛点一致。
- **C（第三角度：装箱比较/数值解析与进制/字符串 API 边界/集合迭代顺序/反射与类初始化时点/异常控制流，并逐例复核那 6 处 `trim`）**：**本轮无 阻断/高/中**；仅 2 条低/口味 —— `Connect.cancelByTag` 的 `tag!!` 被我提到 `if (client != null)` 之外（Java 只在分支内解引用，**已修** `28fad04`）；`SpiderDebug` 的 `"" + msg`（Android 对 null msg 同样渲染 `null`，且是 `LOG.kt` 既有写法，登记不改）。C 另逐点确证：装箱 `==`/拆箱点、`toInt()` ≡ `Integer.parseInt`、`and 0xFF` ≡ `& 0xFF`、Base64 flag 组合、`ArrayList(rules.toList())` ≡ `new ArrayList<>(Arrays.asList(...))`、`getMethod`/`getDeclaredConstructor`/`declaredClasses`/`getMethods` 同源同序、`switch`→`when else`、companion 初始化顺序全部一致；`it <= ' '` 与 Java `String.trim()` 在空串/全空白/单字符/单端空白/U+00A0/U+3000 逐例等价。
- **收敛结论**：第 3 轮（修复后的干净轮）**无 阻断/高/中**，剩余全属低（不可达或仅异常类型/点位）或口味 ⇒ **达到计划的收敛终止线**。逐条见 `skill/review/review-20261006-m9.md`。

- **⚠️ 一条给后续契约类里程碑的方法论**：三轮里**唯一**被两个不同角度同时命中的真问题族是「**Java 与 Kotlin 同名 API 的语义差**」（`trim` 6 处）；这类问题的特征是「编译通过、常规输入无感、边界输入才偏」。所以**别只按"文件"分工复核，要按"API 语义轴"分工**（split/trim/字符集/locale/装箱/数值解析/集合顺序/反射时点各一轮），覆盖面比按文件扫更全。

## Tier B 变化（D9）

实测 **13 个符号**（M0 记 11）：
- 新增 `osc.util.AppManager` —— Java 版 `SpiderApi` 用**内联 FQCN** `com.github.tvbox.osc.util.AppManager.getInstance()` 调用，脚本按 `import` 生成清单时**漏掉了它**（计划附录 B 已登记此盲区）；迁 Kotlin 后变成正规 import，清单因此补齐。
- 新增 `osc.util.RegexUtils` —— 真新增：为保住 Java `String.split(regex)` 的"丢尾部空串"语义而引入（M6b 规则 15 的同一做法）。
- 其余 11 个符号未变，静态调用形态由 `object` + `@JvmStatic` 保住（`javap` 实证）。

## 未验证面（诚实标注）

1. **真机走查未做**（本轮只做只读抓取，未安装、未启动界面）：需在设备上验证 `jar 源 / js 源 / py 源` 各开一次，含搜索、分类、详情、播放、直播、DLNA、代理（`/proxy`）。
2. **`assembleRelease` 未跑**（需用户明确许可）：契约层的 release 侧 `javap`（R8/keep 覆盖）**只做了 debug 侧**。计划 §7.1 规则 j 要求 debug + release 各一次 ⇒ 这一半**空缺**。
3. **danmaku 反射链**：三个 jar 都没有 `com.github.catvod.spider.Danmaku`（该钩子只可能出现在 TVBox 血统的 jar 里）⇒ **用户已决定不做该项验证（2026-10-06）**，相关临时产物（契约桩 jar、三 jar 解包目录、`dexdump` 全量转储共 236 MB）已删。**该链真机未跑、也不再安排。**
4. **wexguard 加固 jar 的 native 解密路径**（`assets/wexguard_*.so` + `DexNative`）未在真机验证。
5. **`spider.Danmu`（数据类）与 `PanWebShare*` 等网盘源的真实调用**未验（需真实站点与账号）。
6. **`OkHttp.reset()/resetClient()` 的 Class 锁在 release 混淆下**未验（debug 侧已确证锁对象一致）。

# 7.21 M10 实测登记（2026-10-06，旧播放骨架拆除：`player` 模块 + `dkplayer-ui` + 残留）

**性质**：本里程碑**不是迁移**（D12 已把 `player` 模块 27 Java 判为"整体替换删除"，不迁 Kotlin），是**纯拆除**。判据 = `player/` 目录与 Gradle include 均不存在，而非"Java 计数为 0"。

**交付**（35 个受版本控制文件删除 + 6 处配置改动）：
- 删 `player/` 整模块（27 Java / 5313 行 + `res/values/attrs.xml` + `jniLibs/arm64-v8a/*.so` ×3 + `proguard-rules.pro` + `.gitignore`）；
- `settings.gradle.kts` 删 `include(":player")`；`app/build.gradle.kts` 删 `implementation(project(":player"))`；
- `gradle/libs.versions.toml` 删 `dkplayerUi = "3.3.7"` 与 `dkplayer-ui = { … }`；
- `.github/dependabot.yml` 删 `xyz.doikki.android.dkplayer:*` 忽略项（M7-0 已删 `api(libs.dkplayer.ui)`，此处是依赖项彻底清零）；
- `gradle.properties` 的 `nonTransitiveRClass` 注释去掉 dkplayer 措辞。

**⚠️ 两处计划未列的隐藏耦合（本轮最重要的发现，拆除类里程碑必查）**：
1. **`api(...)` 传递依赖会在删模块时整批消失**。`app/build.gradle.kts` 原本只声明 `media3-effect`，其余 9 项 media3（`exoplayer` / `-dash` / `-hls` / `-rtsp` / `datasource` / `datasource-rtmp` / `database` / `ui` / `ffmpeg-decoder`）全靠 `:player` 的 `api` 暴露 ⇒ 直接删模块**编译期即炸**。已按 player 的 `api` 清单逐项搬进 app 的 `implementation`（保持同一运行时类路径）。**判据不是"app 源码有没有 import"，而是"依赖是从哪一层传递来的"** —— `implementation(project(":x"))` 会继承 `:x` 的 `api` 面，但**不继承**它的 `implementation` 面（`okhttp`/`androidx.annotation` 因此不需要搬）。
2. **清单权限会随模块删除静默消失**。`WAKE_LOCK` 此前**只在 `player/src/main/AndroidManifest.xml` 声明**，而 app 侧 `PlaybackService.kt:431` 用 `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK, …)` ⇒ 删模块后运行时 `SecurityException`，**编译期与单测都发现不了**。已补进 app manifest。**通用做法：删模块前把该模块清单的 `uses-permission`/`uses-feature`/`<application>` 子项逐条与 app manifest 对账**（本例 5 条权限中 app 已有 4 条，只差 `WAKE_LOCK`）。
3. jniLibs 归位（M7e 复核轮已预告）：`player/src/main/jniLibs/arm64-v8a/{libp2p.so, libxl_stat.so, libxl_thunder_sdk.so}` → `app/src/main/jniLibs/arm64-v8a/`，md5 逐个一致；`P2PClass` 的 `System.loadLibrary("p2p")` 不再断。

**实测**：`:app:assembleDebug` **BUILD SUCCESSFUL**；`:app:testDebugUnitTest` **655 用例 / 0 失败 / 0 错误 / 1 跳过（82 suite）**（与 M9 基线持平 —— 纯拆除无新增用例）；改动文本文件全 LF（`i/crlf` = 0）。

**断言实测（可复现）**：
- `xyz.doikki` 在 `settings.gradle.kts` / `*.toml` / `*.pro` **零命中**；`app/src` **零 import、零代码引用**，仅剩 **8 行 KDoc 溯源注释**（7 文件：`player/ExoPlayer.kt` 2 行 + `player/AppPlayerView.kt`/`player/KernelPlayer.kt`/`player/MyVideoView.kt`/`player/host/PlayerRenderView.kt`/`util/PlayerUtils.kt`/`util/CutoutUtil.kt` 各 1 行，内容为"取代 fork 的 X / 移植自 doikki Y"）—— 判定为**有价值的设计溯源，保留不动**。
- **dex 层（debug APK，29 个 `classes*.dex`）零 `xyz/doikki`、零 `BaseVideoController`/`AbstractPlayer`/`dkplayer` 串**，APK 路径条目零 doikki/dkplayer；**对照 `com/github/catvod/crawler/Spider` 有命中**，证明检索口径有效（`unzip -p` 取 dex 后 `grep -a`）。检索命令：`unzip -o -q app/build/outputs/apk/debug/AVBox_debug.apk 'classes*.dex' -d <tmp> && grep -a -o "xyz/doikki[a-zA-Z0-9/]*" <tmp>/classes*.dex`。
- 依赖归位实证：APK 含 `libffmpegJNI.so`（jellyfin 软解）与 `librtmp-jni.so`（RTMP）；merged manifest 含 `WAKE_LOCK`。

**与计划的偏差（已登记）**：① 计划拆除清单列了「`app/proguard-rules.pro` 中 `xyz.doikki` 相关 keep 规则」，**实测该文件无任何 doikki 专属规则** —— 唯一保留 fork 类的是通用 `-keep public class * extends android.view.View`（`app/proguard-rules.pro:61`），模块删除后自然失效 ⇒ 该项**无事可做**，proguard 未改（原计划把它当成"需清理的 keep 规则"是误判）；② 断言原写「`app/src` 零命中」，实测有 8 行 KDoc（M7e 已声明该口径），按上文保留。

**未验证面（诚实标注）**：① **`:app:assembleRelease` 未跑**（需用户明确许可）⇒ release 侧 dex 清零与 R8/keep 覆盖只做了推理（模块不在类路径 ⇒ 无从保留），未实测；② **真机冒烟未做** —— 重点两条链是 **P2P/迅雷（原生库 `libp2p.so`）** 与 **软解 ffmpeg（`libffmpegJNI.so`）**，另需按 `avbox-playback-service-spec.md` §4 清单走查点播起播/切集/全屏旋转/手势/清晰度/字幕/弹幕/直播/音乐通知/DLNA 投屏；③ 计划前置「M7 全切片真机走查无回归」按 §5 M7 记录**仅手势已走查** ⇒ 本轮删除了 fork 源码这个回退面，回滚须走 `git revert`。

**顺带同步的文档（M10 直接证伪的断言）**：`skill/avbox-code-review-spec.md` 的"技术栈实况"（语言构成 / 模块清单 / 播放内核 / 排除范围四处）、`skill/SKILL.md` 的迁移规范条目与"契约层"高危约束（去掉 `player` 模块与 `xyz.doikki.videoplayer.**`）、`skill/avbox-playback-service-spec.md` 顶部加"播放栈实现口径已换代"横幅（该 spec 自 M7 起实现层描述已作废，全量重写仍未做）。

# 7.22 M11 实测登记（2026-10-06，终审：全库自有源码零 Java）

> ⚠️ **2026-10-10 更正**：本节的"全库零 Java"口径当时**未覆盖 `app/src/test`**（测试源码仍在范围外，实存 11 个 Java，另 `app/src/python/java` 的 5 个已于 M11 前迁完）。当日已把测试侧与 `quickjs/` 本地模块的 Java 全部迁完 ⇒ **现在全仓自有源码零 Java**（含 `quickjs/` 本地包装层；真正第三方的是 maven 依赖，见 §7.24）。详见 §7.23 与 §7.24。

**结论**：四处零 Java 判据全部满足；`:app:assembleDebug` 绿、`:app:testDebugUnitTest` **655 用例 / 0 失败 / 0 错误 / 1 跳过（82 suite）**（`--rerun-tasks` 真跑，与 M9/M10 基线持平）；契约面 `javap -p -s` catvod 公开面 25 类 **旧有新无 = 0**；**收敛轮抓出并修复 2 条「本次迁移引入」的「中」**。

## 本轮新增的一道闸门：全库「API 语义轴」扫描（后续任何迁移项目照用）

M9 已写下教训「**别只按文件分工复核，要按 API 语义轴分工**」，但当时**只在 `catvod` 单包执行**。M11 把它推广到**全库 380 个 `.kt`**，方法：

1. 按语义轴全库 grep：`trim()` / `lowercase()` / `uppercase()` / `split(` / `replaceAll(` / `String(`+`getBytes` / `toInt()` / `roundToInt()` / `Math.round` / `String.format` / `==` / `indexOf` / `substring` / 集合构造与迭代顺序 / `Objects.hash` / `Random` / `SimpleDateFormat` / `Class.forName` 等。
2. **逐处取 Java 原文对照**：`git log --diff-filter=D --name-only -- '**/<类名>.java'` 找删除提交 → `git show <该 commit>^:<旧路径>` 取原文。
3. 判「触发条件是否真实可达」：输入域不含触发字符的降为 低。
4. **判「是否迁移引入」**：`git log --diff-filter=D -1 -- '**/<类名>.java'` 有命中 = 该文件由 Java 迁来；零命中 = 迁移前就存在的 Kotlin（属「既有」，不计入本轮判据）。**这一步很关键** —— 否则会把既有 Kotlin 的 `trim()` 当成迁移缺陷，把 diff 扩大 13 个文件。

**结论：唯一成体系的回归仍是 `trim()` 语义差**（M9 在 catvod 已修 6 处），本轮在 **`player` 包（晚于约定确立）与 `bean` 包（早于约定确立）** 各抓出一批 ⇒ **M9 当时「该问题族只存在于 catvod」的结论是覆盖面不足导致的乐观**。

## 本轮发现与处置

| # | 发现 | 数量 | 严重度 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | 播放链 **UA / header** 用 Kotlin `trim()`（Unicode 版）而非 Java `String.trim()`：`PlayUrlResolver.kt:279,329,433,468`、`PlaybackFetch.kt:149`、`ReexPlayer.kt:69`（原文见 `git show e904581^`/`6022d20^`/`c357978^`） | 6 | **中** | **已修** → `trim { it <= ' ' }` |
| 2 | `PreloadManagerHolder.kt:295` 的 header 签名用 plain `trim()`，引擎侧 `MediaSources.kt:240` 用 `trim { it <= ' ' }` 且 KDoc 声明「与预载侧 key 口径一致」⇒ 两侧 key 不同、**预载缓存静默不命中** | 1 | **中** | **已修** |
| 3 | 同类低危：`bean/AbsJson.kt:239,241`、`bean/Depot.kt:19,23`、`PlayUrlResolver.kt:202,206`、`PlaybackFetch.kt:141`、`danmu/DanmuLoadController.kt:96`、`danmu/Parser.kt:65`、`usecase/M3u8PurifyUseCase.kt:90,94`、`usecase/WebParseUseCase.kt:40,49,51` | 14 | 低 | **已修**（同族一并收敛） |
| 4 | 迁移引入的未使用 import：`dlna/OkHttpStreamClient.kt`（`okhttp3.MediaType`/`Response`，M6b `89a7c3d`）、`player/PlaybackFetch.kt`（`VodInfo`/`HashMap`，M7c `6022d20`） | 4 | 低 | **已修** |
| 5 | 13 个**既有** Kotlin 文件的未使用 import（约 20 个：`EdgeToEdgeTopBar` 6 / `LivePlayActivity` 3 / `CollectPage` 3 / `FloatingNavBar` 2 等） | ~20 | 低 | **登记不改**（非迁移引入；一次扫 13 个 UI 文件会扩大 diff 而不降低风险） |
| 6 | `lowercase()`/`uppercase()` 无参版 = `Locale.ROOT`，原 Java 无参 `toLowerCase()` = `Locale.getDefault()`（10 文件 16 处） | 16 | 低/口味 | **登记不改**（仅土耳其语系可见，且 Kotlin 方向更正确） |
| 7 | `bean/ParseBean.kt:35` `toByteArray()`（固定 UTF-8）替代 Java `getBytes()`（平台默认） | 1 | 低/理论 | **登记不改**（Android `defaultCharset` 恒为 UTF-8，解码侧也显式用 `Charset.defaultCharset()`） |

**合计改动：10 文件 21 处 `trim` + 2 文件 4 个 import = 11 文件 21 insertions / 25 deletions。**

## 本轮确证等价的面（覆盖面证据，供后续复用）

- `split(regex)` 全走 `RegexUtils.getPattern(x).split(y)`（M3u8 11 处、TxtSubscribe、SourceHelper、OkGoHelper…）；`split(regex, limit>0)` 用 Kotlin `split` 且尾部空串语义一致。
- `replaceAll(regex,"$1")` ≡ `replace(Regex, "\$1")`（17 处正则串逐处一致）；Java 对象 `==` 身份比较全部迁成 `===`（`SourceResultParser` 8 处等）。
- 字节↔字符串转换全部显式 `Charsets.UTF_8` / `Charset.defaultCharset()`；`String.format` 的 locale 逐处保留。
- 数值：未出现 `roundToInt()`（新代码用 `Math.round` 保 floor(x+0.5)）；`toInt()` 与 `(int)` / `Integer.parseInt` 逐处一致。
- **Gson**：`bean/` 21 类字段名一字未改，`@SerializedName`/`@Expose` 保留。**XStream**：`@XStreamAlias`/`@XStreamAsAttribute`/`@XStreamImplicit`/`@XStreamConverter` 全保留，`AbsXml`/`AbsSortXml` 继承链未变。
- **Room**：`app/schemas/**/1.json` 未改动，三实体列名/notNull 与源码逐列一致。
- **反射**：宿主侧无对**宿主类**的反射查找；`JarLoader`/`JsLoader` 的反射名与 FQCN 字符串（属**外部 jar 的契约**）逐字未动。
- **JNI**：`P2PClass` 26 个 `external fun` 与 Java 原文名字/参数/返回逐字一致；`.so` 三件在 `app/src/main/jniLibs/arm64-v8a/`。
- **Manifest**：15 个相对类名全部命中 `.kt`；`WAKE_LOCK` 在册。

## 一条给「终审类里程碑」的方法论

1. **语义轴扫描必须覆盖全库**，不能只在「问题首次出现的包」里做 —— 本轮的 2 条「中」全在 catvod 之外。
2. **必须区分「迁移引入」与「既有」**：判据 = 该文件是否有被删的 `.java`（`git log --diff-filter=D -1 -- '**/<类名>.java'`）。既有 Kotlin 的同名写法不计入收敛判据，只登记。
3. **`trim()` 是 Java→Kotlin 迁移的头号语义差**（Java 裁 `<= ' '`，Kotlin 裁 Unicode 空白）。凡 header / UA / URL / 文件名 / 协议串 / 配置值的裁剪点，一律 `trim { it <= ' ' }`；新写代码无 Java 对应物时可用 Kotlin `trim()`。
4. **同一语义的「两侧实现」要成对检查**（本例：预载侧签名 vs 引擎侧缓存 key）—— 单看一个文件永远发现不了「口径不一致」。

# 7.23 测试源码迁移（2026-10-10，`app/src/test` Java 清零）

**性质**：**纯语言迁移、零逻辑改动**（用户要求「将 java 测试全部迁移到 kotlin」）。11 个文件 / 1241 行 → 11 个 `.kt`，**用例数逐类守恒**：ConfigParser 31 / Depot 7 / VodInfoReverse 2 / EpisodeMatcher 16 / FileUtilsNativeLibRepair 7 / SearchHelper 5 / PlaybackAttemptState 6 / SubtitleFilePicker 11 / BootGuard 22 / KVKeySpec 12 / KVDecoder 20 = **139 例**；全量 `:app:testDebugUnitTest` **891 用例 / 0 失败 / 0 错误 / 1 跳过（112 suite）**，与迁移前基线**逐项相同**。迁移后仓库自有源码零 Java，残留 4 个在 `quickjs/` 本地包装层（**同日随后已一并迁完，见 §7.24**）。

**测试迁移与 main 迁移的差别（本节踩点全在这里）**：

1. **私有字段不能用属性语法**：`SourceBean.isIndexSource` 是 `val`（写 `a.isIndexSource`，**不是** `isIndexSource()`）；`Depot.name/url` 是 **private 字段 + `getName()/getUrl()`**，Kotlin 侧只能用 getter（`items[0].getName()`）—— Java 侧"能编译"是因为那本来就是方法调用。
2. **平台类型 vs 可空类型决定断言重载**：`SourceBean.header` 声明 `MutableMap<String,String>?` ⇒ Kotlin 侧要 `!!`（`val h = a.header!!`）；而 `getUrl()/getName()/safeString` 系列返回**非空** ⇒ `assertEquals` 与 Java 侧同重载。
3. **`TypeRegistry` 不是 `fun interface`**：Kotlin **不能**给它传 lambda（`KVDecoder.TypeRegistry { … }` 报 "does not have constructors"）。Java 能写 lambda 是因为 Java 对**任意** SAM 接口都允许。测试侧写 `object : KVDecoder.TypeRegistry { override fun typeOf(key: String): Type? = … }`（或命名类），**不要**为了测试方便把生产接口改成 `fun interface`（那是扩大生产面改动）。
4. **`Int::class.javaPrimitiveType` 是 `Class<Int>?`**，喂给 `Class<*>` 形参类型不匹配 ⇒ 一律写 `Int::class.java`（`coerceNumber` 只按类型判定）。
5. **`null` 实参需要显式类型**：`BootGuard.addDisabledSource(null, …)` 会把 `ArrayList<Nothing>?` 传给 `ArrayList<String>?` ⇒ 先落 `val noList: ArrayList<String>? = null` 再传（Java 由目标类型推断，Kotlin 不会）。
6. **`TemporaryFolder` 规则用 `@get:Rule`**（Kotlin 注解默认落字段，JUnit 要 getter）；`throws Exception` 在 `@Test` 方法上**不加** `@Throws`。
7. **`getBytes`/`String(byte[])` 的平台默认字符集陷阱在测试里同样存在**（§7.9 规则 5）：保留 `content.getBytes(StandardCharsets.UTF_8)`，不要图省事写 `toByteArray()`。
8. **`(long) Integer.MAX_VALUE + 1` → `Integer.MAX_VALUE.toLong() + 1`**：Java 的强转 + 隐式提升必须显式化，否则 `Int` 运算溢出。
9. **复核判据 = 用例数逐类守恒 + 全量基线不变**，不是"编译通过"：跑 `:app:testDebugUnitTest` 后与迁移前的 `TEST-*.xml` 逐类对齐 `tests/failures/errors/skipped`（本次 112 suite / 891 例逐项相同）。**测试迁移最容易出的错是"断言被静默放宽"**（`assertEquals` 换 `assertTrue`、可空断言写成非空断言），这类错编译完全看不出来。

# 7.24 `quickjs/` 本地包装层迁移（2026-10-10，全仓自有源码 Java 清零）

**⚠️ 先纠正一个认知（2026-10-10 实测）**：`quickjs/` 模块**不是** vendored 第三方代码。真正第三方的是两个 maven 依赖：

| 依赖 | 提供 |
|---|---|
| `wang.harlon.quickjs:wrapper-java:3.2.3` | `com.whl.quickjs.wrapper` 包下 **21 个类**（`QuickJSContext`/`JSObject`/`JSArray`/`JSCallFunction`/`JSMethod`/`JSFunction`/`ModuleLoader`…） |
| `wang.harlon.quickjs:wrapper-android:3.2.3` | `com.whl.quickjs.android.QuickJSLoader` + 4 个 ABI 的 `libquickjs-android-wrapper.so` |

`quickjs/` 本地模块里**只有 4 个自己写的文件**，只是**借用了上游包名**（`com.whl.quickjs.wrapper`）：`ContextSetter`/`Function`（注解，抄自上游 0.8.1+，我们依赖的 3.2.3 jar 里**没有**）、`JSUtils`（自有工具 + `BugReview #22` 的 `& 0xFF` 修复）、`UriUtil`（**AOSP `UriUtil` 原样搬运**，与 JS 引擎无关）。⇒ **上游那 21 个类不能迁**（迁了 native 对不上），这 4 个可以，且已迁完。

**结论**：4 个文件 / 实际逻辑约 130 行 → 全 Kotlin，`quickjs/src` **0 Java**。构建侧唯一改动 = `quickjs/build.gradle.kts` 加 `alias(libs.plugins.kotlin.android)` + `kotlin { compilerOptions { jvmTarget = JVM_21 } }`（该模块原本是纯 Java library 模块）。**调用点零改动**（`Connect.kt`/`Global.kt`/`JsSpider.kt` 用 Kotlin 语法照旧）。全量 `:app:testDebugUnitTest` **904 用例 / 0 失败 / 0 错误 / 1 跳过（113 suite）**（891 基线 + 新增 `UriUtilTest` 13 例）。已装机。

## 先写测试再迁（本次的方法论，值得照用）

`UriUtil.resolve` 是 AOSP 的**下标算术**（`StringBuilder.append(cs,start,end)` / `lastIndexOf(char,from)` / `removeDotSegments` 的原地 delete + limit 修正），且是 **JS 模块路径解析的唯一实现**（`JsSpider:398`），此前**零单测覆盖**。故：

1. **先补 `app/src/test/.../quickjs/UriUtilTest.kt`（13 例）并在 Java 版上跑绿** —— 这组用例就是行为基线
2. 再迁 Kotlin，**同一组用例原样跑绿** ⇒ 等价性由用例证明，而不是"看着一样"
3. 覆盖的边界：相对/`./`/`../`（含多级越界不逃根）/根路径/网络路径引用（`//host`）/相对 base 无 scheme/`#frag` 与 `?q=1` 引用/绝对引用里的点段/null 入参（→ 空串）/authority-only base/`isAbsolute`

⚠️ **`resolveToUri` 在 JVM 单测里不可测**：`Uri.parse` 在 Robolectric 下返回 **null**（`android.net.Uri` 静态桩），写了必失败。它是 `resolve` 的一行包壳，只在真机/仪器测试可验 —— 已在用例里注明并删除该条。

## 四个实测踩点

1. **注解的 `Retention` 应显式写 `RUNTIME`（本次写法，属"自证意图"而非"救火"）**：`@Function`/`@ContextSetter` 是被**反射**消费的（`JsSpider.bind` 的 `receiver.javaClass.methods` + `method.isAnnotationPresent(...)` + `method.invoke(...)`），所以保留级别必须可运行时读取。
   ⚠️ **本规范初稿在此写错过一处，此处更正**：初稿称"Kotlin 注解的默认保留级别是 `BINARY`，不写 `@Retention` 就运行时读不到"——**实测不成立**。`kotlin.annotation.Retention` 的 `AnnotationDefault` 本身就是 `AnnotationRetention.RUNTIME`（证据：`javap -v -p -cp <kotlin-stdlib.jar> kotlin.annotation.Retention` 打印 `AnnotationDefault: default_value: Lkotlin/annotation/AnnotationRetention;.RUNTIME`），且 Kotlin 一定会把映射后的 `java.lang.annotation.Retention` 写进字节码。`CLASS` 是 **Java 侧** `java.lang.annotation.Retention` 的默认值，与 Kotlin 注解的生成无关。⇒ 不写 `@Retention` 也能被反射读到，它不是"静默失败点"。
   结论不变的是**判据**：`javap -v` 看到 `RetentionPolicy.RUNTIME` 与 `ElementType.METHOD`（本次两注解实测均满足，见下方证据），而不是"编译通过"。
2. **`import java.lang.reflect.Array as ReflectArray` + 不要 import `java.util.Map`/`java.util.Collection`**：前者会**遮蔽 Kotlin 的 `Array<T>`**（报 "No type arguments expected for 'class Array'"），后者会遮蔽 Kotlin 的 `Map`/`Collection`（报 "Unresolved reference 'entries'" / `iterator()` 歧义）。Kotlin 的 `Map` 映射到 `java.util.Map`，**描述符不变**，去掉 import 零风险。
3. **`ByteArray` 与 `List<*>` 在 `Any?` 入参下重载歧义**：Java 靠静态类型选重载，Kotlin 在 `toJSValue(value: Any?)` 里调 `toArray(ctx, value)` 会歧义 ⇒ 把字节数组那个重载**改名** `toByteArray`（Java 版叫 `toArray(byte[])`）。**本次零调用点使用它**（`JsSpider` 走的 `JSUtils<String>().toArray(ctx, List)`），故改名无影响 —— 但若将来有 Java 调用点要按旧名调，需补 `@JvmName("toArray")`。
4. `JSUtils` 的静态方法保留 `@JvmStatic`（`QuickJSContext`/`JSObject` 等上游类与 Java 调用方仍需静态形态）；`toJsonObject/toJsonArray/isEmpty/isNotEmpty` 的描述符与 Java 逐字一致。

**未验证面（登记）**：`quickjs` 的 native 只有 `arm64-v8a`（app 的 `abiFilters` 也只留它），**JS 桥无法在 JVM/Robolectric 下跑** ⇒ "迁完 JS 源仍能正常初始化 + `@Function` 全局函数可被 JS 调用 + 模块 import 解析正确"这三条只能装机走查，本次已装机但**尚未用真实 JS 源回归**。

# 8. 回滚

每切片一 commit，出问题 `git revert` 或 `git reset` 到上一切片；不推远程除非明确许可。契约层切片回滚前先确认 `javap` 基线仍可比对（产物与源码一致）。
