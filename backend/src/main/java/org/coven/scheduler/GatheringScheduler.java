package org.coven.scheduler;

import org.coven.service.GatheringService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GatheringScheduler {
  private final GatheringService gathering;

  public GatheringScheduler(GatheringService gathering) {
    this.gathering = gathering;
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void midnight() {
    gathering.reconcile();
  }

  // Captures configurable window boundaries while nobody is browsing. Counts
  // always use exact start timestamps, even after a delayed tick or JVM restart.
  @Scheduled(fixedDelayString = "${GATHERING_TICK_MS:15000}", initialDelay = 10000)
  public void windows() {
    gathering.reconcile();
  }
}
