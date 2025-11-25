package ch.purbank.core.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "konto_id", nullable = false)
    @JsonIgnore
    private Konto konto;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter; // Balance after this transaction

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String fromIban;

    @Column(columnDefinition = "TEXT")
    private String message; // Message from sender

    @Column(columnDefinition = "TEXT")
    private String note; // Note by konto owner/manager

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}