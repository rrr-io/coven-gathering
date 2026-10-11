package org.coven.scheduler;

import org.coven.service.RaceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RaceScheduler {
  private final RaceService race;

  public RaceScheduler(RaceService race) {
    this.race = race;
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void midnight() {
    race.reconcile();
  }

  // Captures configurable window boundaries while nobody is browsing. Counts
  // always use exact start timestamps, even after a delayed tick or JVM restart.
  @Scheduled(fixedDelayString = "${RACE_TICK_MS:15000}", initialDelay = 10000)
  public void windows() {
    race.reconcile();
  }
}
