

Eclipse Tools for Cloud Foundry (CFT) is a plugin that enables Cloud Foundry
users to deploy and manage Java and Spring applications on a Cloud Foundry
instance from Eclipse.

The plugin supports Eclipse v4.3 and higher (a Java EE version is recommended), and requires a
Java 7 execution runtime.

This page has instructions for installing and using v1.0.0 of the plugin.

You can use the plugin to perform the following actions:

* Deploy application projects from an Eclipse workspace to a running Cloud Foundry instance. CFT supports the following project types:
  * Java Web
  * Java standalone
  * Spring Boot
  * Spring
* Create, bind, and unbind services.
* View and manage deployed applications and services.
* Start and stop applications.
* For certain Cloud vendors, like Pivotal Cloud Foundry (PCF), Pivotal Web Services (PWS), and IBM BlueMix, application debugging.


## <a id='install'></a>Install CFT ##

Your Eclipse may already have CFT installed, as it is included in certain distributions. 

However, CFT is the successor of the old "Cloud Foundry Eclipse" tools. CFT is now an Eclipse project and has different bundle IDs that start with:
org.eclipse.cft.server, and version 1.0.0. 

The older "Cloud Foundry Eclipse" may be versioned higher (e.g. 1.8.3) than CFT, but have different
bundle IDs: org.cloudfoundry.ide.eclipse.server, and therefore are considered separate, incompatible tools to the current CFT.

Consequently, if you already have the old Cloud Foundry Eclipse (org.cloudfoundry.ide.eclipse.server) with higher version than 1.0.0, you still need
to install CFT, as it is now a separate tool from the older version.

Uninstalling the old version is done automatically when upgrading to CFT, or you can do it manually by:

1. Choose **About Eclipse** (or **About Spring Tool Suite**) from the Eclipse (or **Spring Tool Suite**) menu and click **Installation Details**.
1. In **Installation Details**, select the previous version of the plugin and click **Uninstall**.


