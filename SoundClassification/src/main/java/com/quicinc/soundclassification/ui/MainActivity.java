package com.quicinc.soundclassification.ui;
import com.quicinc.soundclassification.R;
import com.quicinc.soundclassification.classification.SoundClassification;
import com.quicinc.soundclassification.databinding.MainActivityBinding;
import com.quicinc.soundclassification.service.ClassifierHandler;
import com.quicinc.soundclassification.tflite.AIHubDefaults;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private MainActivityBinding binding;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private boolean prefHwAcc;

    // Inference Elements
    private SoundClassification defaultDelegateClassifier;
    private SoundClassification cpuOnlyClassifier;
    private boolean cpuOnlyClassification = !prefHwAcc;
    NumberFormat timeFormatter = new DecimalFormat("0.00");
    ExecutorService backgroundTaskExecutor = Executors.newSingleThreadExecutor();
    Handler mainLooperHandler = new Handler(Looper.getMainLooper());
    float[] audioData = null;

    /**
     * Instantiate the activity on first load.
     * Creates the UI and a background thread that instantiates the classifier  TFLite model.
     *
     * @param savedInstanceState Saved instance state.
     */

    @SuppressLint({"MissingInflatedId", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = MainActivityBinding.inflate(getLayoutInflater()); // 1
        setContentView(binding.getRoot()); // 2
        setBottomNavigationView();

        // 최초 실행 시 HomeFragment 로드
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, new HomeFragment()) // fragment_container는 프래그먼트가 표시될 FrameLayout ID
                    .commit();
        }

        // Exit the UI thread and instantiate the model in the background.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 4);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void setBottomNavigationView() {
        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.fragment_home) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, new HomeFragment())
                        .commit();
                return true;
            }
            else if(id == R.id.fragment_settings) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, new SettingsFragment())
                        .commit();
                return true;
            }
            else{
                return false;
            }


        });
    }


    /**
     * Destroy this activity and release memory used by held objects.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cpuOnlyClassifier != null) cpuOnlyClassifier.close();
        if (defaultDelegateClassifier != null) defaultDelegateClassifier.close();

    }

    void createTFLiteClassifiersAsync() {
        if (defaultDelegateClassifier != null || cpuOnlyClassifier != null) {
            throw new RuntimeException("Classifiers were already created");
        }

        // Exit the UI thread and instantiate the model in the background.
        backgroundTaskExecutor.execute(() -> {
            // Create two classifiers.
            // One uses the default set of delegates (can access NPU, GPU, CPU), and the other uses only XNNPack (CPU).
            String tfLiteModelAsset = this.getResources().getString(R.string.tfLiteModelAsset);
            String tfLiteLabelsAsset = this.getResources().getString(R.string.tfLiteLabelsAsset);
            try {
                defaultDelegateClassifier = new SoundClassification(
                        this,
                        tfLiteModelAsset,
                        tfLiteLabelsAsset,
                        AIHubDefaults.delegatePriorityOrder /* AI Hub Defaults */
                );
                cpuOnlyClassifier = new SoundClassification(
                        this,
                        tfLiteModelAsset,
                        tfLiteLabelsAsset,
                        AIHubDefaults.delegatePriorityOrderForDelegates(new HashSet<>() /* No delegates; cpu only */)
                );
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e.getMessage());
            }

//            mainLooperHandler.post(() -> setInferenceUIEnabled(true));
        });
    }
}
