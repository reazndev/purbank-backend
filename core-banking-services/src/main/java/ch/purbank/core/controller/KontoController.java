package ch.purbank.core.controller;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.*;
import ch.purbank.core.service.KontoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/konten")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Konten", description = "Konto (account) management endpoints")
public class KontoController {

    private final KontoService kontoService;

    @PostMapping
    @Operation(summary = "Create new konto", description = "Creates a new konto for the authenticated user")
    public ResponseEntity<Konto> createKonto(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto creation details", required = true) @Valid @RequestBody CreateKontoRequestDTO request) {

        Konto konto = kontoService.createKonto(request.getName(), currentUser.getId());
        return ResponseEntity.ok(konto);
    }

    @GetMapping
    @Operation(summary = "List all konten", description = "Gets a list of all konten the user has access to")
    public ResponseEntity<List<KontoListItemDTO>> listKonten(@AuthenticationPrincipal User currentUser) {
        List<KontoListItemDTO> konten = kontoService.getAllKontenForUser(currentUser.getId());
        return ResponseEntity.ok(konten);
    }

    @GetMapping("/{kontoId}")
    @Operation(summary = "Get konto details", description = "Gets detailed information about a specific konto")
    public ResponseEntity<KontoDetailDTO> getKontoDetail(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        KontoDetailDTO detail = kontoService.getKontoDetail(kontoId, currentUser.getId());
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{kontoId}/transactions")
    @Operation(summary = "Get transactions", description = "Gets transactions for a konto (default 0-49, or use range parameter)")
    public ResponseEntity<List<TransactionDTO>> getTransactions(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Range in format 'start.end' (e.g., '0.49')", required = false) @RequestParam(required = false) String range) {

        Integer start = null;
        Integer end = null;

        if (range != null && range.contains(".")) {
            String[] parts = range.split("\\.");
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid range format. Use 'start.end' (e.g., '0.49')");
            }
        }

        List<TransactionDTO> transactions = kontoService.getTransactions(kontoId, currentUser.getId(), start, end);
        return ResponseEntity.ok(transactions);
    }

    @PatchMapping("/{kontoId}/transactions/{transactionId}")
    @Operation(summary = "Update transaction note", description = "Updates the note field of a transaction (OWNER/MANAGER only)")
    public ResponseEntity<GenericStatusResponse> updateTransactionNote(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Transaction UUID", required = true) @PathVariable UUID transactionId,
            @Parameter(description = "Note update", required = true) @Valid @RequestBody UpdateTransactionNoteDTO request) {

        kontoService.updateTransactionNote(kontoId, transactionId, currentUser.getId(), request.getNote());
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @PatchMapping("/{kontoId}")
    @Operation(summary = "Update konto", description = "Updates konto details (name) - OWNER only")
    public ResponseEntity<GenericStatusResponse> updateKonto(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Update details", required = true) @Valid @RequestBody UpdateKontoRequestDTO request) {

        kontoService.updateKonto(kontoId, currentUser.getId(), request);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{kontoId}")
    @Operation(summary = "Close konto", description = "Closes a konto (balance must be 0, cancels all pending payments) - OWNER only")
    public ResponseEntity<GenericStatusResponse> closeKonto(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        kontoService.closeKonto(kontoId, currentUser.getId());
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @PostMapping("/{kontoId}/members")
    @Operation(summary = "Invite member", description = "Invites a user to the konto by their contract number - OWNER only")
    public ResponseEntity<GenericStatusResponse> inviteMember(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Invitation details", required = true) @Valid @RequestBody InviteMemberRequestDTO request) {

        kontoService.inviteMember(kontoId, currentUser.getId(), request.getContractNumber(), request.getRole());
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{kontoId}/members/{memberId}")
    @Operation(summary = "Remove member or leave konto", description = "Removes a member from konto (or user leaves if it's themselves)")
    public ResponseEntity<GenericStatusResponse> removeMember(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Member UUID", required = true) @PathVariable UUID memberId) {

        kontoService.removeMember(kontoId, currentUser.getId(), memberId);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @GetMapping("/{kontoId}/members")
    @Operation(summary = "List members", description = "Lists all members of a konto and their roles")
    public ResponseEntity<List<MemberDTO>> listMembers(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        List<MemberDTO> members = kontoService.getMembers(kontoId, currentUser.getId());
        return ResponseEntity.ok(members);
    }
}