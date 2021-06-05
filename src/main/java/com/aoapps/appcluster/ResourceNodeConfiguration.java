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

import java.util.Set;
import org.xbill.DNS.Name;

/**
 * The configuration for one resource node.
 *
 * @author  AO Industries, Inc.
 */
public interface ResourceNodeConfiguration<R extends Resource<R, RN>, RN extends ResourceNode<R, RN>> {

	@Override
	String toString();

	@Override
	boolean equals(Object o);

	@Override
	int hashCode();

	/**
	 * Gets the unique ID of the resource this configuration represents.
	 */
	String getResourceId();

	/**
	 * Gets the unique ID of the node this configuration represents.
	 */
	String getNodeId();

	/**
	 * @see ResourceNode#getNodeRecords()
	 */
	Set<? extends Name> getNodeRecords();

	/**
	 * Creates a new resource node from this configuration.
	 */
	RN newResourceNode(Node node) throws AppClusterConfigurationException;
}
