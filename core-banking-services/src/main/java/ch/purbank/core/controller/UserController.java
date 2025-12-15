package ch.purbank.core.controller;

import ch.purbank.core.domain.User;
import ch.purbank.core.dto.AuthorisationResponseDTO;
import ch.purbank.core.dto.GenericActionRequestDTO;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.service.ActionDecisionService;
import ch.purbank.core.service.AuthorisationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final AuthorisationService authorisationService;
    private final ActionDecisionService actionDecisionService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/secure/action/execute")
    public ResponseEntity<AuthorisationResponseDTO> executeSecureAction(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody GenericActionRequestDTO requestBody,
            HttpServletRequest request) {

        final String ipAddress = request.getRemoteAddr();

        if (!actionDecisionService.requiresApproval(currentUser, requestBody.getDeviceId(), ipAddress,
                requestBody.getActionType(), requestBody.getActionDataPayload())) {

            actionDecisionService.executeImmediateAction(currentUser, requestBody.getActionDataPayload());

            return ResponseEntity.ok(AuthorisationResponseDTO.builder().mobileVerify("").build());
        }

        String mobileVerifyCode = authorisationService.createAuthorisationRequest(
                currentUser,
                requestBody.getDeviceId(),
                ipAddress,
                requestBody.getActionType(),
                requestBody.getActionDataPayload());

        AuthorisationResponseDTO response = AuthorisationResponseDTO.builder()
                .mobileVerify(mobileVerifyCode)
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}