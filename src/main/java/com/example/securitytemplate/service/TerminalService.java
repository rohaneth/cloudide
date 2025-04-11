package com.example.securitytemplate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.securitytemplate.util.JwtUtil;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Service
public class TerminalService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile("(&|;|\\||`|>|<|\\$|\\*|\\?)");

    private static final List<String> ALLOWED_COMMANDS_WINDOWS = List.of(
            "dir", "cd", "echo", "type", "mkdir", "rmdir", "cls",
            "mvn", "mvnw", "mvnw.cmd", "mvn spring-boot:run", "mvn clean install", "mvn clean package",
            "mvn test", "mvn validate", "mvn compile", "mvn verify",
            "./mvnw", "./mvnw.cmd", "./mvnw spring-boot:run", "./mvnw clean install", "./mvnw test",
            "./mvnw package", "./mvnw compile",
            "java", "javac", "java -jar",
            "git", "git clone", "git pull", "git status", "git init", "git log", "git checkout", "git branch",
            "docker", "docker build", "docker run", "docker ps", "docker stop", "docker start", "docker-compose",
            "docker-compose up", "docker-compose down", "docker images", "docker exec",
            "mysql", "mysql -u", "mysql -p", "psql", "sqlite3",
            "gradle", "gradlew", "./gradlew", "gradlew.bat",
            "ngrok", "ngrok http" , "ngrok.exe", "ngrok.exe http",
            "C:\\ngrok\\ngrok.exe http", "C:\\ngrok\\ngrok.exe",
             "javac Main.java , java Main",
          "g++ main.cpp -o main && main",
            "python3 script.py", "python script.py","javac","python","python3",
"java"
    );

    private static final List<String> ALLOWED_COMMANDS_UNIX = List.of(
            "ls", "pwd", "cd", "echo", "cat", "mkdir", "rmdir", "touch",
            "ngrok", "ngrok http"
    );
     
    //Paths.get("C:\\Users\\rohan\\OneDrive\\Desktop\\cloudstoer");
 

    private static Path cloudstoerPath = Paths.get("C:\\Users\\rohan\\OneDrive\\Desktop\\cloudstoer");
    public static Path currentDirectory = cloudstoerPath;
     
    public void executeCommand(String command) {
        String os = System.getProperty("os.name").toLowerCase();

        if (!isCommandAllowed(command, os)) {
            messagingTemplate.convertAndSend("/topic/output", "Error: Command not allowed");
            return;
        }

        if (command.equals("cd ..") || command.trim().startsWith("cd ..")) {
            messagingTemplate.convertAndSend("/topic/output", "Error: Moving up directories is not allowed.");
            return;
        }

        if (command.startsWith("cd")) {
            String response = handleCdCommand(command);
            messagingTemplate.convertAndSend("/topic/output", response);
            return;
        }

        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.directory(currentDirectory.toFile());

                if (os.contains("win")) {
                    processBuilder.command("cmd.exe", "/c", command);
                } else {
                    processBuilder.command("bash", "-c", command);
                }

                Process process = processBuilder.start();

                BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    messagingTemplate.convertAndSend("/topic/output", line + "\n");
                }

                while ((line = stderrReader.readLine()) != null) {
                    messagingTemplate.convertAndSend("/topic/output", "Error: " + line + "\n");
                }

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
                process.waitFor();
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/output", "Error: " + e.getMessage());
            }
        }).start();
    }

    private String handleCdCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 1) {
            return currentDirectory.toString();
        }

        String target = parts[1];
        Path newPath = currentDirectory.resolve(target).normalize();

        if (newPath != null && Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDirectory = newPath;
            return "Changed directory to: " + currentDirectory.toString();
        } else {
            return "Error: Directory not found: " + target;
        }
    }

    private boolean isCommandAllowed(String command, String os) {
        if (DANGEROUS_PATTERNS.matcher(command).find()) return false;
    
        String[] parts = command.trim().split("\\s+");
        String baseCommand = parts[0];
    
        String commandName = Paths.get(baseCommand).getFileName().toString();
        String subCommand = parts.length > 1 ? commandName + " " + parts[1] : commandName;
    
        if (os.contains("win")) {
            return ALLOWED_COMMANDS_WINDOWS.contains(commandName) || ALLOWED_COMMANDS_WINDOWS.contains(subCommand);
        } else {
            return ALLOWED_COMMANDS_UNIX.contains(commandName) || ALLOWED_COMMANDS_UNIX.contains(subCommand);
        }
    }
}
