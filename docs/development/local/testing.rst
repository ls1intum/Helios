==========================
Unit Testing Documentation
==========================

This document provides guidelines for writing unit tests for both the server and client sides of the application.
We are using [JUnit](https://junit.org/junit5/) (as well as the built in spring tools) for the server-side testing infrastructure and [Vitest](https://vitest.dev/) for the client-side testing infrastructure.

There are three types of tests that are commonly used in software development:
1. **Unit Tests:** Tests that are written to test a small unit of code in a isolated way, such as a component or function. 
2. **Integration Tests:** Tests that are written to test the interaction between different components of the application, such as the interaction between the controller and the service layer.
3. **End-to-End Tests:** Tests that are written to test the application as a whole, such as the interaction between the client and the server.

Server-Side Unit Testing
------------------------

1. **How to write a unit test:**: We use JUnit and according spring tools to write unit tests for the server side of the application. Please refer to the documentation [here](https://www.baeldung.com/spring-boot-testing) or look at the examples below.

   Controller Layer Example:

   ```java
@AutoConfigureMockMvc
@WebMvcTest(BranchController.class) // These two annotations are used to test the controller layer and load only necessary context of the spring application
public class BranchControllerTest {
  @Autowired private MockMvc mockMvc;

  // Use @MockitoBean to mock the service layer to ensure the isolation of the controller layer
  @MockitoBean private BranchService branchService; 

  @Autowired private ObjectMapper objectMapper;

  private final List<BranchInfoDto> branches =
      List.of(
          new BranchInfoDto(
              "branch1",
              "sha1",
              0,
              0,
              false,
              false,
              null,
              null,
              new RepositoryInfoDto(1L, "repo", "repo", null, "url")),
          new BranchInfoDto("branch2", "sha2", 0, 0, false, false, null, null, null));

  // All tests should be annotated with @Test so the test loader knows which methods to run
  @Test
  void testGetBranchByExistingId() throws Exception {
    Long id = this.branches.get(0).repository().id();
    String branchName = this.branches.get(0).name();

    when(branchService.getBranchInfo(id, branchName)).thenReturn(Optional.of(branches.get(0)));
    ResultActions request =
        this.mockMvc
            .perform(
                get("/api/branches/repository/{repoId}/branch", id)
                    .param("name", branchName)
                    .with(user("user"))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk());

    assertEquals(
        objectMapper.readValue(
            request.andReturn().getResponse().getContentAsString(), BranchInfoDto.class),
        this.branches.get(0));
  }

  @Test
  void testGetBranchByNonExistingRepoId() throws Exception {
    Long id = -1L;
    String branchName = this.branches.get(0).name();

    when(branchService.getBranchInfo(id, branchName)).thenReturn(Optional.empty());
    this.mockMvc
        .perform(
            get("/api/branches/repository/{repoId}/branch", id)
                .param("name", branchName)
                .with(user("user"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isNotFound());
  }
  ```
2. **How to execute the tests locally**: 
In the application-server directory execute following command:
```bash
./gradlew test
```

Client-Side Unit Testing
------------------------

1. **When to write a unit test:**:
Client-Side unit testing should be done very conciously as it often is not worth the effort of writing tests or there are better ways to write code, so no/less tests are necessary.
You should always have this two rules in mind before starting writing a test:

- **What is the value of the test?** If the test is not providing any value or not testing any (complex) logic in the code, it is not worth writing the test. 
Often it is valuable to test logic in Pipes or Services, but there is not much to test in components itself if it does not provide complex functions itself.
- **Is there a better way to write the code?** Sometimes, writing the code in a different way can make it easier to test or even make the test unnecessary. 
For example moving common logic to a service or implementing filtering logic on the server side.

2. **How to write a unit test:**: We use Vitest to write unit tests for the client side of the application. Please refer to the documentation [here](https://vitest.dev/docs/getting-started) or look at the examples below.
Shallow Test Example (useful for testing only the isolated component): 

```typescript
describe('BranchDetailsComponent', () => {
  let component: BranchDetailsComponent;
  let fixture: ComponentFixture<BranchDetailsComponent>;

  beforeEach(async () => {
    // Create a testing module specifiying all necessary dependencies or mocks for the component
    await TestBed.configureTestingModule({
      imports: [BranchDetailsComponent],
      providers: [provideExperimentalZonelessChangeDetection(), provideNoopAnimations(), provideQueryClient(new QueryClient())],
    })
    // The overrideComponent method is used to override the component's imports, so not all of the child components are loaded as well (shallow testing)
    // This should be used for unit testing so the component is tested isolated. If integration testing a page, you can remove this override.
      .overrideComponent(BranchDetailsComponent, {
        set: { imports: [], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(BranchDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('branchName', 'branch');

    // Mock tanstack query data
    component.query = {
      ...component.query,
      data: signal({ name: 'branch', commitSha: '' }),
    };

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render pipeline component', async () => {
    // Check if child components get the correct input properties
    const pipelineComponent = fixture.debugElement.query(By.css('app-pipeline'));
    expect(pipelineComponent).toBeTruthy();
    expect(pipelineComponent.properties['selector']).toEqual({ branchName: 'branch', repositoryId: 1 });
  });
});
```

3. **How to execute the tests locally**:
In the client directory execute following command:
```bash
yarn
yarn test:unit
```