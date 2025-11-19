package ch.purbank.core.controller;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.GenericStatusResponse;
import ch.purbank.core.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        User saved = adminUserService.createUser(user);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable UUID userId) {
        User u = adminUserService.getUser(userId);
        if (u == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(u);
    }

    @PostMapping("/{userId}/registration")
    public ResponseEntity<RegistrationCodes> createRegistrationCode(@PathVariable UUID userId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        RegistrationCodes rc = adminUserService.createRegistrationCode(userId, title, description);
        return ResponseEntity.ok(rc);
    }

    @GetMapping("/{userId}/registration")
    public ResponseEntity<List<RegistrationCodes>> listRegistrationCodes(@PathVariable UUID userId) {
        List<RegistrationCodes> list = adminUserService.listRegistrationCodes(userId);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{userId}/registration/{codeId}")
    public ResponseEntity<GenericStatusResponse> deleteRegistrationCode(@PathVariable UUID userId,
            @PathVariable UUID codeId) {
        boolean ok = adminUserService.deleteRegistrationCode(userId, codeId);
        return ResponseEntity.ok(new GenericStatusResponse(ok ? "OK" : "FAIL"));
    }
}