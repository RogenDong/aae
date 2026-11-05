# Automata Assembly Engine

这是一个参考 `omsim` 与 `omclone` 分层思路实现的 Java 仿真器基线。它使用无限方格、东南西北四种机构朝向、正交/对角轨道和按周期读取的指令带；运行期检查周期结束后的分子重叠、分子/机构基座重叠与机构基座之间的重叠，不执行变换过程中的实时碰撞采样。

当前版本聚焦最小但可执行的核心：多个机构可沿显式轨道移动、绕基座或抓握点旋转、伸缩、等待；轨道由有序格点定义，分别只允许正交单位步或对角单位步；原子网格以 `int` 保存原子类型、以 `byte` 保存四方向键类型（编码由调用方定义）并提供分子连通分量搜索；输入在每个周期末尾生产模板分子，输出消费类型、键与形状完全一致的未抓取分子并驱动完成条件；指令带支持抓取与释放，同一实体收到不兼容意图时模拟器停机并警告。

## 设计要点

- 坐标使用不可变 `GridPoint(x, y)`；`x` 向东递增，`y` 向南递增。
- `Direction` 提供四邻接移动、90 度旋转、反向和局部坐标变换。符文局部形状按朝东状态定义，`GlyphSpec` 默认朝东；局部正 x 沿实例朝向，局部正 y 沿其右侧。
- `SimulationPlan`、`TrackSpec`、`MechanismSpec`、`InstructionTape` 是不可变定义；机构长度为 `1..3`，位置始终表示基座。
- 一条轨道可以自由混合正交与对角单位步，闭环必须显式声明。不同轨道禁止共享任何格点，包括端点和单点轨道；线段可在格子中心交叉，交叉处不连接。
- `MOVE_FORWARD`/`MOVE_BACKWARD` 分别沿轨道有序点列的 `+`/`-` 方向移动，与机构朝向无关；开放端点和非轨道格点都保持原地。
- `OrthogonalSimulator` 保存可变运行态，并按 decode → intent → conflict → transform → commit → produce → consume 推进每个周期。
- `SimulationPlan` 构造时校验静态布局：I/O 模板不能与任何其他 I/O、机构初始基座、轨道节点或符文占位重叠；符文不能与其他符文、机构初始基座或轨道节点重叠。机构之间不能共享初始基座格点；机构可以位于轨道上，臂杆和抓握点不计入静态占位。
- 推进热路径不采样变换过程；每周期第二半周期结束时检查最终的分子/分子、分子/机构基座和机构基座之间的重叠，冲突会回滚并停机。两个机构可以在同一周期交换基座位置，只要最终格点不同。
- 机构没有 ID，按 `SimulationPlan.mechanisms()` 的声明顺序定位，API 下标从 `0` 开始且在运行、回滚和重置期间保持稳定；下标只适用于当前计划。GUI 按相同顺序显示从 `1` 开始的编号。
- 符文、输入、输出分别按 `SimulationPlan.glyphs()`、`inputs()`、`outputs()` 的声明顺序使用从 `0` 开始的下标，下标在运行、回滚和重置期间稳定且只适用于当前计划。符文 `typeId` 继续标识注册类型。
- `glyphState(int glyphIndex)` 查询符文状态，回调通过 `GlyphContext.glyphIndex()` 获取自身下标；此下标与棋盘 API 的局部占位下标各自独立。`outputCount(int outputIndex)` 查询单个输出计数，`outputCounts()` 返回按输出声明顺序排列的 `long[]` 独立快照；下标查询越界抛出 `IndexOutOfBoundsException`。
- `mechanism(index)` 和 `mechanisms()` 提供机构的只读状态值，包含基座位置、朝向、长度和抓取状态；`heldEntities(index)` 返回指定机构当前持有的实体。单机构查询下标越界时抛出 `IndexOutOfBoundsException`。
- 原子类型是有效范围为 `0..0xFFFF` 的 `int`，类型 `0` 也有效；化学键类型仍为 `byte`。编码由调用方定义，项目只比较数值、不认识类型名称；键字节 `0` 表示无键、`0xFF` 表示本周期临时键。`Atom` 是不可变 record，四方向键独立打包在 `bondCodes` 中。
- `AtomGrid` 是 `GridPoint -> Atom` 的稀疏映射，成键时两端互惠写入；单侧键不构成连通。
- `MoleculeBfs` 用显式队列做广度优先搜索，返回不可变的 `Molecule`；搜索过程不改动网格，也不使用递归。
- `InputSpec` 与 `OutputSpec` 是绝对坐标下的不可变原子模板；棋盘实体使用正数 `long` 作为跨周期稳定标识，没有原子时 `entityAt` 返回 `0L`。
- 指令先展开成 `EntityIntent` 批次，再按实体分组做兼容检查，最后统一提交：抓取与平移/旋转不兼容，释放与平移/旋转兼容，相同位移/相同旋转合并。
- 共持实体的机构即使本周期不运动也会提交 `STAY` 约束；一个持有者保持不动、另一个试图移动时会停机，避免分子脱离静止机构的抓握点。
- 同一实体收到不兼容意图、或两个原子落入同一格时，模拟器停机、记录警告并保持冲突周期之前的状态；`reset()` 才能恢复。
- 输入足迹被任何原子或延迟产物预留占用时该半周期不生产；输出匹配要求分子大小、原子类型与长期键完全一致，且没有任何原子被机构持有。
- 抓取携带当前抓握点上的整个分子；被携带实体可随机构平移、绕基座或抓握点旋转、随活塞伸缩，并同步旋转键位；释放只减少持有计数，实体留在原地。
- 核心库没有第三方运行时依赖；JUnit 仅用于测试。

