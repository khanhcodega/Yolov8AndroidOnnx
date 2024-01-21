package com.example.yolov8androidonnx;


import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class SelectActivity extends AppCompatActivity  {

    private Button btnCamera, btnImg, btnVideo, btnSetting;
    private ImageView imgView;
    private TextureView videoView;
    private Bitmap bitmap;
    private final DataProcess dataProcess = new DataProcess(this);
    private OrtEnvironment ortEnvironment;
    private OrtSession session;
    private RectView rectView;
    public static final int REQUEST_PERMISSION_IMAGE_STORE = 10;
    public static final int REQUEST_PERMISSION_VIDEO_STORE = 11;
    public static final int REQUEST_PERMISSION_CAMERA = 12;
    private int selectedImageWidth;
    private int selectedImageHeight;
    private CardView cardView;
    private MediaController mediaController;
    private Handler videoHandler;
    private boolean isProcessingVideo = false;
    private Uri uriVideo = null;
    private MediaPlayer mediaPlayer;

    private ActivityResultLauncher launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data == null) {
                            return;
                        }
                        Uri uri = data.getData();
                        rectView.clearCanvas();
                        ContentResolver contentResolver = getContentResolver();
                        String type = contentResolver.getType(uri);
                        Log.d("typeContent",type);
                        if (type != null && type.startsWith("image/")){
                            try {
                                setImageView(uri);
                            } catch (OrtException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }else {

                            try {
                                setVideoView(uri);
                            } catch (OrtException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                    }
                }
            }
    );

    private void setImageView(Uri uri) throws OrtException, IOException {
        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        selectedImageWidth = bitmap.getWidth();
        selectedImageHeight = bitmap.getHeight();
        Log.d("RectForm","width : " +selectedImageWidth + "height : " +selectedImageHeight);
//        rectView.clearCanvas();
        videoView.setVisibility(View.INVISIBLE);
        imgView.setVisibility(View.VISIBLE);
        imgView.setImageBitmap(bitmap);
        ProcessImg(bitmap);
    }

    private void setVideoView(Uri uri) throws OrtException, IOException {
        releaseMediaPlayer();
        imgView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.VISIBLE);
        if (videoView.getParent() != null) {
            // If the TextureView has a parent, remove it
            ((ViewGroup) videoView.getParent()).removeView(videoView);
            videoView = new TextureView(getApplicationContext());
        }
        mediaPlayer = new MediaPlayer();

        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {


                try {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            Surface surfaceTexture = new Surface(surface);
                            mediaPlayer.setSurface(surfaceTexture);
                            mediaPlayer.start();
                        }
                    });
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mediaPlayer.seekTo(0);
                            releaseMediaPlayer();
                        }
                    });
                    mediaPlayer.prepareAsync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseMediaPlayer();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                Bitmap bitmap = videoView.getBitmap();
                try {
                    ProcessImg(bitmap);
                } catch (OrtException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        videoView.requestLayout();
        // Add the new TextureView to the layout
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        ((FrameLayout) findViewById(R.id.videoContainer)).addView(videoView, layoutParams);
        videoView.requestLayout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select);
        initUI();
        load();

        videoView.setVisibility(View.INVISIBLE);
        videoHandler = new Handler();

        btnImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAndRequestPermissions("image/*");
            }
        });

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAndRequestPermissions("camera");
            }
        });

        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSettingPermission();
            }
        });
        btnVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAndRequestPermissions("video/*");
            }
        });

    }

    private void ProcessImg(Bitmap bitmap) throws OrtException {
        selectedImageWidth = bitmap.getWidth();
        selectedImageHeight = bitmap.getHeight();
        Log.d("BitmapSize", "width : " +selectedImageWidth + "height : " +selectedImageHeight);

         Bitmap bitmapCustom = Bitmap.createScaledBitmap(bitmap, DataProcess.INPUT_SIZE, DataProcess.INPUT_SIZE, true);
        FloatBuffer floatBuffer = dataProcess.bitmapToFloatBuffer(bitmapCustom);

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

        rectView.transformRect(results ,bitmap);
        rectView.invalidate();
    }
    private void load() {
        // Load the ONNX model and labels
        dataProcess.loadModel();
        dataProcess.loadLabel();

        ortEnvironment = OrtEnvironment.getEnvironment();
        try {
            session = ortEnvironment.createSession(this.getFilesDir().getAbsolutePath() + "/" + DataProcess.FILE_NAME,
                    new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        rectView.setClassLabel(dataProcess.getClasses());
    }
    private void initUI() {
        btnCamera = findViewById(R.id.btnSelectCamera);
        btnImg = findViewById(R.id.btnSelectImg);
        btnVideo = findViewById(R.id.btnSelectVideo);
        imgView = findViewById(R.id.imageView);
        rectView = findViewById(R.id.rectView1);
        btnSetting = findViewById(R.id.btnSetting);
        videoView = findViewById(R.id.videoView);
        cardView = findViewById(R.id.cardView);

    }
    private void checkAndRequestPermissions(String type) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (type.equals("camera")) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
            } else {
                openGallery(type);
            }
            return;
        }

        String[] permissions;
        int requestCode;

        switch (type) {
            case "image/*":
                permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
                requestCode = REQUEST_PERMISSION_IMAGE_STORE;
                break;
            case "video/*":
                permissions = new String[]{Manifest.permission.READ_MEDIA_VIDEO};
                requestCode = REQUEST_PERMISSION_VIDEO_STORE;
                break;
            case "camera":
                permissions = new String[]{Manifest.permission.CAMERA};
                requestCode = REQUEST_PERMISSION_CAMERA;
                break;
            default:
                return;
        }


        if ((checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED) && (type != "camera")) {
            openGallery(type);
        } else if ((checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED) && (type == "camera")) {
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
        } else {
            requestPermissions(permissions, requestCode);
        }


    }
    private void openGallery(String type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type);
        launcher.launch(Intent.createChooser(intent, "choose content"));

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSION_IMAGE_STORE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery("image/*");
                }
                break;

            case REQUEST_PERMISSION_VIDEO_STORE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery("video/*");
                }
                break;
            case REQUEST_PERMISSION_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
                }
                break;
        }

    }
    private void openSettingPermission() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }


}