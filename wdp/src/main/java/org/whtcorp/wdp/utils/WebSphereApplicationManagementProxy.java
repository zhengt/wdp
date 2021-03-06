package org.whtcorp.wdp.utils;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import org.whtcorp.wdp.WebSphereApplicationModel;
import org.whtcorp.wdp.WebSphereTopologyModel;

public class WebSphereApplicationManagementProxy {
	
	private AdminClient adminClient;
	private AppManagement appManagement;
	private Hashtable applicationPreferences;
	
	public WebSphereApplicationManagementProxy(AdminClient adminClient) throws Exception {

        this.adminClient = adminClient;
		this.appManagement = AppManagementProxy.getJMXProxyForClient(adminClient);
        this.applicationPreferences = new Hashtable<String, Serializable>();
	}
	
	public String startApplication(String applicationName) throws Exception {
		
		queryAndGetApplicationManagementBean();
		
		if (appManagement.checkIfAppExists(applicationName, applicationPreferences, null)) {
			return appManagement.startApplication(applicationName, applicationPreferences, null);
		} else {
			return "INFO --- This application is not deployed on this server ---";
		}
	}

	public String stopApplication(String applicationName) throws Exception {

		queryAndGetApplicationManagementBean();
		if (appManagement.checkIfAppExists(applicationName, applicationPreferences, null)) {
			return appManagement.stopApplication(applicationName, applicationPreferences, null);
		} else {
			return "INFO --- This application is not deployed on this server ---";
		}
	}
	
	public String installApplication(WebSphereApplicationModel application, WebSphereTopologyModel websphere_topology, String ear_location) throws Exception {
		
		queryAndGetApplicationManagementBean();
        String targetTopology;
        
        if(websphere_topology.getWebsphere_cluster_name() != null) {
            targetTopology = "WebSphere:cell="+ websphere_topology.getWebsphere_cell_name() + ",cluster=" + websphere_topology.getWebsphere_cluster_name() + ",node=" + websphere_topology.getWebsphere_node_name() + ",server=" + websphere_topology.getWebsphere_server_name();
        } else {
		    targetTopology = "WebSphere:cell="+ websphere_topology.getWebsphere_cell_name() + ",node=" + websphere_topology.getWebsphere_node_name() + ",server=" + websphere_topology.getWebsphere_server_name();
        }
        
		Hashtable<String, String> module2server = new Hashtable<String, String>();
        module2server.put("*", targetTopology);
        applicationPreferences.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);

        Properties bindingProperties = new Properties();
        bindingProperties.put(AppConstants.APPDEPL_DFLTBNDG_VHOST, "default_host");
        if(application.getShared_library() != null) {
            bindingProperties.put(AppConstants.APPDEPL_RESOURCE_MAPPER_LIBRARY, application.getShared_library().getName());
        }

        applicationPreferences.put(AppConstants.APPDEPL_DFLTBNDG, bindingProperties);

        if (appManagement.checkIfAppExists(application.getApplicationName(), applicationPreferences, null)) {
            uninstallApplication(application.getApplicationName());
        }



        String earLocation = ear_location + application.getApplicationName() + ".ear";

        NotificationFilterSupport myFilter = new NotificationFilterSupport();
        myFilter.enableType(AppConstants.NotificationType);
        CountDownLatch latch = new CountDownLatch(1);
        EventsNotificationListener listener = new EventsNotificationListener(adminClient, myFilter, "Install: " + application.getApplicationName(), AppNotification.INSTALL, latch);

        appManagement.installApplication(earLocation, application.getApplicationName(), applicationPreferences, null);
        latch.await();

        String started = startApplication(application.getApplicationName());

        if (started.startsWith("INFO")) {
            return "Application: " + application.getApplicationName() + " installed, but failed to start.";
        } else {
            return "Application: " + application.getApplicationName() + " installed and started.";
        }
    }

	public String uninstallApplication(String applicationName) throws Exception {
		
		queryAndGetApplicationManagementBean();
		
		if (appManagement.checkIfAppExists(applicationName, applicationPreferences, null)) {
			
			NotificationFilterSupport myFilter = new NotificationFilterSupport();
			myFilter.enableType(AppConstants.NotificationType);
			CountDownLatch latch = new CountDownLatch(1);
			NotificationListener listener = new EventsNotificationListener(adminClient, myFilter, "Install: " + applicationName, AppNotification.UNINSTALL, latch);

			appManagement.uninstallApplication(applicationName, applicationPreferences, null);
			
			latch.await(10, TimeUnit.SECONDS); 
			return "Application: " + applicationName + " un-installed.";
		} else {
			return "Application: " + applicationName + " is not installed on this server.";
		}
	}

	private void queryAndGetApplicationManagementBean() throws Exception {

        applicationPreferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
		
		ObjectName on = new ObjectName ("WebSphere:type=AppManagement,*");
		ObjectName appmgmtON = (ObjectName)adminClient.queryNames (on, null).iterator().next();
	}
}