* [Install to Eclipse from Marketplace](#install-to-eclipse)

### <a id='install-to-eclipse'></a>Install to Eclipse from Marketplace ###

Follow the instructions below to install the Cloud Foundry Eclipse Plugin to
Eclipse from the Eclipse Marketplace.

1. Start Eclipse.
1. From the Eclipse **Help** menu, select **Eclipse Marketplace**.
1. In the Eclipse Marketplace window, enter "Cloud Foundry" in the **Find** field. Click **Go**.
1. In the search results, next to the listing for Eclipse Tools for Cloud Foundry, click **Install**.
1. In the **Confirm Selected Features** window, click **Confirm**.
1. The **Review Licenses** window appears. Select "I accept the terms of the license agreement" and click **Finish**.
1. The **Software Updates** window appears. Click **Yes** to restart Eclipse.

### <a id='install-offline'></a>Install a Release Version Offline ###
If you need to install a release version of CFT in offline mode, you can download a release update site zip file and transfer it to the offline environment.

To install a Release Version offline, follow the steps below on a computer
running Eclipse:

1. Browse to [https://projects.eclipse.org/projects/ecd.cft/downloads](https://projects.eclipse.org/projects/ecd.cft/downloads) and download a release update site zip file.

1. In Eclipse, select **Install New Software** from the **Help** menu.

1. In the **Available Software** window, to the right of the **Work with**
field, click **Add**.

1. In the **Add Repository** window, enter `CFT` or a name
of your choice for the repository. Click **Archive**.

1. In the **Open** window, browse to the location of the update site zip file
and click **Open**.

1. In the **Add Repository** window, click **OK**.

1. In the **Available Software** window, select **Core** and **UI** features. Both are mandatory. Click **Next**.

1. In the **Review Licenses** window, select "I accept the terms of the license agreement" and click **Finish**.


## <a id='plugin-ui'></a>About the Plugin User Interface ##

The sections below describe the CFT user interface, and will use Pivotal Web Services as an example. The Eclipse Servers view is one of the primary UI entry points. To expose the Servers view, then select **Window > Show View > Other > Server > Servers**.

The Cloud Foundry editor, outlined in red in the screenshot below, is the primary plugin user interface.
Some workflows involve interacting with standard elements of the Eclipse user interface, such as the **Project Explorer** and the **Console** and **Servers** views.

Note that the Cloud Foundry editor allows you to work with a single Cloud Foundry space. Each space is represented by a distinct server instance in the **Servers** view (B). Multiple editors, each targeting a different space, can be open simultaneously. However, only one editor targeting a particular Cloud Foundry server instance can be open at a time.

### <a id='overview-tab'></a>Overview Tab ###

The follow panes and views are present when the **Overview** tab is selected:

* A --- The **Package Explorer** view lists the projects in the current workspace.
* B -- The **Servers** view lists server instances configured in the current workspace. A server of type **Pivotal Cloud Foundry&reg;** represents a targeted space in a Cloud Foundry instance.
* C -- The **General Information** pane.
* D -- The **Account Information** pane lists your Cloud Foundry credentials and the target organization and space. The pane includes these controls:
    * **Clone Server** --- Use to create additional Pivotal Cloud Foundry&reg; server instances. You must configure a server instance for each Cloud Foundry space that you wish to target. For more information, see [Create Additional Server Instances](#clone).
    * **Change Password** --- Use to change your Cloud Foundry password.
    * **Validate Account** --- Use to verify your currently configured Cloud Foundry credentials.
* E -- The **Server Status** pane shows whether or not you are connected to the target Cloud Foundry space, and the **Disconnect** and **Connect** controls.
* F -- The **Console** view displays status messages when you perform an action such as deploying an application.
* G -- The **Remote Systems** view allows you to view the contents of a file that is part of a deployed application. For more information, see [View an Application File](#view-file).

 <%= image_tag("sts/ui-overview-tab.png", :width =>"1150") %>

### <a id='apps-services-tab'></a>Applications and Services Tab ###

The follow panes are present when the **Applications and Services** tab is selected:

* H --- The **Applications** pane lists the applications deployed to the target space.
* I --- The **Services** pane lists the services provisioned in the targeted space.
* J --- The **General** pane displays the following information for the application currently selected in the **Applications** pane:
      * **Name**
      * **Mapped URLs** -- Lists URLs mapped to the application. You can click a URL to open a browser to the application within Eclipse or STS, and click the pencil icon to add or remove mapped URLs. See [Manage Application URLs](#manage-app-urls).
      * **Memory Limit** -- The amount of memory allocated to the application. You can use the pull-down to change the memory limit.
      * **Instances** -- The number of instances of the application that are deployed. You can use the pull-down to change number of instances.
      * **Start**, **Stop**, **Restart**, **Update and Restart** --- The controls that appear depend on the current state of the application. The **Update and Restart** command will attempt an incremental push of only those application resources that have changed. It will not perform a full application push. See [Push Application Changes](#push-changes) below.

* K --- The **Services** pane lists services that are bound to the application currently selected in the **Applications** pane. The icon in the upper right corner of the pane allows you to create a service, as described in [Create a Service](#create-service).

 <%= image_tag("sts/ui-apps-services-tab.png", :width =>"1150") %>

## <a id='cloud-foundry-server'></a>Create a Cloud Foundry Server ##

This section contains instructions for configuring a server resource that will represent a target Cloud Foundry space. You will create a server for each space in Cloud Foundry to which you will deploy applications. Once you create your first Cloud Foundry service instances using the instructions below, you can create additional instances using the [Clone Server](#clone) feature.

1. Right-click the **Servers** view and select **New > Server**.
1. In the **Define a New Server** window, expand the **Pivotal** folder, select **Cloud Foundry**, and click **Next**.

    <p class="note"><strong>Note</strong>: Do not modify default values for
    <strong>Server host name</strong> or <strong>Server Runtime
    Environment</strong>. These fields are not used</p>

    <%= image_tag("sts/define-new-server.png") %>

1. In the **Cloud Foundry Account** window, if you already have a Pivotal
    Cloud Foundry&reg; Hosted Developer Edition account, enter your email
    account and password credentials and click **Validate Account**.

    <p class="note"><strong>Note</strong>: By default, the <strong>URL</strong>
    field points to the Pivotal Cloud Foundry&reg; Hosted Developer Edition URL
    of https://api.run.pivotal.io. <%=vars.console_2%></p>

    If you do not have a Cloud Foundry account and want to register a new
    Pivotal Cloud Foundry&reg; Hosted Developer Edition account, click **Sign Up**. After you create the account, you can complete this procedure.

    <p class="note"><strong>Note</strong>: The <strong>Register Account</strong>
    button is inactive.</p>

    <%= image_tag("sts/cloud-foundry-account.png") %>

1. The **Cloud Foundry Account** window is refreshed and displays a message indicating whether or not your credentials were valid. Click **Next**.

    <%= image_tag("sts/new-server.png") %>

1. In the **Organizations and Spaces** window, select the space that you want to target, and click **Finish**.

    <p class="note"><strong>Note</strong>: If you do not select a space, the
    server will be configured to connect to the default space, which is the
    first encountered in a list of your spaces.</p>

    <%= image_tag("sts/orgs-and-spaces.png") %>

1. Once you have successfully configured the Pivotal Cloud Foundry&reg; server, it will appear in the **Servers** view of the Eclipse or STS user interface. To familiarize yourself with the plugin user interface, see [About the Plugin User Interface](#plugin-ui). Following this step, proceed to [Deploy an Application](#deploy-an-application).

## <a id='deploy-an-application'></a>Deploy an Application ##

To deploy an application to Cloud Foundry using the plugin:

1. To initiate deployment either:
  * Drag the application from the **Package Explorer** view onto the Pivotal Cloud Foundry&reg; server in the **Servers** view, or
  * Right-click the Pivotal Cloud Foundry&reg; server in the **Servers** view, select **Add and Remove** from the server context menu, and move the application from the **Available** to the **Configured** column.

1. In the **Application Details** window:
    * By default, the **Name** field is populated with the application project name. You can enter a different name. The name is assigned to the deployed application, but does not rename the project.
    * If you want to use an external buildpack to stage the application, enter the URL of the buildpack.

    You can deploy the application without further configuration by clicking **Finish**. Note that because the application default values may take a second or two to load, the **Finish** button might not be enabled immediately. A progress indicator will indicate when the application default values have been loaded, and the "Finish" button will be enabled.

    Click **Next** to continue.

    <%= image_tag("sts/application-details.png") %>

1. In the **Launch Deployment** window:

    **Host** --- By default, contains the name of the application. You can enter a different value if desired. If you push the same application to multiple spaces in the same organization, you must assign a unique **Host** to each.

    **Domain** --- Contains the default domain. If you have mapped custom domains to the target space, they appear in the pull-down list. <p class="note"><strong>Note</strong>: This version of the Cloud Foundry Eclipse plugin does not provide a mechanism for mapping a custom domain to a space. You must use the <code>cf map domain</code> command to do so.</p>

    **Deployed URL** --- By default, contains the value of the **Host** and **Domain** fields, separated by a period (.) character.

    **Memory Reservation** --- Select the amount of memory to allocate to the application from the pull-down list.

    **Start application on deployment** --- If you do not want the application to be started on deployment, uncheck the box.

    <%= image_tag("sts/launch-deployment.png") %>

1. The **Services Selection** window lists services provisioned in the target space. Checkmark the services, if any, that you want to bind to the application, and click **Finish**. You can bind services to the application after deployment, as described in [Bind and Unbind Services](#bind-service).

    <%= image_tag("sts/services-selection.png") %>

   As the deployment proceeds, progress messages appear in the **Console** view. When deployment is complete, the application is listed in the **Applications** pane.

## <a id='create-service'></a>Create a Service ##

Before you can bind a service to an application, you must create it.

To create a service:

1. Select the **Applications and Services** tab.
1. Click the icon in the upper right corner of the **Services** pane.
1. In the **Service Configuration** window, enter a text pattern to **Filter** for a service. Matches are made against both service name and description.
1. Select a service from the **Service List**. The list automatically updates based on the filter text.
1. Enter a **Name** for the service and select a service **Plan** from the drop-down list.

    <%= image_tag("sts/service-configuration.png") %>

1. Click **Finish**. The new service appears in the **Services** pane.

## <a id='bind-service'></a>Bind and Unbind Services ##

You can bind a service to an application when you deploy it. To bind a service to an application that is already deployed, drag the service from the **Services** pane to the **Application Services** pane.  (See the area labelled "G" in the screenshot in the [Applications and Services](#apps-services-tab) above.)

To unbind a service, right-click the service in the **Application Services** pane, and select **Unbind from Application**.

## <a id='view-file'></a>View an Application File ##

You can view the contents of a file in a deployed application by selecting it the **Remote Systems View**. (See the areas labelled "I" and "J" in the screenshot in the [Applications and Services Tab](#overview-tab) above.)

1. If the **Remote Systems View** is not visible:
  * Select the **Applications and Services** tab.
  * Select the application of interest from the **Applications** pane.
  * In the **Instances** pane, click the **Remote Systems View** link.

2. In the **Remote Systems View**, browse to the application and application file of interest, and double-click the file. A new tab appears in the editor area with the contents of the selected file.

    <%= image_tag("sts/file-contents.png") %>

## <a id='undeploy'></a>Undeploy an Application ##

To undeploy an application, right click the application in either the **Servers** or the **Applications** pane and click **Remove**.

## <a id='scale-application'></a>Scale an Application ##

You can change the memory allocation for an application and the number of instances deployed in the **General** pane when the **Applications and Services** tab is selected.  Use the **Memory Limit** and **Instances** selector lists.

Although the scaling can be performed while the application is running, if the scaling has not taken effect, restart the application. If necessary, the application statistics can be manually refreshed by clicking the **Refresh** button in the top, right corner of the "Applications" pane, labelled "H" in the screenshot in [Applications and Services Tab](#apps-services-tab).

## <a id='push-changes'></a>Push Application Changes ##

The Cloud Foundry editor supports these application operations:

* **Start** and **Stop** --- When you **Start** an application, the plugin pushes all application files to the Cloud Foundry instance before starting the application, regardless of whether there are changes to the files or not.

* **Restart** --- When you **Restart** a deployed application, the plugin does not push any resources to the Cloud Foundry instance.

* **Update and Restart** --- When you run this command, the plugin pushes only the changes that were made to the application since last update, not the entire application. This is useful for performing incremental updates to large applications.

## <a id='manage-app-urls'></a>Manage Application URLs ##

You add, edit, and remove URLs mapped to the currently selected application in the **General** pane when the **Applications and Services** tab is selected.  Click the pencil icon to display the **Mapped URIs Configuration** window.

 <%= image_tag("sts/manage-uris-configuration.png") %>

## <a id='console-view'></a>Information in the Console View ##

When you start, restart, or update and restart an application, application output will generally be streamed to the **Console** view (labelled "F" in the screenshot in [Overview Tab](#overview-tab)). The information shown in the **Console** view for a running application instance includes staging information, and the application's  `std.out` and `std.error` logs.

If multiple instances of the application are running, only the output of the first instance appears in the **Console** view. To view the output of another running instance, or to refresh the output that is currently displayed:

1. In the **Applications and Services** tab, select the deployed application in the *Applications* pane.

2. Click **Refresh** on the top right corner of the **Applications** pane.

3. In the **Instances** pane, wait for the application instances to be updated.

4. Once non-zero health is shown for an application instance, right-click on that instance to open the context menu and select **Show Console**.

## <a id='clone'></a>Clone a Cloud Foundry Server Instance ##

Each space in Cloud Foundry to which you want to deploy applications must be represented by a Cloud Foundry server instance in the **Servers** view. After you have created a Cloud Foundry server instance, as described in [Create a Cloud Foundry Server](#cloud-foundry-server), you can clone it to create another.

Follow the step below to clone a server:

1. Perform one of the following actions:
  * In the Cloud Foundry server instance editor "Overview" tab, click **Clone Server**.
  * Right-click a Cloud Foundry server instance in the **Servers** view, and select **Clone Server** from the context menu.

1. In the **Organizations and Spaces** window, select the space that you want to target.
1. The name field will be filled with the name of the space that you selected. If desired, edit the server name before clicking finish **Finish**.

## <a id='add-URL'></a>Add a Cloud Foundry Instance URL ##

You can configure the plugin to work with any Cloud Foundry instances to which you have access. To do so:

1. Perform steps 1 and 2 of [Create a Cloud Foundry Server](#cloud-foundry-server).
1. In the **Cloud Foundry Account** window, enter the email account and password that you use to log on to the target instance, then click **Manage Cloud URLs**

    <%= image_tag("sts/cloud-foundry-account.png") %>

1. In the **Manage Cloud URLs** window, click **Add**.

    <%= image_tag("sts/manage-cloud-urls.png") %>

1. In the **Add a Cloud URL** window, enter the name and URL of the target cloud instance and click **Finish**.

    <%= image_tag("sts/add-cloud-url.png") %>

1. The new cloud instance should appear in the list on the **Manage Cloud URLs** window. Click **OK** to proceed.
1. In the **Cloud Foundry Account** window, click **Validate Account**.
1. The **Cloud Foundry Account** window is refreshed and displays a message indicating whether or not your credentials were valid. Click **Next**.

    <%= image_tag("sts/new-server.png") %>

1. In the **Organizations and Spaces** window, select the space that you want to target, and click **Finish**.

    <p class="note"><strong>Note</strong>: If you do not select a space, the
    server will be configured to connect to the default space, which is the
    first encountered in a list of your spaces.</p>

    <%= image_tag("sts/orgs-and-spaces.png") %>

1. Once you have successfully configured the Pivotal Cloud Foundry&reg; server, it will appear in the **Servers** view of the Eclipse or STS user interface. To familiarize yourself with the plugin user interface, see [About the Plugin User Interface](#plugin-ui). Following this step, proceed to [Deploy an Application](#deploy-an-application).