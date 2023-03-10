package com.example.robotgun;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.robotgun.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.camera.CameraSourceConfig;
import com.google.mlkit.vision.camera.CameraXSource;
import com.google.mlkit.vision.camera.DetectionTaskCallback;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private static final String[] CAMERA_PERMISSION = new String[]{android.Manifest.permission.CAMERA};
    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int CAMERA_REQUEST_CODE = 10;

    private float currentAngle;

    private DetectionTaskCallback<List<FaceMesh>> faceMeshCallBack;
    private ImageCapture imageCapture;
    private OutputStream outputStream;
    private ActivityMainBinding viewBinding;
    private ImageLabeler labeler;
    private FaceMeshDetector detector;
    private DisplayMetrics metrics;
    private CameraSelector cameraSelector;
    private Handler timerHandler = new Handler();
    private ImageAnalysis imageAnalysis;
    private boolean shouldRun, shouldRunDistract, shouldRunFace, shouldRunPhone;
    private Preview preview;
    private ImageView imgFace;
    private Bundle REQUEST_ENABLE_BT;
    private BluetoothDevice hc05;
    private BluetoothSocket bluetoothSocket;

    private BluetoothAdapter bluetoothAdapter;

    private float fieldOfViewAngle = 32.61924f * 2;
    private float awayDistance = 0.5f; //meters

    @Nullable

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        shouldRun = false;
        shouldRunDistract = false;


        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());

        permissionStuff();
        if (!allPermissionsGranted()) {
            requestPermission();
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        previewView = findViewById(R.id.preview_view);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                Log.e("Error", "Failed Three!");
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        FaceMeshDetectorOptions.Builder optionsBuilder = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.BOUNDING_BOX_ONLY);

        FaceMeshDetector defaultDetector =
                FaceMeshDetection.getClient(
                        optionsBuilder.build());

        detector = FaceMeshDetection.getClient(optionsBuilder.build());

        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnConnect = findViewById(R.id.btnConnect);

        Thread thread = new Thread(timerRunnable);

        Thread threadTwo = new Thread(distractRunnable);

        Thread threadThree = new Thread(bluetoothStart);

        imgFace = findViewById(R.id.imgFace);

        imgFace.setBackgroundColor(Color.BLACK);

        shouldRunFace = false;
        shouldRunPhone = false;

        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                shouldRunFace = true;
                shouldRunPhone = true;
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                shouldRunFace = false;
                shouldRunPhone = false;
            }
        });

        shouldRun = true;
        shouldRunDistract = true;


        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);



        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        hc05 = bluetoothAdapter.getRemoteDevice("00:21:09:00:57:0C");
        System.out.println(bluetoothAdapter.getBondedDevices());
        System.out.println(hc05.getName());
        bluetoothSocket = null;

        thread.start();
        threadTwo.start();
        threadThree.start();
    }


    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldRun && shouldRunFace) {
                findFace();
                findPhone();
            }
            timerHandler.postDelayed(this, 2000);
        }
    };

    private Runnable distractRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldRunDistract && shouldRunPhone) {

            }
            timerHandler.postDelayed(this, 2000);
        }
    };

    private Runnable bluetoothStart = new Runnable() {
        @Override
        public void run() {
            permissionStuff();
            int counter = 0;
            System.out.println("no");
            do {
                try {
                    bluetoothSocket = hc05.createInsecureRfcommSocketToServiceRecord(uuid);
                    bluetoothSocket.connect();
                    System.out.println("Success!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                counter++;
            } while(!bluetoothSocket.isConnected() && counter < 10);
            System.out.println("Yes");
            try {
                outputStream = bluetoothSocket.getOutputStream();
                outputStream.write("44;".getBytes());
                System.out.println(bluetoothSocket.isConnected() + "!!!");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    private Bitmap toBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        buffer.rewind();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        byte[] clonedBytes = bytes.clone();
        return BitmapFactory.decodeByteArray(clonedBytes, 0, clonedBytes.length);
    }

    protected void findPhone() {
        if (imageCapture != null) {
            if (!shouldRunDistract) return;

            shouldRunDistract = false;

            imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        @ExperimentalGetImage
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            runPhoneClassification(toBitmap(image));
                            image.close();
                            shouldRunDistract = true;
                        }

                        public void onError(@NonNull ImageCaptureException e) {
                            e.printStackTrace();
                            System.out.println(e.getImageCaptureError());
                            Log.e("Error", "Failed Four!");
                        }
                    }
            );


        }
    }

    protected void runPhoneClassification(Bitmap bitmap) {
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        labeler.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                    @Override
                    public void onSuccess(List<ImageLabel> labels) {
                        for (ImageLabel label : labels) {
                            if (label.getIndex() == 425) {
                                Log.d("Detect", "Phone found");
                                String x = currentAngle + ";";
                                try {
                                    outputStream.write(x.getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Task failed with an exception
                        // ...
                        Log.d("Detect", "Not found Phone");
                        e.printStackTrace();
                    }
                });
    }

    protected void findFace() {
        if (imageCapture != null) {
            if (!shouldRun) return;
            shouldRun = false;

            imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        @ExperimentalGetImage
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            runClassification(toBitmap(image));
                            image.close();
                            shouldRun = true;
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            e.printStackTrace();
                            System.out.println(e.getImageCaptureError());
                            Log.e("Error", "Failed One!");
                        }
                    }
            );


        }

    }

    protected void runClassification(Bitmap bitmap) {

        InputImage inputImage = InputImage.fromBitmap(bitmap, 90);
        detector.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<List<FaceMesh>>() {
                    @Override
                    public void onSuccess(List<FaceMesh> faces) {
                        if (!faces.isEmpty()) {
                            float x = 0;
                            float y = 0;
                            for (FaceMesh face : faces) {
                                x += face.getBoundingBox().centerX();
                                y += face.getBoundingBox().centerY();
                            }


                            x = x * metrics.widthPixels / inputImage.getWidth();
                            y = y * metrics.heightPixels / inputImage.getHeight();

                            imgFace.setX((int) x - imgFace.getWidth() / 2);
                            imgFace.setY((int) y - imgFace.getHeight() / 2);

                            currentAngle = translatePositionToAngle((int) x - imgFace.getWidth() / 2, metrics.widthPixels);

                            String ga = (int)(-1*currentAngle) + ";";
                            try {
                                outputStream.write(ga.getBytes());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            Log.i("Coords", x + ", " + y);

                            System.out.println("Angle: " + ga);
                        } else {
                            System.out.println("Error: Could not detect");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        e.printStackTrace();
                        Log.e("Error", "Failed Two!");
                    }
                });
    }


    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                CAMERA_PERMISSION,
                CAMERA_REQUEST_CODE
        );
    }


    boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        cameraProvider.unbind(preview);

        preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();


        preview.setSurfaceProvider(previewView.getSurfaceProvider());


        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

    }

    private float translatePositionToAngle(float posX, float imageWidth) {

        //Gets fraction of horizontal view
        float xFrac = posX / imageWidth - 0.5f;
        float angle = (float) Math.atan(xFrac * 0.64f / 0.5f);
        return (float) (angle * 180 / Math.PI);
    }

    private void permissionStuff() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 666);
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH}, 666);
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 666);
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 666);
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, 666);
            }
        }
    }



}

