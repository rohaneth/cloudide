package com.example.securitytemplate.controller;

import com.example.securitytemplate.model.AppUser;
import com.example.securitytemplate.repository.UserRepository;
import com.example.securitytemplate.service.TerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import  com.example.securitytemplate.util.JwtUtil;

  
@Controller
public class TerminalController {

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MessageMapping("/terminal")
    @SendTo("/topic/output")
    public void executeCommand(String command) {
        terminalService.executeCommand(command);
    }


    //set path
    @PostMapping("path")
public String postMethodName(@RequestHeader("Authorization") String token) {
    // Sanitize the token
    try {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        token = token.trim();

        String username = jwtUtil.extractUsername(token);
        Optional<AppUser> user = userRepository.findByUsername(username);
        String path = user.get().getPath();            
        Path entity = Path.of(path);
            System.out.println(entity);
        if (!Files.exists(entity)) {
            Files.createDirectories(entity);
        }
        TerminalService.currentDirectory = entity;
        return path;
    } catch (IOException e) {
        e.printStackTrace();
        return "Error creating directory";
    } catch (Exception e) {
        e.printStackTrace();
        return "Invalid token or path";
    }
}

    
} 