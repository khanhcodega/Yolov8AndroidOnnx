package com.example.yolov8androidonnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;


import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private RectView rectView;
    private OrtEnvironment ortEnvironment;
    private OrtSession session;
    private final DataProcess dataProcess = new DataProcess(this);
    public static final int PERMISSION = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        rectView = findViewById(R.id.rectView);

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        //  Hide Navigation Bar and Status Bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Request permissions
//        setPermissions();

        // Load ONNX model and labels
        try {
            load();
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        // Start the camera
        setCamera();

    }


    private void setCamera() {
        // Camera provider
        ProcessCameraProvider processCameraProvider = null;
        try {
            processCameraProvider = ProcessCameraProvider.getInstance(this).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Full screen
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        // Back camera
        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        // 16:9 aspect ratio
        Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

        // Display the preview on previewView
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Refresh the screen continuously while analyzing is ongoing. When analysis is done, analyze the latest photo again.
        ImageAnalysis analysis = new ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
            try {
                imageProcess(imageProxy);
            } catch (OrtException e) {
                throw new RuntimeException(e);
            }
            imageProxy.close();
        });

        // Bind the camera's lifecycle to the main activity
        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);
    }

    private void imageProcess(ImageProxy imageProxy) throws OrtException {
        // Convert ImageProxy to Bitmap and then to FloatBuffer
        Bitmap bitmapSize = imageProxy.toBitmap();
        Bitmap bitmap = dataProcess.imageToBitmap(imageProxy);
        FloatBuffer floatBuffer = dataProcess.bitmapToFloatBuffer(bitmap);

        // Session input tensor shape: [1 3 640 640]
        long[] shape = {DataProcess.BATCH_SIZE, DataProcess.PIXEL_SIZE, DataProcess.INPUT_SIZE,
                DataProcess.INPUT_SIZE};
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape);

        // Run the session
        String inputName = session.getInputNames().iterator().next();
        OrtSession.Result resultTensor = session.run(Collections.singletonMap(inputName, inputTensor));

        // Process the outputs and display on the screen
        Object[] outputs = (Object[]) resultTensor.get(0).getValue();
        ArrayList<Result> results = dataProcess.outputsToNPMSPredictions(outputs);

        rectView.transformRect(results ,bitmapSize);
        rectView.invalidate();
    }

    private void load() throws OrtException {
        // Load the ONNX model and labels
        dataProcess.loadModel();
        dataProcess.loadLabel();

        ortEnvironment = OrtEnvironment.getEnvironment();
        session = ortEnvironment.createSession(this.getFilesDir().getAbsolutePath() + "/" + DataProcess.FILE_NAME,
                new OrtSession.SessionOptions());

        rectView.setClassLabel(dataProcess.getClasses());
    }




}
