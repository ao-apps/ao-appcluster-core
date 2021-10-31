/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2016, 2020, 2021  AO Industries, Inc.
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
 * along with ao-appcluster-core.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoapps.appcluster;

import com.aoapps.lang.i18n.Resources;
import java.util.ResourceBundle;

/**
 * The master records for a resource have a specific status as a result of its configuration
 * and DNS responses.
 *
 * @author  AO Industries, Inc.
 */
public enum MasterDnsStatus {
	UNKNOWN(ResourceStatus.UNKNOWN),
	DISABLED(ResourceStatus.DISABLED),
	STOPPED(ResourceStatus.STOPPED),
	STARTING(ResourceStatus.STARTING),
	CONSISTENT(ResourceStatus.HEALTHY),
	WARNING(ResourceStatus.WARNING),
	INCONSISTENT(ResourceStatus.INCONSISTENT);

	private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, MasterDnsStatus.class);

	private final ResourceStatus resourceStatus;

	private MasterDnsStatus(ResourceStatus resourceStatus) {
		this.resourceStatus = resourceStatus;
	}

	@Override
	public String toString() {
		return RESOURCES.getMessage(name());
	}

	/**
	 * Gets the resource status that this master DNS status will cause.
	 */
	public ResourceStatus getResourceStatus() {
		return resourceStatus;
	}
}
