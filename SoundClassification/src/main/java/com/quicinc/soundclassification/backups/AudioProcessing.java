package com.quicinc.soundclassification.backups;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioProcessing {
    public static float[] loadAudioFromFile(File audioFile) {
        // 오디오 파일 데이터를 불러와 Float 배열로 변환하는 코드 구현
        // 예시로 PCM 데이터를 읽는 코드 제공
        try (FileInputStream fis = new FileInputStream(audioFile)) {
            byte[] rawData = readAllBytes(fis);
            float[] audioData = new float[rawData.length / 2]; // 16-bit PCM assumed
            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = ((rawData[2 * i + 1] << 8) | (rawData[2 * i] & 0xFF)) / 32768.0f;
            }
            return audioData;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024]; // 1KB 버퍼
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

}