完整的 `omsim` 对照分析、取舍和扩展边界见 [docs/architecture.md](docs/architecture.md)。

## 项目结构

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/wrapper
├── examples/gui-preview          # Gradle 子项目 :example
│   ├── build.gradle.kts
│   └── src
│       └── main/java/io/dev/demo # render、om
└── src
    ├── main/java/io/dev/aae
    └── test/java/io/dev/aae
```

项目以 Java 17 为基线，通过 Gradle Wrapper 固定 Gradle 9.2.1，无需单独安装 Gradle。
请安装 JDK 17 并设置 `JAVA_HOME`。首次构建需要联网下载 Gradle 和测试依赖。

## 构建与测试

Windows：

```powershell
.\gradlew.bat clean build
```

macOS / Linux：

```bash
./gradlew clean build
```

`build` 会构建核心库和 `example` 子项目，运行各自的 JUnit 测试，并生成
`build/libs/aae-0.1.2.jar` 以及 `examples/gui-preview/build/` 下的示例 JAR 和应用分发包。
核心库测试报告位于 `build/reports/tests/test/index.html`，示例测试报告位于
`examples/gui-preview/build/reports/tests/test/index.html`。仅运行测试可执行
`.\gradlew.bat test`（macOS / Linux 使用 `./gradlew test`）。

运行 GUI 示例（`GuiDemoMain` 位于 `examples/gui-preview/src/main/java`，
`runExample` 委托给 `:example:run`，自动构建示例及其依赖的核心库）：

```powershell
.\gradlew.bat runExample
```

macOS / Linux 使用 `./gradlew runExample`。运行时需要图形桌面环境。
也可直接执行 `.\gradlew.bat :example:run`；单独构建示例使用
`.\gradlew.bat :example:build`。

## 核心 API 示例

```java
SimulationPlan plan = SimulationPlan.withTracks(
    List.of(new TrackSpec(List.of(
        new GridPoint(0, 0),
        new GridPoint(1, 0),
        new GridPoint(2, 0)))),
    new MechanismSpec(
        new GridPoint(0, 0),
        Direction.EAST,
        InstructionTape.looping("g")),
    new MechanismSpec(
        new GridPoint(2, 0),
        Direction.WEST,
        InstructionTape.looping("t")));

OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
simulator.step();

