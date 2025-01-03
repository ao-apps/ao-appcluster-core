/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2015, 2016, 2021, 2022, 2025  AO Industries, Inc.
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

import java.util.Map;
import org.xbill.DNS.Name;

/**
 * A nameserver may be used by multiple nodes.
 *
 * @author  AO Industries, Inc.
 */
public class Nameserver {

  private final AppCluster cluster;
  private final Name hostname;
  private final boolean strictTtl;

  Nameserver(AppCluster cluster, Name hostname, boolean strictTtl) {
    this.cluster = cluster;
    this.hostname = hostname;
    this.strictTtl = strictTtl;
  }

  @Override
  public String toString() {
    return hostname.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Nameserver)) {
      return false;
    }
    return hostname.equals(((Nameserver) o).hostname);
  }

  @Override
  public int hashCode() {
    return hostname.hashCode();
  }

  /**
   * Gets the cluster this nameserver is part of.
   */
  public AppCluster getCluster() {
    return cluster;
  }

  /**
   * Gets the hostname of this nameserver.
   */
  public Name getHostname() {
    return hostname;
  }

  /**
   * Checks if this nameserver returns strictly matching TTL values.
   * When {@code true} (the default), it is a warning for a TTL to have a value not precisely equal to the expected
   * value.
   * When {@code false}, a TTL value greater than zero and less than or equal to the expected TTL is allowed without
   * warning.
   */
  public boolean isStrictTtl() {
    return strictTtl;
  }

  /**
   * Gets the overall status of the this nameserver based on all resourceNodes that use this nameserver.
   */
  public ResourceStatus getStatus() {
    ResourceStatus status = ResourceStatus.UNKNOWN;
    for (Resource<?, ?> resource : cluster.getResources()) {
      ResourceDnsResult resourceDnsResult = resource.getDnsMonitor().getLastResult();
      Map<? extends Name, ? extends Map<? extends Nameserver, ? extends DnsLookupResult>> masterDnsLookups = resourceDnsResult.getMasterRecordLookups();
      if (masterDnsLookups != null) {
        for (Map<? extends Nameserver, ? extends DnsLookupResult> lookups : masterDnsLookups.values()) {
          DnsLookupResult lookup = lookups.get(this);
          if (lookup != null) {
            status = AppCluster.max(status, lookup.getStatus().getResourceStatus());
          }
        }
      }

      for (ResourceNodeDnsResult nodeDnsResult : resourceDnsResult.getNodeResultMap().values()) {
        Map<? extends Name, ? extends Map<? extends Nameserver, ? extends DnsLookupResult>> nodeDnsLookups = nodeDnsResult.getNodeRecordLookups();
        if (nodeDnsLookups != null) {
          for (Map<? extends Nameserver, ? extends DnsLookupResult> lookups : nodeDnsLookups.values()) {
            DnsLookupResult lookup = lookups.get(this);
            if (lookup != null) {
              status = AppCluster.max(status, lookup.getStatus().getResourceStatus());
            }
          }
        }
      }
    }
    return status;
  }
}
