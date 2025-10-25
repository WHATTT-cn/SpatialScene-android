// 替代原有的实时相机功能
// 提供图片选择和处理功能
// 在后台线程进行深度估计推理
// 显示原始图片和深度估计结果

package org.tensorflow.lite.examples.classification;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Model;

public class GalleryActivity extends AppCompatActivity {
    private static final Logger LOGGER = new Logger();
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSIONS_REQUEST = 2;

    private ImageView originalImageView;
    private ImageView depthImageView;
    private Button selectImageButton;
    private Button processImageButton;
    private TextView inferenceTimeTextView;
    private TextView statusTextView;

    private Classifier classifier;
    private Bitmap selectedBitmap;
    private int imageSizeX;
    private int imageSizeY;
    private Device currentDevice; // 当前使用的设备类型

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        initializeViews();
        setupClickListeners();
        // 在主线程初始化classifier（GPU delegate必须在主线程初始化）
        initializeClassifier();
        checkPermissions();
    }

    private void initializeViews() {
        originalImageView = findViewById(R.id.original_image_view);
        depthImageView = findViewById(R.id.depth_image_view);
        selectImageButton = findViewById(R.id.select_image_button);
        processImageButton = findViewById(R.id.process_image_button);
        inferenceTimeTextView = findViewById(R.id.inference_time_text_view);
        statusTextView = findViewById(R.id.status_text_view);
    }

    private void setupClickListeners() {
        selectImageButton.setOnClickListener(v -> openImagePicker());
        processImageButton.setOnClickListener(v -> processSelectedImage());
        processImageButton.setEnabled(false); // 初始禁用处理按钮
    }

    private void initializeClassifier() {
        try {
            Model model = Model.FLOAT_EFFICIENTNET; // 使用浮点模型
            int numThreads = 4;
            Device device = Device.GPU; // 优先使用GPU
            String deviceUsed = "GPU";

            // 尝试使用GPU初始化
            try {
                classifier = Classifier.create(this, model, device, numThreads);
                if (classifier == null) {
                    throw new Exception("GPU initialization returned null");
                }
                LOGGER.i("GPU classifier created successfully");
                currentDevice = Device.GPU; // 保存使用的设备
            } catch (Exception gpuException) {
                LOGGER.w(gpuException, "GPU initialization failed, falling back to CPU");
                // GPU失败，回退到CPU
                device = Device.CPU;
                deviceUsed = "CPU";
                classifier = Classifier.create(this, model, device, numThreads);
                currentDevice = Device.CPU; // 保存使用的设备
            }

            if (classifier != null) {
                imageSizeX = classifier.getImageSizeX();
                imageSizeY = classifier.getImageSizeY();
                statusTextView.setText("模型加载成功 (设备: " + deviceUsed + ")");
            } else {
                statusTextView.setText("模型加载失败");
            }
        } catch (Exception e) {
            LOGGER.e(e, "Failed to create classifier");
            statusTextView.setText("模型初始化失败: " + e.getMessage());
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                selectedBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // 显示选中的图片
                originalImageView.setImageBitmap(selectedBitmap);
                processImageButton.setEnabled(true);
                statusTextView.setText("图片选择成功，可以开始处理");

                // 清空之前的深度图
                depthImageView.setImageBitmap(null);

            } catch (IOException e) {
                LOGGER.e(e, "Failed to load image");
                statusTextView.setText("图片加载失败");
            }
        }
    }

    private void processSelectedImage() {
        if (selectedBitmap == null || classifier == null) {
            Toast.makeText(this, "请先选择图片或等待模型加载", Toast.LENGTH_SHORT).show();
            return;
        }

        processImageButton.setEnabled(false);

        // 获取当前使用的设备类型，并显示设备信息
        String deviceInfo;
        if (currentDevice == Device.GPU) {
            deviceInfo = "GPU";
        } else if (currentDevice == Device.CPU) {
            deviceInfo = "CPU"; 
        } else {
            deviceInfo = "未知"; // 如果currentDevice为null，显示"未知"
            LOGGER.w("currentDevice is null, this should not happen");
        }
        final boolean isGPUMode = (currentDevice != null && currentDevice == Device.GPU);
        final String finalDeviceInfo = deviceInfo; // 用于在lambda表达式中使用
        statusTextView.setText("正在处理图片... (使用 " + deviceInfo + " 推理)");
        
        // 根据设备类型选择执行线程
        Runnable inferenceRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final long startTime = SystemClock.uptimeMillis();

                    // 调整图片大小以匹配模型输入
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(selectedBitmap, imageSizeX, imageSizeY, true);

                    // 进行深度估计推理
                    float[] depthArray = classifier.recognizeImage(resizedBitmap, 0);

                    final long processingTime = SystemClock.uptimeMillis() - startTime;

                    // 在主线程更新UI
                    runOnUiThread(() -> {
                        displayDepthResult(depthArray, processingTime, finalDeviceInfo);
                        processImageButton.setEnabled(true);
                    });

                } catch (Exception e) {
                    LOGGER.e(e, "Failed to process image");
                    runOnUiThread(() -> {
                        statusTextView.setText("图片处理失败: " + e.getMessage());
                        processImageButton.setEnabled(true);
                        Toast.makeText(GalleryActivity.this, "推理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        };

        // GPU模式需要在主线程运行，CPU模式可以在后台线程运行
        if (isGPUMode) {
            // GPU delegate 必须在初始化它的线程上运行
            runOnUiThread(inferenceRunnable);
        } else {
            // CPU模式在后台线程运行，不阻塞UI
            new Thread(inferenceRunnable).start();
        }
    }



    private void displayDepthResult(float[] depthArray, long processingTime, String deviceInfo) {
        // 归一化深度值
        float maxval = Float.NEGATIVE_INFINITY;
        float minval = Float.POSITIVE_INFINITY;
        for (float cur : depthArray) {
            maxval = Math.max(maxval, cur);
            minval = Math.min(minval, cur);
        }

        float multiplier = 0;
        if ((maxval - minval) > 0) {
            multiplier = 255 / (maxval - minval);
        }

        // 创建深度图
        Bitmap depthBitmap = Bitmap.createBitmap(imageSizeX, imageSizeY, Bitmap.Config.RGB_565);

        for (int x = 0; x < imageSizeX; x++) {
            for (int y = 0; y < imageSizeY; y++) {
                int index = (imageSizeX - x - 1) + (imageSizeY - y - 1) * imageSizeX;
                if (index < depthArray.length) {
                    float normalizedValue = multiplier * (depthArray[index] - minval);
                    int grayValue = (int) normalizedValue;
                    depthBitmap.setPixel(x, y, Color.rgb(grayValue, grayValue, grayValue));
                }
            }
        }

        // 显示深度图和设备信息
        depthImageView.setImageBitmap(depthBitmap);
        inferenceTimeTextView.setText("推理时间: " + processingTime + "ms (使用 " + deviceInfo + ")");
        statusTextView.setText("深度估计完成 (使用 " + deviceInfo + " 推理)");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusTextView.setText("权限已授予，可以选择图片");
            } else {
                statusTextView.setText("需要存储权限来选择图片");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) {
            classifier.close();
        }
    }
}