// 两个机构都试图移动到 (1,0)，冲突周期回滚并停机。
// simulator.isHalted() == true
// simulator.mechanism(0).position().equals(new GridPoint(0, 0))
// simulator.mechanism(1).position().equals(new GridPoint(2, 0))
```

指令带字符：

| 字符 | 指令 |
|---|---|
| `g` | 沿所在轨道的 `+` 方向移动一步 |
| `t` | 沿所在轨道的 `-` 方向移动一步 |
| `q` | 将抓住的分子绕抓握点逆时针旋转 90 度 |
| `e` | 将抓住的分子绕抓握点顺时针旋转 90 度 |
| `a` | 绕机构基座逆时针旋转 90 度 |
| `d` | 绕机构基座顺时针旋转 90 度 |
| `w` | 活塞伸展一格，最大长度为 3 |
| `s` | 活塞收缩一格，最小长度为 1 |
| `f` | 抓取当前抓握点上的整个分子 |
| `r` | 释放当前持有的全部实体 |
| `.` | 等待一个周期 |

空白字符会被解析器忽略。指令带可以只运行一次，也可以循环，并可设置开始周期。
所有机构使用同一个 `MechanismSpec` 模型；出现 `w`/`s` 指令时即表现为活塞，不需要
单独的活塞子类型。长度范围为 `[1, 4)`，不传长度的三参数构造器默认长度为 1：

```java
MechanismSpec piston = new MechanismSpec(
        new GridPoint(0, 0),
        Direction.EAST,
        2,
        InstructionTape.looping("fws"));
```

`MechanismView.position()` 始终返回基座位置，`length()` 返回当前长度，
`grabPosition()` 返回 `position + direction * length` 对应的当前抓握点。

## 轨道

`TrackSpec` 用有序格点定义一条轨道。列表中的后继是 `+` 方向，前驱是 `-` 方向；
因此轨道转弯时不需要根据机构朝向或进入方向选路。`TrackDirection.PLUS` 与
`TrackDirection.MINUS` 是这两个方向的显式 API 表示。

- 使用 `new TrackSpec(points)` 创建开放轨道，或使用 `new TrackSpec(points, true)` 显式创建闭环；点列按顺序支持水平、竖直和对角单位步的任意混合。
- 轨道内部不能重复格点。显式闭环可以在末尾重复一次起点，构造时将其移除；`points()` 返回不可变的规范化节点列表。
- 不同轨道禁止共享任何格点，包括端点、内部节点、闭环节点和单点轨道。共享节点会在计划构造时抛出 `InvalidTrackOverlapException`，原因统一为 `TRACK_OVERLAP`，消息包含冲突坐标及两条轨道从 `0` 开始的声明下标。
- 线段可以在格子中心交叉，只要不共享格点；交叉处不建立连接。
- 开放轨道首点没有 `-` 出口、末点没有 `+` 出口；沿不存在的出口移动是空操作。机构不在轨道上时，`t`/`g` 同样是空操作。单点轨道参与布局占位，但没有移动边。
- 闭环至少包含三个不同节点，末点到首点必须是一单位步；首尾相邻但未显式声明闭环时仍然开放。空路径、重复节点、非单位步及非法闭环抛 `IllegalArgumentException`；坐标计算溢出抛 `ArithmeticException`。
- 轨道移动只平移机构基座及其携带实体，不改变机构朝向。

只含机构时可用 `SimulationPlan.withTracks(List<TrackSpec>, MechanismSpec...)`；带输入输出时，
完整构造器参数依次为机构、轨道、输入、输出和目标输出次数：

```java
TrackSpec mixed = new TrackSpec(List.of(
        new GridPoint(0, 0),
        new GridPoint(1, 0),
        new GridPoint(2, 1)));

SimulationPlan plan = new SimulationPlan(
        mechanisms,
        List.of(mixed),
        inputs,
        outputs,
        targetOutputs);
