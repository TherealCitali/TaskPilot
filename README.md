<p align="center">
  <img src="assets/taskpilot-icon.png" alt="TaskPilot icon" width="220" />
</p>

<h1 align="center">TaskPilot</h1>

<p align="center">
  <strong>Careful, user-authorized AI automation for Android.</strong>
</p>

<p align="center">
  TaskPilot observes the current Android UI, proposes a plan, and performs one validated action at a time through Accessibility Service.
</p>

<p align="center">
  <em>Private by default, transparent while running, and designed to stop rather than guess.</em>
</p>

<hr />

<p align="center">
  <a href="https://github.com/TherealCitali/TaskPilot/issues">Support</a>
  &nbsp; · &nbsp;
  <a href="https://github.com/TherealCitali/TaskPilot/issues/new">Report an issue</a>
  &nbsp; · &nbsp;
  <a href="https://github.com/TherealCitali/TaskPilot/actions">Builds</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Design-Material_3-000000?style=for-the-badge&logo=material-design&color=6366f1&labelColor=1e1e2e" alt="Material Design 3" />
  <img src="https://img.shields.io/github/license/TherealCitali/TaskPilot?style=for-the-badge&color=6366f1&labelColor=1e1e2e" alt="License" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7f52ff?style=for-the-badge&logo=kotlin&color=6366f1&labelColor=1e1e2e" alt="Kotlin Language" />
 <img src="https://img.shields.io/badge/Toolkit-Jetpack_Compose-4285f4?style=for-the-badge&logo=jetpack-compose&color=6366f1&labelColor=1e1e2e" alt="Jetpack Compose Toolkit" />
</p>

## ✦ What is TaskPilot?

TaskPilot is a native Android AI assistant for everyday, user-approved automation. You type an instruction, review the complete plan, approve it, and watch TaskPilot carry out the work through Android's Accessibility Service.

The app is inspired by the focused, dark utility feel of InstallerX Revived and the presentation style of LunarTune, while keeping its own Android-native Material 3 Expressive identity.

> **Project status:** The Android UI and CI scaffold are in place. The AI client, encrypted app storage, redaction engine, overlay service, and real action runner are being implemented incrementally.

## ✦ Example commands

- **YouTube** — “Open YouTube and search for `Minecraft tutorials`.”
- **Chrome** — “Open Chrome and search `Kotlin Coroutines guide`.”
- **Android Settings** — “Open Settings and enable Battery Saver.”
- **WhatsApp** — “Compose a WhatsApp message to John saying `I'll be there in 10 minutes`, but do not send it.”
- **Gallery** — “Open Gallery and delete all screenshots older than 30 days.”

Gallery behavior can differ between Google Photos and OEM Gallery apps. TaskPilot must stop if it cannot confidently identify the intended items or deletion scope.

## ✦ How the agent works

Every task uses a one-action control loop:

```text
┌──────────┐    ┌─────────┐    ┌───────────┐    ┌────────────┐
│ Observe  │ →  │ Think   │ →  │ Validate  │ →  │ Act once   │
└──────────┘    └─────────┘    └───────────┘    └────────────┘
      ↑                                                   │
      └────────────── Observe the new UI state ──────────┘
```

1. Observe the current accessible UI tree.
2. Send a redacted representation to the configured OpenAI-compatible endpoint.
3. Ask the model for one next action or a question for the user.
4. Validate the action against the safety policy and target UI.
5. Execute exactly one action.
6. Observe again and repeat until completion or cancellation.

### Reliability guardrails

- Stop after five consecutive failures.
- Stop if the screen appears stuck or the expected state does not change.
- Keep a floating Stop control available during execution.
- Pause when the target or requested action is ambiguous.
- Never execute unvalidated model output.

## ✦ Safety first

TaskPilot is intended for user-authorized automation, not unattended device control.

### Confirmation levels

A complete plan is shown before any task starts. The user approves the plan once. TaskPilot then asks for an additional confirmation immediately before high-risk actions such as:

- Deleting files or media
- Sending messages or emails
- Purchases or financial transactions
- Granting permissions
- Changing security-sensitive settings
- Factory reset
- Uninstalling applications
- Other irreversible or externally visible actions

### Sensitive information

TaskPilot must not automatically type, reveal, store, or transmit passwords, OTPs, banking credentials, card details, UPI PINs, recovery codes, authentication tokens, or government IDs unless the user explicitly decides to allow a one-time interaction.

When such information is detected, the task pauses. Any permitted sensitive entry should be manual, kept in memory only, excluded from AI context, and excluded from logs and history.

### Privacy

- Accessibility-tree values are redacted by default before transmission.
- Raw accessibility trees are not persisted by default.
- API keys use Android Keystore-backed storage once the data layer is connected.
- Chat history, task history, and diagnostics are intended to contain only encrypted, redacted data.
- Secrets never belong in Git, APK resources, logs, or README files.

## ✦ UI direction

TaskPilot's UI is an Android-native interpretation of the dark, compact utility style shown in InstallerX Revived and LunarTune:

- Dark high-contrast surfaces
- Expressive Material 3 typography and color
- Purple-violet primary accents with a lime waypoint highlight
- Rounded cards for commands, plans, settings, and diagnostics
- Compact bottom navigation
- Strong visual states for ready, running, paused, blocked, and completed
- Clear plan previews before execution
- Floating Stop and question controls during active tasks

The interface should feel technical without feeling intimidating: a fast command surface, a readable plan, and a live log that makes every action understandable.

## ✦ Android identity

- **Namespace:** `dev.citali.taskpilot`
- **Application ID:** `dev.citali.taskpilot`
- **Minimum Android:** Android 10 / API 29
- **Target SDK:** API 35
- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose
- **Design system:** Material 3 Expressive patterns

## ✦ CI pre-releases

Every push runs `.github/workflows/build-apk.yml`. It builds four signed, architecture-specific APKs and uploads each one as a separate artifact:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

The same workflow creates a unique GitHub **pre-release** containing those APKs and `SHA256SUMS` as separate release assets. Only the latest 10 CI pre-releases with `taskpilot-ci-*` tags are retained; older ones are removed automatically.

The workflow uses GitHub's built-in `GITHUB_TOKEN` with `contents: write`, so a separate PAT is not required for release creation. The manual version-bump workflow creates normal versioned releases.

## ✦ Limitations

AccessibilityService cannot control every application reliably. Secure screens, protected fields, custom-rendered controls, WebViews, and apps that restrict accessibility may limit what TaskPilot can observe or operate. In those cases, the correct behavior is to pause, explain the limitation, and let the user decide what to do next.

## ✦ Development principles

- Deny by default when an action is ambiguous.
- Make the Stop control available while a task is running.
- Keep sensitive values out of AI context and persistent storage.
- Show every action in the live log.
- Prefer deterministic validation over trusting model output.
- Test across Android vendors and app UI variations.
- Keep CI signing credentials outside the repository.

## License

TaskPilot is released under the [MIT License](LICENSE).
