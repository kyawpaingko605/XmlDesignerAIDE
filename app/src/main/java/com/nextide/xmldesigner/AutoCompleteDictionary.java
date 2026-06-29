package com.nextide.xmldesigner;

public final class AutoCompleteDictionary {
    private AutoCompleteDictionary() {
    }

    public static String[] words() {
        return new String[] {
                "LinearLayout", "RelativeLayout", "FrameLayout", "ScrollView", "HorizontalScrollView",
                "TextView", "Button", "EditText", "ImageView", "CheckBox", "RadioButton", "RadioGroup",
                "Switch", "ProgressBar", "SeekBar", "Spinner", "ListView", "GridView", "WebView",
                "Space", "View", "include", "merge",
                "androidx.constraintlayout.widget.ConstraintLayout",
                "androidx.recyclerview.widget.RecyclerView",
                "androidx.core.widget.NestedScrollView",
                "androidx.appcompat.widget.Toolbar",
                "androidx.cardview.widget.CardView",
                "androidx.viewpager2.widget.ViewPager2",
                "com.google.android.material.button.MaterialButton",
                "com.google.android.material.textview.MaterialTextView",
                "com.google.android.material.textfield.TextInputLayout",
                "com.google.android.material.textfield.TextInputEditText",
                "com.google.android.material.appbar.MaterialToolbar",
                "androidx.compose.ui.platform.ComposeView",
                "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "xmlns:app=\"http://schemas.android.com/apk/res-auto\"",
                "xmlns:tools=\"http://schemas.android.com/tools\"",
                "android:id=\"@+id/\"",
                "android:layout_width=\"match_parent\"", "android:layout_width=\"wrap_content\"",
                "android:layout_height=\"match_parent\"", "android:layout_height=\"wrap_content\"",
                "android:layout_weight=\"1\"", "android:orientation=\"vertical\"", "android:orientation=\"horizontal\"",
                "android:gravity=\"center\"", "android:gravity=\"center_vertical\"", "android:gravity=\"center_horizontal\"",
                "android:layout_gravity=\"center\"", "android:padding=\"16dp\"", "android:paddingLeft=\"16dp\"",
                "android:paddingTop=\"16dp\"", "android:paddingRight=\"16dp\"", "android:paddingBottom=\"16dp\"",
                "android:layout_margin=\"8dp\"", "android:layout_marginLeft=\"8dp\"", "android:layout_marginTop=\"8dp\"",
                "android:layout_marginRight=\"8dp\"", "android:layout_marginBottom=\"8dp\"",
                "android:text=\"Text\"", "android:hint=\"Hint\"", "android:textSize=\"16sp\"",
                "android:textColor=\"#212121\"", "android:background=\"#FFFFFF\"",
                "android:src=\"@drawable/\"", "app:srcCompat=\"@drawable/\"",
                "android:visibility=\"visible\"", "android:visibility=\"gone\"", "android:visibility=\"invisible\"",
                "android:checked=\"true\"", "android:singleLine=\"true\"", "android:maxLines=\"1\"",
                "android:inputType=\"text\"", "android:inputType=\"textEmailAddress\"", "android:inputType=\"number\"",
                "android:layout_centerInParent=\"true\"", "android:layout_centerHorizontal=\"true\"",
                "android:layout_alignParentTop=\"true\"", "android:layout_alignParentBottom=\"true\"",
                "android:layout_alignParentLeft=\"true\"", "android:layout_alignParentRight=\"true\"",
                "app:layout_constraintTop_toTopOf=\"parent\"", "app:layout_constraintBottom_toBottomOf=\"parent\"",
                "app:layout_constraintStart_toStartOf=\"parent\"", "app:layout_constraintEnd_toEndOf=\"parent\"",
                "tools:text=\"Preview text\"", "tools:visibility=\"visible\"", "tools:context=\".MainActivity\""
        };
    }

    public static String androidTemplate() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:padding=\"16dp\"\n" +
                "    android:background=\"#FFFFFF\">\n\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/titleText\"\n" +
                "        android:layout_width=\"match_parent\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"AIDE XML Designer\"\n" +
                "        android:textSize=\"22sp\"\n" +
                "        android:textColor=\"#263238\" />\n\n" +
                "    <EditText\n" +
                "        android:id=\"@+id/nameInput\"\n" +
                "        android:layout_width=\"match_parent\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_marginTop=\"12dp\"\n" +
                "        android:hint=\"Type here\" />\n\n" +
                "    <Button\n" +
                "        android:id=\"@+id/previewButton\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_marginTop=\"12dp\"\n" +
                "        android:text=\"Preview\" />\n\n" +
                "</LinearLayout>\n";
    }

    public static String androidXTemplate() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:padding=\"16dp\"\n" +
                "    tools:context=\".MainActivity\">\n\n" +
                "    <com.google.android.material.textview.MaterialTextView\n" +
                "        android:id=\"@+id/titleText\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"AndroidX style preview\"\n" +
                "        android:textSize=\"22sp\"\n" +
                "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                "        app:layout_constraintStart_toStartOf=\"parent\" />\n\n" +
                "    <com.google.android.material.button.MaterialButton\n" +
                "        android:id=\"@+id/actionButton\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_marginTop=\"16dp\"\n" +
                "        android:text=\"Material Button\"\n" +
                "        app:layout_constraintTop_toBottomOf=\"@id/titleText\"\n" +
                "        app:layout_constraintStart_toStartOf=\"parent\" />\n\n" +
                "</androidx.constraintlayout.widget.ConstraintLayout>\n";
    }

    public static String kotlinStyleTemplate() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    tools:context=\".MainActivity\">\n\n" +
                "    <androidx.compose.ui.platform.ComposeView\n" +
                "        android:id=\"@+id/composeHolder\"\n" +
                "        android:layout_width=\"match_parent\"\n" +
                "        android:layout_height=\"180dp\"\n" +
                "        android:layout_margin=\"16dp\" />\n\n" +
                "    <TextView\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_gravity=\"center\"\n" +
                "        android:text=\"Kotlin project XML / Compose holder preview\"\n" +
                "        android:textSize=\"18sp\" />\n\n" +
                "</FrameLayout>\n";
    }
}
