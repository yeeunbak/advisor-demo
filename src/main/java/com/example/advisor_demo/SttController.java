package com.example.advisor_demo;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.protobuf.ByteString;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/stt")
public class SttController {

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "lang", defaultValue = "ko-KR") String lang) {

        // 권한(자격증명) 없을 때는 즉시 모의 응답 반환
        boolean credsMissing = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") == null;
        if (credsMissing) {
            Map<String, Object> mock = new HashMap<>();
            mock.put("mode", "mock");
            mock.put("transcript", "이건 데모 결과입니다. (권한 미설정, MOCK)");
            mock.put("confidence", 0.0);
            return ResponseEntity.ok(mock);
        }

        // 실제 STT 호출 (자격증명 설정 후 자동 활성화)
        try (SpeechClient speechClient = SpeechClient.create()) {
            // 짧은 오디오(<=60초)는 동기 인식 권장. LINEAR16/WAV 가정
            // (자세한 설정은 공식 문서 'Transcribe short audio files' 참고) :contentReference[oaicite:2]{index=2}
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setLanguageCode(lang)                         // 예: ko-KR, en-US
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // WAV(PCM)라면 LINEAR16
                    .setEnableAutomaticPunctuation(true)
                    .build();

            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audio.getBytes()))
                    .build();

            RecognizeRequest request = RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(recognitionAudio)
                    .build();

            RecognizeResponse response = speechClient.recognize(request);

            StringBuilder sb = new StringBuilder();
            double bestConfidence = 0.0;
            if (response.getResultsCount() > 0) {
                var alt = response.getResults(0).getAlternatives(0);
                sb.append(alt.getTranscript());
                bestConfidence = alt.getConfidence();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("mode", "live");
            payload.put("transcript", sb.toString());
            payload.put("confidence", bestConfidence);
            return ResponseEntity.ok(payload);

        } catch (Exception e) {
            // 라이브 호출 실패 시 안전한 폴백
            Map<String, Object> err = new HashMap<>();
            err.put("mode", "error-fallback");
            err.put("error", e.getMessage());
            err.put("transcript", "인식 중 오류가 발생하여 임시 결과를 반환합니다. (FALLBACK)");
            err.put("confidence", 0.0);
            return ResponseEntity.ok(err);
        }
    }
}
