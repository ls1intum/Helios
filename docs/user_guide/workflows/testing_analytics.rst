=================
Helios User Guide
=================

Overview
--------
This guide walks you through the testing analytics Helios provides for your GitHub Actions workflows. It includes insights into test results, failure rates, and overall test reliability, helping you maintain high-quality code.


Prerequisites
--------------
- Pipeline and Tests already set up in GitHub and Helios

Testing Analytics
-----------------

1. Pull Request or Branch Selection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For more information on how to select a pull request or branch, refer to the
`Pull Request and Branch Selection Guide <pr_branch_selection.html>`_.

.. raw:: html

   <div class="admonition note"> 
     <p class="admonition-title">Note</p>
     <p>
       If your repository has a build workflow that must complete before deployment can start, please wait for it to finish.
     </p>
     <p>
       For example, in the
       <a href="https://github.com/ls1intum/Artemis" target="_blank" rel="noopener noreferrer">
         Artemis
       </a>
       repository, deployments for PRs and default branches rely on the <strong>build workflow</strong> completing successfully before the deployment workflow begins. For other branches deployed directly to test servers, the build is triggered <em>during</em> deployment. Therefore, deployments of non-default branches (not part of a PR to the default branch) may take more time.
     </p>
     <a href="../../../_static/images/user_guide/build-workflow-status.png" target="_blank">
       <img src="../../../_static/images/user_guide/build-workflow-status.png" alt="GitHub build workflow status example" style="height: 512px;" />
     </a>
   </div>

2. Gain Insights about the Pipeline
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To access pipeline-level insights for a selected branch or pull request:

- Navigate to the **Pipeline** section.
- Here, you will see a visual overview of the CI/CD stages:
  - **Build**
  - **E2E Tests**
  - **Unit/Integration Tests**
  - **CodeQL** (Static Code Analysis)

Each stage is marked with a green check or red cross to indicate success or failure. This helps you quickly identify if any part of the pipeline failed and requires attention.

.. raw:: html

   <a href="../../../_static/images/user_guide/1-pipeline-insights.png" target="_blank">
     <img src="../../../_static/images/user_guide/1-pipeline-insights.png" alt="CI/CD Pipeline Overview" style="max-width: 100%; height: auto;" />
   </a>



Clicking on the GitHub icon of a failed stage allows you to inspect the logs and debug the issue directly in your CI/CD environment.

1. Gain Insights about the Tests
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Helios provides a comprehensive test breakdown under the **Test Results** section:

- Total test cases executed
- Number of passed, failed, and skipped tests
- Runtime duration of the entire test suite

Use the **E2E Tests** and **Server Tests** tabs to filter by test type. Each test case is listed under its respective file path, making it easy to locate and debug issues.

.. raw:: html

   <a href="../../../_static/images/user_guide/2-test-analytics.png" target="_blank">
     <img src="../../../_static/images/user_guide/2-test-analytics.png" alt="Test Result Analytics" style="max-width: 100%; height: auto;" />
   </a>

To dive deeper into specific failures, expand the file entry. Helios will show the exact test case name, file, and line number that failed.

For example, in this case:

- File: `ProgrammingExerciseStaticCodeAnalysis.spec.ts`
- Failed test: *"Configures SCA grading and makes a successful submission with SCA errors"*

.. raw:: html

   <a href="../../../_static/images/user_guide/3-test-case-analytics.png" target="_blank">
     <img src="../../../_static/images/user_guide/3-test-case-analytics.png" alt="Test Case Failure Detail" style="max-width: 100%; height: auto;" />
   </a>

Helios also indicates historical test reliability:
- **Default branch failure rate**
- **All branches failure rate**

This allows you to prioritize fixing flaky or frequently failing tests.

.. tip::
   Always check if a test previously passed by checking the **"previously passed"** label. This can help distinguish between newly introduced bugs and recurring failures.

