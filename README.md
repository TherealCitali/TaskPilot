# TaskPilot

TaskPilot is a native Android AI assistant for reliable, user-authorized UI automation. A user gives TaskPilot an everyday instruction, reviews the proposed execution plan, and lets the agent carry out the task through Android's Accessibility Service.

> **Project status:** Android project scaffold started. The AI, encrypted storage, redaction, and accessibility action layers are still being implemented.

## Vision

TaskPilot should make Android automation feel like giving a careful pilot a destination: it observes the current screen, chooses one safe next step, performs it, and checks the result before continuing.

The first version is intended for everyday workflows across installed Android apps, while failing safely when a screen is protected, ambiguous, or inaccessible.

## Example v1 commands

- **YouTube:** "Open YouTube and search for `Minecraft tutorials`."
- **Chrome:** "Open Chrome and search `Kotlin Coroutines guide`."
- **Android Settings:** "Open Settings and enable Battery Saver."
- **WhatsApp:** "Compose a WhatsApp message to John saying `I'll be there in 10 minutes`, but do not send it."
- **Gallery:** "Open Gallery and delete all screenshots older than 30 days."

The Gallery workflow is expected to vary across Google Photos and OEM Gallery apps. TaskPilot must stop rather than guess if it cannot confidently identify the intended items or action.

## Core execution model

Every task follows a continuous one-action loop:

```text
Observe the current UI
        ↓
Build or update the accessibility UI tree
        ↓
Ask the configured AI for the next action
        ↓
Validate exactly one action
        ↓
Execute exactly one action
        ↓
Observe the UI again
        ↺
```

A task ends when the goal is complete, the user cancels it, the agent reports that it cannot continue, or the safety limits are reached.

### Reliability limits

- Stop after **five consecutive action failures**.
- Stop when the UI appears stuck or the expected state does not change.
- Expose a floating **Stop** control for immediate cancellation.
- Ask the user through a floating input panel when the AI needs clarification.
- Never execute ambiguous or unvalidated model output.

## Safety model

TaskPilot is designed for user-authorized automation, not unattended control of a device.

### Plan approval

Before execution, TaskPilot shows a complete, human-readable plan. The user approves the plan once.

### Additional high-risk confirmation

TaskPilot asks for a second confirmation immediately before high-risk actions, including:

- Deleting files or media
- Sending messages or emails
- Making purchases or financial transactions
- Granting permissions
- Changing security-sensitive settings
- Factory reset
- Uninstalling applications
- Other irreversible or externally visible actions

Battery Saver currently follows the normal plan-approval policy. This policy can be made configurable later.

### Sensitive information

TaskPilot must not automatically type, reveal, store, or transmit:

- Passwords
- OTPs and PINs
- Banking credentials
- Credit or debit card details
- UPI PINs
- Recovery codes
- Authentication tokens
- Government IDs unless explicitly approved

When sensitive information is detected, execution pauses and asks the user. Any permitted sensitive interaction must be explicitly decided by the user, entered manually where possible, kept in memory only, and excluded from AI context, logs, and history.

### Stop and fail-safe behavior

The user must be able to stop a task instantly. If an app exposes an unclear control, protected screen, unexpected state, or unsupported custom UI, TaskPilot should pause and ask rather than guessing.

Accessibility automation cannot guarantee control of every application. Android secure surfaces, custom-rendered controls, WebViews, and apps that restrict accessibility may limit what can be observed or operated.

## Android identity

- Namespace: `dev.citali.taskpilot`
- Application ID: `dev.citali.taskpilot`
- Minimum Android version: Android 10 / API 29
- Target Android SDK: API 35

## GitHub Actions

The repository includes two workflows inspired by the automation patterns used in LunarTune:

### Build signed APKs (`.github/workflows/build-apk.yml`)

Runs on every push/commit and can also be started manually from the Actions tab. It builds four signed release APKs with JDK 17:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

No universal APK is produced. The four APKs and a `SHA256SUMS` file are uploaded as a workflow artifact named `TaskPilot-signed-<commit-sha>`.

### Bump version (`.github/workflows/bump-version.yml`)

Run this workflow manually from the Actions tab. Choose `patch`, `minor`, or `major`, or provide an exact semantic version such as `0.2.0`. The workflow will:

1. Increment `versionCode` and update `versionName`.
2. Commit the version change.
3. Create and push a `v<version>` tag.
4. Build four signed, architecture-specific release APKs.
5. Upload the APKs as an artifact.
6. Create a GitHub Release with all four APKs attached.

### Signing setup

The signing keystore must be created and kept by the project owner. Do not commit it or paste it into chat.

Create a new release keystore locally, for example:

```bash
keytool -genkeypair -v \\
  -keystore taskpilot-release.jks \\
  -alias taskpilot \\
  -keyalg RSA -keysize 4096 \\
  -validity 10000
```

Convert it to one line of Base64 before adding it to GitHub Actions Secrets:

