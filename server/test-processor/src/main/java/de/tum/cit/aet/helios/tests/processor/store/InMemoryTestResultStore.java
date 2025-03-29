package de.tum.cit.aet.helios.tests.processor.store;

import de.tum.cit.aet.helios.common.dto.test.TestSuite;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class InMemoryTestResultStore {
  private final CacheManager cacheManager;

  public String store(Long workflowRunId, List<TestSuite> testSuites) {
    String key = "test-result:" + workflowRunId;
    Cache cache = cacheManager.getCache("testResults");
    if (cache != null) {
      cache.put(key, testSuites);
    }
    return key;
  }

  @Cacheable(cacheNames = "testResults", key = "#key")
  public Optional<List<TestSuite>> get(String key) {
    Cache cache = cacheManager.getCache("testResults");
    if (cache != null) {
      Cache.ValueWrapper wrapper = cache.get(key);
      if (wrapper != null) {
        @SuppressWarnings("unchecked")
        List<TestSuite> testSuites = (List<TestSuite>) wrapper.get();
        return Optional.ofNullable(testSuites);
      }
    }
    return Optional.empty();
  }
}
