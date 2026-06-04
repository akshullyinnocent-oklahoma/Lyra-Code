# Lyra Code

Lyra Code 是一个面向 Android 的本地 AI Agent 应用。它允许用户接入不同平台的大模型，在手机端进行对话、文件处理、代码执行、联网搜索、MCP/SSH/WebDAV 等 Agent 工作流。

## 主要功能

- 多模型服务商：支持 OpenAI Chat Completions 兼容接口、Anthropic Messages API、Gemini GenerateContent API。
- 多模型切换：可保存多个服务商和模型，在对话中快速切换。
- Agent 工具：文件读写、命令执行、联网搜索、网页读取、TODO、文件变更审查、MCP、SSH、WebDAV、备份导入导出等。
- Termux 集成：通过 Termux RunCommandService 执行本地命令，并回传 stdout/stderr。
- MCP 支持：支持 Streamable HTTP / SSE 远程 MCP Server。
- SSH 支持：可配置远程 Linux/Windows/Git 服务器，通过 Agent 执行命令。
- WebDAV 支持：可列出、搜索、上传、下载 WebDAV 文件，并作为备份目标。
- 数据备份：支持导出和导入个人资料、对话历史、模型配置、MCP、SSH、WebDAV、Skills、系统提示词、沉浸扮演设定等数据。
- Markdown / LaTeX：支持 Markdown、表格、代码块、数学公式渲染。
- 多模态：支持图片上传、拍照、裁剪、预览和媒体内容显示。
- Skills：可导入包含 `SKILL.md` 的 zip 能力包，由 Agent 按需读取。
- 沉浸扮演模式：独立角色设定、头像、背景、表情包、好感度和历史记录。

## 项目结构

```text
app/                         Android 应用模块
app/src/main/java/...        Kotlin / Jetpack Compose 源码
third_party/jlatexmath/      JLaTeXMath Android fork，用于公式渲染
gradle/                      Gradle Wrapper 配置
```

## 构建要求

- Android Studio 或命令行 Android SDK
- JDK 11+
- Gradle Wrapper
- Android SDK 36

命令行构建 Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

生成文件通常位于：

```text
app/build/outputs/apk/debug/
```

Release 包建议在 Android Studio 中手动配置签名后构建。请勿把签名密钥、keystore 配置、API Key 或本地环境文件提交到仓库。

## Termux 配置

首次使用 `run_command` 前，需要在 Termux 中允许外部应用调用：

```bash
mkdir -p ~/.termux
grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

随后在 Lyra Code 的设置页面中授予 Termux 通信权限。

## 安全说明

Lyra Code 会处理 API Key、SSH 密码/私钥、MCP Token、WebDAV 密码、对话内容和本地文件。请注意：

- 不要提交 `local.properties`、签名密钥、`.env`、keystore、崩溃日志、调试日志和任何包含个人信息的文件。
- 使用 HTTP 明文 API、MCP 或 WebDAV 服务时，数据可能被中间人读取。
- 让 AI 执行命令、修改文件、上传/下载文件或操作远程服务器前，应审查工具调用内容。
- 包含密钥的备份文件应妥善保存，不要公开分享。

## 许可证

本项目采用双重许可：Lyra Code 原创源代码可在 AGPLv3-or-later 下使用；如果需要闭源分发、私有修改、商业例外或不希望遵守 AGPL copyleft 义务，需要获取商业许可证。第三方组件仍以其各自许可证为准。

详见 [LICENSE](LICENSE)。
