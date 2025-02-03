package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHDeploymentStatus;

@Log4j2
@Entity
@Table(name = "deployment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Deployment extends BaseGitServiceEntity {
  @ManyToOne
  @JoinColumn(name = "environment_id", nullable = false)
  private Environment environment;

  // PR associated with this deployment
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pull_request_id")
  private PullRequest pullRequest;

  @Column(name = "node_id")
  private String nodeId;

  private String url;

  // Enum to represent the current state of the deployment
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = true)
  private State state = State.UNKNOWN;

  @Column(name = "statuses_url")
  private String statusesUrl;

  private String sha;

  private String ref;

  private String task;

  private String environmentName;

  @ManyToOne
  @JoinColumn(name = "user_id")
  private User creator;

  @Column(name = "repository_url")
  private String repositoryUrl;

  // payload field is just empty JSON object

  public enum State {
    PENDING,
    WAITING,
    SUCCESS,
    ERROR,
    FAILURE,
    IN_PROGRESS,
    QUEUED,
    INACTIVE,
    UNKNOWN; // Fallback for unmapped states
  }

  /**
   * Maps a GHDeploymentStatus object to a State enum.
   *
   * @param ghDeploymentStatus The GHDeploymentStatus object.
   * @return The State enum.
   */
  public static State mapToState(GHDeploymentStatus ghDeploymentStatus) {
    // org.kohsuke.github.GHDeploymentStatus didn't implement the state WAITING
    // So calling ghDeploymentStatus.getState() will throw an exception if the state is WAITING
    // Exception message: No enum constant org.kohsuke.github.GHDeploymentState.WAITING
    if (isWaitingState(ghDeploymentStatus)) {
      return State.WAITING;
    }

    return switch (ghDeploymentStatus.getState()) {
      case PENDING -> State.PENDING;
      case SUCCESS -> State.SUCCESS;
      case ERROR -> State.ERROR;
      case FAILURE -> State.FAILURE;
      case IN_PROGRESS -> State.IN_PROGRESS;
      case QUEUED -> State.QUEUED;
      case INACTIVE -> State.INACTIVE;
      default -> State.UNKNOWN;
    };
  }

  /**
   * Checks if the GHDeploymentStatus object is in the WAITING state.
   *
   * @param ghDeploymentStatus The GHDeploymentStatus object.
   * @return True if the state is WAITING, false otherwise.
   */
  private static boolean isWaitingState(GHDeploymentStatus ghDeploymentStatus) {
    return extractRawState(ghDeploymentStatus).equalsIgnoreCase("WAITING");
  }

  /**
   * Extracts the raw state from the GHDeploymentStatus object's toString() method.
   * If any error occurs, it returns an empty string.
   * The returned state is in uppercase.
   *
   * @param ghDeploymentStatus The GHDeploymentStatus object.
   * @return The raw state as a string in uppercase.
   */
  private static String extractRawState(GHDeploymentStatus ghDeploymentStatus) {
    try {
      String toStringValue = ghDeploymentStatus.toString();
      // Use a regex to extract the value of the `state` field
      Pattern pattern = Pattern.compile("state=([a-zA-Z_]+)(?:,|\\])");
      Matcher matcher = pattern.matcher(toStringValue);
      if (matcher.find()) {
        return matcher.group(1).toUpperCase();
      }
      log.warn(
          "Failed to extract raw state from GHDeploymentStatus object: "
              + "state field not found in toString() output");
    } catch (PatternSyntaxException e) {
      log.error(
          "Failed to extract raw state from GHDeploymentStatus object: Due to pattern syntax error",
          e);
    } catch (Exception e) {
      log.error("Error while extracting raw state from GHDeploymentStatus object", e);
    }

    return "";
  }
}
