package com.nextide.xmldesigner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Stack;

public class XmlPreviewBuilder {
    public interface OnPreviewNodeClickListener {
        void onPreviewNodeClicked(View view, PreviewNodeInfo info);
    }

    private final Context context;
    private final HashMap<String, Integer> ids;
    private OnPreviewNodeClickListener nodeClickListener;
    private int nodeIndex;

    public XmlPreviewBuilder(Context context) {
        this.context = context;
        this.ids = new HashMap<String, Integer>();
    }

    public void setNodeClickListener(OnPreviewNodeClickListener listener) {
        this.nodeClickListener = listener;
    }

    public View build(String xml) throws Exception {
        ids.clear();
        nodeIndex = 0;
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));

        Stack<ViewGroup> parentStack = new Stack<ViewGroup>();
        Stack<String> groupNameStack = new Stack<String>();
        View root = null;
        int event = parser.getEventType();

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                nodeIndex++;
                View child = createView(name, parser);
                applyCommonAttributes(child, name, parser);
                attachPreviewNode(child, name, nodeIndex);

                if (root == null) {
                    root = child;
                } else if (!parentStack.empty()) {
                    ViewGroup parent = parentStack.peek();
                    ViewGroup.LayoutParams params = createLayoutParams(parent, parser);
                    safeAdd(parent, child, params);
                }

                if (child instanceof ViewGroup) {
                    parentStack.push((ViewGroup) child);
                    groupNameStack.push(name);
                }
            } else if (event == XmlPullParser.END_TAG) {
                String endName = parser.getName();
                if (!groupNameStack.empty() && endName.equals(groupNameStack.peek())) {
                    groupNameStack.pop();
                    parentStack.pop();
                }
            }
            event = parser.next();
        }

        if (root == null) {
            TextView empty = placeholder("No layout root found", "Add XML to render a preview");
            empty.setGravity(Gravity.CENTER);
            empty.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root = empty;
        }
        return root;
    }


    private void attachPreviewNode(final View child, String rawName, int index) {
        if (child == null) {
            return;
        }
        final PreviewNodeInfo info = new PreviewNodeInfo(rawName, cleanName(rawName), index);
        child.setTag(info);
        child.setClickable(true);
        child.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (nodeClickListener != null) {
                    nodeClickListener.onPreviewNodeClicked(child, info);
                }
            }
        });
    }

    private void safeAdd(ViewGroup parent, View child, ViewGroup.LayoutParams params) {
        try {
            if (parent instanceof ScrollView || parent instanceof HorizontalScrollView) {
                if (parent.getChildCount() > 0) {
                    return;
                }
            }
            parent.addView(child, params);
        } catch (Exception e) {
            try {
                parent.addView(child);
            } catch (Exception ignored) {
            }
        }
    }

    private View createView(String rawName, XmlPullParser parser) {
        String name = cleanName(rawName);

        if ("LinearLayout".equals(name)) {
            LinearLayout layout = new LinearLayout(context);
            String orientation = attr(parser, "orientation");
            if ("horizontal".equals(orientation)) {
                layout.setOrientation(LinearLayout.HORIZONTAL);
            } else {
                layout.setOrientation(LinearLayout.VERTICAL);
            }
            layout.setBaselineAligned(false);
            return layout;
        }
        if ("RelativeLayout".equals(name)) {
            return new RelativeLayout(context);
        }
        if ("FrameLayout".equals(name) || "ConstraintLayout".equals(name) || "CardView".equals(name)) {
            FrameLayout frame = new FrameLayout(context);
            if ("ConstraintLayout".equals(name)) {
                frame.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_outline);
            }
            return frame;
        }
        if ("ScrollView".equals(name) || "NestedScrollView".equals(name)) {
            return new ScrollView(context);
        }
        if ("HorizontalScrollView".equals(name)) {
            return new HorizontalScrollView(context);
        }
        if ("TextView".equals(name) || "MaterialTextView".equals(name)) {
            TextView text = new TextView(context);
            text.setText("TextView");
            return text;
        }
        if ("Button".equals(name) || "MaterialButton".equals(name)) {
            Button button = new Button(context);
            button.setText("Button");
            return button;
        }
        if ("EditText".equals(name) || "TextInputEditText".equals(name)) {
            EditText edit = new EditText(context);
            edit.setText("");
            edit.setHint("EditText");
            return edit;
        }
        if ("TextInputLayout".equals(name)) {
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(context);
            label.setText("TextInputLayout");
            label.setTextSize(12);
            label.setTextColor(0xFF607D8B);
            box.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return box;
        }
        if ("ImageView".equals(name)) {
            TextView image = placeholder("ImageView", valueOr(attr(parser, "src"), attr(parser, "srcCompat")));
            image.setGravity(Gravity.CENTER);
            return image;
        }
        if ("CheckBox".equals(name)) {
            return new CheckBox(context);
        }
        if ("RadioButton".equals(name)) {
            return new RadioButton(context);
        }
        if ("RadioGroup".equals(name)) {
            RadioGroup group = new RadioGroup(context);
            String orientation = attr(parser, "orientation");
            if ("horizontal".equals(orientation)) {
                group.setOrientation(RadioGroup.HORIZONTAL);
            } else {
                group.setOrientation(RadioGroup.VERTICAL);
            }
            return group;
        }
        if ("Switch".equals(name)) {
            return new Switch(context);
        }
        if ("ProgressBar".equals(name)) {
            return new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        }
        if ("SeekBar".equals(name)) {
            return new SeekBar(context);
        }
        if ("Spinner".equals(name)) {
            TextView spin = placeholder("Spinner", "dropdown preview");
            spin.setGravity(Gravity.CENTER_VERTICAL);
            return spin;
        }
        if ("ListView".equals(name)) {
            return makeListPlaceholder("ListView");
        }
        if ("GridView".equals(name)) {
            GridView grid = new GridView(context);
            grid.setNumColumns(2);
            grid.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_outline);
            return grid;
        }
        if ("RecyclerView".equals(name)) {
            return makeListPlaceholder("RecyclerView");
        }
        if ("ViewPager2".equals(name)) {
            TextView pager = placeholder("ViewPager2", "AndroidX preview placeholder");
            pager.setGravity(Gravity.CENTER);
            return pager;
        }
        if ("WebView".equals(name)) {
            WebView web = new WebView(context);
            web.loadData("<html><body><h3>WebView preview</h3></body></html>", "text/html", "utf-8");
            return web;
        }
        if ("Space".equals(name)) {
            return new Space(context);
        }
        if ("Toolbar".equals(name) || "MaterialToolbar".equals(name)) {
            TextView bar = new TextView(context);
            bar.setText(valueOr(attr(parser, "title"), "Toolbar"));
            bar.setTextSize(18);
            bar.setTypeface(Typeface.DEFAULT_BOLD);
            bar.setTextColor(Color.WHITE);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Dimen.dp(context, 16), 0, Dimen.dp(context, 16), 0);
            bar.setBackgroundColor(0xFF263238);
            return bar;
        }
        if ("ComposeView".equals(name)) {
            TextView compose = placeholder("ComposeView", "Kotlin/Compose host placeholder");
            compose.setGravity(Gravity.CENTER);
            return compose;
        }
        if ("View".equals(name)) {
            View v = new View(context);
            v.setBackgroundColor(0xFFE0E0E0);
            return v;
        }
        if ("include".equals(name)) {
            TextView include = placeholder("include", valueOr(attr(parser, "layout"), "layout preview placeholder"));
            include.setGravity(Gravity.CENTER);
            return include;
        }
        if ("merge".equals(name)) {
            LinearLayout merge = new LinearLayout(context);
            merge.setOrientation(LinearLayout.VERTICAL);
            return merge;
        }
        return placeholder(rawName, "Custom view preview placeholder");
    }

    private View makeListPlaceholder(String title) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_outline);
        int i;
        for (i = 1; i <= 4; i++) {
            TextView row = new TextView(context);
            row.setText(title + " item " + i);
            row.setPadding(Dimen.dp(context, 12), Dimen.dp(context, 10), Dimen.dp(context, 12), Dimen.dp(context, 10));
            row.setTextColor(0xFF37474F);
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return list;
    }

    private TextView placeholder(String title, String detail) {
        TextView view = new TextView(context);
        if (detail == null || detail.length() == 0) {
            view.setText(title);
        } else {
            view.setText(title + "\n" + detail);
        }
        view.setTextColor(0xFF455A64);
        view.setTextSize(14);
        view.setPadding(Dimen.dp(context, 8), Dimen.dp(context, 8), Dimen.dp(context, 8), Dimen.dp(context, 8));
        view.setBackgroundResource(com.nextide.xmldesigner.R.drawable.preview_outline);
        return view;
    }

    private void applyCommonAttributes(View view, String rawName, XmlPullParser parser) {
        String idName = Dimen.idName(attr(parser, "id"));
        if (idName != null) {
            int id = Dimen.stableId(idName);
            ids.put(idName, Integer.valueOf(id));
            view.setId(id);
        }

        String padding = attr(parser, "padding");
        int left = Dimen.parseSize(context, valueOr(attr(parser, "paddingLeft"), attr(parser, "paddingStart")), 0);
        int top = Dimen.parseSize(context, attr(parser, "paddingTop"), 0);
        int right = Dimen.parseSize(context, valueOr(attr(parser, "paddingRight"), attr(parser, "paddingEnd")), 0);
        int bottom = Dimen.parseSize(context, attr(parser, "paddingBottom"), 0);
        if (padding != null) {
            int all = Dimen.parseSize(context, padding, 0);
            left = all;
            top = all;
            right = all;
            bottom = all;
        }
        if (left != 0 || top != 0 || right != 0 || bottom != 0) {
            view.setPadding(left, top, right, bottom);
        }

        String background = attr(parser, "background");
        if (background != null) {
            view.setBackgroundColor(Dimen.parseColor(background, 0x00000000));
        }

        String visibility = valueOr(attr(parser, "visibility"), attr(parser, "tools:visibility"));
        if ("gone".equals(visibility)) {
            view.setVisibility(View.GONE);
        } else if ("invisible".equals(visibility)) {
            view.setVisibility(View.INVISIBLE);
        } else {
            view.setVisibility(View.VISIBLE);
        }

        if (view instanceof TextView) {
            applyTextAttributes((TextView) view, parser);
        }
        if (view instanceof EditText) {
            applyEditAttributes((EditText) view, parser);
        }
        if (view instanceof CompoundButton) {
            CompoundButton button = (CompoundButton) view;
            button.setChecked(Dimen.parseBoolean(attr(parser, "checked"), button.isChecked()));
        }
        if (view instanceof ProgressBar) {
            ProgressBar bar = (ProgressBar) view;
            bar.setMax(Dimen.safeInt(attr(parser, "max"), 100));
            bar.setProgress(Dimen.safeInt(attr(parser, "progress"), 45));
        }
    }

    private void applyTextAttributes(TextView view, XmlPullParser parser) {
        String text = valueOr(attr(parser, "text"), attr(parser, "tools:text"));
        if (text != null) {
            view.setText(Dimen.cleanText(text));
        }
        String hint = attr(parser, "hint");
        if (hint != null) {
            view.setHint(Dimen.cleanText(hint));
        }
        String color = attr(parser, "textColor");
        if (color != null) {
            view.setTextColor(Dimen.parseColor(color, view.getCurrentTextColor()));
        }
        String size = attr(parser, "textSize");
        if (size != null) {
            float sp = Dimen.parseFloat(Dimen.stripUnit(size), view.getTextSize());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
        String gravity = attr(parser, "gravity");
        if (gravity != null) {
            view.setGravity(Dimen.parseGravity(gravity, view.getGravity()));
        }
        String style = attr(parser, "textStyle");
        if ("bold".equals(style)) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        } else if ("italic".equals(style)) {
            view.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        } else if ("bold|italic".equals(style) || "italic|bold".equals(style)) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD_ITALIC);
        }
        String lines = attr(parser, "maxLines");
        if (lines != null) {
            view.setMaxLines(Dimen.safeInt(lines, Integer.MAX_VALUE));
        }
        String single = attr(parser, "singleLine");
        if (single != null) {
            view.setSingleLine(Dimen.parseBoolean(single, false));
        }
    }

    private void applyEditAttributes(EditText view, XmlPullParser parser) {
        String input = attr(parser, "inputType");
        if (input == null) {
            return;
        }
        if (input.indexOf("number") >= 0) {
            view.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if (input.indexOf("Email") >= 0 || input.indexOf("email") >= 0) {
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        } else if (input.indexOf("password") >= 0) {
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            view.setInputType(InputType.TYPE_CLASS_TEXT);
        }
    }

    private ViewGroup.LayoutParams createLayoutParams(ViewGroup parent, XmlPullParser parser) {
        int width = Dimen.parseSize(context, attr(parser, "layout_width"), ViewGroup.LayoutParams.WRAP_CONTENT);
        int height = Dimen.parseSize(context, attr(parser, "layout_height"), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent instanceof LinearLayout || parent instanceof RadioGroup) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            lp.weight = Dimen.parseFloat(attr(parser, "layout_weight"), 0);
            applyMargins(lp, parser);
            lp.gravity = Dimen.parseGravity(attr(parser, "layout_gravity"), lp.gravity);
            return lp;
        }
        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
            applyMargins(lp, parser);
            applyRelativeRules(lp, parser);
            return lp;
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            applyMargins(lp, parser);
            lp.gravity = Dimen.parseGravity(attr(parser, "layout_gravity"), lp.gravity);
            return lp;
        }
        if (parent instanceof ScrollView || parent instanceof HorizontalScrollView) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            applyMargins(lp, parser);
            return lp;
        }
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(width, height);
        applyMargins(lp, parser);
        return lp;
    }

    private void applyMargins(ViewGroup.MarginLayoutParams lp, XmlPullParser parser) {
        String margin = attr(parser, "layout_margin");
        int left = Dimen.parseSize(context, valueOr(attr(parser, "layout_marginLeft"), attr(parser, "layout_marginStart")), 0);
        int top = Dimen.parseSize(context, attr(parser, "layout_marginTop"), 0);
        int right = Dimen.parseSize(context, valueOr(attr(parser, "layout_marginRight"), attr(parser, "layout_marginEnd")), 0);
        int bottom = Dimen.parseSize(context, attr(parser, "layout_marginBottom"), 0);
        if (margin != null) {
            int all = Dimen.parseSize(context, margin, 0);
            left = all;
            top = all;
            right = all;
            bottom = all;
        }
        lp.setMargins(left, top, right, bottom);
    }

    private void applyRelativeRules(RelativeLayout.LayoutParams lp, XmlPullParser parser) {
        if (Dimen.parseBoolean(attr(parser, "layout_centerInParent"), false)) {
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        }
        if (Dimen.parseBoolean(attr(parser, "layout_centerHorizontal"), false)) {
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        }
        if (Dimen.parseBoolean(attr(parser, "layout_centerVertical"), false)) {
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
        }
        if (Dimen.parseBoolean(attr(parser, "layout_alignParentTop"), false)) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        }
        if (Dimen.parseBoolean(attr(parser, "layout_alignParentBottom"), false)) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        if (Dimen.parseBoolean(valueOr(attr(parser, "layout_alignParentLeft"), attr(parser, "layout_alignParentStart")), false)) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        }
        if (Dimen.parseBoolean(valueOr(attr(parser, "layout_alignParentRight"), attr(parser, "layout_alignParentEnd")), false)) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        addRuleWithId(lp, RelativeLayout.BELOW, attr(parser, "layout_below"));
        addRuleWithId(lp, RelativeLayout.ABOVE, attr(parser, "layout_above"));
        addRuleWithId(lp, RelativeLayout.RIGHT_OF, valueOr(attr(parser, "layout_toRightOf"), attr(parser, "layout_toEndOf")));
        addRuleWithId(lp, RelativeLayout.LEFT_OF, valueOr(attr(parser, "layout_toLeftOf"), attr(parser, "layout_toStartOf")));
    }

    private void addRuleWithId(RelativeLayout.LayoutParams lp, int verb, String ref) {
        String name = Dimen.idName(ref);
        if (name == null) {
            return;
        }
        Integer value = ids.get(name);
        if (value == null) {
            value = Integer.valueOf(Dimen.stableId(name));
            ids.put(name, value);
        }
        lp.addRule(verb, value.intValue());
    }

    private String attr(XmlPullParser parser, String localName) {
        if (parser == null || localName == null) {
            return null;
        }
        String wanted = localName;
        int count = parser.getAttributeCount();
        int i;
        for (i = 0; i < count; i++) {
            String name = parser.getAttributeName(i);
            if (wanted.equals(name)) {
                return parser.getAttributeValue(i);
            }
            if (name != null && name.endsWith(":" + wanted)) {
                return parser.getAttributeValue(i);
            }
            if (wanted.indexOf(':') >= 0 && wanted.equals(name)) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private String cleanName(String rawName) {
        if (rawName == null) {
            return "View";
        }
        int dot = rawName.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < rawName.length()) {
            return rawName.substring(dot + 1);
        }
        return rawName;
    }

    private String valueOr(String first, String second) {
        if (first != null && first.length() > 0) {
            return first;
        }
        return second;
    }
}
