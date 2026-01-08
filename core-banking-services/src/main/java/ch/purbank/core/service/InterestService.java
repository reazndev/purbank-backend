package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Transaction;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.TransactionType;
import ch.purbank.core.repository.KontoRepository;
import ch.purbank.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestService {

    private final KontoRepository kontoRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Calculates and accrues daily interest for all active konten.
     * Formula: daily_interest = balance × (annual_rate / 365)
     * This should be run nightly.
     */
    @Transactional
    public void calculateDailyInterest() {
        LocalDate today = LocalDate.now();
        log.info("Starting daily interest calculation for {}", today);

        List<Konto> activeKonten = kontoRepository.findAll().stream()
                .filter(k -> k.getStatus() == KontoStatus.ACTIVE)
                .toList();

        int processedCount = 0;
        for (Konto konto : activeKonten) {
            // Skip if we already calculated interest for today
            if (konto.getLastInterestCalcDate() != null &&
                    !konto.getLastInterestCalcDate().isBefore(today)) {
                log.debug("Skipping konto {} - interest already calculated for {}",
                        konto.getId(), today);
                continue;
            }

            BigDecimal balance = konto.getBalance();
            BigDecimal annualRate = konto.getZinssatz();

            // Calculate daily interest: balance × (annual_rate / 365)
            BigDecimal dailyInterest = balance
                    .multiply(annualRate)
                    .divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

            // Add to accrued interest
            konto.setAccruedInterest(konto.getAccruedInterest().add(dailyInterest));
            konto.setLastInterestCalcDate(today);

            kontoRepository.save(konto);
            processedCount++;

            log.debug("Konto {}: balance={}, rate={}, daily_interest={}, accrued_total={}",
                    konto.getId(), balance, annualRate, dailyInterest, konto.getAccruedInterest());
        }

        log.info("Daily interest calculation completed. Processed {} konten", processedCount);
    }

    /**
     * Processes quarterly interest settlement (Abrechnung).
     * Creates transactions for accrued interest and resets the accrued interest balance.
     * This is called by the quarterly scheduler or manually by admin.
     */
    @Transactional
    public void processQuarterlyAbrechnung() {
        log.info("Starting quarterly interest Abrechnung");

        List<Konto> activeKonten = kontoRepository.findAll().stream()
                .filter(k -> k.getStatus() == KontoStatus.ACTIVE)
                .toList();

        int processedCount = 0;
        BigDecimal totalInterestPaid = BigDecimal.ZERO;

        for (Konto konto : activeKonten) {
            BigDecimal accruedInterest = konto.getAccruedInterest();

            // Only create transaction if there's accrued interest
            if (accruedInterest.compareTo(BigDecimal.ZERO) > 0) {
                // Round accrued interest to 2 decimal places for currency
                BigDecimal roundedInterest = accruedInterest.setScale(2, RoundingMode.HALF_UP);

                // Add rounded interest to balance
                konto.addToBalance(roundedInterest);

                // Create transaction record
                Transaction transaction = new Transaction();
                transaction.setKonto(konto);
                transaction.setAmount(roundedInterest);
                transaction.setBalanceAfter(konto.getBalance());
                transaction.setTransactionType(TransactionType.INTEREST);
                transaction.setCurrency(konto.getCurrency());
                transaction.setMessage(String.format("Quarterly interest at %.2f%% rate",
                        konto.getZinssatz().multiply(BigDecimal.valueOf(100))));
                transaction.setIban(konto.getIban());

                transactionRepository.save(transaction);

                // Reset accrued interest
                konto.setAccruedInterest(BigDecimal.ZERO);
                kontoRepository.save(konto);

                totalInterestPaid = totalInterestPaid.add(roundedInterest);
                processedCount++;

                log.info("Konto {}: Paid interest {} (rounded from {}), new balance {}",
                        konto.getId(), roundedInterest, accruedInterest, konto.getBalance());
            }
        }

        log.info("Quarterly Abrechnung completed. Processed {} konten, total interest paid: {}",
                processedCount, totalInterestPaid);
    }

    /**
     * Processes manual Abrechnung without calculating today's daily interest.
     * This is called by the admin endpoint.
     */
    @Transactional
    public void processManualAbrechnung() {
        log.info("Starting manual interest Abrechnung (without today's daily calculation)");
        processQuarterlyAbrechnung();
    }
}