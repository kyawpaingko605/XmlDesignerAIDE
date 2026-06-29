package com.nextide.xmldesigner;

public class PreviewNodeInfo {
    private final String rawName;
    private final String displayName;
    private final int index;

    public PreviewNodeInfo(String rawName, String displayName, int index) {
        this.rawName = rawName;
        this.displayName = displayName;
        this.index = index;
    }

    public String getRawName() {
        return rawName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getIndex() {
        return index;
    }
}
