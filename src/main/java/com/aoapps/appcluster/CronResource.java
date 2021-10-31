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

import java.util.Collection;

/**
 * A resource that is scheduled by CronDaemon.
 * 
 * @author  AO Industries, Inc.
 */
public abstract class CronResource<R extends CronResource<R, RN>, RN extends CronResourceNode<R, RN>> extends Resource<R, RN> {

	private final int synchronizeTimeout;
	private final int testTimeout;

	protected CronResource(AppCluster cluster, CronResourceConfiguration<R, RN> resourceConfiguration, Collection<? extends ResourceNode<?, ?>> resourceNodes) throws AppClusterConfigurationException {
		super(cluster, resourceConfiguration, resourceNodes);
		this.synchronizeTimeout = resourceConfiguration.getSynchronizeTimeout();
		this.testTimeout = resourceConfiguration.getTestTimeout();
	}

	/**
	 * The synchronize timeout for this resource in seconds.
	 */
	public int getSynchronizeTimeout() {
		return synchronizeTimeout;
	}

	/**
	 * The test timeout for this resource in seconds.
	 */
	public int getTestTimeout() {
		return testTimeout;
	}

	@Override
	protected abstract CronResourceSynchronizer<R, RN> newResourceSynchronizer(RN localResourceNode, RN remoteResourceNode, ResourceConfiguration<R, RN> resourceConfiguration) throws AppClusterConfigurationException;
}
