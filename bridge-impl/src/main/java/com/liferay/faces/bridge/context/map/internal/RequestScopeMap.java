/**
 * Copyright (c) 2000-2015 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.bridge.context.map.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.ExternalContext;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletRequest;

import com.liferay.faces.bridge.BridgeFactoryFinder;
import com.liferay.faces.bridge.bean.internal.BeanManager;
import com.liferay.faces.bridge.bean.internal.BeanManagerFactory;
import com.liferay.faces.bridge.bean.internal.PreDestroyInvoker;
import com.liferay.faces.bridge.bean.internal.PreDestroyInvokerFactory;
import com.liferay.faces.bridge.config.internal.PortletConfigParam;
import com.liferay.faces.bridge.context.BridgeContext;
import com.liferay.faces.bridge.scope.BridgeRequestScope;
import com.liferay.faces.util.config.ApplicationConfig;
import com.liferay.faces.util.map.AbstractPropertyMap;
import com.liferay.faces.util.map.AbstractPropertyMapEntry;
import com.liferay.faces.util.product.Product;
import com.liferay.faces.util.product.ProductConstants;
import com.liferay.faces.util.product.ProductMap;


/**
 * @author  Neil Griffin
 */
public class RequestScopeMap extends AbstractPropertyMap<Object> {

	// Private Constants
	private static final boolean LIFERAY_PORTAL_DETECTED = ProductMap.getInstance().get(ProductConstants.LIFERAY_PORTAL)
		.isDetected();
	private static final boolean NULL_PATH_ATTRIBUTES;
	private static final String REQUEST_SCOPED_FQCN = "javax.faces.bean.RequestScoped";

	static {

		// Versions of Liferay Portal prior to 6.1 have a bug in PortletRequest.removeAttribute(String) that needs to
		// be worked-around in this class. See: http://issues.liferay.com/browse/FACES-1233
		// Additionally, Resin requires a similar workaround. See: http://issues.liferay.com/browse/FACES-1612
		Product liferay = ProductMap.getInstance().get(ProductConstants.LIFERAY_PORTAL);
		Product resin = ProductMap.getInstance().get(ProductConstants.RESIN);
		NULL_PATH_ATTRIBUTES = (liferay.isDetected() && (resin.isDetected() || (liferay.getBuildId() < 6100)));
	}

	// Private Data Members
	private BeanManager beanManager;
	private boolean distinctRequestScopedManagedBeans;
	private String namespace;
	private PortletRequest portletRequest;
	private PreDestroyInvoker preDestroyInvoker;
	private boolean preferPreDestroy;
	private Set<String> removedAttributeNames;

	public RequestScopeMap(BridgeContext bridgeContext) {

		String appConfigAttrName = ApplicationConfig.class.getName();
		PortletContext portletContext = bridgeContext.getPortletContext();
		ApplicationConfig applicationConfig = (ApplicationConfig) portletContext.getAttribute(appConfigAttrName);
		BeanManagerFactory beanManagerFactory = (BeanManagerFactory) BridgeFactoryFinder.getFactory(
				BeanManagerFactory.class);
		this.beanManager = beanManagerFactory.getBeanManager(applicationConfig.getFacesConfig());

		// Determines whether or not JSF @ManagedBean classes annotated with @RequestScoped should be distinct for
		// each portlet when running under Liferay Portal.
		boolean distinctRequestScopedManagedBeans = false;

		PortletConfig portletConfig = bridgeContext.getPortletConfig();

		if (LIFERAY_PORTAL_DETECTED) {
			distinctRequestScopedManagedBeans = PortletConfigParam.DistinctRequestScopedManagedBeans.getBooleanValue(
					portletConfig);
		}

		this.distinctRequestScopedManagedBeans = distinctRequestScopedManagedBeans;

		this.namespace = bridgeContext.getPortletResponse().getNamespace();

		this.portletRequest = bridgeContext.getPortletRequest();

		// Determines whether or not methods annotated with the @PreDestroy annotation are preferably invoked
		// over the @BridgePreDestroy annotation.
		this.preferPreDestroy = PortletConfigParam.PreferPreDestroy.getBooleanValue(portletConfig);

		ContextMapFactory contextMapFactory = (ContextMapFactory) BridgeFactoryFinder.getFactory(
				ContextMapFactory.class);
		Map<String, Object> applicationScopeMap = contextMapFactory.getApplicationScopeMap(bridgeContext);
		PreDestroyInvokerFactory preDestroyInvokerFactory = (PreDestroyInvokerFactory) BridgeFactoryFinder.getFactory(
				PreDestroyInvokerFactory.class);
		this.preDestroyInvoker = preDestroyInvokerFactory.getPreDestroyInvoker(applicationScopeMap);

		BridgeRequestScope bridgeRequestScope = bridgeContext.getBridgeRequestScope();

		if (bridgeRequestScope != null) {
			this.removedAttributeNames = bridgeRequestScope.getRemovedAttributeNames();
		}
		else {
			this.removedAttributeNames = new HashSet<String>();
		}
	}

