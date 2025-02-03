package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.user.User;
import java.io.Serializable;
import java.util.Objects;

public class TagEvaluationId implements Serializable {

  private Tag tag;
  private User evaluatedBy;

  public TagEvaluationId() {}

  public TagEvaluationId(Tag tag, User evaluatedBy) {
    this.tag = tag;
    this.evaluatedBy = evaluatedBy;
  }

  // Equals and hashCode
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TagEvaluationId tagEvalId = (TagEvaluationId) o;
    return Objects.equals(tag, tagEvalId.tag) && Objects.equals(evaluatedBy, tagEvalId.evaluatedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag, evaluatedBy);
  }
}
