# Lyra Code

<p align="center">
  <img src="logo.png" alt="Lyra Code Logo" width="140" />
</p>

<p align="center">
  <strong>An Android-first local AI Agent app</strong>
</p>

<p align="center">
  <a href="README_zh-CN.md">简体中文</a> ·
  <a href="https://github.com/Soffd/Lyra-Code">GitHub</a> ·
  <a href="https://gitee.com/yukisoffd/lyra-code">Gitee</a>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-2.1.4-blue" />
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/license-Dual%20License%20%2F%20AGPLv3-orange" />
  <img alt="MCP" src="https://img.shields.io/badge/MCP-HTTP%20%2F%20SSE-purple" />
</p>

Lyra Code is an Android-first local AI Agent app. It brings model chat, file tools, command execution, web search, MCP, SSH, WebDAV, backups, and Skills into a mobile workflow, so an Android device can handle coding, writing, research, remote maintenance, and automation tasks.

## Screenshots

| Chat | Settings | Agent Tools |
| --- | --- | --- |
| <img src="example-img/chat.png" alt="Chat screen" width="260" /> | <img src="example-img/set.png" alt="Settings screen" width="260" /> | <img src="example-img/agent.png" alt="Agent tools screen" width="260" /> |

## Highlights

### Model Providers

- OpenAI Chat Completions-compatible APIs.
- Anthropic Messages API.
- Gemini GenerateContent API.
- Multiple providers, API keys, base URLs, and saved models.
- Fast provider/model/system-prompt/reasoning-depth switching during chat.
- HTTP API URLs are supported with an explicit security warning.

### Agent Tools

- File read, write, append, rename, move, delete, directory creation, and global file search.
- Command execution through Termux RunCommandService with stdout/stderr feedback.
- TODO planning, process records, file-change review, and diff visualization.
- Web search, web page reading, and source annotation.
- Time awareness, location awareness, and configuration management.
- Multi-round tool loops with user review, approval, and per-session confirmation bypass.

### MCP / SSH / WebDAV

- MCP: remote MCP servers over Streamable HTTP or SSE.
- SSH: password/key login for Linux, Windows, and Git servers, with user-approved command execution.
- WebDAV: file listing, PROPFIND, search, upload, download, and cloud backup.
- Natural-language configuration management for MCP, SSH, WebDAV, Skills, and Agent tools.

### Skills

- Import zip packages or a single `SKILL.md` file.
- Import a full GitHub / Gitee / GitLab repository.
- Manually create a Skill by editing `SKILL.md`.
- The Agent first reads `name` / `description` to judge relevance, then reads internal files on demand to avoid context bloat.

### Multimodal and Rendering

- Image upload, photo capture, cropping, rotation, brush annotation, and mosaic annotation.
- Thumbnail preview, full-screen preview, and save support for user/AI images and videos.
- Markdown, tables, code blocks, LaTeX math, and media Data URL rendering.
- Preview and save AI-returned base64 media, remote URLs, and local media files.

### Data and Backup

- Export/import profile data, conversation history, model providers, MCP, SSH, WebDAV, system prompts, Skills, and immersive roleplay scenarios.
- Local zip backup and WebDAV cloud backup.
- Supplement import mode with deduplication to reduce the risk of overwriting existing secrets.
- Safe export without API keys, or full migration export with secrets included.

### Immersive Roleplay

- Import character scenario packages and configure AI nickname, avatar, chat background, and sticker shortcodes.
- Separate history between normal chat and immersive roleplay.
- Affection state, sticker replacement, and character memory.
- Chat-bubble UI optimized for roleplay sessions.

## Project Structure

```text
app/                         Android application module
app/src/main/java/...        Kotlin / Jetpack Compose source code
third_party/jlatexmath/      JLaTeXMath Android fork for LaTeX rendering
example-img/                 README screenshots
gradle/                      Gradle Wrapper configuration
```

## Build Requirements

- Android Studio or command-line Android SDK
- JDK 11+
- Android SDK 36
- Gradle Wrapper

Build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is usually under:

```text
app/build/outputs/apk/debug/
```

For release builds, configure signing in Android Studio manually. Do not commit signing keys, keystores, API keys, `.env`, `local.properties`, or local private files.

## Termux Setup

Before using `run_command`, enable external app calls in Termux:

```bash
mkdir -p ~/.termux && (grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings
```

Then grant the Termux communication permission in Lyra Code settings. If permission is not granted, `run_command` is disabled automatically.

## Security Notes

Lyra Code may handle API keys, SSH passwords/private keys, MCP tokens, WebDAV passwords, conversations, local files, and remote server output. Please note:

- HTTP API, MCP, and WebDAV endpoints are insecure unless protected by HTTPS or a trusted network.
- Review tool calls before allowing AI to execute commands, edit files, upload/download files, or operate remote servers.
- Backup files containing secrets must be stored carefully and must not be shared publicly.
- This project does not verify that remote scripts, MCP servers, Skill repositories, or SSH commands are trustworthy.

## PR Notice

This project does not accept external pull requests. If you have feedback or suggestions, please submit an issue. If you need long-term modifications, fork this repository for independent maintenance or contact the repository owner.

## License

This project uses a dual-license model. The original Lyra Code source code is available under AGPLv3-or-later. A commercial license is required for closed-source distribution, private modifications, commercial exceptions, or use cases where you do not want to comply with AGPL copyleft obligations. Third-party components remain governed by their own licenses.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
