package com.nextide.xmldesigner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "designer_state";
    private static final String KEY_XML = "xml";
    private static final String KEY_FILE_URI = "file_uri";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_FILE_ACCESS_ACCEPTED = "file_access_accepted";
    private static final int REQUEST_OPEN_XML = 7001;
    private static final int REQUEST_CREATE_XML = 7002;
    private static final int REQUEST_LEGACY_STORAGE = 7003;
    private static final int MAX_UNDO_HISTORY = 10;
    private static final Pattern HEX_VALUE_PATTERN = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b");

    private XmlCodeEditText editor;
    private FrameLayout previewHost;
    private ScrollView previewScroll;
    private TextView statusText;
    private Button codeButton;
    private Button previewButton;
    private Uri currentFileUri;
    private String currentFileName = "untitled_layout.xml";
    private boolean previewMode;
    private PreviewNodeInfo selectedPreviewNode;
    private View selectedPreviewView;
    private boolean restoringHistory;
    private String lastHistoryText = "";
    private final ArrayList<String> undoHistory = new ArrayList<String>();
    private final ArrayList<String> redoHistory = new ArrayList<String>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable renderRunnable;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.parseColor("#1C313A"));
        }
        setTitle("XML Designer");
        buildUi();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedUri = prefs.getString(KEY_FILE_URI, null);
        String savedName = prefs.getString(KEY_FILE_NAME, null);
        if (savedUri != null && savedUri.length() > 0 && isXmlFileName(savedName)) {
            currentFileUri = Uri.parse(savedUri);
            currentFileName = savedName;
        }
        String saved = prefs.getString(KEY_XML, null);
        if (saved == null || saved.length() == 0) {
            editor.setText(AutoCompleteDictionary.androidTemplate());
        } else {
            editor.setText(saved);
        }
        editor.setSelection(0);
        editor.requestSyntaxHighlight();
        resetUndoHistoryToCurrent();
        showCodeMode();
        ensureFileAccessGate();
    }

    protected void onPause() {
        super.onPause();
        saveCurrentXml();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#ECEFF1"));
        setContentView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(makeTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(this, 52)));
        root.addView(makeSecondBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(this, 44)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        editor = new XmlCodeEditText(this);
        editor.setHint("Type an Android layout XML file here...");
        content.addView(editor, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        previewScroll = new ScrollView(this);
        previewScroll.setFillViewport(true);
        previewScroll.setBackgroundColor(Color.parseColor("#CFD8DC"));
        previewHost = new FrameLayout(this);
        previewHost.setPadding(Dimen.dp(this, 14), Dimen.dp(this, 14), Dimen.dp(this, 14), Dimen.dp(this, 14));
        previewHost.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_canvas);
        previewScroll.addView(previewHost, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(previewScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#37474F"));
        statusText.setTextSize(12);
        statusText.setTypeface(Typeface.MONOSPACE);
        statusText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        statusText.setPadding(Dimen.dp(this, 10), 0, Dimen.dp(this, 10), 0);
        statusText.setBackgroundResource(com.nextide.xmldesigner.R.drawable.status_panel);
        root.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(this, 34)));

        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCodeStatus();
                if (previewMode) {
                    schedulePreviewRender();
                }
            }

            public void afterTextChanged(Editable s) {
                trackUndoHistory(s.toString());
            }
        });
    }

    private View makeTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Dimen.dp(this, 8), Dimen.dp(this, 6), Dimen.dp(this, 8), Dimen.dp(this, 6));
        bar.setBackgroundColor(Color.parseColor("#263238"));

        TextView title = new TextView(this);
        title.setText("XML Designer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        codeButton = makeTopButton("Code");
        previewButton = makeTopButton("Preview");
        Button helpButton = makeTopButton("Help");

        codeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCodeMode();
            }
        });
        previewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPreviewMode();
            }
        });
        helpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showHelp();
            }
        });

        bar.addView(codeButton, new LinearLayout.LayoutParams(Dimen.dp(this, 74), ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(previewButton, new LinearLayout.LayoutParams(Dimen.dp(this, 88), ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(helpButton, new LinearLayout.LayoutParams(Dimen.dp(this, 70), ViewGroup.LayoutParams.MATCH_PARENT));
        return bar;
    }

    private View makeSecondBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.parseColor("#37474F"));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Dimen.dp(this, 6), Dimen.dp(this, 5), Dimen.dp(this, 6), Dimen.dp(this, 5));
        scroll.addView(bar, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button open = makeSmallButton("Open");
        Button save = makeSmallButton("Save");
        Button saveAs = makeSmallButton("Save As");
        Button undo = makeSmallButton("Undo");
        Button redo = makeSmallButton("Redo");
        Button templates = makeSmallButton("Templates");
        Button format = makeSmallButton("Format");
        Button selectAll = makeSmallButton("Select All");
        Button cut = makeSmallButton("Cut");
        Button copy = makeSmallButton("Copy");
        Button paste = makeSmallButton("Paste");
        Button colour = makeSmallButton("Colour");
        Button props = makeSmallButton("Props");
        Button clear = makeSmallButton("Clear");
        Button insertTag = makeSmallButton("Insert Tag");

        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openXmlFile();
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveXmlFile();
            }
        });
        saveAs.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveXmlFileAs();
            }
        });
        undo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                performUndo();
            }
        });
        redo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                performRedo();
            }
        });
        templates.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTemplateDialog();
            }
        });
        format.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                formatEditorText();
            }
        });
        selectAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectAllCode();
            }
        });
        cut.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cutSelection();
            }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyXml();
            }
        });
        paste.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pasteClipboard();
            }
        });
        colour.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showColourPicker();
            }
        });
        props.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSelectedVisualProperties();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmClear();
            }
        });
        insertTag.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showInsertDialog();
            }
        });

        bar.addView(open, smallParams());
        bar.addView(save, smallParams());
        bar.addView(saveAs, smallParams());
        bar.addView(undo, smallParams());
        bar.addView(redo, smallParams());
        bar.addView(templates, smallParams());
        bar.addView(insertTag, smallParams());
        bar.addView(format, smallParams());
        bar.addView(selectAll, smallParams());
        bar.addView(cut, smallParams());
        bar.addView(copy, smallParams());
        bar.addView(paste, smallParams());
        bar.addView(colour, smallParams());
        bar.addView(props, smallParams());
        bar.addView(clear, smallParams());
        return scroll;
    }

    private LinearLayout.LayoutParams smallParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Dimen.dp(this, 94), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, Dimen.dp(this, 6), 0);
        return lp;
    }

    private Button makeTopButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundResource(com.nextide.xmldesigner.R.drawable.top_button);
        return button;
    }

    private Button makeSmallButton(String text) {
        Button button = makeTopButton(text);
        button.setTextSize(11);
        return button;
    }

    private void showCodeMode() {
        previewMode = false;
        editor.setVisibility(View.VISIBLE);
        previewScroll.setVisibility(View.GONE);
        codeButton.setBackgroundResource(com.nextide.xmldesigner.R.drawable.top_button_selected);
        previewButton.setBackgroundResource(com.nextide.xmldesigner.R.drawable.top_button);
        updateCodeStatus();
    }

    private void showPreviewMode() {
        previewMode = true;
        editor.setVisibility(View.GONE);
        previewScroll.setVisibility(View.VISIBLE);
        codeButton.setBackgroundResource(com.nextide.xmldesigner.R.drawable.top_button);
        previewButton.setBackgroundResource(com.nextide.xmldesigner.R.drawable.top_button_selected);
        renderPreviewNow();
    }

    private void schedulePreviewRender() {
        if (renderRunnable != null) {
            handler.removeCallbacks(renderRunnable);
        }
        renderRunnable = new Runnable() {
            public void run() {
                renderPreviewNow();
            }
        };
        handler.postDelayed(renderRunnable, 250);
    }

    private void renderPreviewNow() {
        previewHost.removeAllViews();
        selectedPreviewNode = null;
        selectedPreviewView = null;
        XmlPreviewBuilder builder = new XmlPreviewBuilder(this);
        builder.setNodeClickListener(new XmlPreviewBuilder.OnPreviewNodeClickListener() {
            public void onPreviewNodeClicked(View view, PreviewNodeInfo info) {
                handlePreviewNodeClicked(view, info);
            }
        });
        try {
            View view = builder.build(editor.getText().toString());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            previewHost.addView(view, lp);
            statusText.setText("Visual edit  |  tap a preview element to edit XML attributes  |  " + lineCount(editor.getText().toString()) + " lines");
        } catch (Exception e) {
            TextView error = new TextView(this);
            error.setText("XML preview failed\n\n" + e.getMessage());
            error.setTextColor(Color.parseColor("#B00020"));
            error.setTextSize(14);
            error.setTypeface(Typeface.MONOSPACE);
            error.setPadding(Dimen.dp(this, 16), Dimen.dp(this, 16), Dimen.dp(this, 16), Dimen.dp(this, 16));
            previewHost.addView(error, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            statusText.setText("Preview error: " + safeMessage(e));
        }
    }

    private void updateCodeStatus() {
        String xml = editor.getText().toString();
        statusText.setText("Code view  |  " + safeFileName() + "  |  XML only  |  autocomplete active  |  " + lineCount(xml) + " lines  |  " + xml.length() + " chars");
    }

    private int lineCount(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        int count = 1;
        int i;
        for (i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getName();
        }
        return message;
    }


    private void ensureFileAccessGate() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_FILE_ACCESS_ACCEPTED, false)) {
            requestLegacyStoragePermissionIfNeeded(true);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("File access required");
        builder.setMessage("XML Designer can only open and save .xml files.\n\nOn Android 13 and newer, Open and Save use the Android file picker. On older Android versions the app may ask for storage permission.\n\nPress Allow to continue. If you refuse, the app will close.");
        builder.setCancelable(false);
        builder.setNegativeButton("Close app", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (!requestLegacyStoragePermissionIfNeeded(true)) {
                    markFileAccessAccepted();
                }
            }
        });
        builder.show();
    }

    private boolean requestLegacyStoragePermissionIfNeeded(boolean closeIfDenied) {
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 33) {
            return false;
        }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (closeIfDenied) {
            requestPermissions(legacyPermissions(), REQUEST_LEGACY_STORAGE);
            return true;
        }
        return false;
    }

    private void markFileAccessAccepted() {
        SharedPreferences.Editor edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        edit.putBoolean(KEY_FILE_ACCESS_ACCEPTED, true);
        edit.apply();
    }

    private String[] legacyPermissions() {
        if (Build.VERSION.SDK_INT <= 28) {
            return new String[] { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
        }
        return new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE) {
            boolean granted = true;
            int i;
            if (grantResults == null || grantResults.length == 0) {
                granted = false;
            } else {
                for (i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        granted = false;
                        break;
                    }
                }
            }
            if (!granted) {
                Toast.makeText(this, "Storage permission refused. Closing app.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                markFileAccessAccepted();
            }
        }
    }

    private void openXmlFile() {
        showCodeMode();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "text/xml", "application/xml", "text/plain" });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OPEN_XML);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker found on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void saveXmlFile() {
        if (currentFileUri == null) {
            saveXmlFileAs();
            return;
        }
        if (!isXmlFileName(currentFileName)) {
            Toast.makeText(this, "Current file is not a .xml file. Use Save As with a .xml name.", Toast.LENGTH_LONG).show();
            return;
        }
        writeEditorToUri(currentFileUri, currentFileName);
    }

    private void saveXmlFileAs() {
        showCodeMode();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedXmlName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CREATE_XML);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file creator found on this device", Toast.LENGTH_LONG).show();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        takePersistablePermission(data, uri);
        if (requestCode == REQUEST_OPEN_XML) {
            handleXmlOpenResult(uri);
        } else if (requestCode == REQUEST_CREATE_XML) {
            handleXmlCreateResult(uri);
        }
    }

    private void handleXmlOpenResult(Uri uri) {
        String name = getDisplayName(uri);
        if (!isXmlFileName(name)) {
            Toast.makeText(this, "Only .xml files can be opened", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String content = readTextFromUri(uri);
            editor.setText(content);
            editor.setSelection(0);
            editor.requestSyntaxHighlight();
            resetUndoHistoryToCurrent();
            currentFileUri = uri;
            currentFileName = name;
            saveCurrentXml();
            showCodeMode();
            Toast.makeText(this, "Opened " + safeFileName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleXmlCreateResult(Uri uri) {
        String name = getDisplayName(uri);
        if (!isXmlFileName(name)) {
            Toast.makeText(this, "Save cancelled: filename must end with .xml", Toast.LENGTH_LONG).show();
            return;
        }
        currentFileUri = uri;
        currentFileName = name;
        writeEditorToUri(uri, name);
    }

    private void writeEditorToUri(Uri uri, String name) {
        OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                throw new IOException("Could not open output stream");
            }
            byte[] bytes = editor.getText().toString().getBytes("UTF-8");
            out.write(bytes);
            out.flush();
            saveCurrentXml();
            statusText.setText("Saved  |  " + name + "  |  " + bytes.length + " bytes");
            Toast.makeText(this, "Saved " + name, Toast.LENGTH_SHORT).show();
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, "Save failed: UTF-8 not supported", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String readTextFromUri(Uri uri) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("Could not open input stream");
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 not supported");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void takePersistablePermission(Intent data, Uri uri) {
        if (Build.VERSION.SDK_INT < 19 || data == null || uri == null) {
            return;
        }
        int flags = data.getFlags();
        int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (takeFlags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {
        }
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        if (uri != null && "content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null || result.length() == 0) {
            result = uri == null ? null : uri.getLastPathSegment();
        }
        if (result == null || result.length() == 0) {
            result = "layout.xml";
        }
        return result;
    }

    private boolean isXmlFileName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".xml");
    }

    private String suggestedXmlName() {
        if (isXmlFileName(currentFileName)) {
            return currentFileName;
        }
        return "layout.xml";
    }

    private String safeFileName() {
        if (currentFileName == null || currentFileName.length() == 0) {
            return "untitled_layout.xml";
        }
        return currentFileName;
    }

    private void showTemplateDialog() {
        final String[] names = new String[] {
                "Android LinearLayout screen",
                "AndroidX ConstraintLayout style",
                "Kotlin XML / Compose holder style"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose XML template");
        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    editor.setText(AutoCompleteDictionary.androidTemplate());
                } else if (which == 1) {
                    editor.setText(AutoCompleteDictionary.androidXTemplate());
                } else {
                    editor.setText(AutoCompleteDictionary.kotlinStyleTemplate());
                }
                editor.setSelection(0);
                showCodeMode();
                saveCurrentXml();
            }
        });
        builder.show();
    }

    private void showInsertDialog() {
        final String[] tags = new String[] {
                "TextView", "Button", "EditText", "ImageView", "LinearLayout vertical",
                "LinearLayout horizontal", "AndroidX RecyclerView", "AndroidX ConstraintLayout",
                "Material Button", "Kotlin ComposeView holder"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Insert layout tag");
        builder.setItems(tags, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                insertXml(tagSnippet(which));
            }
        });
        builder.show();
    }

    private String tagSnippet(int which) {
        if (which == 0) {
            return "\n<TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"TextView\" />\n";
        }
        if (which == 1) {
            return "\n<Button\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Button\" />\n";
        }
        if (which == 2) {
            return "\n<EditText\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"Input\" />\n";
        }
        if (which == 3) {
            return "\n<ImageView\n    android:layout_width=\"96dp\"\n    android:layout_height=\"96dp\"\n    android:src=\"@drawable/ic_launcher\" />\n";
        }
        if (which == 4) {
            return "\n<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"vertical\">\n\n</LinearLayout>\n";
        }
        if (which == 5) {
            return "\n<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"horizontal\">\n\n</LinearLayout>\n";
        }
        if (which == 6) {
            return "\n<androidx.recyclerview.widget.RecyclerView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"180dp\" />\n";
        }
        if (which == 7) {
            return "\n<androidx.constraintlayout.widget.ConstraintLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:padding=\"12dp\">\n\n</androidx.constraintlayout.widget.ConstraintLayout>\n";
        }
        if (which == 8) {
            return "\n<com.google.android.material.button.MaterialButton\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Material Button\" />\n";
        }
        return "\n<androidx.compose.ui.platform.ComposeView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"160dp\" />\n";
    }

    private void insertXml(String value) {
        int start = Math.max(editor.getSelectionStart(), 0);
        editor.getText().insert(start, value);
        editor.setSelection(start + value.length());
        showCodeMode();
    }

    private void selectAllCode() {
        showCodeMode();
        editor.requestFocus();
        editor.setSelection(0, editor.length());
        statusText.setText("Selection active  |  all code selected  |  " + editor.length() + " chars");
    }

    private void cutSelection() {
        showCodeMode();
        int start = normalizedSelectionStart();
        int end = normalizedSelectionEnd();
        if (start == end) {
            Toast.makeText(this, "Select code to cut", Toast.LENGTH_SHORT).show();
            return;
        }
        String selected = editor.getText().toString().substring(start, end);
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("layout selection", selected));
        }
        editor.getText().delete(start, end);
        editor.setSelection(start);
        editor.requestSyntaxHighlight();
        Toast.makeText(this, "Selection cut", Toast.LENGTH_SHORT).show();
    }

    private void copyXml() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return;
        }
        int start = normalizedSelectionStart();
        int end = normalizedSelectionEnd();
        if (start != end) {
            String selected = editor.getText().toString().substring(start, end);
            manager.setPrimaryClip(ClipData.newPlainText("layout selection", selected));
            Toast.makeText(this, "Selection copied", Toast.LENGTH_SHORT).show();
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("layout.xml", editor.getText().toString()));
            Toast.makeText(this, "XML copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteClipboard() {
        showCodeMode();
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip() || manager.getPrimaryClip() == null || manager.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence paste = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (paste == null) {
            Toast.makeText(this, "Clipboard has no text", Toast.LENGTH_SHORT).show();
            return;
        }
        int start = normalizedSelectionStart();
        int end = normalizedSelectionEnd();
        editor.getText().replace(start, end, paste);
        editor.setSelection(start + paste.length());
        editor.requestSyntaxHighlight();
        Toast.makeText(this, "Pasted", Toast.LENGTH_SHORT).show();
    }

    private void showColourPicker() {
        showCodeMode();
        final HexRange range = findSelectedHexRange();
        if (range == null) {
            Toast.makeText(this, "Select or tap a hex colour like #FFFFFF", Toast.LENGTH_LONG).show();
            return;
        }
        final String current = editor.getText().toString().substring(range.start, range.end);
        editor.requestFocus();
        editor.setSelection(range.start, range.end);
        HexColorPickerDialog dialog = new HexColorPickerDialog(this, current, new HexColorPickerDialog.OnColorPickedListener() {
            public void onColorPicked(String hexValue) {
                replaceColour(range, hexValue);
            }
        });
        dialog.show();
    }

    private void replaceColour(HexRange range, String value) {
        if (range == null || value == null) {
            return;
        }
        int len = editor.length();
        int start = range.start;
        int end = range.end;
        if (start < 0) {
            start = 0;
        }
        if (end > len) {
            end = len;
        }
        if (start > end) {
            start = end;
        }
        editor.getText().replace(start, end, value);
        editor.setSelection(start, start + value.length());
        editor.requestSyntaxHighlight();
        saveCurrentXml();
        Toast.makeText(this, "Colour updated to " + value, Toast.LENGTH_SHORT).show();
    }

    private HexRange findSelectedHexRange() {
        String text = editor.getText().toString();
        int start = normalizedSelectionStart();
        int end = normalizedSelectionEnd();
        if (text.length() == 0) {
            return null;
        }
        if (start != end) {
            String selected = text.substring(start, end);
            if (HexColorPickerDialog.normalizeHex(selected) != null) {
                return new HexRange(start, end);
            }
            Matcher selectedMatcher = HEX_VALUE_PATTERN.matcher(selected);
            if (selectedMatcher.find()) {
                return new HexRange(start + selectedMatcher.start(), start + selectedMatcher.end());
            }
        }
        int cursor = editor.getSelectionStart();
        if (cursor < 0) {
            cursor = 0;
        }
        Matcher matcher = HEX_VALUE_PATTERN.matcher(text);
        while (matcher.find()) {
            if (cursor >= matcher.start() && cursor <= matcher.end()) {
                return new HexRange(matcher.start(), matcher.end());
            }
        }
        return null;
    }

    private int normalizedSelectionStart() {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0) {
            a = 0;
        }
        if (b < 0) {
            b = a;
        }
        return Math.min(a, b);
    }

    private int normalizedSelectionEnd() {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0) {
            a = 0;
        }
        if (b < 0) {
            b = a;
        }
        return Math.max(a, b);
    }

    private static class HexRange {
        int start;
        int end;

        HexRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private void confirmClear() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Clear XML?");
        builder.setMessage("This clears the editor text.");
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Clear", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                editor.setText("");
                showCodeMode();
            }
        });
        builder.show();
    }

    private void formatEditorText() {
        String src = editor.getText().toString();
        String formatted = simpleFormat(src);
        editor.setText(formatted);
        editor.setSelection(0);
        Toast.makeText(this, "Basic XML formatting applied", Toast.LENGTH_SHORT).show();
    }

    private String simpleFormat(String src) {
        if (src == null) {
            return "";
        }
        String s = src.replace("><", ">\n<");
        String[] lines = s.split("\n");
        StringBuffer out = new StringBuffer();
        int depth = 0;
        int i;
        for (i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("</") && depth > 0) {
                depth--;
            }
            appendIndent(out, depth);
            out.append(line);
            out.append('\n');
            if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") && !line.startsWith("<!") && !line.endsWith("/>") && line.indexOf("</") < 0) {
                depth++;
            }
        }
        return out.toString();
    }

    private void appendIndent(StringBuffer out, int count) {
        int i;
        for (i = 0; i < count; i++) {
            out.append("    ");
        }
    }

    private void showHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Designer support");
        builder.setMessage("Modes:\n" +
                "• Open: loads .xml files only using the Android file picker. Non-XML files are refused.\n" +
                "• Save / Save As: writes XML back to the opened file or creates a new .xml document.\n" +
                "• Undo / Redo: rolls editor changes backward or forward. Undo keeps the latest 10 changes only.\n" +
                "• Code: AIDE-style XML editor with autocomplete, selection, cut/copy/paste and syntax highlighting.\n" +
                "• Colour: select or tap a hex value like #FFFFFF, adjust it visually, then OK replaces the old value.\n" +
                "• Preview / Visual edit: renders XML visually. Tap a preview element to edit common XML attributes, then Apply writes changes back into the code.\n\n" +
                "Preview support:\n" +
                "• Native Android widgets: LinearLayout, RelativeLayout, FrameLayout, TextView, Button, EditText and more.\n" +
                "• AndroidX tags are recognised and mapped to safe native preview placeholders, so the project still compiles without AndroidX jars.\n" +
                "• Kotlin project XML styles such as ComposeView host layouts are recognised as preview placeholders.\n\n" +
                "This is an AIDE-compatible Java-only project: no lambdas and no external dependencies.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }


    private void resetUndoHistoryToCurrent() {
        undoHistory.clear();
        redoHistory.clear();
        lastHistoryText = editor == null || editor.getText() == null ? "" : editor.getText().toString();
    }

    private void trackUndoHistory(String current) {
        if (restoringHistory) {
            return;
        }
        if (current == null) {
            current = "";
        }
        if (lastHistoryText == null) {
            lastHistoryText = current;
            return;
        }
        if (!current.equals(lastHistoryText)) {
            addUndoSnapshot(lastHistoryText);
            redoHistory.clear();
            lastHistoryText = current;
        }
    }

    private void addUndoSnapshot(String value) {
        if (value == null) {
            value = "";
        }
        int size = undoHistory.size();
        if (size > 0 && value.equals(undoHistory.get(size - 1))) {
            return;
        }
        undoHistory.add(value);
        while (undoHistory.size() > MAX_UNDO_HISTORY) {
            undoHistory.remove(0);
        }
    }

    private void performUndo() {
        if (undoHistory.size() == 0) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        String current = editor.getText().toString();
        redoHistory.add(current);
        while (redoHistory.size() > MAX_UNDO_HISTORY) {
            redoHistory.remove(0);
        }
        String previous = undoHistory.remove(undoHistory.size() - 1);
        applyHistoryText(previous, "Undo");
    }

    private void performRedo() {
        if (redoHistory.size() == 0) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        String current = editor.getText().toString();
        addUndoSnapshot(current);
        String next = redoHistory.remove(redoHistory.size() - 1);
        applyHistoryText(next, "Redo");
    }

    private void applyHistoryText(String value, String label) {
        if (value == null) {
            value = "";
        }
        int cursor = editor.getSelectionStart();
        restoringHistory = true;
        editor.setText(value);
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > editor.length()) {
            cursor = editor.length();
        }
        editor.setSelection(cursor);
        editor.requestSyntaxHighlight();
        restoringHistory = false;
        lastHistoryText = value;
        saveCurrentXml();
        if (previewMode) {
            renderPreviewNow();
        } else {
            updateCodeStatus();
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
    }

    private void handlePreviewNodeClicked(View view, PreviewNodeInfo info) {
        selectedPreviewView = view;
        selectedPreviewNode = info;
        if (view != null) {
            try {
                view.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_outline);
            } catch (Exception ignored) {
            }
        }
        if (info != null) {
            statusText.setText("Visual edit  |  selected " + info.getDisplayName() + "  |  tap Props or edit attributes now");
            showVisualPropertyDialog(info);
        }
    }

    private void showSelectedVisualProperties() {
        if (!previewMode) {
            showPreviewMode();
            Toast.makeText(this, "Tap a preview element first, then press Props", Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedPreviewNode == null) {
            Toast.makeText(this, "Tap a preview element first", Toast.LENGTH_SHORT).show();
            return;
        }
        showVisualPropertyDialog(selectedPreviewNode);
    }

    private void showVisualPropertyDialog(final PreviewNodeInfo info) {
        if (info == null) {
            return;
        }
        final TagRange range = findStartTagRangeByIndex(editor.getText().toString(), info.getIndex());
        if (range == null) {
            Toast.makeText(this, "Could not find the selected XML tag", Toast.LENGTH_LONG).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Dimen.dp(this, 18), Dimen.dp(this, 12), Dimen.dp(this, 18), Dimen.dp(this, 4));
        scroll.addView(form, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView intro = new TextView(this);
        intro.setText("Selected: " + info.getDisplayName() + "\nEdit values, then press Apply. Blank existing values remove that attribute.");
        intro.setTextColor(Color.parseColor("#263238"));
        intro.setTextSize(13);
        intro.setPadding(0, 0, 0, Dimen.dp(this, 8));
        form.addView(intro, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final VisualField[] fields = new VisualField[] {
                addVisualField(form, "id", "android:id", range.tagText),
                addVisualField(form, "layout_width", "android:layout_width", range.tagText),
                addVisualField(form, "layout_height", "android:layout_height", range.tagText),
                addVisualField(form, "text", "android:text", range.tagText),
                addVisualField(form, "hint", "android:hint", range.tagText),
                addVisualField(form, "orientation", "android:orientation", range.tagText),
                addVisualField(form, "background", "android:background", range.tagText),
                addVisualField(form, "textColor", "android:textColor", range.tagText),
                addVisualField(form, "padding", "android:padding", range.tagText),
                addVisualField(form, "layout_margin", "android:layout_margin", range.tagText)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Visual properties");
        builder.setView(scroll);
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Code", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                selectVisualTagInCode(info);
            }
        });
        builder.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                applyVisualFields(info, fields);
            }
        });
        builder.show();
    }

    private VisualField addVisualField(LinearLayout parent, String label, String attrName, String tagText) {
        TextView title = new TextView(this);
        title.setText(label + "  (" + attrName + ")");
        title.setTextColor(Color.parseColor("#37474F"));
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, Dimen.dp(this, 7), 0, 0);
        parent.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setSelectAllOnFocus(false);
        input.setPadding(Dimen.dp(this, 6), 0, Dimen.dp(this, 6), 0);
        String value = extractAttrValue(tagText, attrName);
        if (value != null) {
            input.setText(value);
            input.setSelection(input.length());
        }
        parent.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(this, 44)));
        return new VisualField(attrName, value, input);
    }

    private void applyVisualFields(PreviewNodeInfo info, VisualField[] fields) {
        if (info == null || fields == null) {
            return;
        }
        String xml = editor.getText().toString();
        TagRange range = findStartTagRangeByIndex(xml, info.getIndex());
        if (range == null) {
            Toast.makeText(this, "Could not update: XML tag moved", Toast.LENGTH_LONG).show();
            return;
        }
        String tag = range.tagText;
        boolean changed = false;
        int i;
        for (i = 0; i < fields.length; i++) {
            VisualField field = fields[i];
            if (field == null || field.input == null) {
                continue;
            }
            String newValue = field.input.getText().toString();
            String oldValue = field.originalValue;
            if (oldValue == null && newValue.length() == 0) {
                continue;
            }
            if (oldValue == null || !oldValue.equals(newValue)) {
                tag = setTagAttribute(tag, field.attrName, newValue);
                changed = true;
            }
        }
        if (!changed) {
            Toast.makeText(this, "No property changes", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.getText().replace(range.start, range.end, tag);
        editor.setSelection(range.start, range.start + tag.length());
        editor.requestSyntaxHighlight();
        saveCurrentXml();
        renderPreviewNow();
        Toast.makeText(this, "Visual XML properties applied", Toast.LENGTH_SHORT).show();
    }

    private void selectVisualTagInCode(PreviewNodeInfo info) {
        if (info == null) {
            return;
        }
        TagRange range = findStartTagRangeByIndex(editor.getText().toString(), info.getIndex());
        if (range == null) {
            Toast.makeText(this, "Could not find XML tag", Toast.LENGTH_SHORT).show();
            return;
        }
        showCodeMode();
        editor.requestFocus();
        editor.setSelection(range.start, range.end);
        statusText.setText("Code view  |  selected " + info.getDisplayName() + " tag from visual editor");
    }

    private TagRange findStartTagRangeByIndex(String xml, int wantedIndex) {
        if (xml == null || wantedIndex <= 0) {
            return null;
        }
        Pattern pattern = Pattern.compile("<\\s*([A-Za-z_][A-Za-z0-9_.$:-]*)\\b(?:(?:\"[^\"]*\")|[^\"<>])*?>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        int index = 0;
        while (matcher.find()) {
            index++;
            if (index == wantedIndex) {
                return new TagRange(matcher.start(), matcher.end(), matcher.group(1), matcher.group(0));
            }
        }
        return null;
    }

    private String extractAttrValue(String tagText, String attrName) {
        if (tagText == null || attrName == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("([A-Za-z_][A-Za-z0-9_:.\\-]*)\\s*=\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(tagText);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (attrNameMatches(name, attrName)) {
                return unescapeXml(matcher.group(2));
            }
        }
        return null;
    }

    private String setTagAttribute(String tagText, String attrName, String value) {
        if (tagText == null) {
            return "";
        }
        if (value == null) {
            value = "";
        }
        Pattern pattern = Pattern.compile("([A-Za-z_][A-Za-z0-9_:.\\-]*)\\s*=\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(tagText);
        StringBuffer out = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            String name = matcher.group(1);
            if (attrNameMatches(name, attrName)) {
                found = true;
                if (value.length() == 0) {
                    matcher.appendReplacement(out, "");
                } else {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(name + "=\"" + escapeXml(value) + "\""));
                }
            }
        }
        matcher.appendTail(out);
        String result = out.toString();
        if (found) {
            return result;
        }
        if (value.length() == 0) {
            return tagText;
        }
        int close = result.lastIndexOf("/>");
        if (close < 0) {
            close = result.lastIndexOf(">");
        }
        if (close < 0) {
            return result;
        }
        String insert = "\n    " + attrName + "=\"" + escapeXml(value) + "\"";
        return result.substring(0, close) + insert + result.substring(close);
    }

    private boolean attrNameMatches(String found, String wanted) {
        if (found == null || wanted == null) {
            return false;
        }
        if (found.equals(wanted)) {
            return true;
        }
        String wantedLocal = wanted;
        int colon = wanted.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < wanted.length()) {
            wantedLocal = wanted.substring(colon + 1);
        }
        return found.indexOf(':') < 0 && found.equals(wantedLocal);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        String result = value.replace("&", "&amp;");
        result = result.replace("\"", "&quot;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        return result;
    }

    private String unescapeXml(String value) {
        if (value == null) {
            return null;
        }
        String result = value.replace("&quot;", "\"");
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&amp;", "&");
        return result;
    }

    private static class TagRange {
        int start;
        int end;
        String name;
        String tagText;

        TagRange(int start, int end, String name, String tagText) {
            this.start = start;
            this.end = end;
            this.name = name;
            this.tagText = tagText;
        }
    }

    private static class VisualField {
        String attrName;
        String originalValue;
        EditText input;

        VisualField(String attrName, String originalValue, EditText input) {
            this.attrName = attrName;
            this.originalValue = originalValue;
            this.input = input;
        }
    }

    private void saveCurrentXml() {
        SharedPreferences.Editor edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        edit.putString(KEY_XML, editor.getText().toString());
        edit.putString(KEY_FILE_NAME, safeFileName());
        if (currentFileUri != null) {
            edit.putString(KEY_FILE_URI, currentFileUri.toString());
        } else {
            edit.remove(KEY_FILE_URI);
        }
        edit.apply();
    }
}
