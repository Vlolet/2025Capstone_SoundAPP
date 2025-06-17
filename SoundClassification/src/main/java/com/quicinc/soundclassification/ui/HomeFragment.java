package com.quicinc.soundclassification.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.quicinc.soundclassification.R;
import com.quicinc.soundclassification.service.SoundClassificationService;

import java.util.Arrays;

public class HomeFragment extends Fragment {
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private boolean prefHwAcc;

    private TextView resultText;
    private BroadcastReceiver resultReceiver;

    // UI Elements
    TextView prefHwView;
    private ToggleButton recordToggleButton;

    public HomeFragment() {
        // Required empty public constructor
    }

    // 뷰 생성
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    // 뷰 생성 후 호출 (뷰 접근 가능 상태)
    @SuppressLint("WrongViewCast")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainActivity activity = (MainActivity) requireActivity();

        resultText = view.findViewById(R.id.rsttext);

        // 설정 값 가져오기 (하드웨어 가속 옵션)
        prefHwView = view.findViewById(R.id.hardwareAccText);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        prefHwAcc = sharedPreferences.getBoolean("use_gpu", false);
        prefHwView.setText(prefHwAcc ? "하드웨어 가속중" : "CPU 사용중");

        prefListener = (prefs, key) -> {
            if ("use_gpu".equals(key)) {
                boolean hardwareAcc = prefs.getBoolean(key, false);
                if (hardwareAcc != prefHwAcc) {
                    prefHwAcc = hardwareAcc;
//                    clearPredictionResults();

                    if (prefHwView != null) {
                        prefHwView.setText(prefHwAcc ? "하드웨어 가속중" : "CPU 사용중");
                    }
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener);

        // 처음 진입 시에도 한 번 적용
        prefListener.onSharedPreferenceChanged(sharedPreferences, "use_gpu");


        // 토글버튼 이벤트리스너
        recordToggleButton = view.findViewById(R.id.toggleButton);
        recordToggleButton.setOnCheckedChangeListener(((buttonView, isChecked) -> {
            Context context = requireContext();
            Intent serviceIntent = new Intent(context, SoundClassificationService.class);

            if (isChecked) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.stopService(serviceIntent);
            }
        }));

        registerReceiver();

    }
    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(resultReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    private void registerReceiver() {
        resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String label = intent.getStringExtra("label");
                float score = intent.getFloatExtra("score", 0f);
                String rst = "label: " + label + " / score: " + score;
                Log.e("homeFrag", rst);

                if (label != null && score > 0.03f) {
                    resultText.setText("소리 감지됨: " + label);
                } else {
                    resultText.setText("감지된 소리 없음");
                }
            }
        };

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            IntentFilter filter = new IntentFilter("AUDIO_CLASSIFICATION_RESULT");

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                requireContext().registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
//            } else {
//                requireContext().registerReceiver(resultReceiver, filter);
//            }
            requireContext().registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(resultReceiver, filter);
    }

    long classficationInterval = 500;       // 0.5 sec (샘플링 주기)
//    static final float MINIMUM_DISPLAY_THRESHOLD = 0.03F;
//    static final String MODEL_FILE = "yamnet.tflite";

//    private void startAudioClassification() {
//        try {
//            AudioClassifier classifier = AudioClassifier.createFromFile(requireContext(), MODEL_FILE);
//            audioClassifier = classifier;
//
//            TensorAudio audioTensor = classifier.createInputTensorAudio();
//
//            AudioRecord record = classifier.createAudioRecord();
//            audioRecord = record;
//            record.startRecording();
//
//            Runnable run = new Runnable() {
//                @Override
//                public void run() {
//                    audioTensor.load(record);
//                    List<Classifications> output = classifier.classify(audioTensor);
//                    List<Category> filterModelOutput = output.get(0).getCategories();
////                    for (Category c : filterModelOutput) {
////                        if (c.getScore() > MINIMUM_DISPLAY_THRESHOLD)
////                            Log.d("tensorAudio_java", " label : " + c.getLabel() + " score : " + c.getScore());
////                    }
////
////                    audioHandler.postDelayed(this, classficationInterval);
//                    // 점수가 높은 항목만 모아서 문자열 만들기
//                    StringBuilder resultBuilder = new StringBuilder();
//                    for (Category c : filterModelOutput) {
//                        if (c.getScore() > MINIMUM_DISPLAY_THRESHOLD) {
//                            resultBuilder.append(c.getLabel())
//                                    .append(String.format(" (%.2f)", c.getScore()))
//                                    .append("\n");
//                        }
//                    }
//
//                    // UI 업데이트는 반드시 메인 쓰레드에서
//                    String resultText = resultBuilder.length() > 0
//                            ? "감지된 소리:\n" + resultBuilder.toString()
//                            : "감지된 위험 소리: 없음";
//
//                    requireActivity().runOnUiThread(() -> {
//                        TextView rstText = requireView().findViewById(R.id.rsttext);
//                        rstText.setText(resultText);
//                    });
//
//                    // 다음 분류 예약
//                    audioHandler.postDelayed(this, classficationInterval);
//                }
//            };
//
//            audioHandler.post(run);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}