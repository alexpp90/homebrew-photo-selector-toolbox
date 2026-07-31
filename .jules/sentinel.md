## 2024-08-01 - Prevent ActivityNotFoundException on Implicit Intents
**Vulnerability:** Implicit intents (like ACTION_VIEW) triggered without checking if an app exists to handle them can crash the app with an ActivityNotFoundException.
**Learning:** Wrapping the intent launch in a try-catch block is the safest way to prevent a crash, especially on newer Android versions where package visibility restrictions make `resolveActivity` unreliable.
**Prevention:** Always wrap implicit intent launches in a try-catch block and show a user-friendly message (like a Toast) if no app is found.
