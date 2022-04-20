/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2015, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoapps.cron.MatcherSchedule;
import com.aoapps.cron.Schedule;
import com.aoapps.lang.Strings;
import com.aoapps.lang.Throwables;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.lang.util.PropertiesUtils;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;

/**
 * The configuration is provided in a properties file.
 *
 * @author  AO Industries, Inc.
 */
public class AppClusterPropertiesConfiguration implements AppClusterConfiguration {

  private static final Logger logger = Logger.getLogger(AppClusterPropertiesConfiguration.class.getName());

  private static final Resources RESOURCES =
    Resources.getResources(ResourceBundle::getBundle, AppClusterPropertiesConfiguration.class);

  private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY + 1;

  /**
   * Checks once every five seconds for configuration file updates.
   */
  private static final long FILE_CHECK_INTERVAL = 5000;

  private final List<AppClusterConfigurationListener> listeners = new ArrayList<>();

  private final File file;

  private final Object fileMonitorLock = new Object();
  private Thread fileMonitorThread; // All access uses fileMonitorLock
  private long fileLastModified; // All access uses fileMonitorLock
  private Properties properties; // All access uses fileMonitorLock

  /**
   * Loads the properties from the provided file.  Will detect changes in the
   * file based on modified time, checking once every FILE_CHECK_INTERVAL milliseconds.
   */
  public AppClusterPropertiesConfiguration(File file) {
    this.file = file;
    this.properties = null;
  }

  /**
   * Uses the provided configuration.  No changes to the properties will be detected.
   */
  public AppClusterPropertiesConfiguration(Properties properties) {
    this.file = null;
    // Make defensive copy
    this.properties = new Properties();
    for (String key : properties.stringPropertyNames()) {
      this.properties.setProperty(key, properties.getProperty(key));
    }
  }

  @Override
  @SuppressWarnings({"NestedSynchronizedStatement", "SleepWhileInLoop", "SleepWhileHoldingLock", "UseSpecificCatch", "TooBroadCatch"})
  public void start() throws AppClusterConfigurationException {
    if (file != null) {
      try {
        synchronized (fileMonitorLock) {
          if (fileMonitorThread == null) {
            // Load initial properties
            fileLastModified = file.lastModified();
            this.properties = PropertiesUtils.loadFromFile(file);
            fileMonitorThread = new Thread(
              () -> {
                final Thread currentThread = Thread.currentThread();
                while (!currentThread.isInterrupted()) {
                  try {
                    try {
                      Thread.sleep(FILE_CHECK_INTERVAL);
                    } catch (InterruptedException exc) {
                      logger.log(Level.WARNING, null, exc);
                      // Restore the interrupted status
                      currentThread.interrupt();
                      break;
                    }
                    boolean notifyListeners = false;
                    synchronized (fileMonitorLock) {
                      if (currentThread != fileMonitorThread || currentThread.isInterrupted()) {
                        break;
                      }
                      long newLastModified = file.lastModified();
                      if (newLastModified != fileLastModified) {
                        // Reload the configuration
                        fileLastModified = newLastModified;
                        Properties newProperties = PropertiesUtils.loadFromFile(file);
                        AppClusterPropertiesConfiguration.this.properties = newProperties;
                        notifyListeners = true;
                      }
                    }
                    if (notifyListeners) {
                      synchronized (listeners) {
                        for (AppClusterConfigurationListener listener : listeners) {
                          try {
                            listener.onConfigurationChanged();
                          } catch (ThreadDeath td) {
                            throw td;
                          } catch (Throwable t) {
                            logger.log(Level.SEVERE, null, t);
                          }
                        }
                      }
                    }
                  } catch (ThreadDeath td) {
                    throw td;
                  } catch (Throwable t) {
                    logger.log(Level.SEVERE, null, t);
                  }
                }
              },
              AppClusterPropertiesConfiguration.class.getName()+".fileMonitorThread"
            );
            fileMonitorThread.setPriority(THREAD_PRIORITY);
            fileMonitorThread.start();
          }
        }
      } catch (Throwable t) {
        throw Throwables.wrap(t, AppClusterConfigurationException.class, AppClusterConfigurationException::new);
      }
    }
  }

  @Override
  public void stop() {
    if (file != null) {
      synchronized (fileMonitorLock) {
        fileMonitorThread = null;
        properties = null;
      }
    }
  }

  @Override
  public void addConfigurationListener(AppClusterConfigurationListener listener) {
    synchronized (listeners) {
      boolean found = false;
      for (AppClusterConfigurationListener existing : listeners) {
        if (existing == listener) {
          found = true;
          break;
        }
      }
      if (!found) {
        listeners.add(listener);
      }
    }
  }

  @Override
  @SuppressWarnings("AssignmentToForLoopParameter")
  public void removeConfigurationListener(AppClusterConfigurationListener listener) {
    synchronized (listeners) {
      for (int i=0; i<listeners.size(); i++) {
        if (listeners.get(i) == listener) {
          listeners.remove(i--);
        }
      }
    }
  }

  /**
   * Gets a trimmed property value, if required, not allowing null or empty string.
   * If not required, will convert empty string to null.
   */
  public String getString(String propertyName, boolean required) throws AppClusterConfigurationException {
    String value;
    synchronized (fileMonitorLock) {
      value = properties.getProperty(propertyName);
    }
    if (value == null || (value=value.trim()).length() == 0) {
      if (required) {
        throw new AppClusterConfigurationException(RESOURCES.getMessage("getString.missingValue", propertyName));
      } else {
        value = null;
      }
    }
    return value;
  }

  public boolean getBoolean(String propertyName) throws AppClusterConfigurationException {
    String value = getString(propertyName, true);
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new AppClusterConfigurationException(RESOURCES.getMessage("getBoolean.invalidValue", propertyName, value));
  }

