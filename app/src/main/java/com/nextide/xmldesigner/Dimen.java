package com.nextide.xmldesigner;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;

public final class Dimen {
    private Dimen() {
    }

    public static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    public static int parseSize(Context context, String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.length() == 0) {
            return fallback;
        }
        if ("match_parent".equals(v) || "fill_parent".equals(v)) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        if ("wrap_content".equals(v)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if (v.endsWith("dp") || v.endsWith("dip")) {
            return dp(context, safeInt(stripUnit(v), 0));
        }
        if (v.endsWith("sp")) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, safeInt(stripUnit(v), 0), context.getResources().getDisplayMetrics());
        }
        if (v.endsWith("px")) {
            return safeInt(stripUnit(v), fallback);
        }
        return safeInt(v, fallback);
    }

    public static float parseFloat(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int safeInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        if ("true".equals(v)) {
            return true;
        }
        if ("false".equals(v)) {
            return false;
        }
        return fallback;
    }

    public static String stripUnit(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        int i = 0;
        while (i < v.length()) {
            char c = v.charAt(i);
            if ((c < '0' || c > '9') && c != '-' && c != '.') {
                break;
            }
            i++;
        }
        return v.substring(0, i);
    }

    public static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.length() == 0 || v.startsWith("@") || v.startsWith("?")) {
            return fallback;
        }
        try {
            if ("black".equalsIgnoreCase(v)) {
                return Color.BLACK;
            }
            if ("white".equalsIgnoreCase(v)) {
                return Color.WHITE;
            }
            if ("red".equalsIgnoreCase(v)) {
                return Color.RED;
            }
            if ("green".equalsIgnoreCase(v)) {
                return Color.GREEN;
            }
            if ("blue".equalsIgnoreCase(v)) {
                return Color.BLUE;
            }
            if ("gray".equalsIgnoreCase(v) || "grey".equalsIgnoreCase(v)) {
                return Color.GRAY;
            }
            if ("transparent".equalsIgnoreCase(v)) {
                return Color.TRANSPARENT;
            }
            return Color.parseColor(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int parseGravity(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String[] parts = value.split("\\|");
        int result = 0;
        int found = 0;
        int i;
        for (i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if ("center".equals(p)) {
                result |= Gravity.CENTER;
                found++;
            } else if ("center_horizontal".equals(p)) {
                result |= Gravity.CENTER_HORIZONTAL;
                found++;
            } else if ("center_vertical".equals(p)) {
                result |= Gravity.CENTER_VERTICAL;
                found++;
            } else if ("left".equals(p) || "start".equals(p)) {
                result |= Gravity.LEFT;
                found++;
            } else if ("right".equals(p) || "end".equals(p)) {
                result |= Gravity.RIGHT;
                found++;
            } else if ("top".equals(p)) {
                result |= Gravity.TOP;
                found++;
            } else if ("bottom".equals(p)) {
                result |= Gravity.BOTTOM;
                found++;
            } else if ("fill".equals(p)) {
                result |= Gravity.FILL;
                found++;
            }
        }
        if (found == 0) {
            return fallback;
        }
        return result;
    }

    public static CharSequence cleanText(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("@string/")) {
            return value.substring("@string/".length()).replace('_', ' ');
        }
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            return value;
        }
        return TextUtils.htmlEncode(value).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    public static String idName(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("@+id/")) {
            return v.substring(5);
        }
        if (v.startsWith("@id/")) {
            return v.substring(4);
        }
        return null;
    }

    public static int stableId(String name) {
        if (name == null) {
            return 0;
        }
        int id = Math.abs(name.hashCode());
        if (id < 1) {
            id = 1;
        }
        return id;
    }
}
