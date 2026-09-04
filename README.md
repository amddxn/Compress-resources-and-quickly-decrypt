# 压缩文件后缀批量修改工具

一个使用 Java 17 和 Swing 编写的本地文件重命名工具。它只修改文件名，不读取或改变文件内容，也不执行压缩或解压。

## 修改规则

- 后缀精确为 `.zip`、`.7z` 或 `.rar`（忽略大小写）的文件保持不变。
- 其他文件替换为界面中选择的 `.zip`、`.7z` 或 `.rar`。
- `.z1ip`、`.rar1`、`.71z`、`.7zip`、`.7z1` 等近似后缀会被替换。
- 只替换最后一段后缀，例如 `archive.tar.gz` 会变成 `archive.tar.zip`。
- 目标文件已存在时跳过，不覆盖已有文件。

## 运行

Windows 下可以直接双击 `run.bat`，脚本只负责编译并启动界面。

也可以直接运行已经生成的 Windows 安装程序：

```text
release/ExtensionRenamer-1.0.0.exe
```

安装包内置精简 Java 运行时，目标电脑不需要单独安装 Java。`dist/ExtensionRenamer/ExtensionRenamer.exe` 是无需安装的便携版本，但必须和同目录下的 `app`、`runtime` 文件夹一起使用。

在 IntelliJ IDEA 中打开项目，确认 Project SDK 为 Java 17，然后运行 `com.example.Main`。

也可以在项目根目录执行：

```powershell
javac -encoding UTF-8 --release 17 -d target/classes (Get-ChildItem src/main/java -Filter *.java -Recurse).FullName
java -cp target/classes com.example.Main
```

程序启动后只会显示界面，不会自动修改文件。选择文件并点击“开始修改”，再次确认后才会执行重命名。
