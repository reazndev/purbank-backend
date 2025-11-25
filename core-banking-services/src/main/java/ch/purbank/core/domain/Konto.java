package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.KontoStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "konten")
@Data
public class Konto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal zinssatz = new BigDecimal("0.0100"); // TODO: how do we handle zinse? apply them? set them?

    @Column(nullable = false, unique = true)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KontoStatus status = KontoStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "konto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KontoMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "konto", cascade = CascadeType.ALL)
    private List<Transaction> transactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (iban == null) {
            iban = generateIban();
        }
    }

    private String generateIban() {
        // Swiss IBAN: CH + 2 check digits + 5 bank code + 12 account number
        String bankCode = "99999"; // Reserved/non-existent test bank code
        String accountNumber = String.format("%012d", System.currentTimeMillis() % 1000000000000L);

        String checkDigits = calculateIbanCheckDigits(bankCode, accountNumber);

        return "CH" + checkDigits + bankCode + accountNumber;
    }

    private String calculateIbanCheckDigits(String bankCode, String accountNumber) {
        // IBAN mod-97 algorithm
        // Format: bankCode + accountNumber + "CH00" -> convert to numbers -> mod 97
        String rearranged = bankCode + accountNumber + "CH00";

        // Convert letters to numbers (C=12, H=17)
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else {
                numeric.append(c);
            }
        }

        int remainder = 0;
        String numStr = numeric.toString();
        for (int i = 0; i < numStr.length(); i += 7) {
            int end = Math.min(i + 7, numStr.length());
            String chunk = remainder + numStr.substring(i, end);
            remainder = Integer.parseInt(chunk) % 97;
        }

        int checksum = 98 - remainder;
        return String.format("%02d", checksum);
    }

    public void close() {
        this.status = KontoStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    public boolean canBeClosed() {
        return balance.compareTo(BigDecimal.ZERO) == 0 && status == KontoStatus.ACTIVE;
    }

    public void addToBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void subtractFromBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
}