package com.example.yolov8androidonnx;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;


import androidx.camera.core.ImageProxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class DataProcess {
    public static final int BATCH_SIZE = 1;
    public static final int INPUT_SIZE = 640;
    public static final int PIXEL_SIZE = 3;
    public static final String FILE_NAME = "best.onnx";
    private static final String LABEL_NAME = "labels.txt";

    private Context context;
    private String[] classes;

    public String[] getClasses() {
        return classes;
    }
    public DataProcess(Context context) {
        this.context = context;
    }

    //load model
    public void loadModel() {

        try {
            File outputFile = new File(context.getFilesDir() + "/" + FILE_NAME);
            //load model onnx from assets
            InputStream inputStream = context.getAssets().open(FILE_NAME);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            // Copy data from input stream to output stream
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
//            Log.d("DataProcess","Input :" + inputStream +"  output :" + outputFlite + String.valueOf(buffer));
//            Log.d("DataProcess", "model file  :" + outputFlite);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    //load label
    public void loadLabel() {

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(context.getAssets().open(LABEL_NAME))
                )
        ) {
            String line;
            ArrayList<String> classList = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                classList.add(line);
            }
            classes = classList.toArray(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Bitmap imageToBitmap(ImageProxy imageProxy) {
        Bitmap bitmap = imageProxy.toBitmap();
        return Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
    }

    public FloatBuffer bitmapToFloatBuffer(Bitmap bitmap) {
        float imageSTD = 255.0f;
        Log.d("BitmapData", String.valueOf(bitmap));

        int cap = BATCH_SIZE * PIXEL_SIZE * INPUT_SIZE * INPUT_SIZE;
        ByteOrder byteOrder = ByteOrder.nativeOrder();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(cap * Float.BYTES).order(byteOrder);
        FloatBuffer buffer = byteBuffer.asFloatBuffer();

        int area = INPUT_SIZE * INPUT_SIZE;
        int[] bitmapData = new int[area];
        bitmap.getPixels(bitmapData,
                0,
                bitmap.getWidth(),
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight());
        Log.d("BitmapData", bitmap.getWidth() + ", " +bitmap.getHeight());

        for (int i = 0; i < INPUT_SIZE - 1; i++) {
            for (int j = 0; j < INPUT_SIZE - 1; j++) {
                int idx = INPUT_SIZE * i + j;
                int pixelValue = bitmapData[idx];

                float red = ((pixelValue >> 16) & 0xff) / imageSTD;
                float green = ((pixelValue >> 8) & 0xff) / imageSTD;
                float blue = (pixelValue & 0xff) / imageSTD;

                buffer.put(idx, red);
                buffer.put(idx + area, green);
                buffer.put(idx + area * 2, blue);
            }
        }
        buffer.rewind();
        return buffer;
    }

    public ArrayList<Result> outputsToNPMSPredictions(Object[] outputs) {
        float confidenceThreshold = 0.5f;
        ArrayList<Result> results = new ArrayList<>();
        int rows, cols;

        Object[] outputArray = (Object[]) outputs[0];

            rows = outputArray.length;
            cols = ((float[]) outputArray[0]).length;


        // Convert the array shape from [6 8400] to [8400 6]
        float[][] output = new float[cols][rows];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                output[j][i] = ((float[]) ((Object[]) outputs[0])[i])[j];
            }
        }

        for (int i = 0; i < cols; i++) {
            int detectionClass = -1;
            float maxScore = 0f;
            float[] classArray = new float[classes.length];

// Extract only the label to a one-dimensional array (0~3 are coordinate values)
            System.arraycopy(output[i], 4, classArray, 0, classes.length);
            // Select the largest value among the labels
            for (int j = 0; j < classes.length; j++) {
                if (classArray[j] > maxScore) {
                    detectionClass = j;
                    maxScore = classArray[j];
                }
            }

// If the largest probability value among the 80 coco datasets exceeds a certain value (currently 45% probability), save that value
            if (maxScore > confidenceThreshold) {
                float xPos = output[i][0];
                float yPos = output[i][1];
                float width = output[i][2];
                float height = output[i][3];

// The rectangle cannot go outside the screen, so if it exceeds the screen, it has the maximum screen value
                RectF rectF = new RectF(
                        Math.max(0f, xPos - width / 2f),
                        Math.max(0f, yPos - height / 2f),
                        Math.min(INPUT_SIZE - 1f, xPos + width / 2f),
                        Math.min(INPUT_SIZE - 1f, yPos + height / 2f)
                );

                Result result = new Result(detectionClass, maxScore, rectF);
                Log.d("Bitmap", String.valueOf(result.getRectF()) +", "
                        +String.valueOf(result.getScore())+", "
                        +String.valueOf(result.getClassIndex()) );

                results.add(result);
            }
        }

        return nms(results);
    }

    private ArrayList<Result> nms(ArrayList<Result> results) {
        ArrayList<Result> list = new ArrayList<>();

        for (int i = 0; i < classes.length; i++) {
            // 1. Find the class (labels) that had the highest probability value among the classes
            PriorityQueue<Result> pq = new PriorityQueue<>(50, Comparator.comparing(o -> o.getScore()));
            ArrayList<Result> classResults = new ArrayList<>();
            for (Result result : results) {
                if (result.getClassIndex() == i) {
                    classResults.add(result);
                }
            }
            pq.addAll(classResults);

            // NMS processing
            while (!pq.isEmpty()) {
                // Save the class with the highest probability value in the queue
                Result[] detections = pq.toArray(new Result[0]);
                Result max = detections[0];
                list.add(max);

                // Remove the maximum probability result from the queue
                pq.clear();

                // Check the intersection ratio and remove if it exceeds 50%
                for (int k = 0; k < detections.length; k++) {
                    Result detection = detections[k]; // Use poll() to remove and get the element
                    RectF rectF = detection.getRectF();
                    float iouThresh = 0.5f;
                    if (boxIOU(max.getRectF(), rectF) < iouThresh) {
                        pq.add(detection);
                    }
                }
            }
        }
        return list;
    }

    // Overlapping ratio (intersection / union)
    private float boxIOU(RectF a, RectF b) {
        return boxIntersection(a, b) / boxUnion(a, b);
    }


    private float boxIntersection(RectF a, RectF b) {
        float w = overlap(
                (a.left + a.right) / 2f, a.right - a.left,
                (b.left + b.right) / 2f, b.right - b.left
        );
        float h = overlap(
                (a.top + a.bottom) / 2f, a.bottom - a.top,
                (b.top + b.bottom) / 2f, b.bottom - b.top
        );
        return (w < 0 || h < 0) ? 0 : w * h;
    }

    private float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
    }

    private float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = Math.max(l1, l2);
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = Math.min(r1, r2);
        return Math.max(0, right - left);
    }
}
