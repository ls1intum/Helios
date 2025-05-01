=================
Helios User Guide
=================

Overview
--------
Helios is a deployment management tool that simplifies the process of deploying pull requests and branches to your environments. This guide walks you through the basic deployment workflow.

Prerequisites
--------------
- GitHub account with access to the repository
  (should have at least **WRITE** permissions)
- Available GitHub environments
  (configured in the repository’s **Settings > Environments** section)

Deployment Workflow
--------------------


1. Repository Selection
~~~~~~~~~~~~~~~~~~~~~~~~
.. raw:: html

   <p>
     Select the repository you want to deploy from the
     <a href="https://helios.aet.cit.tum.de" target="_blank" rel="noopener noreferrer">
       Helios home page
     </a>.
   </p>

.. raw:: html

   <a href="../../../_static/images/user_guide/select-repo.png" target="_blank">
     <img src="../../../_static/images/user_guide/select-repo.png" alt="Repository selection screen" style="height: 512px;" />
   </a>
   

2. Authentication
~~~~~~~~~~~~~~~~~~
- Log in with your GitHub account.

.. raw:: html

   <a href="../../../_static/images/user_guide/login.png" target="_blank">
     <img src="../../../_static/images/user_guide/login.png" alt="Login with GitHub" style="height: 512px;" />
   </a>
   
   
.. raw:: html

   <a href="../../../_static/images/user_guide/login-gh.png" target="_blank">
     <img src="../../../_static/images/user_guide/login-gh.png" alt="GitHub authentication" style="height: 512px;" />
   </a>
   

3. Pull Request Selection
~~~~~~~~~~~~~~~~~~~~~~~~~~
- Choose the pull request or branch you want to deploy.

.. raw:: html

   <a href="../../../_static/images/user_guide/select-pr.png" target="_blank">
     <img src="../../../_static/images/user_guide/select-pr.png" alt="Pull request table screen" style="height: 512px;" />
   </a>

.. raw:: html

   <a href="../../../_static/images/user_guide/open-pr.png" target="_blank">
     <img src="../../../_static/images/user_guide/open-pr.png" alt="Pull request details screen" style="height: 512px;" />
   </a>
   
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


4. Deployment Initiation
~~~~~~~~~~~~~~~~~~~~~~~~~
- Click the deploy button to start the deployment process.

.. raw:: html

   <a href="../../../_static/images/user_guide/deploy.png" target="_blank">
     <img src="../../../_static/images/user_guide/deploy.png" alt="Deploy button and options" style="height: 512px;" />
   </a>
   


- The deployment enters a pending state. This is the initial phase that begins immediately after the workflow is triggered.

During this phase, the workflow may:

- Check whether a successful build for the selected branch or commit already exists.
- Run the build pipeline before proceeding to the actual deployment.
- Wait in the GitHub Actions queue if there is high activity in the repository (e.g., many workflows running concurrently).

Depending on the repository setup and GitHub's action runners, this phase may take varying amounts of time.

.. raw:: html

   <a href="../../../_static/images/user_guide/deployment-pending.png" target="_blank">
     <img src="../../../_static/images/user_guide/deployment-pending.png" alt="Deployment pending status" style="height: 512px;" />
   </a>


- The deployment moves to the in-progress state during active deployment.

.. raw:: html

   <a href="../../../_static/images/user_guide/deployment-in-progress.png" target="_blank">
     <img src="../../../_static/images/user_guide/deployment-in-progress.png" alt="Deployment progress status" style="height: 512px;" />
   </a>

- Once deployment is complete, the workflow should display a green check mark indicating success, and the server's status should also appear as healthy.

.. raw:: html

   <a href="../../../_static/images/user_guide/deployment-success.png" target="_blank">
     <img src="../../../_static/images/user_guide/deployment-success.png" alt="Successful deployment with green check" style="height: 512px;" />
   </a>

5. Unlock the environment once you finish testing.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. raw:: html

   <a href="../../../_static/images/user_guide/unlock.png" target="_blank">
     <img src="../../../_static/images/user_guide/unlock.png" alt="Unlock test environment" style="height: 512px;" />
   </a>


Troubleshooting
----------------

Manual Deployment Fallback
~~~~~~~~~~~~~~~~~~~~~~~~~~~~
.. raw:: html

   <div class="admonition warning">
     <p class="admonition-title">Warning ⚠️</p>
     <p>
       Manual workflow deployment should be used only as a last resort, as it may conflict with Helios' environment locking mechanism.
     </p>
     <p>
       If Helios is temporarily unresponsive, inaccessible, or experiencing issues that prevent you from triggering deployments, you can fall back to manually triggering a GitHub Actions workflow.
     </p>
     <p>
       This fallback mechanism requires a working CI/CD pipeline in your repository. The exact workflow name and configuration may vary across repositories.
     </p>
     <p>
       As an example, in the
       <a href="https://github.com/ls1intum/Artemis" target="_blank" rel="noopener noreferrer">Artemis</a>
       repository, deployments to test servers and staging/production environments are handled by two separate workflows:
     </p>
     <ul>
       <li><a href="https://github.com/ls1intum/Artemis/actions/workflows/testserver-deployment.yml" target="_blank" rel="noopener noreferrer">Test Server Deployment Workflow</a></li>
       <li><a href="https://github.com/ls1intum/Artemis/actions/workflows/prod-like-deployment.yml" target="_blank" rel="noopener noreferrer">Staging/Production Deployment Workflow</a></li>
     </ul>
   </div>

.. raw:: html

   <a href="../../../_static/images/user_guide/github-deployment.png" target="_blank">
     <img src="../../../_static/images/user_guide/github-deployment.png" alt="GitHub deployment workflow" style="height: 512px;" />
   </a>

Required Inputs
*****************
- **Use workflow from**: Select your target branch
- **Which branch to deploy**: Again, select your target branch
- **Commit SHA to deploy**: Write the commit SHA you want to deploy
- **Which environment to deploy**: Use format ``artemis-test7.artemis.cit.tum.de``
- **Username**: Your GitHub username

.. note::

   Some of the input fields listed above may be optional depending on how the deployment workflow is implemented in your repository.
   Each repository defines its own required and optional inputs, so refer to the workflow YAML file or the GitHub Actions UI to understand which fields are used.

Support
-------
For assistance:

.. raw:: html

   <ul>
     <li>
       Join the
       <a href="https://ls1tum.slack.com/archives/C08BPLNT8FL" target="_blank" rel="noopener noreferrer">
         Helios Support Slack channel
       </a>
     </li>
     <li>
       Report issues or bugs via the
       <a href="https://github.com/ls1intum/Helios/issues/new/choose" target="_blank" rel="noopener noreferrer">
         GitHub issue tracker
       </a>
     </li>
   </ul>