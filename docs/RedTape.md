# Automata Assembly Engine

这是一个参考 `omsim` 与 `omclone` 分层思路实现的 Java 仿真器基线。它使用无限方格、东南西北四种机构朝向、正交/对角轨道和按周期读取的指令带；运行期检查周期结束后的分子重叠、分子/机构基座重叠与机构基座之间的重叠，不执行变换过程中的实时碰撞采样。

当前版本聚焦最小但可执行的核心：机构可沿显式轨道移动、绕基座或抓握点旋转、等待，活塞机构额外支持伸缩；轨道由有序格点定义，分别只允许正交单位步或对角单位步；原子网格以 `int` 保存原子类型、以 `byte` 保存四方向键类型（编码由调用方定义）并提供分子连通分量搜索；输入在每个周期末尾生产模板分子，输出消费类型、键与形状完全一致的未抓取分子并驱动完成条件；指令带支持抓取与释放，同一实体收到不兼容意图时模拟器停机并警告。

## 设计要点

- 坐标使用不可变 `GridPoint(x, y)`；`x` 向东递增，`y` 向南递增。
- `Direction` 提供四邻接移动、90 度旋转、反向和局部坐标变换。符文局部形状按朝东状态定义，`GlyphSpec` 默认朝东；局部正 x 沿实例朝向，局部正 y 沿其右侧。
- `SimulationPlan`、`TrackSpec`、`InstructionTape` 与 `MechanismSpec` 的两个具体子类 `StationarySpec`、`PistonSpec` 是不可变定义；机构长度为 `1..3`，位置始终表示基座。固定臂长机构支持组合多臂，活塞仅支持默认单臂。
- 一条轨道可以自由混合正交与对角单位步，闭环必须显式声明。不同轨道禁止共享任何格点，包括端点和单点轨道；线段可在格子中心交叉，交叉处不连接。
- `MOVE_FORWARD`/`MOVE_BACKWARD` 分别沿轨道有序点列的 `+`/`-` 方向移动，与机构朝向无关；开放端点和非轨道格点都保持原地。
- `OrthogonalSimulator` 保存可变运行态，并按 decode → intent → conflict → transform → commit → produce → consume 推进每个周期。
- `SimulationPlan` 构造时校验静态布局：I/O 模板不能与任何其他 I/O、机构初始基座、轨道节点或符文占位重叠；符文不能与其他符文、机构初始基座或轨道节点重叠。机构之间不能共享初始基座格点；机构可以位于轨道上，臂杆和抓握点不计入静态占位。
- 推进热路径不采样变换过程；每周期第二半周期结束时检查最终的分子/分子、分子/机构基座和机构基座之间的重叠，冲突会回滚并停机。两个机构可以在同一周期交换基座位置，只要最终格点不同。
- 机构没有 ID，按 `SimulationPlan.mechanisms()` 的声明顺序定位，API 下标从 `0` 开始且在运行、回滚和重置期间保持稳定；下标只适用于当前计划。GUI 按相同顺序显示从 `1` 开始的编号。
- 符文、输入、输出分别按 `SimulationPlan.glyphs()`、`inputs()`、`outputs()` 的声明顺序使用从 `0` 开始的下标，下标在运行、回滚和重置期间稳定且只适用于当前计划。符文 `typeId` 继续标识注册类型。
- `glyphState(int glyphIndex)` 查询符文状态，回调通过 `GlyphContext.glyphIndex()` 获取自身下标；此下标与棋盘 API 的局部占位下标各自独立。`outputCount(int outputIndex)` 查询单个输出计数，`outputCounts()` 返回按输出声明顺序排列的 `long[]` 独立快照；下标查询越界抛出 `IndexOutOfBoundsException`。
- `mechanism(index)` 和 `mechanisms()` 提供机构的只读状态值，包含基座位置、基准朝向、长度、组合编码和机构级抓取状态；`heldEntities(index)` 返回该机构当前持有实体的有序、去重、不可变快照。单机构查询下标越界时抛出 `IndexOutOfBoundsException`。
- 原子类型是有效范围为 `0..0xFFFF` 的 `int`，类型 `0` 也有效；化学键类型仍为 `byte`。编码由调用方定义，项目只比较数值、不认识类型名称；键字节 `0` 表示无键、`0xFF` 表示本周期临时键。`Atom` 是不可变 record，四方向键独立打包在 `bondCodes` 中。
- `AtomGrid` 是 `GridPoint -> Atom` 的稀疏映射，成键时两端互惠写入；单侧键不构成连通。
- `MoleculeBfs` 用显式队列做广度优先搜索，返回不可变的 `Molecule`；搜索过程不改动网格，也不使用递归。
- `InputSpec` 与 `OutputSpec` 是绝对坐标下的不可变原子模板；棋盘实体使用正数 `long` 作为跨周期稳定标识，没有原子时 `entityAt` 返回 `0L`。
- 指令先展开成 `EntityIntent` 批次，再按实体分组做兼容检查，最后统一提交：抓取与平移/旋转不兼容，释放与平移/旋转兼容，相同位移/相同旋转合并。
- 共持实体的机构即使本周期不运动也会提交 `STAY` 约束；一个持有者保持不动、另一个试图移动时会停机，避免分子脱离静止机构的抓握点。
- 同一实体收到不兼容意图、或两个原子落入同一格时，模拟器停机、记录警告并保持冲突周期之前的状态；`reset()` 才能恢复。
- 输入足迹被任何原子或延迟产物预留占用时该半周期不生产；输出匹配要求分子大小、原子类型与长期键完全一致，且没有任何原子被机构持有。
- `f` 收集全部启用分支抓点上的完整分子，按实体去重后登记机构共享持有；同一机构每实体只计一次，不同机构共持则分别计数。`r` 统一释放，每实体减一次，实体留在原地。机构已有持有实体时重复 `f` 整体为空操作，不给空分支补抓；空抓不建立自动抓取状态。
- 分支仅通过运动关联提供枢轴中心，没有逐臂抓握状态或独立持有关系。轨道移动与 `a/d` 作用于整个共享集合；`q/e` 使用各关联分支的实际抓点。相同运动只执行一次，不同枢轴的旋转不兼容，警告停机并回滚整个周期；成键后继续按现有连通分子展开运动。只有默认单臂活塞的 `w/s` 会沿朝向平移持有分子。
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
    │   ├── glyph                # 符文公开 API
    │   ├── mech                 # 机械臂和指令公开 API
    │   └── track                # 轨道公开 API
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
`build/libs/aae-0.4.0.jar` 以及 `examples/gui-preview/build/` 下的示例 JAR 和应用分发包。
核心库测试报告位于 `build/reports/tests/test/index.html`。仅运行测试可执行
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
    new StationarySpec(
        new GridPoint(0, 0),
        Direction.EAST,
        InstructionTape.parse("g", 0, InstructionTape.Playback.LOOPING)),
    new StationarySpec(
        new GridPoint(2, 0),
        Direction.WEST,
        InstructionTape.parse("t", 0, InstructionTape.Playback.LOOPING)));

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
| `w` | 仅 `PistonSpec` 伸展一格，最大长度为 3；`StationarySpec` 等待 |
| `s` | 仅 `PistonSpec` 收缩一格，最小长度为 1；`StationarySpec` 等待 |
| `f` | 抓取全部启用分支抓点上的分子，登记为机构共享持有 |
| `r` | 释放当前持有的全部实体 |
| `.` | 等待一个周期 |

