package gpt.general;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class MainClass {
    private final String apiKey;

    public MainClass() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        String keyFromEnv = dotenv.get("SECRET_SH_OPEN_AI_KEY");
        this.apiKey = (keyFromEnv != null && !keyFromEnv.isEmpty())
                ? keyFromEnv
                : System.getenv("SECRET_SH_OPEN_AI_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key not found in .env file or environment variables.");
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            String questionFilePath = "src/main/resources/question_file.txt";
            String userQuestion = new String(Files.readAllBytes(Paths.get(questionFilePath)));

            String filePath = "src/main/resources/restaurant_info.txt";

            MainClass main = new MainClass();
            MethodsClass methodsClass = new MethodsClass(main.apiKey);
            methodsClass.run(userQuestion, filePath);
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
