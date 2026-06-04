# Lyra Code

Lyra Code is an Android-first local AI Agent application. It lets users connect models from different providers and run mobile workflows such as chat, file operations, code execution, web search, MCP, SSH, WebDAV, and backup management.

## Features

- Multiple model providers: OpenAI Chat Completions-compatible APIs, Anthropic Messages API, and Gemini GenerateContent API.
- Fast model switching: save multiple providers and models, then switch them during a conversation.
- Agent tools: file read/write, command execution, web search, web page reading, TODO tracking, file change review, MCP, SSH, WebDAV, backup import/export, and more.
- Termux integration: execute local commands through Termux RunCommandService and return stdout/stderr.
- MCP support: connect to remote MCP servers through Streamable HTTP or SSE.
- SSH support: configure remote Linux/Windows/Git servers and let the Agent execute approved commands.
- WebDAV support: list, search, upload, and download WebDAV files, and use WebDAV as a backup target.
- Data backup: export and import profile data, conversation history, model profiles, MCP, SSH, WebDAV, Skills, system prompts, and immersive roleplay scenarios.
- Markdown / LaTeX: render Markdown, tables, code blocks, and math formulas.
- Multimodal support: upload images, take photos, crop images, preview media, and display media returned by AI.
- Skills: import zip packages containing `SKILL.md`; the Agent inspects and reads relevant Skill files on demand.
- Immersive roleplay mode: separate character scenarios, avatars, backgrounds, stickers, affection state, and conversation history.

## Project Structure

```text
app/                         Android application module
app/src/main/java/...        Kotlin / Jetpack Compose source code
third_party/jlatexmath/      JLaTeXMath Android fork for formula rendering
gradle/                      Gradle Wrapper configuration
```

## Build Requirements

- Android Studio or command-line Android SDK
- JDK 11+
- Gradle Wrapper
- Android SDK 36

Build a debug APK from the command line:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is usually under:

```text
app/build/outputs/apk/debug/
```

For release builds, configure signing in Android Studio manually. Do not commit signing keys, keystore configuration, API keys, or local environment files.

## Termux Setup

Before using `run_command`, enable external app calls in Termux:

```bash
mkdir -p ~/.termux
grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

Then grant the Termux communication permission from Lyra Code settings.

## Security Notes

Lyra Code may handle API keys, SSH passwords/private keys, MCP tokens, WebDAV passwords, conversation content, and local files. Please note:

- Do not commit `local.properties`, signing keys, `.env` files, keystores, crash logs, debug logs, or files containing personal information.
- HTTP API, MCP, and WebDAV endpoints are insecure unless protected by HTTPS or a trusted network.
- Review tool calls before allowing AI to execute commands, modify files, upload/download files, or operate remote servers.
- Backup files containing secrets must be stored carefully and must not be shared publicly.

## PR Notice

No pull requests (PRs) will be accepted for this project. If you have any feedback or suggestions, please submit an issue instead. Any PR intended to modify the original repository will be rejected. Should you require source code modification privileges, contact the repository owner or fork this repository for independent maintenance.

## License

This project uses a dual-license model. The original Lyra Code source code is available under AGPLv3-or-later. A commercial license is required for closed-source distribution, private modifications, commercial exceptions, or use cases where you do not want to comply with AGPL copyleft obligations. Third-party components remain governed by their own licenses.

See [LICENSE](LICENSE) for details.
