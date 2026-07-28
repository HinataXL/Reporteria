package com.erick.soporte.controller;

import com.erick.soporte.entity.Role;
import com.erick.soporte.entity.User;
import com.erick.soporte.repository.RoleRepository;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
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
            @RequestParam Long roleId,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String normalizedEmail = correo == null ? "" : correo.trim().toLowerCase();

        if (nombre == null || nombre.trim().length() < 2 ||
                apellido == null || apellido.trim().length() < 2 ||
                normalizedEmail.isBlank() ||
                password == null || password.length() < 8 ||
                roleId == null) {
            redirectAttributes.addFlashAttribute("error", "Completa todos los campos requeridos antes de crear el usuario.");
            return "redirect:/users/create";
        }

        if (userRepository.findByCorreo(normalizedEmail).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ya existe un usuario registrado con ese correo.");
            return "redirect:/users/create";
        }

        Role role = roleRepository.findById(roleId)
                .orElse(null);

        if (role == null) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un rol valido para el usuario.");
            return "redirect:/users/create";
        }

        User user = new User();
        user.setNombre(nombre.trim());
        user.setApellido(apellido.trim());
        user.setCorreo(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setEstado(1);
        user.setRole(role);

        userRepository.syncUserIdSequence();
        userRepository.save(user);

        auditLogService.registrar(
                "CREAR_USUARIO",
                "USUARIOS",
                "Se creo el usuario " + normalizedEmail + " con rol " + role.getNombre(),
                authentication,
                request
        );

        redirectAttributes.addFlashAttribute("success", "Usuario creado correctamente.");
        return "redirect:/users";
    }
}
