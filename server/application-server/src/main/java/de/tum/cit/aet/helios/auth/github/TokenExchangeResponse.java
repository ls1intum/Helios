package de.tum.cit.aet.helios.auth.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenExchangeResponse {
  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private Integer expiresIn;

  @JsonProperty("refresh_expires_in")
  private Integer refreshExpiresIn;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("not-before-policy")
  private Integer notBeforePolicy;

  @JsonProperty("scope")
  private String scope;
}
