# XML Designer AIDE Project

A Java-only Android layout XML designer built for on-device AIDE style editing.

## Features

- Initial code view with a monospaced XML editor.
- Open and Save toolbar actions for XML files.
- Only `.xml` files are accepted by the Open flow; non-XML files are rejected before editing.
- Save writes back to the currently opened XML document; Save As creates a new `.xml` document.
- First launch file-access gate: refusing access closes the app. Android 12 and below may also show the standard storage permission dialog; Android 13+ uses the Android file picker for XML documents.
- Toggleable live preview / visual edit mode.
- XML autocomplete for Android, AndroidX, Material-style and Kotlin-project XML tags.
- Syntax highlighting for XML tags, tag names, attributes, quoted values, comments, entities and hex colours.
- Code selection support with toolbar actions:
  - Select All
  - Cut
  - Copy selected text, or copy all XML when nothing is selected
  - Paste over the selected range, or insert at the cursor
- Undo and redo toolbar actions. Undo is capped to the latest 10 remembered changes.
- AIDE-style visual editing: tap a rendered preview element, edit common XML attributes, and Apply writes the values back into the XML source.
- Props toolbar action to reopen the property editor for the currently selected preview element.
- Visual hex colour picker:
  - Select a colour such as `#FFFFFF`, `#FFFFFFFF`, `#FFF` or `#FFFF`.
  - Tap **Colour**.
  - Adjust Alpha, Red, Green and Blue sliders.
  - Press **OK** and the selected hex value is replaced in the editor.
- Safe AndroidX preview mapping without needing AndroidX dependencies to compile.
- Native preview renderer for common layouts and widgets:
  - LinearLayout, RelativeLayout, FrameLayout, ScrollView
  - TextView, Button, EditText, ImageView, CheckBox, RadioButton, Switch
  - ProgressBar, SeekBar, WebView and placeholders for RecyclerView, ConstraintLayout and ComposeView
- Quick templates:
  - Android LinearLayout
  - AndroidX ConstraintLayout style
  - Kotlin XML / Compose holder style
- Java code only. No lambdas. No external dependencies.
- Version 1.3 adds Undo, Redo and tap-to-edit visual XML properties.
- Uses Android Storage Access Framework for modern file access and legacy storage permissions only where Android still supports them.

## Build target

- compileSdkVersion: 34
- targetSdkVersion: 34
- minSdkVersion: 21
- Android Gradle Plugin: 7.4.2

If your AIDE install does not have API 34 installed, install Android SDK platform 34 or lower the compileSdkVersion/targetSdkVersion in `app/build.gradle` to an installed API such as 33.

## Notes

This project intentionally does not depend on AndroidX jars. The designer still recognises AndroidX XML tags and renders them as native placeholder previews so the app remains easy to compile inside AIDE.

File access is handled through Android's document picker on modern Android versions, so XML files can be opened and saved without requesting broad all-files access. On older Android versions the manifest includes the normal legacy read/write storage permissions.