```

## 原子、化学键与分子

原子类型使用 `int`，有效范围为 `0..0xFFFF`，化学键类型使用 `byte`。编码由调用方自己定义：
模拟器不认识原子名称或键类型名称，只按完整数值保存、比较和传播。`Atom.of(int)`、
`Atom.withType(int)`、`Atom.requireAtomCode(int)` 和公开构造器统一校验原子类型范围，
越界抛出 `IllegalArgumentException`。`Molecule.countOf(int)` 按完整类型计数；
`Atom.withBond(Direction, byte)` 与 `AtomGrid.bond(...)` 继续接收键字节。

实现内部只依赖两条数值约定：

| 数值 | 含义 |
|---|---|
| 键字节 `0` | 该方向没有键 |
| 键字节 `0xFF` | 本周期刚建立、下一周期会被清除的临时键 |
| 其余非零键字节 | 长期键，即 `Atom.isRealBond(byte)` 为真 |

类型 `0` 是真实原子，允许写入 `AtomGrid`、由符文生成或通过 `setType` 替换得到。
“格点不存在”代表没有原子；清空格点使用删除操作。负数不是有效原子类型。

双参数创建统一使用公开 record 构造器，可以直接还原完整类型和打包键：

```java
Atom atom = Atom.of(0x8000).withBond(Direction.EAST, (byte) 2);
Atom restored = new Atom(atom.type(), atom.bondCodes());
// restored.equals(atom) == true
```

不提供双参数 `Atom.of` 或旧 `byte` 原子类型重载。示例中的 OM 固定编码和测试编码辅助类
仍可使用自身的 `byte` 编码，传入原子接口时由 Java 自动扩宽；引擎不对负字节作无符号转换。

类似 `omsim, omclone` 的位移量（`SALT` = 1 … `QUINTESSENCE` = 16）只是调用方可以选择的一种
编码方案，本项目不会把它固化成常量、掩码或判据。

```java
AtomGrid grid = new AtomGrid();
GridPoint left = new GridPoint(0, 0);
GridPoint right = new GridPoint(1, 0);
// 原子类型与键字节都是调用方自己的编码：这里用 0 与 0x0101 表示两种原子、2 表示长期键。
grid.put(left, Atom.of(0));
grid.put(right, Atom.of(0x0101));

// 成键会在两端同时写入反方向的同一键字节。
grid.bond(left, Direction.EAST, (byte) 2);

Molecule molecule = MoleculeBfs.moleculeAt(grid, left);
// molecule.size() == 2，原子按 BFS 发现顺序保存
// molecule.countOf(0) == 1，molecule.countOf(0x0101) == 1

List<Molecule> molecules = MoleculeBfs.molecules(grid);
// 把网格上的全部原子划分成连通分量
```

分子搜索要求两侧互为反方向的长期键，单侧写入的键不算连通。默认忽略临时键字节 `0xFF`；
需要观察半周期内刚建立、尚未清除的键时传入自定义判据（`BondFilter` 是接收
`byte` 的函数式接口，逐方向的判据不会装箱）：

```java
Molecule withTemporary =
    MoleculeBfs.moleculeAt(grid, left, bondType -> bondType != 0);
```

`AtomGrid.bond` 还可以带 `unless` 参数：任一侧在该方向上已有的键字节等于 `unless`（且 `unless` 不为 `0`）时放弃写入。

```java
grid.bond(left, Direction.EAST, (byte) 3, (byte) 2);
// 该方向已经是 2，因此返回 false 且不改动网格
```

## 输入、输出与实体意图

`SimulationPlan` 在构造阶段拒绝上述非法重叠，符文定义快照建立后即完成布局校验，
不创建符文运行态。非法布局抛出 `IllegalArgumentException`，消息包含冲突
格点、双方部件类别及 ID（轨道使用从 0 开始的声明索引）。I/O 按模板的全部原子格点检查，
符文按实例位置和朝向转换后的全部 `placeholder` 格点检查，不使用包围矩形。
这些检查只在计划构造时执行；运行期仍可把分子搬到符文或输出上进行处理。

带输入输出的计划额外维护一个原子棋盘，每个周期的推进顺序是：解码 → 意图收集 →
冲突检查 → 机构与实体变换 → 提交 → 输入生产 → 输出匹配 → 周期加一。

```java
SimulationPlan plan = new SimulationPlan(
        List.of(new MechanismSpec(
                new GridPoint(0, 1), Direction.NORTH,
                InstructionTape.looping(".fgr"))),
        List.of(new TrackSpec(List.of(
                new GridPoint(0, 1),
                new GridPoint(-1, 1)))),
        List.of(InputSpec.single(new GridPoint(0, 0), Atom.of(1))),
        List.of(OutputSpec.single(new GridPoint(-1, 0), Atom.of(1))),
        1);

OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
simulator.run(4);
// simulator.isComplete() == true, simulator.outputCount(0) == 1
```

- 输入足迹（模板全部格点）空闲时，输入在每个周期末尾生产模板原子；任一格点被
  占用则跳过该周期。模板原子的四方向键写在 `Atom.bondCodes` 中，带键分子原样生产。
- 输出模板构造时必须是完整、互惠且连通的单个分子，不允许临时键或指向模板外的键。
  每个半周期的符文阶段之后，逐个检查模板坐标上的实体、抓握计数和完整 `Atom` 值；
  全部匹配后按模板坐标移除原子及实体索引，输出计数加一。全部输出达到 `targetOutputs`
  时 `isComplete()` 为真。
- 移动与转向会携带机构持有的实体（抓取面向格点上的整个分子）；`f` 在已经持有
  实体时是空操作，`r` 在没有持有实体时是空操作。多个机构可以同时持有同一实体。

同一实体在同一周期收到的多个意图按下面的规则合并或停机：

| 意图组合 | 结果 |
|---|---|
| 平移 + 相同平移 | 合并，只移动一次 |
| 旋转 + 相同中心、相同转向 | 合并，只旋转一次 |
| 抓取 + 抓取 / 抓取 + 释放 | 合并，持有计数净增减 |
| 释放 + 任意 | 兼容 |
| 抓取 + 平移 / 抓取 + 旋转 | 停机并警告 |
| 平移 + 不同平移 / 平移 + 旋转 / 旋转 + 不同旋转 | 停机并警告 |

停机是确定性的业务规则而不是碰撞检测：冲突周期不提交任何状态，后续
`step()`/`run()` 不推进，警告记录在 `warnings()` 并输出到标准错误流，
`reset()` 恢复。两个原子落入同一格也会停机，避免棋盘数据被静默覆盖。

## 基准测试

当前源码树未包含 JMH 基准代码，因此 Gradle 构建仅配置核心库与 JUnit 测试。
后续添加基准代码时，再引入独立的 JMH source set 或子项目。

## 版本管理

仓库使用 Git 管理。建议后续开发保持每个提交只包含一个可验证主题，并在提交前执行：

```powershell
.\gradlew.bat clean build
git status --short
```

符文行为访问棋盘的唯一定位方式是当前 `GlyphDefinition.placeholder` 的 **0-based 索引**。
`InputSlot.position()`、`OutputSlot.position()` 和所有 `GlyphReader` / `GlyphBoard` 方法
都使用该索引；槽位必须属于定义的 placeholder，输入、输出各自不能重复，也不能共享索引。
非法索引抛出 `IndexOutOfBoundsException`，消息包含索引、占位数量和符文类型。

所有 `InputSlot` 都要求格点上有原子，`requiredAtomType()` 返回独立的 `OptionalInt` 类型约束。
`occupied(position)` 的类型约束为空；`of(position, type)`、`bonded(...)` 及接收 `int` 类型的
构造器精确匹配 `0..0xFFFF` 的类型，负数及越界类型均被拒绝。
完整构造器可用 `OptionalInt.empty()` 组合不限制类型、键要求和自定义判据，或用
`OptionalInt.of(type)` 附加精确类型约束；`requiredAtomType` 不接受 `null`。

每个半周期共用一个棋盘视图，每次执行符文前重新绑定当前实例，索引按该定义和实例朝向
解析成世界格点。方向参数使用世界方向；方向键操作只允许连接当前 placeholder 内的邻居，
形状外的键读取返回 `false`（或不报告该方向），写操作返回 `SKIPPED`。
`moleculeAt(index)` 从合法索引开始返回完整连通分子的只读值；
`GlyphContext.worldPoint(index)` 和 `neighbor(index, direction)` 通过占位索引提供几何查询；
上下文仅保留 `toLocal(GridPoint)` 用于世界坐标到局部坐标的逆变换。
