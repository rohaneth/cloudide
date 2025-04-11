package com.example.securitytemplate.controller;



import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class NormalController {
    
    @GetMapping("/user")
    public String user() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
@GetMapping("/user-details")
public Object userDetails() {
    return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
}

    
}