空白字符会被解析器忽略。指令带可以只运行一次，也可以循环，并可设置开始周期。

模拟器初始化时以计划中最长的原始指令带为准，同步每个机械臂的读取长度（与各带的开始周期无关，也不改写指令带内容）：
较短指令带在自身内容之后补等待槽位，循环播放按统一长度取模，最长指令带的内容与节拍保持不变。
`OrthogonalSimulator.tapeLength()` 返回这个统一长度，模拟推进、`motionIntents` 与动画插值都使用同一视图。

`MechanismSpec` 是不可直接实例化的抽象 sealed 基类，只允许两个不可变 final 子类：

- `StationarySpec` 固定臂长并保存 `ArmCombination`，可为单臂或多臂。“静态”只表示长度固定，
  仍支持轨道移动、绕基座旋转、绕抓握点旋转及抓放；`w/s` 是占用原周期的等待，持有时仍提交 `STAY` 共持约束。
- `PistonSpec` 支持全部指令，包括 `w/s`；只使用默认 bit0 单臂，不提供组合参数。

计划、指令带 UI 等调用方仍可使用多态 `MechanismSpec`。公共访问器为 `initialPosition()`、
`initialDirection()`、`length()`、`initialLength()`、`tape()`、`combination()`；
`supportsInstruction(Instruction)` 查询能力，`instructionAt(cycle)` 返回当前周期的有效指令；
`instructionAt(cycle, tapeLength)` 按统一长度读取，供模拟器与动画共用同一节拍。
原始 `tape()` 不会删除或改写不受支持的指令；模拟推进和机械臂动画都应读取 `instructionAt(...)`。
长度范围为 `1..3`，省略长度时默认为 1，字符串构造器默认从周期 0 开始循环：

