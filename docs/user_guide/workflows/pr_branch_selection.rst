=================
Helios User Guide
=================

Overview
--------
Helios brings clarity and control to GitHub Actions based CI/CD workflows. Named after the Greek god of the sun—symbolizing insight, reliability, and visibility—Helios offers:

🌟 Unified Dashboard
All your branches, PRs, builds, test suites, and deployments in one canvas. Helios automatically syncs with GitHub, so you always see the latest activity.

🚀 Smooth Deployment Flow
Pick from available environments with a friendly UI. Helios locks your chosen environment, prevents collisions, and displays live progress until tests finish or a timeout is reached.

🔍 Test Insights
No more scrolling through raw logs. Helios ingests structured reports (JUnit XML) so you can click into individual test suites and cases—see pass/fail, error messages, and log snippets. Track flaky tests across branches over time.

📦 Release Management
Draft, review, and publish release candidates without leaving Helios. Automatically generate release notes, tag your release, and see everything in one timeline.

Prerequisites
--------------
- At least one connected (open-source) GitHub repository with at least one branch or pull request.

Select Workspace Workflow
--------------------


1. Repository Selection
~~~~~~~~~~~~~~~~~~~~~~~~
.. raw:: html

   <p>
     Select the repository you want to take actions on from the
     <a href="https://helios.aet.cit.tum.de" target="_blank" rel="noopener noreferrer">
       Helios home page
     </a>.
   </p>

.. raw:: html

   <a href="../../../_static/images/user_guide/select-repo.png" target="_blank">
     <img src="../../../_static/images/user_guide/select-repo.png" alt="Repository selection screen" style="height: 512px;" />
   </a>

2a. Pull Request Selection
~~~~~~~~~~~~~~~~~~~~~~~~~~
- Choose the pull request you want to action on.

.. raw:: html

   <a href="../../_static/images/user_guide/select-pr.png" target="_blank">
     <img src=" ../../_static/images/user_guide/select-pr.png" alt="Pull request table screen" style="height: 512px;" />
   </a>

.. raw:: html

   <a href="../../../_static/images/user_guide/open-pr.png" target="_blank">
     <img src="../../../_static/images/user_guide/open-pr.png" alt="Pull request details screen" style="height: 512px;" />
   </a>
   
2b. Branch Selection
~~~~~~~~~~~~~~~~~~~~~~~~~~
- Choose the branch you want to action on.

.. raw:: html

   <a href="../../../_static/images/user_guide/select-branch.png" target="_blank">
     <img src="../../../_static/images/user_guide/select-branch.png" alt="Branch   table screen" style="height: 512px;" />
   </a>

.. raw:: html

   <a href="../../../_static/images/user_guide/open-branch.png" target="_blank">
     <img src="../../../_static/images/user_guide/open-branch.png" alt="Branch details screen" style="height: 512px;" />
   </a>

Troubleshooting
----------------

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