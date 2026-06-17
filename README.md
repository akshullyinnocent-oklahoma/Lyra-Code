<h1 align="center">Lyra Code</h1>

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
  <img alt="Version" src="https://img.shields.io/badge/version-2.5.0-blue" />
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/license-Dual%20License%20%2F%20AGPLv3-orange" />
  <img alt="MCP" src="https://img.shields.io/badge/MCP-HTTP%20%2F%20SSE-purple" />
</p>

Lyra Code is an Android-first local AI Agent app. It brings model chat, file tools, native downloads, command execution, web search, MCP, SSH, WebDAV, a built-in mini server, backups, Skills, device diagnostics, and usage statistics into a mobile workflow, so an Android device can handle coding, writing, research, remote maintenance, local web debugging, and automation tasks.

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
- Native HTTP/HTTPS file downloads to the workspace or Android shared storage, with redirects, custom headers, timeouts, and optional SHA-256 verification.
- Command execution through Termux RunCommandService with stdout/stderr feedback; command review now blocks only high-risk blacklist patterns instead of rejecting private commands or newly installed tools that are not on a fixed allowlist.
- TODO planning, process records, file-change review, and diff visualization.
- Web search, web page reading, source annotation, and a configurable domain blacklist to avoid unwanted, spammy, or low-quality sources.
- Time awareness, location awareness, and configuration management.
- Multi-round tool loops with user review, approval, and per-session confirmation bypass.
- Native downloads are preferred; Termux `curl` / `wget` are retained only as fallback options.

### Device and System Tools

- Device information page covering manufacturer, model, Android version, CPU/hardware, ABI, memory, storage, display, network, Bluetooth, and battery state.
- Hardware inspection Agent for device diagnostics, troubleshooting, and hardware comparison.
- Installed-app recognition with app name, package name, version, size, category, and signing certificate SHA-256.
- Optional Shizuku Shell and Root command tools with configurable `su` commands, permission fallback, full command preview, and explicit user approval.
- System-level tools can be disabled independently from the Agent settings page.

### MCP / SSH / WebDAV

- MCP: remote MCP servers over Streamable HTTP or SSE.
- SSH: password/key login for Linux, Windows, and Git servers, with user-approved command execution.
- WebDAV: file listing, PROPFIND, search, upload, download, and cloud backup.
- Natural-language configuration management for MCP, SSH, WebDAV, Skills, and Agent tools.

### Mini Server

- Built-in local HTTP/HTTPS static file server for previewing and debugging local websites, documentation sites, and generated front-end projects.
- Configurable host, port, password authentication, custom domains, HTTPS certificate chain/private key, and force-HTTPS mode.
- Works with localhost, LAN, intranet tunneling, public access, and mDNS-style local discovery scenarios when the network environment allows it.
- Terminal-style live logs for connections, resource loading, 404/auth failures, and page JavaScript errors; the Agent can read these logs to help diagnose and fix local web issues.
- The Agent can start or stop the mini server after user approval.

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

### Theme and Display

- Light, dark, and system-following theme modes.
- Optional Material You dynamic color, applying wallpaper-derived colors without changing the app layout.
- Adjustable font size with live preview, system-following mode, and a wider custom range.
- Refresh-rate preference: system smart mode, 30 Hz, 60 Hz, 90 Hz, or 120 Hz. The final effective rate still depends on device and Android display policy.

### Data and Backup

- Export/import profile data, conversation history, model providers, MCP, SSH, WebDAV, system prompts, Skills, and immersive roleplay scenarios.
- Local zip backup and WebDAV cloud backup.
- Supplement import mode with deduplication to reduce the risk of overwriting existing secrets.
- Safe export without API keys, or full migration export with secrets included.

### Usage Statistics

- Counts conversations, messages, user-input tokens, and AI-output tokens, including reasoning and tool context.
- Offline token estimation without a network dependency.
- Daily, weekly, monthly, yearly, total, and historical date-range views.

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

- HTTP API, MCP, WebDAV, and mini server endpoints are insecure unless protected by HTTPS or a trusted network.
- Exposing the mini server to LAN, tunneling, or public networks may leak local files if the served directory, authentication, or HTTPS configuration is wrong. Review the workspace and password before enabling external access.
- Review tool calls before allowing AI to execute commands, edit files, upload/download files, or operate remote servers.
- `run_command` no longer uses a fixed command allowlist, but it still blocks obvious high-risk operations such as `rm -rf /`, writes to `/dev/block`, and filesystem formatting commands. Review commands before approving them.
- The web search blacklist is stored locally. Add domains such as `x.com`, `https://x.com`, or `https://x.com/` in Settings to prevent the Agent from searching or reading those domains and their subdomains.
- Backup files containing secrets must be stored carefully and must not be shared publicly.
- This project does not verify that remote scripts, MCP servers, Skill repositories, or SSH commands are trustworthy.

## PR Notice

This project does not accept external pull requests. If you have feedback or suggestions, please submit an issue. If you need long-term modifications, fork this repository for independent maintenance or contact the repository owner.

## License

This project uses a dual-license model. The original Lyra Code source code is available under AGPLv3-or-later. A commercial license is required for closed-source distribution, private modifications, commercial exceptions, or use cases where you do not want to comply with AGPL copyleft obligations. Third-party components remain governed by their own licenses.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
