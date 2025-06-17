package com.quicinc.soundclassification.service;

import android.content.Context;

import com.quicinc.soundclassification.classification.SoundClassification;
import com.quicinc.soundclassification.tflite.AIHubDefaults;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.quicinc.soundclassification.R;

public class ClassifierHandler {
    private static SoundClassification defaultDelegateClassifier = null;
    private static SoundClassification cpuOnlyClassifier = null;
    private static boolean initialized = false;

    public interface OnClassifierReadyCallback {
        void onReady();
        void onError(Exception e);
    }

    public static void createAsync(Context context, OnClassifierReadyCallback callback) {
        if (initialized) {
            callback.onReady();
            return;
        }

        ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
        backgroundExecutor.execute(() -> {
            try {
                String modelAsset = context.getString(R.string.tfLiteModelAsset);
                String labelAsset = context.getString(R.string.tfLiteLabelsAsset);

                defaultDelegateClassifier = new SoundClassification(
                        context,
                        modelAsset,
                        labelAsset,
                        AIHubDefaults.delegatePriorityOrder
                );

                cpuOnlyClassifier = new SoundClassification(
                        context,
                        modelAsset,
                        labelAsset,
                        AIHubDefaults.delegatePriorityOrderForDelegates(new HashSet<>())
                );

                initialized = true;
                callback.onReady();

            } catch (IOException | NoSuchAlgorithmException e) {
                callback.onError(e);
            }
        });
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SoundClassification getDefaultClassifier() {
        return defaultDelegateClassifier;
    }

    public static SoundClassification getCpuOnlyClassifier() {
        return cpuOnlyClassifier;
    }
}
