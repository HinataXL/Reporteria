package com.erick.soporte.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        System.out.println("Mostrando pantalla login");
        return "login";
    }

}