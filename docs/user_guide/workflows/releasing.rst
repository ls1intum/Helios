=================
Helios User Guide
=================

Overview
--------
This guide walks you through the process of drafting, testing, and publishing releases using Helios. It includes steps for generating release notes, testing releases on staging environments, and deploying to production.

Prerequisites
--------------
- GitHub account with at least maintainer permissions in the repository
- Available GitHub environments for deployment


Publishing Releases Workflow
----------------------------

The following steps describe the complete workflow for publishing a release in Helios. Each step is illustrated with screenshots for clarity.

1. Release Overview
~~~~~~~~~~~~~~~~~~~
The Releases page provides an overview of all releases, their status, and available actions.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/1-release-overview.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/1-release-overview.png" alt="Release Overview" style="max-width: 100%; height: auto;" />
   </a>

2. Create a New Release
~~~~~~~~~~~~~~~~~~~~~~~
To start a new release, click the "Create Release" button.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/2-create-release-button.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/2-create-release-button.png" alt="Create Release Button" style="max-width: 100%; height: auto;" />
   </a>

3. Select Tag and Branch
~~~~~~~~~~~~~~~~~~~~~~~~
Choose the tag and branch for the release. This ensures the correct code is included in the release.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/3-release-tag-branch.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/3-release-tag-branch.png" alt="Select Tag and Branch" style="max-width: 100%; height: auto;" />
   </a>

4. Fill in Release Details
~~~~~~~~~~~~~~~~~~~~~~~~~~
A modal will appear where you can enter the release title, description, and other details.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/4-create-release-modal.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/4-create-release-modal.png" alt="Create Release Modal" style="max-width: 100%; height: auto;" />
   </a>

5. Review Release Details
~~~~~~~~~~~~~~~~~~~~~~~~~
After filling in the details, review the information before proceeding. The following elements are highlighted in the screenshot below:

1. **Generate Release / Edit Button**
   - Use the **Generate Release** button to automatically generate release notes based on recent changes. If you wish to modify or create your own release notes, use the **Edit** button.

2. **Test Server Overview and Review Section**
   - **2a. Test Servers Overview**: This section provides a detailed overview of all test servers, showing which release is currently deployed on each. You can use the **Deploy** button to deploy and test the new release on a selected test environment.
   - **2b. Review Section**: Here, users can mark the release as either working or broken after testing it on a test environment. You can also add a comment to your review and see reviews submitted by other users.

3. **Publish Draft to GitHub**
   - Once you are satisfied with the release details and testing, use this button to publish the release as a draft to GitHub.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/5-release-details.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/5-release-details.png" alt="Release Details" style="max-width: 100%; height: auto;" />
   </a>

6. Draft Release on GitHub
~~~~~~~~~~~~~~~~~~~~~~~~~~
The release is now drafted on GitHub, where you can make further edits if necessary. To publish the release, also click the highlighted edit button.

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/6-github-draft-release.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/6-github-draft-release.png" alt="GitHub Draft Release" style="max-width: 100%; height: auto;" />
   </a>

Once you are satisfied with the draft, publish the release on GitHub as shown below:

.. raw:: html

   <a href="../../../_static/images/user_guide/releasing/7-github-publish release.png" target="_blank">
     <img src="../../../_static/images/user_guide/releasing/7-github-publish release.png" alt="GitHub Publish Release" style="max-width: 100%; height: auto;" />
   </a>

1. (Optional) Deploy to Production
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
After publishing, you can deploy the release to the production environment if required.


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