# Consumer rules for :core — the shared Android baseline.
# Keep the EXIF model intact: it is a plain data holder read reflectively by nothing,
# but R8 full mode is enabled in both apps, so state the intent explicitly.
-keep class com.photoselector.core.model.** { *; }
