package ch.purbank.core.controller;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.dto.*;
import ch.purbank.core.service.InterestService;
import ch.purbank.core.service.KontoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/konten")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Konto Management", description = "Admin endpoints for managing konten (accounts)")
public class AdminKontoController {

    private final KontoService kontoService;
    private final InterestService interestService;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get konten for user", description = "Admin: Gets all konten for a specific user with accrued interest data")
    public ResponseEntity<List<AdminKontoListItemDTO>> getKontenForUser(
            @Parameter(description = "User UUID", required = true) @PathVariable UUID userId,
            @Parameter(description = "Include closed konten (true=only closed, false/null=only open)", required = false)
            @RequestParam(required = false) Boolean includeClosed) {

        List<AdminKontoListItemDTO> konten = kontoService.getAllKontenForUserAdmin(userId, includeClosed);
        return ResponseEntity.ok(konten);
    }

    @GetMapping("/{kontoId}")
    @Operation(summary = "Get konto details", description = "Admin: Gets detailed information about any konto with accrued interest data")
    public ResponseEntity<AdminKontoDetailDTO> getKontoDetail(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "User UUID (for authorization context)", required = true) @RequestParam UUID userId) {

        AdminKontoDetailDTO detail = kontoService.getKontoDetailAdminExtended(kontoId);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/user/{userId}")
    @Operation(summary = "Create konto for user", description = "Admin: Creates a new konto for a specific user")
    public ResponseEntity<Konto> createKontoForUser(
            @Parameter(description = "User UUID", required = true) @PathVariable UUID userId,
            @Parameter(description = "Konto creation details", required = true) @Valid @RequestBody CreateKontoRequestDTO request) {

        Konto konto = kontoService.createKonto(request.getName(), userId, request.getCurrency());
        return ResponseEntity.ok(konto);
    }

    @PatchMapping("/{kontoId}")
    @Operation(summary = "Update konto", description = "Admin: Updates konto details (name, zinssatz, balance)")
    public ResponseEntity<GenericStatusResponse> updateKonto(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Update details (name, zinssatz)", required = true) @Valid @RequestBody UpdateKontoRequestDTO request,
            @Parameter(description = "Balance adjustment (optional)", required = false) @RequestParam(required = false) BigDecimal balanceAdjustment) {

        kontoService.updateKontoAdmin(kontoId, request, balanceAdjustment);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{kontoId}")
    @Operation(summary = "Close konto", description = "Admin: Closes a konto (balance must be 0)")
    public ResponseEntity<GenericStatusResponse> closeKonto(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        kontoService.closeKontoAdmin(kontoId);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @GetMapping("/{kontoId}/members")
    @Operation(summary = "List konto members", description = "Admin: Lists all members of a konto")
    public ResponseEntity<List<MemberDTO>> listMembers(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        List<MemberDTO> members = kontoService.getMembersAdmin(kontoId);
        return ResponseEntity.ok(members);
    }

    @PostMapping("/abrechnung")
    @Operation(summary = "Process manual Abrechnung", description = "Admin: Manually trigger quarterly interest settlement (Abrechnung) without today's daily calculation")
    public ResponseEntity<GenericStatusResponse> processManualAbrechnung() {
        log.info("Admin triggered manual Abrechnung");
        interestService.processManualAbrechnung();
        return ResponseEntity.ok(new GenericStatusResponse("Manual Abrechnung completed successfully"));
    }

    @PostMapping("/daily-calculation")
    @Operation(summary = "Force daily interest calculation", description = "Admin: Manually trigger daily interest calculation for all konten (ignoring last calculation date)")
    public ResponseEntity<GenericStatusResponse> processManualDailyCalculation() {
        log.info("Admin triggered manual daily interest calculation");
        interestService.processManualDailyCalculation();
        return ResponseEntity.ok(new GenericStatusResponse("Manual daily calculation completed successfully"));
    }
}