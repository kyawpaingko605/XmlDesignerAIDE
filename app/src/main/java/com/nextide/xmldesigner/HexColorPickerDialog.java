package com.nextide.xmldesigner;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class HexColorPickerDialog {
    public interface OnColorPickedListener {
        void onColorPicked(String hexValue);
    }

    private Context context;
    private String startHex;
    private OnColorPickedListener listener;
    private boolean includeAlpha;
    private boolean internalUpdate;
    private int alpha;
    private int red;
    private int green;
    private int blue;
    private TextView swatch;
    private TextView alphaLabel;
    private TextView redLabel;
    private TextView greenLabel;
    private TextView blueLabel;
    private EditText hexEdit;
    private SeekBar alphaSeek;
    private SeekBar redSeek;
    private SeekBar greenSeek;
    private SeekBar blueSeek;

    public HexColorPickerDialog(Context context, String currentHex, OnColorPickedListener listener) {
        this.context = context;
        this.startHex = currentHex;
        this.listener = listener;
    }

    public void show() {
        int parsed = parseHex(startHex, 0xFFFFFFFF);
        alpha = Color.alpha(parsed);
        red = Color.red(parsed);
        green = Color.green(parsed);
        blue = Color.blue(parsed);
        includeAlpha = shouldKeepAlpha(startHex) || alpha < 255;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Dimen.dp(context, 18), Dimen.dp(context, 12), Dimen.dp(context, 18), Dimen.dp(context, 4));

        swatch = new TextView(context);
        swatch.setTextColor(Color.WHITE);
        swatch.setTextSize(14);
        swatch.setGravity(Gravity.CENTER);
        swatch.setText("Preview");
        root.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(context, 54)));

        hexEdit = new EditText(context);
        hexEdit.setSingleLine(true);
        hexEdit.setTextSize(16);
        hexEdit.setFilters(new InputFilter[] { new InputFilter.LengthFilter(9) });
        hexEdit.setPadding(Dimen.dp(context, 8), 0, Dimen.dp(context, 8), 0);
        root.addView(hexEdit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(context, 50)));

        alphaLabel = makeLabel();
        alphaSeek = makeSeek();
        root.addView(alphaLabel, labelParams());
        root.addView(alphaSeek, seekParams());

        redLabel = makeLabel();
        redSeek = makeSeek();
        root.addView(redLabel, labelParams());
        root.addView(redSeek, seekParams());

        greenLabel = makeLabel();
        greenSeek = makeSeek();
        root.addView(greenLabel, labelParams());
        root.addView(greenSeek, seekParams());

        blueLabel = makeLabel();
        blueSeek = makeSeek();
        root.addView(blueLabel, labelParams());
        root.addView(blueSeek, seekParams());

        setSliderValues();
        updateLabelsAndHex();
        attachListeners();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Pick colour");
        builder.setView(root);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String value = normalizeHex(hexEdit.getText().toString());
                if (value == null) {
                    Toast.makeText(context, "Invalid hex colour", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (listener != null) {
                    listener.onColorPicked(value);
                }
            }
        });
        builder.show();
    }

    private TextView makeLabel() {
        TextView label = new TextView(context);
        label.setTextColor(Color.parseColor("#37474F"));
        label.setTextSize(13);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return label;
    }

    private SeekBar makeSeek() {
        SeekBar seek = new SeekBar(context);
        seek.setMax(255);
        return seek;
    }

    private LinearLayout.LayoutParams labelParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(context, 24));
        lp.setMargins(0, Dimen.dp(context, 6), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams seekParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dimen.dp(context, 36));
    }

    private void setSliderValues() {
        internalUpdate = true;
        alphaSeek.setProgress(alpha);
        redSeek.setProgress(red);
        greenSeek.setProgress(green);
        blueSeek.setProgress(blue);
        internalUpdate = false;
    }

    private void attachListeners() {
        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (internalUpdate) {
                    return;
                }
                alpha = alphaSeek.getProgress();
                red = redSeek.getProgress();
                green = greenSeek.getProgress();
                blue = blueSeek.getProgress();
                if (alpha < 255) {
                    includeAlpha = true;
                }
                updateLabelsAndHex();
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        alphaSeek.setOnSeekBarChangeListener(seekListener);
        redSeek.setOnSeekBarChangeListener(seekListener);
        greenSeek.setOnSeekBarChangeListener(seekListener);
        blueSeek.setOnSeekBarChangeListener(seekListener);

        hexEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (internalUpdate) {
                    return;
                }
                String value = normalizeHex(s.toString());
                if (value == null) {
                    return;
                }
                int color = parseHex(value, Color.argb(alpha, red, green, blue));
                alpha = Color.alpha(color);
                red = Color.red(color);
                green = Color.green(color);
                blue = Color.blue(color);
                includeAlpha = shouldKeepAlpha(value) || alpha < 255;
                setSliderValues();
                updateLabelsOnly();
            }
        });
    }

    private void updateLabelsAndHex() {
        internalUpdate = true;
        String value = makeHexValue();
        hexEdit.setText(value);
        hexEdit.setSelection(hexEdit.getText().length());
        updateLabelsOnly();
        internalUpdate = false;
    }

    private void updateLabelsOnly() {
        alphaLabel.setText("Alpha: " + alpha);
        redLabel.setText("Red: " + red);
        greenLabel.setText("Green: " + green);
        blueLabel.setText("Blue: " + blue);
        int color = Color.argb(alpha, red, green, blue);
        swatch.setBackgroundColor(color);
        swatch.setText(makeHexValue());
        if (red + green + blue > 430 && alpha > 130) {
            swatch.setTextColor(Color.BLACK);
        } else {
            swatch.setTextColor(Color.WHITE);
        }
    }

    private String makeHexValue() {
        if (includeAlpha || alpha < 255) {
            return "#" + two(alpha) + two(red) + two(green) + two(blue);
        }
        return "#" + two(red) + two(green) + two(blue);
    }

    private String two(int value) {
        String s = Integer.toHexString(value).toUpperCase();
        if (s.length() == 1) {
            return "0" + s;
        }
        if (s.length() > 2) {
            return s.substring(s.length() - 2);
        }
        return s;
    }

    private static boolean shouldKeepAlpha(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        return v.length() == 4 || v.length() == 8;
    }

    public static String normalizeHex(String input) {
        if (input == null) {
            return null;
        }
        String v = input.trim();
        if (v.length() == 0) {
            return null;
        }
        if (!v.startsWith("#")) {
            v = "#" + v;
        }
        String body = v.substring(1);
        if (!isHex(body)) {
            return null;
        }
        if (body.length() == 3) {
            return "#" + repeat(body.charAt(0)) + repeat(body.charAt(1)) + repeat(body.charAt(2));
        }
        if (body.length() == 4) {
            return "#" + repeat(body.charAt(0)) + repeat(body.charAt(1)) + repeat(body.charAt(2)) + repeat(body.charAt(3));
        }
        if (body.length() == 6 || body.length() == 8) {
            return "#" + body.toUpperCase();
        }
        return null;
    }

    private static String repeat(char c) {
        String s = String.valueOf(c).toUpperCase();
        return s + s;
    }

    private static boolean isHex(String text) {
        int i;
        for (i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static int parseHex(String input, int fallback) {
        String value = normalizeHex(input);
        if (value == null) {
            return fallback;
        }
        try {
            String body = value.substring(1);
            long raw = Long.parseLong(body, 16);
            if (body.length() == 6) {
                return (int) (0xFF000000L | raw);
            }
            return (int) raw;
        } catch (Exception e) {
            return fallback;
        }
    }
}
