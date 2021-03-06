/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.metatype.impl;

import org.eclipse.equinox.metatype.EquinoxMetaTypeInformation;
import org.eclipse.equinox.metatype.EquinoxMetaTypeService;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Hashtable;
import javax.xml.parsers.*;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.xml.sax.SAXException;

/**
 * Implementation of MetaTypeService
 */
public class MetaTypeServiceImpl implements EquinoxMetaTypeService, SynchronousBundleListener {

	SAXParserFactory _parserFactory;
	private Hashtable<Long, EquinoxMetaTypeInformation> _mtps = new Hashtable<Long, EquinoxMetaTypeInformation>(7);

	private final LogService logger;
	private final ServiceTracker<Object, Object> metaTypeProviderTracker;

	/**
	 * Constructor of class MetaTypeServiceImpl.
	 */
	public MetaTypeServiceImpl(SAXParserFactory parserFactory, LogService logger, ServiceTracker<Object, Object> metaTypeProviderTracker) {
		this._parserFactory = parserFactory;
		this.logger = logger;
		this.metaTypeProviderTracker = metaTypeProviderTracker;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.service.metatype.MetaTypeService#getMetaTypeInformation(org.osgi.framework.Bundle)
	 */
	public EquinoxMetaTypeInformation getMetaTypeInformation(Bundle bundle) {
		return getMetaTypeProvider(bundle);
	}

	/**
	 * Internal Method - to get MetaTypeProvider object.
	 */
	private EquinoxMetaTypeInformation getMetaTypeProvider(final Bundle b) {
		final LogService loggerTemp = this.logger;
		final ServiceTracker<Object, Object> tracker = this.metaTypeProviderTracker;
		try {
			Long bID = new Long(b.getBundleId());
			synchronized (_mtps) {
				if (_mtps.containsKey(bID))
					return _mtps.get(bID);
				// Avoid synthetic accessor method warnings.

				EquinoxMetaTypeInformation mti = AccessController.doPrivileged(new PrivilegedExceptionAction<EquinoxMetaTypeInformation>() {
					public EquinoxMetaTypeInformation run() throws ParserConfigurationException, SAXException {
						MetaTypeInformationImpl impl = new MetaTypeInformationImpl(b, newParser(), loggerTemp);
						if (!impl._isThereMeta)
							return new MetaTypeProviderTracker(b, loggerTemp, tracker);
						return impl;
					}
				});
				_mtps.put(bID, mti);
				return mti;
			}
		} catch (Exception e) {
			logger.log(LogService.LOG_ERROR, NLS.bind(MetaTypeMsg.EXCEPTION_MESSAGE, e.getMessage()), e);
			return new MetaTypeProviderTracker(b, loggerTemp, tracker);
		}
	}

	SAXParser newParser() throws ParserConfigurationException, SAXException {
		boolean namespaceAware = _parserFactory.isNamespaceAware();
		boolean validating = _parserFactory.isValidating();
		// Always want a non-validating parser.
		_parserFactory.setValidating(false);
		try {
			// If the factory is already namespace aware, we know it can create namespace aware parsers
			// because that was checked in the service tracker.
			if (namespaceAware) {
				return _parserFactory.newSAXParser();
			}
			// If the factory is not already namespace aware, it may or may not be able to create
			// namespace aware parsers.
			_parserFactory.setNamespaceAware(true);
			try {
				return _parserFactory.newSAXParser();
			} catch (Exception e) {
				// Factory cannot create namespace aware parsers. Go with the last resort.
				_parserFactory.setNamespaceAware(false);
				return _parserFactory.newSAXParser();
			}
		} finally {
			// Restore the previous settings in all cases.
			_parserFactory.setNamespaceAware(namespaceAware);
			_parserFactory.setValidating(validating);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleListener#bundleChanged(org.osgi.framework.BundleEvent)
	 */
	public void bundleChanged(BundleEvent event) {

		int type = event.getType();
		Long bID = new Long(event.getBundle().getBundleId());

		switch (type) {
			case BundleEvent.UPDATED :
			case BundleEvent.UNINSTALLED :
				_mtps.remove(bID);
				break;
			case BundleEvent.INSTALLED :
			case BundleEvent.RESOLVED :
			case BundleEvent.STARTED :
			case BundleEvent.STOPPED :
			case BundleEvent.UNRESOLVED :
			default :
				break;
		}
	}
}
