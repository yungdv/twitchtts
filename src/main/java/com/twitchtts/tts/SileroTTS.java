package com.twitchtts.tts;

import com.twitchtts.TwitchTts;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class SileroTTS {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private static final String SERVER_URL = "http://localhost:5000/tts";
    
    public static byte[] generateSpeech(String text, String speaker) {
        if (text == null || text.isEmpty()) {
            TwitchTts.LOGGER.error("Text is empty!");
            return null;
        }
        
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
            String url = SERVER_URL + "?text=" + encodedText + "&speaker=" + speaker + "&sample_rate=48000";
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    TwitchTts.LOGGER.error("Silero TTS server error: {} - {}", 
                            response.code(), response.body() != null ? response.body().string() : "No response");
                    return null;
                }
                
                byte[] audioData = response.body() != null ? response.body().bytes() : null;
                TwitchTts.LOGGER.info("Generated speech: {} bytes", audioData != null ? audioData.length : 0);
                return audioData;
            }
            
        } catch (IOException e) {
            TwitchTts.LOGGER.error("Failed to call Silero TTS server", e);
            return null;
        }
    }
}