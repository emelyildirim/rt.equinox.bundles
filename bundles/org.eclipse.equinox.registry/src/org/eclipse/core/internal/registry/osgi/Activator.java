/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.registry.osgi;

import java.io.File;
import java.util.Hashtable;
import org.eclipse.core.internal.registry.*;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The extension registry bundle. This activator will create the default OSGi registry 
 * unless told otherwise by setting the following system property to false:
 * <code>eclipse.createRegistry=false</code>
 * 
 * The default registry will be stopped on the bundle shutdown.
 * 
 * @see IRegistryConstants#PROP_DEFAULT_REGISTRY
 */
public class Activator implements BundleActivator {

	private static BundleContext bundleContext;

	/**
	 * Location of the default registry relative to the configuration area
	 */
	private static final String STORAGE_DIR = "org.eclipse.core.runtime"; //$NON-NLS-1$

	private Object masterRegistryKey = new Object();
	private Object userRegistryKey = new Object();

	private IExtensionRegistry defaultRegistry = null;
	private ServiceRegistration registryRegistration;
	private RegistryProviderOSGI defaultProvider;

	/**
	 * This method is called upon plug-in activation
	 */
	public void start(BundleContext context) throws Exception {
		bundleContext = context;
		RegistryProperties.setContext(bundleContext);
		processCommandLine();
		startRegistry();
	}

	/**
	 * This method is called when the plug-in is stopped
	 */
	public void stop(BundleContext context) throws Exception {
		stopRegistry();
		RegistryProperties.setContext(null);		
		bundleContext = null;
	}

	public static BundleContext getContext() {
		return bundleContext;
	}

	/**
	 * Look for the no registry cache flag and check to see if we should NOT be lazily loading plug-in 
	 * definitions from the registry cache file.
	 * NOTE: this command line processing is only performed in the presence of OSGi
	 * 
	 * @param args - command line arguments
	 */
	private void processCommandLine() {
		ServiceTracker environmentTracker = new ServiceTracker(bundleContext, EnvironmentInfo.class.getName(), null);
		environmentTracker.open();
		EnvironmentInfo environmentInfo = (EnvironmentInfo) environmentTracker.getService();
		environmentTracker.close();
		if (environmentInfo == null)
			return;
		String[] args = environmentInfo.getNonFrameworkArgs();
		if (args == null || args.length == 0)
			return;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase(IRegistryConstants.NO_REGISTRY_CACHE))
				RegistryProperties.setProperty(IRegistryConstants.PROP_NO_REGISTRY_CACHE, "true"); //$NON-NLS-1$
			else if (args[i].equalsIgnoreCase(IRegistryConstants.NO_LAZY_REGISTRY_CACHE_LOADING))
				RegistryProperties.setProperty(IRegistryConstants.PROP_NO_LAZY_CACHE_LOADING, "true"); //$NON-NLS-1$
		}
	}

	public void startRegistry() throws CoreException {
		// see if the customer suppressed the creation of default registry
		String property = bundleContext.getProperty(IRegistryConstants.PROP_DEFAULT_REGISTRY);
		if (property != null && property.equalsIgnoreCase("false")) //$NON-NLS-1$
			return;

		// check to see if we need to use null as a userToken
		if ("true".equals(bundleContext.getProperty(IRegistryConstants.PROP_REGISTRY_NULL_USER_TOKEN))) //$NON-NLS-1$
			userRegistryKey = null;

		// Determine primary and alternative registry locations. Eclipse extension registry cache 
		// can be found in one of the two locations:
		// a) in the local configuration area (standard location passed in by the platform) -> priority
		// b) in the shared configuration area (typically, shared install is used) 
		File[] registryLocations;
		boolean[] readOnlyLocations;

		Location configuration = OSGIUtils.getDefault().getConfigurationLocation();
		File primaryDir = new File(configuration.getURL().getPath() + '/' + STORAGE_DIR);
		boolean primaryReadOnly = configuration.isReadOnly();

		Location parentLocation = configuration.getParentLocation();
		if (parentLocation != null) {
			File secondaryDir = new File(parentLocation.getURL().getFile() + '/' + IRegistryConstants.RUNTIME_NAME);
			registryLocations = new File[] {primaryDir, secondaryDir};
			readOnlyLocations = new boolean[] {primaryReadOnly, true}; // secondary Eclipse location is always read only
		} else {
			registryLocations = new File[] {primaryDir};
			readOnlyLocations = new boolean[] {primaryReadOnly};
		}

		EquinoxRegistryStrategy registryStrategy = new EquinoxRegistryStrategy(registryLocations, readOnlyLocations, masterRegistryKey);
		defaultRegistry = RegistryFactory.createRegistry(registryStrategy, masterRegistryKey, userRegistryKey);

		registryRegistration = Activator.getContext().registerService(IExtensionRegistry.class.getName(), defaultRegistry, new Hashtable());
		defaultProvider = new RegistryProviderOSGI();
		// Set the registry provider and specify this as a default registry:
		RegistryProviderFactory.setDefault(defaultProvider);
	}

	private void stopRegistry() {
		if (defaultRegistry != null) {
			RegistryProviderFactory.releaseDefault();
			defaultProvider.release();
			registryRegistration.unregister();
			defaultRegistry.stop(masterRegistryKey);
		}
	}

}
