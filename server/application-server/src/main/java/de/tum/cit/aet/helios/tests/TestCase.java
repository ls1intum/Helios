package de.tum.cit.aet.helios.tests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "test_suite_id")
  private TestSuite testSuite;

  @Column(nullable = false)
  private String name;

  public void setName(String name) {
    this.name = name.length() > 255 ? name.substring(0, 255) : name;
  }

  @Column(nullable = false, name = "class_name")
  private String className;

  public void setClassName(String className) {
    this.className = className.length() > 255 ? className.substring(0, 255) : className;
  }

  @Column(nullable = false)
  private Double time;

  @Enumerated(EnumType.STRING)
  private TestStatus status;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(name = "stack_trace", columnDefinition = "TEXT")
  private String stackTrace;

  @Column(name = "error_type")
  private String errorType;

  public static enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
  }
}
