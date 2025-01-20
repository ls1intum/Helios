package de.tum.cit.aet.helios.error;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A DTO that represents a standardized error response returned to the client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ApiError {
  private int status;
  private String error;
  private String message;
  private String path;
  private Instant timestamp;
}
