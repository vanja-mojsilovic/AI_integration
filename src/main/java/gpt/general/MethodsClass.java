package gpt.general;

import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MethodsClass {
    private final String apiKey;
    private final String apiUrl = "https://api.openai.com/v1/chat/completions";

    private static final ThreadFactory daemonThreadFactory = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("OkHttp-Daemon-" + thread.getId());
        return thread;
    };

    private static final Dispatcher dispatcher = new Dispatcher(Executors.newCachedThreadPool(daemonThreadFactory));
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .build();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.connectionPool().evictAll();
            dispatcher.executorService().shutdown();
        }));
    }

    public MethodsClass(String apiKey) {
        this.apiKey = apiKey;
    }

    public void run(String userQuestion, String filePath) throws IOException {
        String restaurantData = loadRestaurantData(filePath);
        String fullPrompt = buildFullPrompt(restaurantData, userQuestion);
        String modelId = "gpt-4-1106-preview"; // or "gpt-3.5-turbo"
        List<Map<String, String>> messages = buildMessages(fullPrompt);
        Map<String, Object> payload = buildPayload(modelId, messages);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(payload);

        System.out.println("Sending request to OpenAI...");

        Request request = buildRequest(json);

        try (Response response = client.newCall(request).execute()) {
            handleResponse(response, mapper);
        }

        System.exit(0);
    }

    private List<Map<String, String>> buildMessages(String userQuestion) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful assistant."));
        messages.add(Map.of("role", "user", "content", userQuestion));
        return messages;
    }

    private Map<String, Object> buildPayload(String modelId, List<Map<String, String>> messages) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelId);
        payload.put("temperature", 0.3);
        payload.put("messages", messages);
        return payload;
    }

    private Request buildRequest(String json) {
        return new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
    }

    private void handleResponse(Response response, ObjectMapper mapper) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";

        //System.out.println("Raw response:\n" + responseBody);

        if (response.isSuccessful()) {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.size() == 0) {
                System.out.println("AI reply: [No choices returned]");
                return;
            }

            JsonNode messageNode = choices.get(0).path("message").path("content");
            String reply = messageNode.isMissingNode() ? "" : messageNode.asText();

            if (reply == null || reply.trim().isEmpty()) {
                System.out.println("AI reply: [Empty content]");
            } else {
                System.out.println("AI reply:\n" + reply);
            }
        } else {
            System.err.println("Error: " + response.code() + " - " + response.message());
            System.err.println("Details: " + responseBody);
            throw new IOException("Request failed with status code: " + response.code());
        }
    }

    private String loadRestaurantData(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private String buildFullPrompt(String restaurantData, String userQuestion) {
        return restaurantData + "\n\n" + userQuestion;
    }
}
