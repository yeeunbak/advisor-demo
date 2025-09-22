package com.example.advisor_demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SttController {

  @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> stt(@RequestPart("audio") MultipartFile audio) {
    if (audio == null || audio.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
    }
    try (SpeechClient client = SpeechClient.create()) {
      // WAV(PCM/Linear16) 가정. 필요시 sampleRateHertz 지정
      ByteString content = ByteString.copyFrom(audio.getBytes());
      RecognitionAudio recAudio = RecognitionAudio.newBuilder().setContent(content).build();

      RecognitionConfig config = RecognitionConfig.newBuilder()
          .setLanguageCode("ko-KR")
          .setEnableAutomaticPunctuation(true)
          .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
          // .setSampleRateHertz(16000)
          .build();

      RecognizeResponse response = client.recognize(config, recAudio);
      String transcript = response.getResultsList().stream()
          .flatMap(r -> r.getAlternativesList().stream())
          .map(SpeechRecognitionAlternative::getTranscript)
          .collect(Collectors.joining(" "));

      return ResponseEntity.ok(new SttResult(transcript == null ? "" : transcript.trim()));
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "STT failed", "details", e.getMessage()));
    }
  }

  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("ok", "pong");
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SttResult(String transcript) {}
}
