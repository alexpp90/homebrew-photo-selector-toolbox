🎯 **What:** Moved the `MIN_SHARPNESS_FOR_AESTHETIC` constant from a hardcoded value in `ScanImagesUseCase` to an adjustable setting backed by DataStore in `SettingsRepository`. Added a slider in `SettingsScreen` to configure the blur rejection threshold on the fly.

💡 **Why:** A TODO existed in `ScanImagesUseCase` indicating that this threshold needed device calibration tuning. Exposing it in the settings removes the hardcoded constant and fulfills the code health need, allowing developers and end-users to easily tune the strictness of the blur check without recompiling the application.

✅ **Verification:** Verified by confirming the UI exposes the parameter in Settings, correctly sets it in DataStore, and `ScanImagesUseCase` successfully reads the updated threshold using `settingsRepository.minSharpnessForAesthetic.first()`. Also ran the full test suite (`cd android && ./gradlew test`) which passed with zero regressions.

✨ **Result:** The `MIN_SHARPNESS_FOR_AESTHETIC` value is now a clean, tunable setting with no loss of the original default behavior (40.0 threshold is preserved).
