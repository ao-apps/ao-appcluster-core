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
 * along with ao-appcluster-core.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoapps.appcluster;

/**
 * Is notified when the status of a resource changes.
 *
 * @author  AO Industries, Inc.
 */
public interface ResourceListener {

	/**
	 * Called whenever a new result is available.
	 *
	 * @param oldResult will never be <code>null</code>
	 */
	void onResourceDnsResult(ResourceDnsResult oldResult, ResourceDnsResult newResult);

	/**
	 * Called whenever a new synchronization result is available.
	 *
	 * @param oldResult will be <code>null</code> for the first synchronization pass
	 */
	void onResourceSynchronizationResult(ResourceSynchronizationResult oldResult, ResourceSynchronizationResult newResult);
}
