package ch.purbank.core.controller;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.GenericStatusResponse;
import ch.purbank.core.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - User Management", description = "Admin endpoints for managing users and registration codes")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user in the system")
    public ResponseEntity<User> createUser(
            @Parameter(description = "User details", required = true) @Valid @RequestBody User user) {
        User saved = adminUserService.createUser(user);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique ID")
    public ResponseEntity<User> getUser(
            @Parameter(description = "User UUID", required = true, example = "434bb289-7bd8-4f0c-ba85-d73e7c0aa1c2") @PathVariable("userId") UUID userId) {
        User u = adminUserService.getUser(userId);
        if (u == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(u);
    }

    @PostMapping("/{userId}/registration")
    @Operation(summary = "Create registration code", description = "Creates a new registration code for a user")
    public ResponseEntity<RegistrationCodes> createRegistrationCode(
            @Parameter(description = "User UUID", required = true, example = "434bb289-7bd8-4f0c-ba85-d73e7c0aa1c2") @PathVariable("userId") UUID userId,
            @Parameter(description = "Title for the registration code", required = true, example = "Mobile App Registration") @RequestParam("title") String title,
            @Parameter(description = "Optional description", required = false, example = "Registration code for new iPhone") @RequestParam(value = "description", required = false) String description) {
        RegistrationCodes rc = adminUserService.createRegistrationCode(userId, title, description);
        return ResponseEntity.ok(rc);
    }

    @GetMapping("/{userId}/registration")
    @Operation(summary = "List registration codes", description = "Lists all registration codes for a specific user")
    public ResponseEntity<List<RegistrationCodes>> listRegistrationCodes(
            @Parameter(description = "User UUID", required = true, example = "434bb289-7bd8-4f0c-ba85-d73e7c0aa1c2") @PathVariable("userId") UUID userId) {
        List<RegistrationCodes> list = adminUserService.listRegistrationCodes(userId);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{userId}/registration/{codeId}")
    @Operation(summary = "Delete registration code", description = "Deletes a registration code by its ID")
    public ResponseEntity<GenericStatusResponse> deleteRegistrationCode(
            @Parameter(description = "User UUID", required = true, example = "434bb289-7bd8-4f0c-ba85-d73e7c0aa1c2") @PathVariable("userId") UUID userId,
            @Parameter(description = "Registration Code UUID", required = true, example = "6d30db8e-9ab4-4c9a-8214-80f00a28fa96") @PathVariable("codeId") UUID codeId) {
        boolean ok = adminUserService.deleteRegistrationCode(userId, codeId);
        return ResponseEntity.ok(new GenericStatusResponse(ok ? "OK" : "FAIL"));
    }
}