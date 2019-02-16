package com.fta.testcontacts.utils;


import android.support.annotation.Nullable;

public class Objects {
    public static boolean equal(@Nullable Object a, @Nullable Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
