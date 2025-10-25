// 提供图片加载和处理工具方法
// 处理图片旋转和大小调整

package org.tensorflow.lite.examples.classification;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    /**
     * 从Uri加载图片
     */
    public static Bitmap loadBitmapFromUri(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        inputStream.close();
        return bitmap;
    }

    /**
     * 调整图片大小
     */
    public static Bitmap resizeBitmap(Bitmap original, int targetWidth, int targetHeight) {
        return Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
    }

    /**
     * 旋转图片
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees != 0 && bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.setRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    /**
     * 获取图片的EXIF方向信息
     */
    public static int getImageOrientation(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            // 这里可以添加EXIF读取逻辑
            inputStream.close();
            return 0; // 默认不旋转
        } catch (IOException e) {
            return 0;
        }
    }
}