```java
MechanismSpec stationary = new StationarySpec(
        new GridPoint(0, 0), Direction.EAST, "fdra", 2);
MechanismSpec piston = new PistonSpec(
        new GridPoint(4, 0), Direction.EAST, 2,
        InstructionTape.parse("fwrs", 0, InstructionTape.Playback.LOOPING));
```

`StationarySpec` 承接原定义的全部构造形式，包括 `(position, direction, int combinationCode, String)`；
该形式中的整数是组合编码，不是长度。单臂字符串构造器显式指定长度时使用 `(position, direction, String, int length)`。
`PistonSpec` 接受 `(position, direction, int length, tape/string)`，也接受
`(position, direction, tape/string[, int length])`，但不接受 `ArmCombination` 或组合编码。

### 组合臂与共享抓握

`ArmCombination` 是不可变八位组合值，`new ArmCombination(code)` 严格接受 `1..255`
（`0x01..0xFF`，共 255 种组合）；`0`、负数或大于 255 的 int 均无效，不会截断。
`ArmCombination.fromByte((byte) 0x80)` 显式转换 Java 有符号 byte，结果编码为 128。
`StationarySpec` 省略组合时使用单臂 `0x01`；`PistonSpec` 始终使用此默认单臂。
`armCount()` 返回启用分支数，`enabledBits()` 按 bit 下标升序遍历。

| 位 | 相对基准朝向的角度 | 单位局部偏移 |
|---|---|---|
| bit0 | 0° | `(1,0)` |
| bit1 | 90° | `(0,1)` |
| bit2 | 180° | `(-1,0)` |
| bit3 | 270° | `(0,-1)` |
| bit4 | 45° | `(1,1)` |
| bit5 | 135° | `(-1,1)` |
| bit6 | 225° | `(-1,-1)` |
| bit7 | 315° | `(1,-1)` |

```java
MechanismSpec multiArm = new StationarySpec(
        new GridPoint(0, 0), Direction.EAST, 2,
        new ArmCombination(0x11), InstructionTape.once("fdr"));
// 初始抓点为 (2,0)、(2,2)；两个分支共享长度、基座、朝向、指令带与抓放状态。
MechanismView view = new OrthogonalSimulator(
        SimulationPlan.withTracks(List.of(), multiArm)).mechanism(0);
List<GridPoint> all = view.grabPositions();
GridPoint diagonal = view.grabPosition(4); // 固定 bit 下标，不是遍历序号
```

