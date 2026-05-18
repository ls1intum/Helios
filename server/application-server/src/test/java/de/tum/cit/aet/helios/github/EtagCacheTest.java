package de.tum.cit.aet.helios.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EtagCacheTest {

  @Test
  void putThenGetEtagAndBody() {
    EtagCache cache = new EtagCache();
    cache.put("/runners", "\"abc\"", "payload-1");
    assertTrue(cache.getEtag("/runners").isPresent());
    assertEquals("\"abc\"", cache.getEtag("/runners").get());
    assertEquals("payload-1", cache.getBody("/runners", String.class).get());
  }

  @Test
  void missesAreEmpty() {
    EtagCache cache = new EtagCache();
    assertFalse(cache.getEtag("/missing").isPresent());
    assertFalse(cache.getBody("/missing", String.class).isPresent());
  }

  @Test
  void bodyTypeMismatchReturnsEmpty() {
    EtagCache cache = new EtagCache();
    cache.put("/k", "\"abc\"", "string-body");
    assertFalse(cache.getBody("/k", Integer.class).isPresent(),
        "wrong type should miss rather than ClassCastException");
  }

  @Test
  void invalidateClearsEntry() {
    EtagCache cache = new EtagCache();
    cache.put("/k", "\"abc\"", "body");
    cache.invalidate("/k");
    assertFalse(cache.getEtag("/k").isPresent());
  }

  @Test
  void putOverwritesPreviousEntry() {
    EtagCache cache = new EtagCache();
    cache.put("/k", "\"v1\"", "body-1");
    cache.put("/k", "\"v2\"", "body-2");
    assertEquals("\"v2\"", cache.getEtag("/k").get());
    assertEquals("body-2", cache.getBody("/k", String.class).get());
  }
}
