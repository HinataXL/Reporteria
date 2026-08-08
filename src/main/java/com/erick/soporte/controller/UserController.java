package com.erick.soporte.controller;

import com.erick.soporte.entity.Role;
import com.erick.soporte.entity.User;
import com.erick.soporte.repository.RoleRepository;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.service.AuditLogService;
import com.erick.soporte.service.UserPasswordMailService;
import com.erick.soporte.security.CustomUserPrincipal;
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
    private final UserPasswordMailService userPasswordMailService;

    public UserController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            UserPasswordMailService userPasswordMailService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.userPasswordMailService = userPasswordMailService;
    }

    @GetMapping
    public String index(Model model, Authentication authentication) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("currentUserId", currentUserId(authentication));
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

    @PostMapping("/{id}/role")
    public String updateRole(
            @PathVariable Long id,
            @RequestParam Long roleId,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request
    ) {
        if (id.equals(currentUserId(authentication))) {
            redirectAttributes.addFlashAttribute("error", "No puedes cambiar tu propio rol desde esta pantalla.");
            return "redirect:/users";
        }

        User user = userRepository.findById(id).orElse(null);
        Role role = roleRepository.findById(roleId).orElse(null);

        if (user == null || role == null) {
            redirectAttributes.addFlashAttribute("error", "No fue posible actualizar el rol del usuario.");
            return "redirect:/users";
        }

        String previousRole = user.getRole() != null ? user.getRole().getNombre() : "Sin rol";
        user.setRole(role);
        userRepository.save(user);

        auditLogService.registrar(
                "CAMBIAR_ROL_USUARIO",
                "USUARIOS",
                "Se cambio el rol de " + user.getCorreo() + " de " + previousRole + " a " + role.getNombre(),
                authentication,
                request
        );

        redirectAttributes.addFlashAttribute("success", "Rol actualizado correctamente.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/password")
    public String sendNewPassword(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/users";
        }

        String temporaryPassword = userPasswordMailService.generateTemporaryPassword();
        try {
            userPasswordMailService.sendTemporaryPassword(user, temporaryPassword);
            user.setPassword(passwordEncoder.encode(temporaryPassword));
            userRepository.save(user);

            auditLogService.registrar(
                    "REENVIAR_PASSWORD_USUARIO",
                    "USUARIOS",
                    "Se genero y envio una nueva contrasena temporal para " + user.getCorreo(),
                    authentication,
                    request
            );

            redirectAttributes.addFlashAttribute("success", "Nueva contrasena enviada a " + user.getCorreo() + ".");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo enviar la nueva contrasena. Revisa la configuracion SMTP.");
        }

        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request
    ) {
        if (id.equals(currentUserId(authentication))) {
            redirectAttributes.addFlashAttribute("error", "No puedes eliminar tu propio usuario.");
            return "redirect:/users";
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/users";
        }

        user.setEstado(0);
        userRepository.save(user);

        auditLogService.registrar(
                "ELIMINAR_USUARIO",
                "USUARIOS",
                "Se desactivo el usuario " + user.getCorreo(),
                authentication,
                request
        );

        redirectAttributes.addFlashAttribute("success", "Usuario eliminado correctamente.");
        return "redirect:/users";
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            return principal.getId();
        }

        return null;
    }
}
