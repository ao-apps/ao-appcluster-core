/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2016, 2021, 2022  AO Industries, Inc.
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
 * The configuration for a cron resource.
 *
 * @author  AO Industries, Inc.
 */
public abstract class CronResourceNodePropertiesConfiguration<R extends CronResource<R, N>, N extends CronResourceNode<R, N>>
    extends ResourceNodePropertiesConfiguration<R, N> implements CronResourceNodeConfiguration<R, N> {

  protected CronResourceNodePropertiesConfiguration(AppClusterPropertiesConfiguration properties, String resourceId, String nodeId) throws AppClusterConfigurationException {
    super(properties, resourceId, nodeId);
  }

  @Override
  public abstract N newResourceNode(Node node) throws AppClusterConfigurationException;
}
