package com.quicinc.soundclassification.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.quicinc.soundclassification.R;
import com.quicinc.soundclassification.backups.AudioProcessing;
import com.quicinc.soundclassification.classification.SoundClassification;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SoundClassificationService extends Service {

    private Handler handler;
    private AudioClassifier audioClassifier;
    private TensorAudio audioTensor;
    private AudioRecord audioRecord;

    ExecutorService backgroundTaskExecutor;

    long classficationInterval = 500;       // 0.5 sec (샘플링 주기)
    static final float MINIMUM_DISPLAY_THRESHOLD = 0.03F;


    private final String CHANNEL_ID = "AudioClassificationChannel";

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        backgroundTaskExecutor = Executors.newSingleThreadExecutor();

        HandlerThread thread = new HandlerThread("AudioThread");
        thread.start();
        handler = new Handler(thread.getLooper());

        startForegroundServiceWithNotification();
        startClassificationLoop();
//        updatePredictionDataAsync();
    }

    private void startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Audio Classification", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("소리 감지 서비스 실행 중")
                .setSmallIcon(R.drawable.ic_notification)
                .build();

        startForeground(1, notification);
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void startClassificationLoop() {
        SoundClassification soundClassification;

        if (ClassifierHandler.isInitialized()) {
            soundClassification = ClassifierHandler.getDefaultClassifier();
            // 예시
        } else {
            Log.e("Home", "Classifier 아직 초기화되지 않음");
            soundClassification = ClassifierHandler.getCpuOnlyClassifier();
            // 또는 대기 로직 추가 가능
        }

        try {

            AudioClassifier classifier = AudioClassifier.createFromFile(this, "yamnet.tflite");
            audioClassifier = classifier;

            audioTensor = classifier.createInputTensorAudio();

            AudioRecord record = classifier.createAudioRecord();
            audioRecord = record;
            record.startRecording();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    audioTensor.load(audioRecord);

                    double db = calculateDecibel(audioRecord);
                    //                String result = soundClassification.runSlidingWindowInference(audioTensor).stream()
                    //                        .collect(Collectors.joining(", "));
                    List<Classifications> result = audioClassifier.classify(audioTensor);
                    List<Category> categories = result.get(0).getCategories();
                    Log.e("db", Double.toString(db));

                    boolean isLoud = db > 10;  // 데시벨 기준
                    boolean isSpecificLabelHigh = false;

                    for (Category c : categories) {
                        if (c.getScore() > MINIMUM_DISPLAY_THRESHOLD) {
                            String label = c.getLabel();
                            float score = c.getScore();

                            // 조건 2: 특정 레이블 예시
//                            if (label.equals("Speech")){
                            if ((label.equals("Siren") || label.contains("siren") || label.contains("horn")) && score > 0.3) {
                                isSpecificLabelHigh = true;
                                if(label.equals("Siren") || label.contains("siren")){
                                    label = "siren";
                                }
                                else {
                                    label = "horn";
                                }

                                Intent intent = new Intent("AUDIO_CLASSIFICATION_RESULT");
                                intent.putExtra("label", label);
                                intent.putExtra("score", score);
                                Log.e("service", "label: " + label + " / score: " + score);
                                LocalBroadcastManager.getInstance(SoundClassificationService.this).sendBroadcast(intent);
                                break; // 원하는 경우 하나만
                            }
                        }

                    }

                    if (isLoud || isSpecificLabelHigh) {
                        vibratePhone();
                        turnOnFlash();
                    }
                    handler.postDelayed(this, classficationInterval); // 1초마다 실행
                }
            });
        } catch (IOException e){
            e.printStackTrace();
        }
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("Service", "onDestroy called");
        turnOffFlash();
        stopForeground(true); // 알림 제거
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 바인딩 사용 안 함
    }

    private void vibratePhone() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(500);
        }
    }

    private void turnOnFlash() {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = cameraManager.getCameraIdList()[0]; // 보통 후면 카메라
            cameraManager.setTorchMode(cameraId, true); // true: 켜기, false: 끄기
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void turnOffFlash() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // 후면 카메라 ID
            cameraManager.setTorchMode(cameraId, false); // 플래시 OFF
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private double calculateDecibel(AudioRecord audioRecord) {
        int bufferSize = 1024;
        short[] buffer = new short[bufferSize];
        int read = audioRecord.read(buffer, 0, bufferSize);

        double sum = 0;
        for (int i = 0; i < read; i++) {
            sum += buffer[i] * buffer[i];
        }

        if (read > 0) {
            double rms = Math.sqrt(sum / read);
            if (rms > 0) {
                return 20 * Math.log10(rms);
            }
        }
        return 0;
    }



    /**
     * Run the classifier on the currently selected image.
     * Prediction will run asynchronously to the main UI thread.
     * Disables inference UI before inference and re-enables it afterwards.
     */
//    public void updatePredictionDataAsync() {
////        if (recordedAudioFile == null) {
////            Toast.makeText(this, "No audio recorded!", Toast.LENGTH_SHORT).show();
////            return;
////        }
//
//        // Exit the main UI thread and execute the model in the background.
//        backgroundTaskExecutor.execute(() -> {
//            // Background task
////            String result = soundClassification.predictClassesFromImage(selectedImage).stream().collect(Collectors.joining(", "));
//            float[] audioData = AudioProcessing.loadAudioFromFile(recordedAudioFile);
//
//            String result = soundClassification.runSlidingWindowInference(audioData).stream()
//                    .collect(Collectors.joining(", "));
////            String result = soundClassification.runSlidingWindowInference(audioData);
//
//            long inferenceTime = soundClassification.getLastInferenceTime();
//            long predictionTime = soundClassification.getLastPostprocessingTime() + inferenceTime + soundClassification.getLastPreprocessingTime();
////            String inferenceTimeText = timeFormatter.format((double) inferenceTime / 1000000);
////            String predictionTimeText = timeFormatter.format((double) predictionTime / 1000000);
//
//            mainLooperHandler.post(() -> {
//                // In main UI thread
////                predictedClassesView.setText(result);
////                setInferenceUIEnabled(true);
//            });
//        });
//    }
}