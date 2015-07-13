package com.android.mms.data;

public enum AttachmentType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    SLIDESHOW,
    VCARD,
    VCAL;

    public boolean isMms() {
        return this.ordinal() > TEXT.ordinal();
    }
}
