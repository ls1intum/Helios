=================
Helios User Guide
=================

Overview
--------
Helios is a deployment management tool that simplifies the process of deploying pull requests and branches to test environments. This guide walks you through the basic deployment workflow.

Prerequisites
--------------
- GitHub account with access to the repository
- Proper permissions to deploy
- Available test environment

Deployment Workflow
--------------------


1. Repository Selection
~~~~~~~~~~~~~~~~~~~~~~~~
Select the repository you want to deploy from the main dashboard.

.. raw:: html

   <a href="/_static/images/user_guide/1-select-repo.png" target="_blank">
     <img src="/_static/images/user_guide/1-select-repo.png" alt="Repository selection screen" style="height: 512px;" />
   </a>
   

2. Authentication
~~~~~~~~~~~~~~~~~~
Log in with your GitHub credentials.

.. raw:: html

   <a href="/_static/images/user_guide/2-login.png" target="_blank">
     <img src="/_static/images/user_guide/2-login.png" alt="Login with GitHub" style="height: 512px;" />
   </a>
   
   
.. raw:: html

   <a href="/_static/images/user_guide/3-login-gh.png" target="_blank">
     <img src="/_static/images/user_guide/3-login-gh.png" alt="GitHub authentication" style="height: 512px;" />
   </a>
   

3. Pull Request Selection
~~~~~~~~~~~~~~~~~~~~~~~~~~
Choose the pull request or branch you want to deploy.

.. raw:: html

   <a href="/_static/images/user_guide/4-open-pr.png" target="_blank">
     <img src="/_static/images/user_guide/4-open-pr.png" alt="Pull request details screen" style="height: 512px;" />
   </a>
   

4. Deployment Initiation
~~~~~~~~~~~~~~~~~~~~~~~~~
Click the deploy button to start the deployment process.

.. raw:: html

   <a href="/_static/images/user_guide/5-deploy.png" target="_blank">
     <img src="/_static/images/user_guide/5-deploy.png" alt="Deploy button and options" style="height: 512px;" />
   </a>
   


The deployment enters a pending state while resources are being allocated.

.. raw:: html

   <a href="/_static/images/user_guide/6-deployment-pending.png" target="_blank">
     <img src="/_static/images/user_guide/6-deployment-pending.png" alt="Deployment pending status" style="height: 512px;" />
   </a>


The deployment moves to the in-progress state during active deployment.

.. raw:: html

   <a href="/_static/images/user_guide/7-deployment-in-progress.png" target="_blank">
     <img src="/_static/images/user_guide/7-deployment-in-progress.png" alt="Deployment progress status" style="height: 512px;" />
   </a>

5. Unlock the test environment once you finish testing.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. raw:: html

   <a href="/_static/images/user_guide/8-unlock.png" target="_blank">
     <img src="/_static/images/user_guide/8-unlock.png" alt="Unlock test environment" style="height: 512px;" />
   </a>


Troubleshooting
----------------

Manual Deployment Fallback
~~~~~~~~~~~~~~~~~~~~~~~~~~~~
⚠️ **Warning**: Manual workflow deployment should be used only as a last resort as it may conflict with Helios environment locking.

If Helios deployment fails, you can use the GitHub workflow as a fallback:

Access the workflow at:
   https://github.com/ls1intum/Artemis/actions/workflows/testserver-deployment.yml

.. raw:: html

   <a href="/_static/images/user_guide/github-deployment.png" target="_blank">
     <img src="/_static/images/user_guide/github-deployment.png" alt="GitHub deployment workflow" style="height: 512px;" />
   </a>

Required Inputs
*****************
- **Use workflow from**: Select your target branch
- **Which branch to deploy**: Again, select your target branch
- **Which environment to deploy**: Use format ``artemis-test7.artemis.cit.tum.de``
- **Username** (optional): Your GitHub username


Support
-------
For assistance:

- Join the Helios Support Slack channel https://ls1tum.slack.com/archives/C08BPLNT8FL
- Report a new issue if you encounter a bug or need help https://github.com/ls1intum/Helios/issues/new/choose