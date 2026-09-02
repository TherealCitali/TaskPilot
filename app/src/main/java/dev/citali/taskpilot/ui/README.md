# UI

`TaskPilotApp.kt` is the Compose shell. It is wired to the real agent and data
layers rather than mock data:

- **Home** — command surface, plan preview (`CommandPlanner`), and a live log +
  pause/resume/stop controls bound to `AgentEngine.state`.
- **Plan preview** — steps come from `CommandPlanner.parse`, with high-risk steps
  flagged before anything runs.
- **History** — redacted task history persisted via `HistoryStore`.
- **Settings** — provider endpoint/model/API key (Keystore-encrypted via
  `SecureStore`), plus safety and developer toggles persisted via `SettingsStore`.
- **Question dialog** — driven by the engine for high-risk confirmations and
  open questions.
