/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2016, 2020, 2021, 2022, 2025  AO Industries, Inc.
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
import java.util.Map;
import java.util.Set;
import org.xbill.DNS.Name;

/**
 * The configuration for a node.
 *
 * @author  AO Industries, Inc.
 */
public class NodePropertiesConfiguration implements NodeConfiguration {

  protected final AppClusterPropertiesConfiguration properties;
  protected final String id;
  protected final boolean enabled;
  protected final String display;
  protected final Name hostname;
  protected final String username;
  protected final Map<? extends Name, Boolean> nameservers;

  protected NodePropertiesConfiguration(AppClusterPropertiesConfiguration properties, String id) throws AppClusterConfigurationException {
    this.properties = properties;
    this.id = id;
    this.enabled = properties.getBoolean("appcluster.node." + id + ".enabled");
    this.display = properties.getString("appcluster.node." + id + ".display", true);
    this.hostname = properties.getName("appcluster.node." + id + ".hostname");
    this.username = properties.getString("appcluster.node." + id + ".username", true);
    Set<? extends Name> nameserverNames = properties.getUniqueNames("appcluster.node." + id + ".nameservers");
    Map<Name, Boolean> newNameservers = AoCollections.newLinkedHashMap(nameserverNames.size());
    for (Name name : nameserverNames) {
      newNameservers.put(name,
          properties.getBoolean("appcluster.node." + id + ".nameserver." + name.toString(true) + ".strictTtl", true));
    }
    this.nameservers = AoCollections.optimalUnmodifiableMap(newNameservers);
  }

  @Override
  public String toString() {
    return display;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NodeConfiguration)) {
      return false;
    }
    return id.equals(((NodeConfiguration) o).getId());
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public String getDisplay() {
    return display;
  }

  @Override
  public Name getHostname() {
    return hostname;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Map<? extends Name, Boolean> getNameservers() {
    return nameservers;
  }
}
