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

普通按钮和分卷按钮相互独立。选择普通 ZIP、7Z 或 RAR 时，合法普通压缩后缀仍保持不变；选择任一分卷按钮时，则以该按钮为最终输出格式，原文件当前是 `.zip`、`.7z`、`.rar` 或其他后缀都不会改变这一点。

分卷模式会按目录和实际主体名称分别分组，提取标准或受干扰后缀中的卷号，并替换主体名称之后的全部内容。支持的线索包括 `001`、`part1`、`z01`，以及 `0s0c1`、`0卡0啊s2`、`12part1`、`part2sad`、`z443101`、`zsdf02` 等包含明显卷号规律的干扰形式。

最终输出统一为：

- 7Z 分卷：`name.7z.001`、`name.7z.002`……
- ZIP 分卷：第 1 卷为 `name.zip`，第 2 卷为 `name.z01`，第 3 卷为 `name.z02`……（不再生成 `name.zip.001`）
- RAR 分卷：`name.part1.rar`、`name.part2.rar`……

同一组中已有的可信卷号会保留；无卷号文件补最小缺失编号。例如已有第 2、3 卷时补第 1 卷，已有第 1、3 卷时补第 2 卷。ZIP 分卷模式中的单独 `.zip` 文件视为主文件，不参与缺号分配。不同主体名称独立编号，因此三组文件交错加入时仍分别得到 `1、2`，不会按整个列表连续编号。

例如 `6月新补.ra1r.001` 与 `6月新补.1ra3r3.002` 选择 RAR 分卷后，会恢复为 `6月新补.part1.rar` 与 `6月新补.part2.rar`。`2025.06.7z.mp3` 不会生成重复的 `.7z.7z.001`。

## 运行

Windows 下可以直接双击 `run.bat`，脚本只负责编译并启动界面。

也可以直接运行已经生成的 Windows 安装程序：

```text
release/ExtensionRenamer-2.0.3.exe
```

安装包内置精简 Java 运行时，目标电脑不需要单独安装 Java。`dist/2.0.3/ExtensionRenamer/ExtensionRenamer.exe` 是无需安装的便携版本，但必须和同目录下的 `app`、`runtime` 文件夹一起使用。

在 IntelliJ IDEA 中打开项目，确认 Project SDK 为 Java 17，然后运行 `com.example.Main`。

也可以在项目根目录执行：

```powershell
javac -encoding UTF-8 --release 17 -d target/classes (Get-ChildItem src/main/java -Filter *.java -Recurse).FullName
java -cp target/classes com.example.Main
```

程序启动后只会显示界面，不会自动修改文件。选择文件并点击“开始修改”，再次确认后才会执行重命名。
