/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2016, 2021, 2022, 2023, 2024  AO Industries, Inc.
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
 * Each resource has one synchronizer between it and any other node for the
 * resource.
 *
 * <p>Every resource type should support testing.  The test should ensure point-in-time
 * consistency between two nodes as much as possible.</p>
 *
 * <p>Resources are only required to support synchronization when is a master.
 * They may optionally support synchronization when a slave (such as a csync2
 * master on a slave node).</p>
 *
 * @author  AO Industries, Inc.
 */
public abstract class ResourceSynchronizer<R extends Resource<R, N>, N extends ResourceNode<R, N>> {

  protected final N localResourceNode;
  protected final N remoteResourceNode;

  protected ResourceSynchronizer(N localResourceNode, N remoteResourceNode) {
    R resource = localResourceNode.getResource();
    if (resource != remoteResourceNode.getResource()) {
      throw new IllegalArgumentException("localResourceNode.resource != remoteResourceNode.resource");
    }
    if (!localResourceNode.getNode().equals(resource.getCluster().getLocalNode())) {
      throw new IllegalArgumentException("localResourceNode.node != localResourceNode.resource.cluster.localNode");
    }
    this.localResourceNode = localResourceNode;
    this.remoteResourceNode = remoteResourceNode;
  }

  @Override
  public String toString() {
    return localResourceNode.getResource() + ": " + localResourceNode.getNode() + " → " + remoteResourceNode.getNode();
  }

  /**
   * Gets the local resource node.
   */
  public N getLocalResourceNode() {
    return localResourceNode;
  }

  /**
   * Gets the remote resource node.
   */
  public N getRemoteResourceNode() {
    return remoteResourceNode;
  }

  /**
   * Gets the current synchronization state.
   */
  public abstract ResourceSynchronizerState getState();

  /**
   * Gets a description of the current state or <code>null</code> for no
   * specific message.
   */
  public abstract String getStateMessage();

  /**
   * Schedules an immediate synchronization if possible.
   */
  public abstract void synchronizeNow(ResourceSynchronizationMode mode);

  /**
   * Gets the last synchronization result or <code>null</code> if unavailable.
   */
  public abstract ResourceSynchronizationResult getLastResult();

  /**
   * Gets the synchronization result status.  Considered as STOPPED/DISABLED/STARTING if last result is not available.
   */
  public ResourceStatus getResultStatus() {
    ResourceSynchronizationResult result = getLastResult();
    if (result != null) {
      return result.getResourceStatus();
    }
    // No result, base off synchronizer state for STOPPED/DISABLED/STARTING
    ResourceStatus status = getState().getResourceStatus();
    return status == ResourceStatus.HEALTHY ? ResourceStatus.STARTING : status;
  }

  /**
   * Starts the synchronizer.
   */
  protected abstract void start();

  /**
   * Stops the synchronizer.
   */
  protected abstract void stop();
}
