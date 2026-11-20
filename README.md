# Automata Assembly Engine

这是一个参考 `omsim` 与 `omclone` 分层思路实现的 Java 仿真器基线。它采用方格棋盘，支持四向机构、正交/对角轨道和按周期读取的指令带。运行期检查周期结束后的分子重叠、分子/机构基座重叠与机构基座之间的重叠，不执行变换过程中的实时碰撞采样。

当前版本聚焦最小但可执行的核心：机构可沿显式轨道移动、绕基座或抓握点旋转、等待，活塞机构额外支持伸缩；轨道由有序格点定义，分别只允许正交单位步或对角单位步；原子网格以 `int` 保存原子类型、以 `byte` 保存四方向键类型（编码由调用方定义）并提供分子连通分量搜索；输入在每个周期末尾生产模板分子，输出消费类型、键与形状完全一致的未抓取分子并驱动完成条件；指令带支持抓取与释放，同一实体收到不兼容意图时模拟器停机并警告。

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

`build` 会构建核心库和 `example` 子项目，运行各自的 JUnit 测试。
核心库测试报告位于 `build/reports/tests/test/index.html`。仅运行测试可执行
`.\gradlew.bat test`（macOS / Linux 使用 `./gradlew test`）。

运行 GUI 示例：

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

## 棋盘范围

默认使用无限棋盘。传入宽高即可启用有界棋盘，例如 `new OrthogonalSimulator(plan, 8, 6)`。
原点 `(0,0)` 在西北角；校验与停机规则见 [棋盘范围与越界停机](docs/RedTape.md#棋盘范围与越界停机)。

## 基准测试

当前源码树未包含 JMH 基准代码，因此 Gradle 构建仅配置核心库与 JUnit 测试。
后续添加基准代码时，再引入独立的 JMH source set 或子项目。
