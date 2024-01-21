package com.example.yolov8androidonnx;

import android.graphics.Bitmap;

public class BitmapUtils {
    public static int calculateBitmapWidth(Bitmap bitmap, int viewWidth, int viewHeight) {
        if (bitmap == null || viewWidth <= 0 || viewHeight <= 0) {
            return 0;
        }

        float scale = (float) viewHeight / (float) bitmap.getHeight();
        float scaledWidth = scale * bitmap.getWidth();

        if (scaledWidth > viewWidth) {
            scale = (float) viewWidth / (float) bitmap.getWidth();
        }

        return (int) (scale * bitmap.getWidth());
    }

    public static int calculateBitmapHeight(Bitmap bitmap, int viewWidth, int viewHeight) {
        if (bitmap == null || viewWidth <= 0 || viewHeight <= 0) {
            return 0;
        }

        float scale = (float) viewWidth / (float) bitmap.getWidth();
        float scaledHeight = scale * bitmap.getHeight();

        if (scaledHeight > viewHeight) {
            scale = (float) viewHeight / (float) bitmap.getHeight();
        }

        return (int) (scale * bitmap.getHeight());
    }
}
