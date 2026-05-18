package de.tum.cit.aet.helios.workflow.queue.reconcile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rolls completed workflow_job rows for the previous closed hour into queue_wait_stat using
 * PERCENTILE_CONT, then UPSERTs. See plan §B5.
 */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class QueueWaitStatRollup {

  @PersistenceContext
  private EntityManager em;

  private static final String UPSERT_SQL = """
      INSERT INTO queue_wait_stat (
        repository_id, workflow_name, job_name, head_branch, label_set_hash,
        bucket_start, samples,
        queue_p50, queue_p90, queue_p95,
        run_p50, run_p90, run_p95
      )
      SELECT
        repository_id,
        workflow_name,
        name AS job_name,
        head_branch,
        label_set_hash,
        :bucketStart AS bucket_start,
        COUNT(*) AS samples,
        PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY queue_wait_seconds) AS queue_p50,
        PERCENTILE_CONT(0.9)  WITHIN GROUP (ORDER BY queue_wait_seconds) AS queue_p90,
        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY queue_wait_seconds) AS queue_p95,
        PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY run_duration_seconds) AS run_p50,
        PERCENTILE_CONT(0.9)  WITHIN GROUP (ORDER BY run_duration_seconds) AS run_p90,
        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY run_duration_seconds) AS run_p95
      FROM workflow_job
      WHERE status = 'completed'
        AND completed_at >= :bucketStart
        AND completed_at <  :bucketEnd
        AND queue_wait_seconds IS NOT NULL
      GROUP BY repository_id, workflow_name, name, head_branch, label_set_hash
      ON CONFLICT (repository_id, workflow_name, job_name, head_branch,
                   label_set_hash, bucket_start)
      DO UPDATE SET
        samples   = EXCLUDED.samples,
        queue_p50 = EXCLUDED.queue_p50,
        queue_p90 = EXCLUDED.queue_p90,
        queue_p95 = EXCLUDED.queue_p95,
        run_p50   = EXCLUDED.run_p50,
        run_p90   = EXCLUDED.run_p90,
        run_p95   = EXCLUDED.run_p95
      """;

  @Scheduled(fixedRateString = "${helios.queue.reconcile.rollup.fixedRateMs:300000}")
  @Transactional
  public void rollupPreviousHour() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime bucketEnd = now.truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime bucketStart = bucketEnd.minusHours(1);

    int rows = em.createNativeQuery(UPSERT_SQL)
        .setParameter("bucketStart", bucketStart)
        .setParameter("bucketEnd", bucketEnd)
        .executeUpdate();
    if (rows > 0) {
      log.info("QueueWaitStatRollup: upserted {} rows for bucket {}..{}", rows, bucketStart,
          bucketEnd);
    }
  }
}
