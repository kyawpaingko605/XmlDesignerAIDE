package com.nextide.xmldesigner;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.MultiAutoCompleteTextView.Tokenizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlCodeEditText extends MultiAutoCompleteTextView {
    private static final int COLOR_TEXT = 0xFFECEFF1;
    private static final int COLOR_TAG = 0xFF80CBC4;
    private static final int COLOR_TAG_NAME = 0xFFFFAB91;
    private static final int COLOR_ATTRIBUTE = 0xFF90CAF9;
    private static final int COLOR_VALUE = 0xFFFFF59D;
    private static final int COLOR_COMMENT = 0xFF78909C;
    private static final int COLOR_HEX = 0xFFA5D6A7;
    private static final int COLOR_ENTITY = 0xFFCE93D8;

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_:.\\-]*)(\\s*=)");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"[^\"]*\"");
    private static final Pattern HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b");
    private static final Pattern ENTITY_PATTERN = Pattern.compile("&[A-Za-z0-9#]+;");

    private boolean internalChange;
    private boolean highlightPending;
    private Handler handler;
    private Runnable highlightRunnable;

    public XmlCodeEditText(Context context) {
        super(context);
        init(context);
    }

    public XmlCodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public XmlCodeEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        handler = new Handler(Looper.getMainLooper());
        setTextColor(COLOR_TEXT);
        setHintTextColor(0xFF90A4AE);
        setTextSize(14);
        setTypeface(Typeface.MONOSPACE);
        setBackgroundResource(com.nextide.xmldesigner.R.drawable.editor_background);
        setPadding(Dimen.dp(context, 12), Dimen.dp(context, 12), Dimen.dp(context, 12), Dimen.dp(context, 12));
        setHorizontallyScrolling(true);
        setSingleLine(false);
        setMinLines(18);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
        setImeOptions(EditorInfo.IME_ACTION_NONE);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setMovementMethod(ArrowKeyMovementMethod.getInstance());
        setSelectAllOnFocus(false);
        setLongClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, AutoCompleteDictionary.words());
        setAdapter(adapter);
        setTokenizer(new XmlTokenizer());
        setThreshold(1);
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (internalChange) {
                    return;
                }
                autoCloseTag(s);
                requestSyntaxHighlight();
            }
        });
        highlightRunnable = new Runnable() {
            public void run() {
                highlightPending = false;
                refreshSyntaxHighlight();
            }
        };
    }

    public void requestSyntaxHighlight() {
        if (handler == null || highlightRunnable == null) {
            return;
        }
        if (highlightPending) {
            handler.removeCallbacks(highlightRunnable);
        }
        highlightPending = true;
        handler.postDelayed(highlightRunnable, 80);
    }

    public void refreshSyntaxHighlight() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        int length = editable.length();
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        internalChange = true;
        clearSyntaxSpans(editable);
        if (length > 0) {
            applyXmlHighlight(editable);
        }
        restoreSelectionSafe(selStart, selEnd);
        internalChange = false;
    }

    private void clearSyntaxSpans(Editable editable) {
        ForegroundColorSpan[] colourSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        int i;
        for (i = 0; i < colourSpans.length; i++) {
            editable.removeSpan(colourSpans[i]);
        }
        StyleSpan[] styleSpans = editable.getSpans(0, editable.length(), StyleSpan.class);
        for (i = 0; i < styleSpans.length; i++) {
            editable.removeSpan(styleSpans[i]);
        }
    }

    private void applyXmlHighlight(Editable editable) {
        String text = editable.toString();
        int len = text.length();
        int pos = 0;
        while (pos < len) {
            int open = text.indexOf('<', pos);
            if (open < 0) {
                break;
            }
            int close = findTagClose(text, open + 1);
            if (close < 0) {
                close = len - 1;
            }
            int end = Math.min(close + 1, len);
            applySpan(editable, COLOR_TAG, open, end);
            if (startsAt(text, open, "<!--")) {
                int commentEnd = text.indexOf("-->", open + 4);
                if (commentEnd < 0) {
                    commentEnd = end - 3;
                }
                applySpan(editable, COLOR_COMMENT, open, Math.min(commentEnd + 3, len));
                applyStyle(editable, Typeface.ITALIC, open, Math.min(commentEnd + 3, len));
                pos = Math.min(commentEnd + 3, len);
            } else {
                highlightTagContents(editable, text, open, end);
                pos = end;
            }
        }
        applyPattern(editable, text, HEX_PATTERN, COLOR_HEX, true);
        applyPattern(editable, text, ENTITY_PATTERN, COLOR_ENTITY, false);
    }

    private boolean startsAt(String text, int index, String prefix) {
        if (index < 0 || index + prefix.length() > text.length()) {
            return false;
        }
        return text.substring(index, index + prefix.length()).equals(prefix);
    }

    private int findTagClose(String text, int start) {
        boolean inQuote = false;
        int i;
        for (i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == '>' && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    private void highlightTagContents(Editable editable, String text, int open, int end) {
        int nameStart = open + 1;
        if (nameStart < end && text.charAt(nameStart) == '/') {
            nameStart++;
        }
        if (nameStart < end && (text.charAt(nameStart) == '?' || text.charAt(nameStart) == '!')) {
            nameStart++;
        }
        while (nameStart < end && Character.isWhitespace(text.charAt(nameStart))) {
            nameStart++;
        }
        int nameEnd = nameStart;
        while (nameEnd < end) {
            char c = text.charAt(nameEnd);
            if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '?') {
                break;
            }
            nameEnd++;
        }
        applySpan(editable, COLOR_TAG_NAME, nameStart, nameEnd);
        applyStyle(editable, Typeface.BOLD, nameStart, nameEnd);

        String body = text.substring(open, end);
        Matcher attrMatcher = ATTRIBUTE_PATTERN.matcher(body);
        while (attrMatcher.find()) {
            int aStart = open + attrMatcher.start(1);
            int aEnd = open + attrMatcher.end(1);
            if (aStart >= nameEnd) {
                applySpan(editable, COLOR_ATTRIBUTE, aStart, aEnd);
            }
        }
        Matcher quotedMatcher = QUOTED_PATTERN.matcher(body);
        while (quotedMatcher.find()) {
            int qStart = open + quotedMatcher.start();
            int qEnd = open + quotedMatcher.end();
            applySpan(editable, COLOR_VALUE, qStart, qEnd);
        }
    }

    private void applyPattern(Editable editable, String text, Pattern pattern, int colour, boolean bold) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            applySpan(editable, colour, matcher.start(), matcher.end());
            if (bold) {
                applyStyle(editable, Typeface.BOLD, matcher.start(), matcher.end());
            }
        }
    }

    private void applySpan(Spannable editable, int colour, int start, int end) {
        if (start < 0 || end <= start || end > editable.length()) {
            return;
        }
        editable.setSpan(new ForegroundColorSpan(colour), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void applyStyle(Spannable editable, int style, int start, int end) {
        if (start < 0 || end <= start || end > editable.length()) {
            return;
        }
        editable.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void restoreSelectionSafe(int start, int end) {
        int len = length();
        if (start < 0) {
            start = 0;
        }
        if (end < 0) {
            end = start;
        }
        if (start > len) {
            start = len;
        }
        if (end > len) {
            end = len;
        }
        try {
            setSelection(start, end);
        } catch (Exception e) {
            setSelection(Math.min(start, len));
        }
    }

    private void autoCloseTag(Editable editable) {
        int pos = getSelectionStart();
        if (pos < 1 || pos > editable.length()) {
            return;
        }
        char justTyped = editable.charAt(pos - 1);
        if (justTyped != '>') {
            return;
        }
        int open = findPrevious(editable, pos - 2, '<');
        if (open < 0) {
            return;
        }
        if (open + 1 < editable.length()) {
            char next = editable.charAt(open + 1);
            if (next == '/' || next == '!' || next == '?') {
                return;
            }
        }
        if (pos >= 2 && editable.charAt(pos - 2) == '/') {
            return;
        }
        String name = readTagName(editable, open + 1, pos - 1);
        if (name.length() == 0 || isSelfClosingName(name)) {
            return;
        }
        String close = "</" + name + ">";
        internalChange = true;
        editable.insert(pos, close);
        setSelection(pos);
        internalChange = false;
    }

    private int findPrevious(CharSequence text, int start, char target) {
        int i;
        for (i = start; i >= 0; i--) {
            if (text.charAt(i) == target) {
                return i;
            }
            if (text.charAt(i) == '>') {
                return -1;
            }
        }
        return -1;
    }

    private String readTagName(CharSequence text, int start, int end) {
        StringBuffer sb = new StringBuffer();
        int i;
        for (i = start; i < end && i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '>') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isSelfClosingName(String name) {
        return "include".equals(name) || "requestFocus".equals(name) || "tag".equals(name);
    }

    private static class XmlTokenizer implements Tokenizer {
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0) {
                char c = text.charAt(i - 1);
                if (Character.isWhitespace(c) || c == '<' || c == '/' || c == '=' || c == '"') {
                    break;
                }
                i--;
            }
            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c) || c == '<' || c == '/' || c == '=' || c == '"' || c == '>') {
                    return i;
                }
                i++;
            }
            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            return text;
        }
    }
}
