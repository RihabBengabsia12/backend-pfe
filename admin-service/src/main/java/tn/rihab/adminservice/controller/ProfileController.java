package tn.rihab.adminservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.adminservice.DTO.UserResponse;
import tn.rihab.adminservice.security.SecurityUtils;
import tn.rihab.adminservice.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final UserService userService;

    // GET /api/admin/profile  → mon profil (rôle + permissions)
    @GetMapping
    public ResponseEntity<UserResponse> getProfile() {
        String email = SecurityUtils.getCurrentUserEmail();
        return ResponseEntity.ok(userService.findByEmail(email));
    }

    // PUT /api/admin/profile  → modifier mon fullName
    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(
            @RequestBody Map<String, String> body) {
        String email = SecurityUtils.getCurrentUserEmail();
        UserResponse me = userService.findByEmail(email);
        return ResponseEntity.ok(
                userService.updateFullName(me.getId(), body.get("fullName"), email));
    }
}