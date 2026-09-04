# 压缩文件后缀批量修改工具

一个使用 Java 17 和 Swing 编写的本地文件重命名工具。它只修改文件名，不读取或改变文件内容，也不执行压缩或解压。

## 修改规则

- 后缀精确为 `.zip`、`.7z` 或 `.rar`（忽略大小写）的文件保持不变。
- 其他文件替换为界面中选择的 `.zip`、`.7z` 或 `.rar`。
- `.z1ip`、`.rar1`、`.71z`、`.7zip`、`.7z1` 等近似后缀会被替换。
- 只替换最后一段后缀，例如 `archive.tar.gz` 会变成 `archive.tar.zip`。
- 目标文件已存在时跳过，不覆盖已有文件。

## 分卷压缩包自动识别

界面提供六个互斥选项：普通 ZIP、7Z、RAR，以及 ZIP 分卷、7Z 分卷、RAR 分卷。

普通压缩包始终保持不变：单独选择 `name.zip`、`name.7z` 或 `name.rar` 时，即使当前界面选中了对应分卷模式，也不会把它改成 `.001`。只有同时存在主体名称相同的标准分卷成员时，才把普通后缀文件视为该组待恢复的缺失卷。

明确选择分卷模式时，程序会按主体名称匹配同组文件、收集已有卷号，并把非标准文件分配到最小缺失卷号。例如同时选择 `name.MP3` 和 `name.7z.002`，选择“7Z 分卷”后会预览为 `name.7z.001`；如果已有 `name.7z.001、.003、.005`，另一个同名非标准文件会补为 `name.7z.002`。

主体名判断会优先和同组分卷进行完整匹配：`2025.06` 与 `2025.06.7z.002` 同时出现时，前者视为无后缀文件并改成 `2025.06.7z.001`；如果只有 `2025.05.7z.002` 与 `2025.06`，则 `.06` 按普通后缀处理并改成 `2025.7z.001`。`name.7z.mp3` 会移除伪后缀并复用已有的 `.7z` 标记，不会生成重复的 `.7z.7z.001`。

上述主体名校验同样适用于 ZIP 和 RAR：`name.zip.mp3` 会生成 `name.zip.001`，`name.rar.mp3` 会生成 `name.part001.rar`。检测到传统 ZIP `.z01/.z02` 或旧式 RAR `.rar/.r00/.r01` 时，新增卷会继续沿用该组已有风格，不与数字式 ZIP 或当前 RAR 分卷命名混用。

选择文件后自动识别以下分卷命名，不需要手动开启分卷模式：

- 7Z：`name.7z.001`、`name.7z.002`……
- ZIP 数字分卷：`name.zip.001`、`name.zip.002`……
- ZIP 传统分卷：`name.z01`、`name.z02`……、`name.zip`
- RAR 当前分卷：`name.part001.rar`、`name.part002.rar`……
- RAR 旧式分卷：`name.rar`、`name.r00`、`name.r01`……

识别采用完整文件名规则，`.71z.001`、`.z1`、`.r1`、`.part1.rar1` 等近似写法不会被误认为合法分卷。合法分卷文件保持原名，其他文件按界面选择的目标后缀修改。

如果同一次选择中包含至少两个主体名称完全相同、纯数字后缀不同的文件，例如 `name.001`、`name.003`、`name.005`，程序会将其识别为待恢复的数字分卷，并根据目标格式生成：

- 选择 7Z：`name.7z.001`、`name.7z.003`、`name.7z.005`
- 选择 ZIP：`name.zip.001`、`name.zip.003`、`name.zip.005`
- 选择 RAR：`name.part001.rar`、`name.part003.rar`、`name.part005.rar`

编号可以不连续，也不要求从 `001` 开始；程序会原样保留每个卷号。必须至少选择两个主体名称相同、数字后缀不同的文件才会自动采用此规则，单独的 `.001` 文件仍按普通文件处理。

## 运行

Windows 下可以直接双击 `run.bat`，脚本只负责编译并启动界面。

也可以直接运行已经生成的 Windows 安装程序：

```text
release/ExtensionRenamer-1.7.0.exe
```

安装包内置精简 Java 运行时，目标电脑不需要单独安装 Java。`dist/1.7.0/ExtensionRenamer/ExtensionRenamer.exe` 是无需安装的便携版本，但必须和同目录下的 `app`、`runtime` 文件夹一起使用。

在 IntelliJ IDEA 中打开项目，确认 Project SDK 为 Java 17，然后运行 `com.example.Main`。

也可以在项目根目录执行：

```powershell
javac -encoding UTF-8 --release 17 -d target/classes (Get-ChildItem src/main/java -Filter *.java -Recurse).FullName
java -cp target/classes com.example.Main
```

程序启动后只会显示界面，不会自动修改文件。选择文件并点击“开始修改”，再次确认后才会执行重命名。