`MechanismView.position()` 始终返回基座，`length()` 返回共享长度，`combination()` 返回组合。
`grabPositions()` 返回启用位升序的不可变抓点列表；`grabPosition(bitIndex)` 只接受启用位
（越界抛 `IndexOutOfBoundsException`，未启用位抛 `IllegalArgumentException`）。
无参 `grabPosition()` 对任意单比特组合返回实际抓点，多臂时抛 `IllegalStateException`。
编码相对基准朝向定义，`a/d` 只改变朝向、不改编码，仍每次旋转 90°。
对角分支长度 n 表示两个坐标轴各偏移 n，绘制杆长为 `sqrt(2) * n`；
不扩展四方向键，不新增臂杆、抓手或旋转扫掠碰撞。

所有分支的 `grabbing()` 一致，由机构共享持有集合决定；部分抓点为空也不拆分状态。
运动关联随共享抓取建立、统一释放/reset 清除，并与实体和抓握计数一起参与周期回滚。
`motionIntents(index)` 返回当前指令基于已提交状态推导的不可变实体运动约束快照
（`STAY`/`MOVE`/`ROTATE`，释放时为空），供动画等只读调用方归并兼容运动；
它不推进周期、不公开逐臂持有，也不承诺整个周期一定成功。

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

实现内部只依赖数值约定：

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

带输入输出的计划额外维护一个原子棋盘和一份 `Inventory`，每个周期的推进顺序是：解码 →
意图收集 → 冲突检查 → 机构与实体变换 → 提交 → 库存取料生产 → 输出匹配入库 → 周期加一。

```java
SimulationPlan plan = new SimulationPlan(
        List.of(new StationarySpec(
                new GridPoint(0, 1), Direction.NORTH,
                InstructionTape.parse(".fgr", 0, InstructionTape.Playback.LOOPING))),
        List.of(new TrackSpec(List.of(
                new GridPoint(0, 1),
                new GridPoint(-1, 1)))),
        List.of(InputSpec.single(4, new GridPoint(0, 0), Atom.of(1))),
        List.of(OutputSpec.single(new GridPoint(-1, 0), Atom.of(1))),
        1);

OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
simulator.inventory().replenish(plan.inputs().get(0), 1); // 运行期补货（可选）
simulator.run(4);
// simulator.isComplete() == true, simulator.outputCount(0) == 1
// simulator.inventory().productCount(plan.outputs().get(0)) == 1

long shipped = simulator.inventory().takeProduct(plan.outputs().get(0), 1); // 外部消费产品
// shipped == 1，产品余量归零；outputCount(0) 仍为 1，完成条件不回退
```

- `InputSpec.single(stock, position, atom)` / `InputSpec.of(stock, atoms...)` 声明该输入的
  初始原料配额；不写配额时为 `InputSpec.NO_STOCK`，必须先用
  `simulator.inventory().replenish(input, n)` 补货才会生产。模拟器构造时把计划声明的配额
  一次性注入库存。`reset()` 保留库存、不重新注入配额；`init()` 重置后从现有库存取料，
  因此调用方可以在初始化前补货。
- 输入足迹（模板全部格点）空闲且库存有余量时，输入在每个周期末尾扣减一份原料并生产模板
  原子；占位被占用或原料不足则跳过该周期，且不会扣料。模板原子的四方向键写在
  `Atom.bondCodes` 中，带键分子原样生产。
- 输出模板构造时必须是完整、互惠且连通的单个分子，不允许临时键或指向模板外的键。
  每个半周期的符文阶段之后，逐个检查模板坐标上的实体、抓握计数和完整 `Atom` 值；
  全部匹配后按模板坐标移除原子及实体索引、提交一份产品到库存，输出计数加一。全部输出
  达到 `targetOutputs` 时 `isComplete()` 为真。
- 库存条目按 `IOSpec.signature()` 的规范化物料签名识别：原子类型、键位与相对排布完全相同、
  只是整体平移的模板共享同一份原料余量；旋转、换键或换原子类型都是另一种物料。原料与产品
  分开记账，取产品不影响原料余量，也不改变输出计数。
- 库存参与周期事务：`step()` 抛出异常回滚时，本周期扣掉的原料和提交的产品一并恢复。
  取货接口 `takeRaw`/`takeProduct` 只取走现有余量，不会把库存变成负数。
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