  public int getInt(String propertyName) throws AppClusterConfigurationException {
    String value = getString(propertyName, true);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exc) {
      throw new AppClusterConfigurationException(RESOURCES.getMessage("getInt.invalidValue", propertyName, value));
    }
  }

  public Name getName(String propertyName) throws AppClusterConfigurationException {
    try {
      return Name.fromString(getString(propertyName, true));
    } catch (TextParseException exc) {
      throw new AppClusterConfigurationException(exc);
    }
  }

  public Schedule getSchedule(String propertyName) throws AppClusterConfigurationException {
    try {
      return MatcherSchedule.parseSchedule(getString(propertyName, true));
    } catch (IllegalArgumentException exc) {
      throw new AppClusterConfigurationException(exc);
    }
  }

  /**
   * Gets a unique set of trimmed strings.  Must have at least one value when required.
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public Set<String> getUniqueStrings(String propertyName, boolean required) throws AppClusterConfigurationException {
    String paramValue = getString(propertyName, required);
    if (paramValue == null) {
      return Collections.emptySet();
    }
    List<String> values = Strings.splitCommaSpace(paramValue);
    Set<String> set = AoCollections.newLinkedHashSet(values.size());
    for (String value : values) {
      value = value.trim();
      if (value.length()>0 && !set.add(value)) {
        throw new AppClusterConfigurationException(
          RESOURCES.getMessage("getStrings.duplicate", propertyName, value)
        );
      }
    }
    if (required && set.isEmpty()) {
      throw new AppClusterConfigurationException(RESOURCES.getMessage("getString.missingValue", propertyName));
    }
    return AoCollections.optimalUnmodifiableSet(set);
  }

  /**
   * Gets a unique set of trimmed names.  Must have at least one value.
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public Set<? extends Name> getUniqueNames(String propertyName) throws AppClusterConfigurationException {
    try {
      List<String> values = Strings.splitCommaSpace(getString(propertyName, true));
      Set<Name> set = AoCollections.newLinkedHashSet(values.size());
      for (String value : values) {
        value = value.trim();
        if (value.length()>0 && !set.add(Name.fromString(value))) {
          throw new AppClusterConfigurationException(
            RESOURCES.getMessage("getStrings.duplicate", propertyName, value)
          );
        }
      }
      if (set.isEmpty()) {
        throw new AppClusterConfigurationException(RESOURCES.getMessage("getString.missingValue", propertyName));
      }
      return AoCollections.optimalUnmodifiableSet(set);
    } catch (TextParseException exc) {
      throw new AppClusterConfigurationException(exc);
    }
  }

  @Override
  public boolean isEnabled() throws AppClusterConfigurationException {
    return getBoolean("appcluster.enabled");
  }

  @Override
  public String getDisplay() throws AppClusterConfigurationException {
    return getString("appcluster.display", true);
  }

  @Override
  public Set<? extends NodePropertiesConfiguration> getNodeConfigurations() throws AppClusterConfigurationException {
    Set<String> ids = getUniqueStrings("appcluster.nodes", true);
    Set<NodePropertiesConfiguration> nodes = AoCollections.newLinkedHashSet(ids.size());
    for (String id : ids) {
      if (
        !nodes.add(new NodePropertiesConfiguration(this, id))
      ) {
        throw new AssertionError();
      }
    }
    return AoCollections.optimalUnmodifiableSet(nodes);
  }

  private static final Map<String, ResourcePropertiesConfigurationFactory<?, ?>> factoryCache = new HashMap<>();
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  private static ResourcePropertiesConfigurationFactory<?, ?> getResourcePropertiesConfigurationFactory(String classname) throws AppClusterConfigurationException {
    synchronized (factoryCache) {
      ResourcePropertiesConfigurationFactory<?, ?> factory = factoryCache.get(classname);
      if (factory == null) {
        try {
          try {
            factory = Class.forName(classname).asSubclass(ResourcePropertiesConfigurationFactory.class).getConstructor().newInstance();
            factoryCache.put(classname, factory);
          } catch (InvocationTargetException e) {
            // Unwrap cause for more direct stack traces
            Throwable cause = e.getCause();
            throw (cause == null) ? e : cause;
          }
        } catch (Throwable t) {
          throw Throwables.wrap(t, AppClusterConfigurationException.class, AppClusterConfigurationException::new);
        }
      }
      return factory;
    }
  }

  @Override
  public Set<? extends ResourceConfiguration<?, ?>> getResourceConfigurations() throws AppClusterConfigurationException {
    // Get all of the resource types
    Set<String> types = getUniqueStrings("appcluster.resourceTypes", true);
    Map<String, ResourcePropertiesConfigurationFactory<?, ?>> factories = AoCollections.newHashMap(types.size());
    for (String type : types) {
      factories.put(type, getResourcePropertiesConfigurationFactory(getString("appcluster.resourceType."+type+".factory", true)));
    }
    Set<String> ids = getUniqueStrings("appcluster.resources", true);
    Set<ResourceConfiguration<?, ?>> resources = AoCollections.newLinkedHashSet(ids.size());
    for (String id : ids) {
      String propertyName = "appcluster.resource."+id+".type";
      String type = getString(propertyName, true);
      ResourcePropertiesConfigurationFactory<?, ?> factory = factories.get(type);
      if (factory == null) {
        throw new AppClusterConfigurationException(RESOURCES.getMessage("getResourceConfigurations.unexpectedType", propertyName, type));
      }
      if (!resources.add(factory.newResourcePropertiesConfiguration(this, id))) {
        throw new AssertionError();
      }
    }
    return AoCollections.optimalUnmodifiableSet(resources);
  }
}
