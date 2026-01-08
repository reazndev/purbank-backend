package ch.purbank.core.scheduler;

import ch.purbank.core.service.InterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterestScheduler {

    private final InterestService interestService;

    /**
     * Runs nightly at 23:59 (11:59 PM) to calculate daily interest for all konten.
     * After calculating daily interest, checks if today is a quarter-end date.
     * If it is, automatically triggers the quarterly Abrechnung.
     *
     * Quarter-end dates: March 31, June 30, September 30, December 31
     *
     * Cron format: second minute hour day month weekday
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void runDailyInterestCalculation() {
        LocalDate today = LocalDate.now();
        log.info("=== Starting scheduled nightly interest calculation for {} ===", today);

        try {
            // Always calculate daily interest first
            interestService.calculateDailyInterest();
            log.info("=== Nightly interest calculation completed successfully ===");

            // Check if today is a quarter-end date
            if (isQuarterEndDate(today)) {
                log.info("=== Today is a quarter-end date, starting Abrechnung ===");
                interestService.processQuarterlyAbrechnung();
                log.info("=== Quarterly Abrechnung completed successfully ===");
            }
        } catch (Exception e) {
            log.error("Error during nightly interest calculation or Abrechnung", e);
        }
    }

    /**
     * Checks if the given date is a quarter-end date.
     * Quarter-end dates are: March 31, June 30, September 30, December 31
     */
    private boolean isQuarterEndDate(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        return (month == 3 && day == 31) ||   // Q1: March 31
                (month == 6 && day == 30) ||   // Q2: June 30
                (month == 9 && day == 30) ||   // Q3: September 30
                (month == 12 && day == 31);    // Q4: December 31
    }
}