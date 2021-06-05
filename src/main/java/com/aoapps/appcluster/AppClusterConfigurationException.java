/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2016, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-appcluster-core.
 *
 * ao-appcluster-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-appcluster-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-appcluster-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoapps.appcluster;

import com.aoapps.lang.Throwables;

/**
 * @author  AO Industries, Inc.
 */
public class AppClusterConfigurationException extends AppClusterException {

	private static final long serialVersionUID = 8980544839280152074L;

	public AppClusterConfigurationException() {
	}

	public AppClusterConfigurationException(String message) {
		super(message);
	}

	public AppClusterConfigurationException(Throwable cause) {
		super(cause);
	}

	public AppClusterConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	static {
		Throwables.registerSurrogateFactory(AppClusterConfigurationException.class, (template, cause) ->
			new AppClusterConfigurationException(template.getMessage(), cause)
		);
	}
}
