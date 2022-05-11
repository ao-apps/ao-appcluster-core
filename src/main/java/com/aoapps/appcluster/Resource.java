/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2015, 2016, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.collections.AoCollections;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.xbill.DNS.Name;

/**
 * Monitors the status of a resource by monitoring its role based on DNS entries
 * and synchronizing the resource on an as-needed and/or scheduled basis.
 *
 * @see  ResourceDnsMonitor
 *
 * @author  AO Industries, Inc.
 */
public abstract class Resource<R extends Resource<R, N>, N extends ResourceNode<R, N>> {

  private final AppCluster cluster;
  private final String id;
  private final boolean enabled;
  private final String display;
  private final Set<? extends Name> masterRecords;
  private final int masterRecordsTtl;
  private final String type;
  private final Set<? extends N> resourceNodes;
  private final Set<? extends Nameserver> enabledNameservers;

  private final ResourceDnsMonitor dnsMonitor;
  private final Map<Node, ResourceSynchronizer<R, N>> synchronizers;

  @SuppressWarnings("OverridableMethodCallInConstructor")
  protected Resource(AppCluster cluster, ResourceConfiguration<R, N> resourceConfiguration, Collection<? extends ResourceNode<?, ?>> resourceNodes) throws AppClusterConfigurationException {
    this.cluster = cluster;
    this.id = resourceConfiguration.getId();
    this.enabled = cluster.isEnabled() && resourceConfiguration.isEnabled();
    this.display = resourceConfiguration.getDisplay();
    this.masterRecords = AoCollections.unmodifiableCopySet(resourceConfiguration.getMasterRecords());
    this.masterRecordsTtl = resourceConfiguration.getMasterRecordsTtl();
    this.type = resourceConfiguration.getType();
    @SuppressWarnings("unchecked")
    R rthis = (R) this;
    Set<N> newResourceNodes = AoCollections.newLinkedHashSet(resourceNodes.size());
    for (ResourceNode<?, ?> resourceNode : resourceNodes) {
      @SuppressWarnings("unchecked")
      N rn = (N) resourceNode;
      rn.init(rthis);
      newResourceNodes.add(rn);
    }
    this.resourceNodes = AoCollections.optimalUnmodifiableSet(newResourceNodes);
    final Set<Nameserver> newEnabledNameservers = new LinkedHashSet<>();
    for (ResourceNode<?, ?> resourceNode : resourceNodes) {
      Node node = resourceNode.getNode();
      if (node.isEnabled()) {
        newEnabledNameservers.addAll(node.getNameservers());
      }
    }
    this.enabledNameservers = AoCollections.optimalUnmodifiableSet(newEnabledNameservers);

    this.dnsMonitor = new ResourceDnsMonitor(this);

    Node localNode = cluster.getLocalNode();
    if (localNode == null) {
      // The local node is not part of the cluster.
      synchronizers = Collections.emptyMap();
    } else {
      // Find local node in the resource
      N localResourceNode = null;
      for (N resourceNode : this.resourceNodes) {
        if (resourceNode.getNode().equals(localNode)) {
          localResourceNode = resourceNode;
          break;
        }
      }
      if (localResourceNode == null) {
        // The local node is not part of this resource.
        synchronizers = Collections.emptyMap();
      } else {
        Map<Node, ResourceSynchronizer<R, N>> newSynchronizers = AoCollections.newLinkedHashMap(this.resourceNodes.size() - 1);
        for (N resourceNode : this.resourceNodes) {
          Node node = resourceNode.getNode();
          if (!node.equals(localNode)) {
            ResourceSynchronizer<R, N> synchronizer = newResourceSynchronizer(localResourceNode, resourceNode, resourceConfiguration);
            if (synchronizer != null) {
              newSynchronizers.put(node, synchronizer);
            }
          }
        }
        this.synchronizers = AoCollections.optimalUnmodifiableMap(newSynchronizers);
      }
    }
  }

  @Override
  public String toString() {
    return display;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Resource)) {
      return false;
    }
    return id.equals(((Resource) o).getId());
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Gets the cluster this resource is part of.
   */
  public AppCluster getCluster() {
    return cluster;
  }

  /**
   * The unique ID of this resource.
   */
  public String getId() {
    return id;
  }

  /**
   * Determines if both the cluster and this resource are enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Gets the display name of this resource.
   */
  public String getDisplay() {
    return display;
  }

  /**
   * Gets the set of master records that must all by the same.
   * The master node is determined by matching these records against
   * the resource node configuration's node records.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<? extends Name> getMasterRecords() {
    return masterRecords;
  }

  /**
   * Gets the expected TTL value for the master record.
   */
  public int getMasterRecordsTtl() {
    return masterRecordsTtl;
  }

  /**
   * Gets the set of all nameservers used by all enabled nodes.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<? extends Nameserver> getEnabledNameservers() {
    return enabledNameservers;
  }

  /**
   * Gets the DNS monitor for this resource.
   */
  public ResourceDnsMonitor getDnsMonitor() {
    return dnsMonitor;
  }

  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<? extends N> getResourceNodes() {
    return resourceNodes;
  }

  /**
   * Gets the status of this resource based on disabled, last monitoring results, and synchronization state.
   */
  public ResourceStatus getStatus() {
    ResourceStatus status = ResourceStatus.UNKNOWN;
    if (!isEnabled()) {
      status = AppCluster.max(status, ResourceStatus.DISABLED);
    }
    status = AppCluster.max(status, getDnsMonitor().getLastResult().getResourceStatus());
    for (ResourceSynchronizer<R, N> synchronizer : synchronizers.values()) {
      // Overall synchronizer state
      status = AppCluster.max(status, synchronizer.getState().getResourceStatus());
      // Synchronization result
      status = AppCluster.max(status, synchronizer.getResultStatus());
    }
    return status;
  }

  /**
   * Gets if this resource allows multiple master servers.
   */
  public abstract boolean getAllowMultiMaster();

  /**
   * Gets the replication type of this resource.
   */
  public String getType() {
    return type;
  }

  /**
   * Starts the DNS monitor and all synchronizers.
   */
  void start() {
    dnsMonitor.start();
    for (ResourceSynchronizer<R, N> synchronizer : synchronizers.values()) {
      synchronizer.start();
    }
  }

  /**
   * Stops all synchronizers and the DNS monitor.
   */
  void stop() {
    for (ResourceSynchronizer<R, N> synchronizer : synchronizers.values()) {
      synchronizer.stop();
    }
    dnsMonitor.stop();
  }

  /**
   * Creates the resource synchronizer for this specific type of resource or <code>null</code>
   * if never performs any synchronization between these two nodes.
   */
  protected abstract ResourceSynchronizer<R, N> newResourceSynchronizer(
      N localResourceNode,
      N remoteResourceNode,
      ResourceConfiguration<R, N> resourceConfiguration
  ) throws AppClusterConfigurationException;

  /**
   * Gets the set of resource synchronizers.
   */
  public Collection<ResourceSynchronizer<R, N>> getSynchronizers() {
    return synchronizers.values();
  }

  /**
   * Gets a map-view of the resource synchronizers keyed on remote node.
   * If the local node is not part of the resource nodes, returns an empty map.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Map<Node, ResourceSynchronizer<R, N>> getSynchronizerMap() {
    return synchronizers;
  }
}
