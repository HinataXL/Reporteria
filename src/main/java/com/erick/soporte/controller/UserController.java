package com.erick.soporte.controller;

import com.erick.soporte.entity.Role;
import com.erick.soporte.entity.User;
import com.erick.soporte.repository.RoleRepository;
import com.erick.soporte.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users/index";
    }

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("roles", roleRepository.findAll());
        return "users/create";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam String nombre,
            @RequestParam String apellido,
            @RequestParam String correo,
            @RequestParam String password,
            @RequestParam Long roleId
    ) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        User user = new User();
        user.setNombre(nombre);
        user.setApellido(apellido);
        user.setCorreo(correo);
        user.setPassword(passwordEncoder.encode(password));
        user.setEstado(1);
        user.setRole(role);

        userRepository.save(user);

        return "redirect:/users";
    }
}
