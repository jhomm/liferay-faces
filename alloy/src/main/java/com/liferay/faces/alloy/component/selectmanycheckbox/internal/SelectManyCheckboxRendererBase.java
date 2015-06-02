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
package com.liferay.faces.alloy.component.selectmanycheckbox.internal;
//J-


import javax.annotation.Generated;

import com.liferay.faces.alloy.component.selectmanycheckbox.SelectManyCheckbox;

import com.liferay.faces.alloy.component.select.internal.SelectDelegatingRendererBase;


/**
 * @author	Bruno Basto
 * @author	Kyle Stiemann
 */
@Generated(value = "com.liferay.alloy.tools.builder.FacesBuilder")
public abstract class SelectManyCheckboxRendererBase extends SelectDelegatingRendererBase {

	// Protected Constants
	protected static final String LABEL = "label";
	protected static final String STYLE_CLASS = "styleClass";

	@Override
	public String getDelegateComponentFamily() {
		return SelectManyCheckbox.COMPONENT_FAMILY;
	}

	@Override
	public String getDelegateRendererType() {
		return "javax.faces.Checkbox";
	}
}
//J+
