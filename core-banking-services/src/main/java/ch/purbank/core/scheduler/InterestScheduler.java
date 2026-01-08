package ch.purbank.core.scheduler;

import ch.purbank.core.service.InterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterestScheduler {

    private final InterestService interestService;

    /**
     * Runs nightly at 23:59 (11:59 PM) to calculate daily interest for all konten.
     * Cron format: second minute hour day month weekday
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void runDailyInterestCalculation() {
        log.info("=== Starting scheduled nightly interest calculation ===");
        try {
            interestService.calculateDailyInterest();
            log.info("=== Nightly interest calculation completed successfully ===");
        } catch (Exception e) {
            log.error("Error during nightly interest calculation", e);
        }
    }

    /**
     * Runs quarterly on the last day of each quarter at 23:59.
     * Quarters end on: March 31, June 30, September 30, December 31
     * Cron format: second minute hour day month weekday
     * This runs on the 31st of March, June, September, and December, plus 30th of June and September
     *
     * Note: Using three separate cron expressions to handle end of quarters properly
     */
    @Scheduled(cron = "0 59 23 31 3 *")  // March 31 at 23:59
    public void runQuarterlyAbrechnungQ1() {
        runQuarterlyAbrechnung();
    }

    @Scheduled(cron = "0 59 23 30 6 *")  // June 30 at 23:59
    public void runQuarterlyAbrechnungQ2() {
        runQuarterlyAbrechnung();
    }

    @Scheduled(cron = "0 59 23 30 9 *")  // September 30 at 23:59
    public void runQuarterlyAbrechnungQ3() {
        runQuarterlyAbrechnung();
    }

    @Scheduled(cron = "0 59 23 31 12 *") // December 31 at 23:59
    public void runQuarterlyAbrechnungQ4() {
        runQuarterlyAbrechnung();
    }

    private void runQuarterlyAbrechnung() {
        log.info("=== Starting scheduled quarterly interest Abrechnung ===");
        try {
            // First, calculate today's daily interest
            interestService.calculateDailyInterest();
            log.info("Daily interest for today calculated");

            // Then process the quarterly Abrechnung
            interestService.processQuarterlyAbrechnung();
            log.info("=== Quarterly Abrechnung completed successfully ===");
        } catch (Exception e) {
            log.error("Error during quarterly Abrechnung", e);
        }
    }
}