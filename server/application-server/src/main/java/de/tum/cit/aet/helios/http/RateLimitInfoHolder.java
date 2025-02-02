package de.tum.cit.aet.helios.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class RateLimitInfoHolder {
  private volatile RateLimitInfo latestRateLimitInfo;
}