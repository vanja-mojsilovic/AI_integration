package gpt.general;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import io.github.cdimascio.dotenv.Dotenv;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class MethodsClass {
    private final OpenAiService service;
    private final boolean isLocal;

    public MethodsClass(String apiKey) {
        // Use default OpenAiService constructor with just the API key
        this.service = new OpenAiService(apiKey);

        // Load environment flag to determine if running locally
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String localFlag = dotenv.get("IS_LOCAL");
        this.isLocal = "true".equalsIgnoreCase(localFlag);
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
        String reply;
        if (choices == null || choices.isEmpty()) {
            reply = "[No choices returned]";
        } else {
            reply = choices.get(0).getMessage().getContent();
            if (reply == null || reply.trim().isEmpty()) {
                reply = "[Empty content]";
            }
        }

        if (isLocal) {
            Files.write(
                    Paths.get("src/main/resources/result.txt"),
                    reply.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("AI reply saved to result.txt");
        } else {
            System.out.println("AI reply:\n" + reply);
        }

        System.exit(0);
    }

    public void runAcumulated(String userQuestion, String sourceDirectory) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode resultArray = mapper.createArrayNode();

        List<Path> sourceFiles = Files.list(Paths.get(sourceDirectory))
                .filter(path -> path.getFileName().toString().matches("source_code_\\d+\\.txt"))
                .sorted()
                .toList();

        for (Path filePath : sourceFiles) {
            String chunk = Files.readString(filePath);
            String fullPrompt = buildFullPrompt(chunk, userQuestion);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-4-1106-preview")
                    .temperature(0.3)
                    .messages(List.of(
                            new ChatMessage("system", "You are a helpful assistant."),
                            new ChatMessage("user", fullPrompt)
                    ))
                    .build();

            System.out.println("Sending request for " + filePath.getFileName() + "...");
            List<ChatCompletionChoice> choices = service.createChatCompletion(request).getChoices();
            String reply;
            if (choices == null || choices.isEmpty()) {
                reply = "[]";
            } else {
                reply = choices.get(0).getMessage().getContent();
                if (reply == null || reply.trim().isEmpty()) {
                    reply = "[]";
                }
            }

            // Clean up markdown formatting
            String cleaned = reply.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

            try {
                JsonNode node = mapper.readTree(cleaned);
                if (node.isArray()) {
                    resultArray.addAll((ArrayNode) node); // flatten array
                } else {
                    resultArray.add(node); // single object
                }
            } catch (Exception e) {
                System.err.println("Failed to parse JSON from " + filePath.getFileName() + ": " + e.getMessage());
            }
        }

        if (isLocal) {
            Path outputPath = Paths.get("src/main/resources/result.txt");
            Files.write(
                    outputPath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(resultArray),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("Flattened JSON saved to result.txt");
        } else {
            System.out.println("Flattened JSON:\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultArray));
        }

        System.exit(0);
    }



    private String loadRestaurantData(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private String buildFullPrompt(String restaurantData, String userQuestion) {
        return restaurantData + "\n\n" + userQuestion;
    }

    public static void splitRestaurantInfoFile(String inputFilePath, int maxCharsPerFile) throws IOException {
        // Read the entire content of restaurant_info.txt
        String fullText = new String(Files.readAllBytes(Paths.get(inputFilePath)));
        int fileIndex = 1;
        int start = 0;
        while (start < fullText.length()) {
            int end = Math.min(start + maxCharsPerFile, fullText.length());
            String chunk = fullText.substring(start, end);

            Path outputPath = Paths.get("src/main/resources/source_code_" + fileIndex + ".txt");
            Files.write(outputPath, chunk.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            fileIndex++;
            start = end;
        }
        System.out.println("Split complete: " + (fileIndex - 1) + " files created.");
    }
}
