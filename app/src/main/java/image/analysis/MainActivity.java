package image.analysis;

import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.media.Image;
        import android.os.Bundle;
        import android.os.Environment;
        import android.util.Log;
        import android.util.Size;
        import android.view.View;
        import android.widget.TextView;

        import androidx.annotation.NonNull;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.camera.camera2.Camera2Config;
        import androidx.camera.core.Camera;
        import androidx.camera.core.CameraSelector;
        import androidx.camera.core.CameraXConfig;
        import androidx.camera.core.ImageAnalysis;
        import androidx.camera.core.ImageCapture;
        import androidx.camera.core.ImageCaptureException;
        import androidx.camera.core.ImageProxy;
        import androidx.camera.core.Preview;
        import androidx.camera.lifecycle.ProcessCameraProvider;
        import androidx.camera.view.PreviewView;
        import androidx.core.content.ContextCompat;
        import androidx.lifecycle.LifecycleOwner;

        import com.google.android.gms.tasks.OnFailureListener;
        import com.google.android.gms.tasks.OnSuccessListener;
        import com.google.common.util.concurrent.ListenableFuture;
        import com.google.mlkit.common.model.LocalModel;
        import com.google.mlkit.vision.common.InputImage;
        import com.google.mlkit.vision.label.ImageLabel;
        import com.google.mlkit.vision.label.ImageLabeler;
        import com.google.mlkit.vision.label.ImageLabeling;
        import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;

        import org.tensorflow.lite.support.image.TensorImage;
        import org.tensorflow.lite.support.label.Category;

        import java.io.File;
        import java.util.List;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.Executor;
        import java.util.concurrent.Executors;

        import image.analysis.R;
        import image.analysis.ml.Model;

public class MainActivity extends AppCompatActivity implements CameraXConfig.Provider {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private TextView tv_labels;

    public void onClick_Finish(View arg0) {
        finish();
    }

    public void onClick_Capture(View arg0) {
        String file_path = getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/pic.jpg";
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(new File(file_path)).build();
        Executor cameraExecutor = Executors.newSingleThreadExecutor();
        imageCapture.takePicture(outputFileOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        // insert your code here.
                        Log.i("SAVED", file_path);
                    }

                    @Override
                    public void onError(ImageCaptureException error) {
                        // insert your code here.
                        Log.i("ERROR", file_path);
                    }
                }
        );
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        // Preview
        Preview preview = new Preview.Builder()
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        LocalModel localModel =
                new LocalModel.Builder()
                        .setAssetFilePath("model.tflite")
                        // or .setAbsoluteFilePath(absolute file path to model file)
                        // or .setUri(URI to model file)
                        .build();

        // Analysis
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        //.setTargetResolution(new Size(32,32))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)   // Obligatoire: This guarantees only one image will be delivered for analysis at a time. If more images are produced when the analyzer is busy, they will be dropped automatically and not queued for delivery. Once the image being analyzed is closed by calling ImageProxy.close(), the next latest image will be delivered.
                        .build();
        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                // insert your code here.
                Log.i("ROTATION", "" + rotationDegrees);

                Image mediaImage = image.getImage();
                if (mediaImage != null) {
                    InputImage imageToProcess = InputImage.fromMediaImage(mediaImage, rotationDegrees);
                    // Pass image to an ML Kit Vision API
                    CustomImageLabelerOptions customImageLabelerOptions =
                            new CustomImageLabelerOptions.Builder(localModel)
                                    .setConfidenceThreshold(0.5f)
                                    .setMaxResultCount(5)
                                    .build();

                    //org.tensorflow.lite.support.image.TensorImage imageToProcess2 = TensorImage.fromBitmap(toBitmap(image))

                    ImageLabeler labeler = ImageLabeling.getClient(customImageLabelerOptions);
                    labeler.process(imageToProcess)
                            .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                                @Override
                                public void onSuccess(List<ImageLabel> labels) {
                                    // Task completed successfully
                                    String s = "";
                                    for (ImageLabel label : labels) {
                                        String text = label.getText();
                                        float confidence = label.getConfidence();
                                        int index = label.getIndex();
                                        s += text + " (" + confidence + ")\n";
                                    }
                                    tv_labels.setText(s);

                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.i("IMAGE LABELING", e.getMessage());
                                    //e.printStackTrace();
                                }
                            })
                            .addOnCompleteListener(results -> {
                                image.close();
                            });
                }
            }
        });

        // Image capture  Surface.ROTATION_0
        imageCapture =
                new ImageCapture.Builder()
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .build();

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageCapture, preview, imageAnalysis);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = (PreviewView) findViewById(R.id.previewView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        tv_labels = (TextView) findViewById(R.id.digit);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
    }
}

