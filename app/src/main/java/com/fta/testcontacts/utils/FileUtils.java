package com.fta.testcontacts.utils;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FileUtils {

    public static boolean sdcardExist() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }


    /**
     * 外置SD卡是否挂载
     * @param context
     * @return
     */
    public static boolean isSDMounted(Context context) {
        boolean isMounted = false;
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

        try {
            Method getVolumList = StorageManager.class.getMethod("getVolumeList", null);
            getVolumList.setAccessible(true);
            Object[] results = (Object[]) getVolumList.invoke(sm, null);
            if (results != null) {
                for (Object result : results) {
                    Method mRemoveable = result.getClass().getMethod("isRemovable", null);
                    Boolean isRemovable = (Boolean) mRemoveable.invoke(result, null);
                    if (isRemovable) {
                        Method getPath = result.getClass().getMethod("getPath", null);
                        String path = (String) mRemoveable.invoke(result, null);
                        Method getState = sm.getClass().getMethod("getVolumeState", String.class);
                        String state = (String) getState.invoke(sm, path);
                        if (state.equals(Environment.MEDIA_MOUNTED)) {
                            isMounted = true;
                            break;
                        }
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return isMounted;

    }
}
