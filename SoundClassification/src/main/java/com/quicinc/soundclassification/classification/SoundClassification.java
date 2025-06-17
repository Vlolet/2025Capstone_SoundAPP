// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.soundclassification.classification;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.quicinc.soundclassification.tflite.AIHubDefaults;
import com.quicinc.soundclassification.tflite.TFLiteHelpers;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public class SoundClassification implements AutoCloseable {
    private static final String TAG = "SoundClassification";
    private final Interpreter tfLiteInterpreter;
    private final Map<TFLiteHelpers.DelegateType, Delegate> tfLiteDelegateStore;
    private final List<String> labelList;
    private final int[] inputShape;
    private final DataType inputType;
    private final DataType outputType;
    private long preprocessingTime;
    private long postprocessingTime;
    private static final int TOPK = 3;
//    private final SoundProcessor soundProcessor;
    // ImageProcessor라는 tflite의 모듈이 있는거라서 sound는 따로 없음


    /**
     * Create an Image Classifier from the given model.
     * Ignores compute units that fail to load.
     *
     * @param context     App context.
     * @param modelPath   Model path to load.
     * @param labelsPath  Labels path to load.
     * @param delegatePriorityOrder Priority order of delegate sets to enable.
     * @throws IOException If the model can't be read from disk.
     */
    public SoundClassification(Context context,
                               String modelPath,
                               String labelsPath,
                               TFLiteHelpers.DelegateType[][] delegatePriorityOrder) throws IOException, NoSuchAlgorithmException {
        // Load labels
        try (BufferedReader labelsFile = new BufferedReader(new InputStreamReader(context.getAssets().open(labelsPath)))) {
            labelList = labelsFile.lines().collect(Collectors.toCollection(ArrayList::new));
        }

        // Load TF Lite model
        Pair<MappedByteBuffer, String> modelAndHash = TFLiteHelpers.loadModelFile(context.getAssets(), modelPath);
        Pair<Interpreter, Map<TFLiteHelpers.DelegateType, Delegate>> iResult = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
            modelAndHash.first,
            delegatePriorityOrder,
            AIHubDefaults.numCPUThreads,
            context.getApplicationInfo().nativeLibraryDir,
            context.getCacheDir().getAbsolutePath(),
            modelAndHash.second
        );
        tfLiteInterpreter = iResult.first;
        tfLiteDelegateStore = iResult.second;

//        MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, modelPath);
//        tfLiteInterpreter = new Interpreter(modelBuffer);


        // Validate TF Lite model fits requirements for this app
//        assert tfLiteInterpreter.getInputTensorCount() == 1;
//        Tensor inputTensor = tfLiteInterpreter.getInputTensor(0);
//        inputShape = inputTensor.shape();
//        inputType = inputTensor.dataType();
//        assert inputShape.length == 4; // 4D Input Tensor: [Batch, Height, Width, Channels]
//        assert inputShape[0] == 1; // Batch size is 1
//        assert inputShape[3] == 3; // Input tensor should have 3 channels
//        assert inputType == DataType.UINT8 || inputType == DataType.FLOAT32; // INT8 (Quantized) and FP32 Input Supported
//
//        assert tfLiteInterpreter.getOutputTensorCount() == 1;
//        Tensor outputTensor = tfLiteInterpreter.getOutputTensor(0);
//        int[] outputShape = outputTensor.shape();
//        outputType = outputTensor.dataType();
//        assert outputShape.length == 2; // 2D Output Tensor: [Batch, # of Labels]
//        assert inputShape[0] == 1; // Batch size is 1
//        assert outputShape[1] == labelList.size(); // # of labels == output dim
//        assert outputType == DataType.UINT8 || outputType == DataType.INT8 | outputType == DataType.FLOAT32; // U/INT8 (Quantized) and FP32 Output Supported

        Tensor inputTensor = tfLiteInterpreter.getInputTensor(0);
        inputShape = inputTensor.shape(); // Expecting [Batch, Samples]
        inputType = inputTensor.dataType();
//        assert inputShape.length == 2; // 2D Input Tensor: [Batch, Samples]
//        assert inputType == DataType.FLOAT32; // YAMNet expects float32 input

        Tensor outputTensor = tfLiteInterpreter.getOutputTensor(0);
        outputType = outputTensor.dataType();
        assert outputType == DataType.FLOAT32; // YAMNet outputs float32 probabilities


//
//    // Set-up preprocessor
//        imageProcessor = new ImageProcessor.Builder().add(new NormalizeOp(0.0f, 255.0f)).build();
    }

    /**
     * Free resources used by the classifier.
     */
    @Override
    public void close() {
        tfLiteInterpreter.close();

        // YAMNet은 기본적으로 GPU가속 대신 CPU 기반으로 동작함
        for (Delegate delegate: tfLiteDelegateStore.values()) {
            delegate.close();
        }
    }

    /**
     * @return last preprocessing time in microseconds.
     */
    public long getLastPreprocessingTime() {
//        if (preprocessingTime == 0) {
//            throw new RuntimeException("Cannot get preprocessing time as model has not yet been executed.");
//        }
        return preprocessingTime;
    }

    /**
     * @return last inference time in microseconds.
     */
    public long getLastInferenceTime() {
        return tfLiteInterpreter.getLastNativeInferenceDurationNanoseconds();
    }

    /**
     * @return last postprocessing time in microseconds.
     */
    public long getLastPostprocessingTime() {
//        if (postprocessingTime == 0) {
//            throw new RuntimeException("Cannot get postprocessing time as model has not yet been executed.");
//        }
        return postprocessingTime;
    }



    private float[] preprocess(float[] audioData) {
        long prepStartTime = System.nanoTime();

        // Normalize audio data to range [-1.0, 1.0]
//        float maxAmplitude = Arrays.stream(audioData).max().orElse(1.0f);
        float maxAmplitude = 1.0f;
        for (float val : audioData) {
            if (val > maxAmplitude) maxAmplitude = val;
        }

        for (int i = 0; i < audioData.length; i++) {
            audioData[i] /= maxAmplitude;
        }

        preprocessingTime = System.nanoTime() - prepStartTime;
        Log.d(TAG, "Preprocessing Time: " + preprocessingTime / 1000000 + " ms");

        return audioData;
    }



    /**
     * Reads the output buffers on tfLiteModel and processes them into readable output classes.
     *
     * @return Predicted object class names, in order of confidence (highest confidence first).
     */

    private List<String> postprocess(float[] outputScores) {
        long postStartTime = System.nanoTime();

        // Find top K indices
        List<Integer> topKIndices = findTopKIndices(outputScores, TOPK);
        List<String> topLabels = topKIndices.stream().map(labelList::get).collect(Collectors.toList());

        postprocessingTime = System.nanoTime() - postStartTime;
        Log.d(TAG, "Postprocessing Time: " + postprocessingTime / 1000000 + " ms");

        return topLabels;
    }


    /**
     * Predict the most likely classes of the object in the image.
     *
//     * @param image RGBA-8888 bitmap image to predict class of.
     * @return Predicted object class names, in order of confidence (highest confidence first).
     */
    public List<String> predictClassesFromAudio(float[] audioData) {
        // Preprocessing
        float[] processedAudio = preprocess(audioData);

        // Inference
        float[][] outputScores = new float[1][labelList.size()];
        tfLiteInterpreter.run(new float[][]{processedAudio}, outputScores);


        // Postprocessing
        return postprocess(outputScores[0]);
    }

    private static List<Integer> findTopKIndices(float[] scores, int k) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> Float.compare(scores[b], scores[a]));
        for (int i = 0; i < scores.length; i++) {
            maxHeap.add(i);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        List<Integer> topK = new ArrayList<>(maxHeap);
        Collections.reverse(topK);
        return topK;
    }


    // 슬라이딩 윈도우 파라미터 설정
    int windowSize = 15600; // 0.975초의 16kHz 샘플
    int stride = 7800; // 0.5초마다 슬라이드 (윈도우 크기 절반)

    public List<String> runSlidingWindowInference(float[] audioData) {
        int audioLength = audioData.length;
        List<float[]> allResults = new ArrayList<>();

        // 슬라이딩 윈도우 방식으로 오디오 데이터를 잘라서 모델에 넣음
        for (int start = 0; start + windowSize <= audioLength; start += stride) {
            float[] window = new float[windowSize];
            System.arraycopy(audioData, start, window, 0, windowSize); // 오디오 데이터 슬라이싱

            // 여기에서 window 배열을 모델에 넣어 inference 수행
            float[] result = runInference(window);
            allResults.add(result);
        }

        if (allResults.isEmpty()) return Collections.emptyList();

        // 결과 평균
        int numClasses = allResults.get(0).length;
        float[] averagedScores = new float[numClasses];
        for (float[] result : allResults) {
            for (int i = 0; i < numClasses; i++) {
                averagedScores[i] += result[i];
            }
        }
        for (int i = 0; i < numClasses; i++) {
            averagedScores[i] /= allResults.size();
        }

        Log.d("outputs", Arrays.toString(averagedScores));

        // Top-K 클래스 선택
        List<Integer> topKIndices = findTopKIndices(averagedScores, TOPK);
        return topKIndices.stream().map(labelList::get).collect(Collectors.toList());
    }

    float[] runInference(float[] inputData) {
        // 입력 데이터를 float형 배열로 변환하여 모델에 넣기
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputData.length);
        inputBuffer.order(ByteOrder.nativeOrder());
        for (float f : inputData) {
            inputBuffer.putFloat(f);
        }

        // 모델의 입력과 출력 초기화
//        float[][] output = new float[1][521]; // 예: 521개의 출력 (클래스 수)

        float[][] output = new float[1][labelList.size()];
        // 모델 실행
        tfLiteInterpreter.run(inputBuffer, output);

        // 결과 반환
        return output[0];
    }

    int handleInferenceResult(float[] result) {
        // 예측 결과 처리
        // 예: 결과를 처리하고 UI에 표시
        // 예측 값에서 최대값을 찾는 예시
        int predictedClass = findMaxIndex(result);
        Log.d("Inference Result", "Predicted class: " + predictedClass);
        return predictedClass;
    }

    int findMaxIndex(float[] result) {
        int maxIndex = 0;
        float maxValue = result[0];
        for (int i = 1; i < result.length; i++) {
            if (result[i] > maxValue) {
                maxValue = result[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }


}