```bash
# Linux
base64 -w 0 taskpilot-release.jks > taskpilot-release.jks.b64

# macOS
base64 taskpilot-release.jks | tr -d '\\n' > taskpilot-release.jks.b64
```

Add these repository secrets under **Settings → Secrets and variables → Actions**:

- `TASKPILOT_KEYSTORE_BASE64` — contents of `taskpilot-release.jks.b64`
- `TASKPILOT_KEYSTORE_PASSWORD` — keystore password
- `TASKPILOT_KEY_ALIAS` — normally `taskpilot`
- `TASKPILOT_KEY_PASSWORD` — key password

The workflows decode the keystore only into the temporary GitHub runner directory. They do not store it in the repository. Keep a secure offline backup of the keystore; losing it prevents signing updates with the same identity.

## AI provider configuration

The first version will support configurable OpenAI-compatible services. The user will be able to provide:

- API base URL
- Model name
- API key

The API key must be stored using Android Keystore-backed encryption. It must never be committed to Git, included in logs, or bundled into the application.

Although the model may communicate conversationally, its proposed action must pass through a strict internal validator and allowlist before execution. A practical action vocabulary may include:

- Launch an application
- Find an accessible element
- Tap or click an element
- Long press when explicitly allowed
- Enter ordinary, non-sensitive text
- Scroll
- Navigate back
- Wait for a state change
- Ask the user a question
- Report completion or inability to continue

Only one validated action may be executed per observe-think-act cycle.

## UI context and privacy

TaskPilot will initially use the Accessibility Service UI tree rather than screenshots. The tree may still contain personal information such as names, conversations, email addresses, or account details.

Before transmission, likely sensitive values should be redacted by default. Raw passwords, tokens, PINs, payment data, and similar values must not be sent to the AI.

Chat history, task history, and diagnostic logs should use encrypted, redacted local storage with configurable retention. Raw accessibility trees should not be persisted by default.

## Planned screens

1. Welcome / Onboarding
2. Accessibility permission setup
3. API key and model settings
4. Home / Command screen
5. Plan preview
6. Live execution log
7. Chat / Command history
8. Task history
9. Safety and permissions settings
10. Debug / Developer options
11. About / Diagnostics
12. Floating task controls and question input

## Design direction

The interface should be an Android-native adaptation inspired by InstallerX Revived rather than a direct copy:

- Dark, high-contrast surfaces
- Expressive Material 3 color and typography
- Compact navigation with clear hierarchy
- Rounded cards for tasks, plans, providers, and diagnostics
- Strong accent colors for active states and warnings
- Dense but readable technical information
- Clear separation between safe actions, pending confirmation, running state, and blocked state
- Motion used to communicate progress, not decoration

The primary interaction should feel like a focused utility: quick to start a task, easy to understand before execution, and impossible to miss when something needs confirmation.

## Proposed architecture

The implementation can be organized into focused layers:

```text
app/
├── ui/                 Compose screens, navigation, themes, components
├── accessibility/     AccessibilityService, UI tree extraction, actions
├── agent/              Observe-think-act orchestration and task state
├── ai/                 OpenAI-compatible client, prompts, response parsing
├── safety/             Action allowlist, sensitive-field detection, policies
├── overlay/            Stop control and question input overlay
├── data/               Encrypted settings, history, logs, diagnostics
└── domain/             Commands, plans, actions, results, task models
```

The action executor should be independent from any single AI provider so that the provider client can be replaced without changing the AccessibilityService or safety policy.

## Icon concept

### Working concept: **Waypoint Pilot**

Create a simple adaptive icon showing a bright navigation waypoint being guided by a small pilot-wing or paper-plane shape. The mark should communicate **direction, task progress, and controlled automation** without using a generic robot or brain symbol.

Suggested visual treatment:

- Deep midnight-indigo or near-black background
- One bold electric-violet or blue-violet navigation mark
- A small mint or lime waypoint dot as the focal accent
- Rounded geometry that remains recognizable at small sizes
- No text, letters, or tiny interface details
- A strong silhouette that works in Android's adaptive-icon safe zone

A possible composition is a curved orbital path forming a subtle `T` or compass shape, with the waypoint dot positioned ahead of it. The path can double as a pilot's flight route and a task execution trail.

For the icon source, prepare:

- An adaptive foreground vector with transparent background
- A solid adaptive background layer
- Light and dark variants
- A monochrome Android icon variant
- A 66% safe-zone composition so masking does not crop the mark

## Development principles

- Native Kotlin and Jetpack Compose
- Material 3 Expressive patterns with accessible contrast
- Deny by default when an action is ambiguous
- Keep sensitive values out of AI context and persistent storage
- Make every action observable in the live log
- Keep the Stop control available during execution
- Prefer deterministic validation over trusting model output
- Design for graceful failure across different Android vendors and app UIs

## Repository and contribution safety

Do not commit API keys, GitHub tokens, local secrets, accessibility dumps, screenshots, or personal task history. Use a short-lived, fine-grained repository token for Git operations and keep credentials outside the project files.
