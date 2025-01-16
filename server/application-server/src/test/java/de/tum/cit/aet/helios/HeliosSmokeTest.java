package de.tum.cit.aet.helios;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.branch.BranchController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = HeliosApplication.class)
class HeliosSmokeTest {

  @Autowired BranchController branchController;

  @Test
  void contextLoads() {
    assertThat(branchController).isNotNull();
  }
}
