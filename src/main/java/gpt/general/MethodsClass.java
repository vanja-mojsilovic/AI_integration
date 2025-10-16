package gpt.general;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


public class MethodsClass {
    private final OpenAiService service;

    public MethodsClass(String apiKey) {
        this.service = new OpenAiService(apiKey);
    }

    public void run(String userQuestion, String filePath) throws IOException {
        String restaurantData = loadRestaurantData(filePath);
        String fullPrompt = buildFullPrompt(restaurantData, userQuestion);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4-1106-preview")
                .temperature(0.3)
                .messages(List.of(
                        new ChatMessage("system", "You are a helpful assistant."),
                        new ChatMessage("user", fullPrompt)
                ))
                .build();

        System.out.println("Sending request to OpenAI...");

        List<ChatCompletionChoice> choices = service.createChatCompletion(request).getChoices();
        if (choices == null || choices.isEmpty()) {
            System.out.println("AI reply: [No choices returned]");
        } else {
            String reply = choices.get(0).getMessage().getContent();
            if (reply == null || reply.trim().isEmpty()) {
                System.out.println("AI reply: [Empty content]");
            } else {
                System.out.println("AI reply:\n" + reply);
            }
        }

        System.exit(0);
    }

    private String loadRestaurantData(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private String buildFullPrompt(String restaurantData, String userQuestion) {
        return restaurantData + "\n\n" + userQuestion;
    }
}
