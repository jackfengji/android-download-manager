package cn.kukool.downloader.util;

import android.content.ContentValues;

/**
 * User: jackfengji
 * Date: 12-8-22
 */
public class ContentValuesFactory {
    private ContentValues mValues = new ContentValues();

    public ContentValuesFactory put(java.lang.String key, java.lang.String value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory putAll(android.content.ContentValues other) {
        mValues.putAll(other);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Byte value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Short value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Integer value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Long value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Float value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Double value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, java.lang.Boolean value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory put(java.lang.String key, byte[] value) {
        mValues.put(key, value);
        return this;
    }

    public ContentValuesFactory putNull(java.lang.String key) {
        mValues.putNull(key);
        return this;
    }

    public ContentValues getValues() {
        return mValues;
    }
}
