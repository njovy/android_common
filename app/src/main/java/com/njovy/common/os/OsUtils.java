package com.njovy.common.os;

import android.os.Build;
import android.os.Looper;

/**
 * Created by CaptainPark on 5/24/15.
 */
public class OsUtils {
  public static boolean isHigherOrEqual(int sdk) {
    return Build.VERSION.SDK_INT >= sdk;
  }

  public static boolean isMain() {
    return Looper.myLooper() == Looper.getMainLooper();
  }
}