	/**
	 * According to the JSF 2.0 JavaDocs for {@link ExternalContext#getRequestMap}, before a managed-bean is removed
	 * from the map, any public no-argument void return methods annotated with javax.annotation.PreDestroy must be
	 * called first.
	 */
	@Override
	public Object remove(Object key) {

		String keyAsString = (String) key;
		Object potentialManagedBeanValue = super.remove(key);

		if (beanManager.isManagedBean(keyAsString, potentialManagedBeanValue)) {
			preDestroyInvoker.invokeAnnotatedMethods(potentialManagedBeanValue, preferPreDestroy);
		}

		return potentialManagedBeanValue;
	}

	@Override
	protected AbstractPropertyMapEntry<Object> createPropertyMapEntry(String name) {
		return new RequestScopeMapEntry(portletRequest, name);
	}

	@Override
	protected void removeProperty(String name) {
		removedAttributeNames.add(name);
		portletRequest.removeAttribute(name);
	}

	@Override
	protected Object getProperty(String name) {

		if ((NULL_PATH_ATTRIBUTES) &&
				("javax.servlet.include.path_info".equals(name) || "javax.servlet.include.servlet_path".equals(name))) {
			return null;
		}
		else {
			Object attributeValue = portletRequest.getAttribute(name);

			// FACES-1446: Strictly enforce Liferay Portal's private-request-attribute feature so that each portlet
			// will have its own managed-bean instance.
			if (distinctRequestScopedManagedBeans) {

				if (attributeValue != null) {

					boolean requestScopedBean = false;
					Annotation[] annotations = attributeValue.getClass().getAnnotations();

					if (annotations != null) {

						for (Annotation annotation : annotations) {

							if (annotation.annotationType().getName().equals(REQUEST_SCOPED_FQCN)) {
								requestScopedBean = true;

								break;
							}
						}
					}

					if (requestScopedBean) {

						// If the private-request-attribute feature is enabled in WEB-INF/liferay-portlet.xml, then the
						// NamespaceServletRequest.getAttribute(String) method first tries to get the attribute value by
						// prepending the portlet namespace. If the value is null, then it attempts to get it WITHOUT
						// the prepending the portlet namespace. But that causes a problem for @RequestScoped
						// managed-beans if the same name is used in a different portlet. In the case that the JSF
						// runtime is trying to resolve an EL-expression like "#{backingBean}", then this method must
						// return null if the bean has not yet been created (for this portlet) by the JSF managed-bean
						// facility.
						Object namespacedAttributeValue = portletRequest.getAttribute(namespace + name);

						if (namespacedAttributeValue != attributeValue) {
							attributeValue = null;
						}
					}
				}
			}

			return attributeValue;
		}
	}

	@Override
	protected void setProperty(String name, Object value) {

		// If the specified attribute name is regarded as previously removed, then no longer regard it as removed since
		// it is being added back now.
		if (removedAttributeNames.contains(name)) {
			removedAttributeNames.remove(name);
		}

		// Set the attribute value on the underlying request.
		portletRequest.setAttribute(name, value);
	}

	@Override
	protected Enumeration<String> getPropertyNames() {

		List<String> attributeNames = new ArrayList<String>();

		Enumeration<String> portletRequestAttributeNames = portletRequest.getAttributeNames();

		if (portletRequestAttributeNames != null) {

			while (portletRequestAttributeNames.hasMoreElements()) {
				String attributeName = portletRequestAttributeNames.nextElement();

				if (!removedAttributeNames.contains(attributeName)) {
					attributeNames.add(attributeName);
				}
			}
		}

		return Collections.enumeration(attributeNames);
	}
}
