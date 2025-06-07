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

3. Gain Insights about the Tests
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

