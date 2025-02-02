package de.tum.cit.aet.helios.http;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RateLimitInfo {
  private int limit;
  private int remaining;
  private int used;
  private Instant reset;
}