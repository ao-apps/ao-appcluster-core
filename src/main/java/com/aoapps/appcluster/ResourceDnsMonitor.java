/*
 * ao-appcluster-core - Application-level clustering tools.
 * Copyright (C) 2011, 2015, 2016, 2018, 2019, 2020, 2021, 2022, 2025  AO Industries, Inc.
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
import com.aoapps.lang.Strings;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.lang.util.ErrorPrinter;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 * Monitors the status of a resource by monitoring its role based on DNS entries
 * Monitors DNS entries to determine which nodes are masters and which are slaves
 * while being careful to detect any inconsistent states.
 *
 * @author  AO Industries, Inc.
 */
public class ResourceDnsMonitor {

  private static final Logger logger = Logger.getLogger(ResourceDnsMonitor.class.getName());

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, ResourceDnsMonitor.class);

  private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;

  /**
   * Checks the DNS settings once every 30 seconds.
   */
  public static final Duration DNS_CHECK_INTERVAL = Duration.ofSeconds(30);

  /**
   * The number of DNS tries.
   */
  public static final int DNS_ATTEMPTS = 2;

  /**
   * DNS queries time-out at 30 seconds.
   */
  public static final Duration DNS_CHECK_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Only one resolver will be created for each unique nameserver (case-insensitive on unique).
   */
  private static final ConcurrentMap<Nameserver, SimpleResolver> resolvers = new ConcurrentHashMap<>();

  private static SimpleResolver getSimpleResolver(Nameserver hostname) throws UnknownHostException {
    SimpleResolver resolver = resolvers.get(hostname);
    if (resolver == null) {
      resolver = new SimpleResolver(hostname.toString());
      resolver.setTimeout(DNS_CHECK_TIMEOUT);
      SimpleResolver existing = resolvers.putIfAbsent(hostname, resolver);
      if (existing != null) {
        resolver = existing;
      }
    }
    return resolver;
  }

  /**
   * Gets a mapping for all nodes with the same status.
   */
  private static Map<? extends Node, ? extends ResourceNodeDnsResult> getNodeResults(
      Resource<?, ?> resource,
      Map<? extends Name, ? extends Map<? extends Nameserver, ? extends DnsLookupResult>> nodeRecordLookups,
      NodeDnsStatus nodeStatus,
      Collection<String> nodeStatusMessages
  ) {
    Set<? extends ResourceNode<?, ?>> resourceNodes = resource.getResourceNodes();
    Map<Node, ResourceNodeDnsResult> nodeResults = AoCollections.newHashMap(resourceNodes.size());
    for (ResourceNode<?, ?> resourceNode : resourceNodes) {
      nodeResults.put(
          resourceNode.getNode(),
          new ResourceNodeDnsResult(
              resourceNode,
              nodeRecordLookups,
              nodeStatus,
              nodeStatusMessages
          )
      );
    }
    return nodeResults;
  }

  private final Resource<?, ?> resource;

  private final Object threadLock = new Object();
  private Thread thread; // All access uses threadLock
  private ResourceDnsResult lastResult; // All access uses threadLock

  ResourceDnsMonitor(Resource<?, ?> resource) {
    this.resource = resource;
    long currentTime = System.currentTimeMillis();
    this.lastResult = new ResourceDnsResult(
        resource,
        currentTime,
        currentTime,
        null,
        MasterDnsStatus.STOPPED,
        null,
        getNodeResults(resource, null, NodeDnsStatus.STOPPED, null)
    );
  }

  /**
   * Gets the resource this monitor is for.
   */
  public Resource<?, ?> getResource() {
    return resource;
  }

  private void setDnsResult(ResourceDnsResult newResult) {
    assert Thread.holdsLock(threadLock);
    ResourceDnsResult oldResult = this.lastResult;
    this.lastResult = newResult;

    // Notify listeners
    resource.getCluster().notifyResourceListenersOnDnsResult(oldResult, newResult);
  }

  /**
   * Gets the last result.
   */
  public ResourceDnsResult getLastResult() {
    synchronized (threadLock) {
      return lastResult;
    }
  }

  private static final Name[] emptySearchPath = new Name[0];

  /**
   * If both the cluster and this resource are enabled, starts the resource DNS monitor.
   */
  @SuppressWarnings({"NestedSynchronizedStatement", "UseSpecificCatch", "TooBroadCatch", "SleepWhileHoldingLock", "SleepWhileInLoop"})
  void start() {
    synchronized (threadLock) {
      if (!resource.getCluster().isEnabled()) {
        long currentTime = System.currentTimeMillis();
        Collection<String> messages = Collections.singleton(RESOURCES.getMessage("start.clusterDisabled.statusMessage"));
        setDnsResult(
            new ResourceDnsResult(
                resource,
                currentTime,
                currentTime,
                null,
                MasterDnsStatus.DISABLED,
                messages,
                getNodeResults(resource, null, NodeDnsStatus.DISABLED, messages)
            )
        );
      } else if (!resource.isEnabled()) {
        long currentTime = System.currentTimeMillis();
        Collection<String> messages = Collections.singleton(RESOURCES.getMessage("start.resourceDisabled.statusMessage"));
        setDnsResult(
            new ResourceDnsResult(
                resource,
                currentTime,
                currentTime,
                null,
                MasterDnsStatus.DISABLED,
                messages,
                getNodeResults(resource, null, NodeDnsStatus.DISABLED, messages)
            )
        );
      } else {
        if (thread == null) {
          long currentTime = System.currentTimeMillis();
          Collection<String> unknownMessage = Collections.singleton(RESOURCES.getMessage("start.newThread.statusMessage"));
          Collection<String> nodeDisabledMessages = Collections.singleton(RESOURCES.getMessage("nodeDisabled"));
          Set<? extends ResourceNode<?, ?>> resourceNodes = resource.getResourceNodes();
          Map<Node, ResourceNodeDnsResult> nodeResults = AoCollections.newHashMap(resourceNodes.size());
          for (ResourceNode<?, ?> resourceNode : resourceNodes) {
            Node node = resourceNode.getNode();
            if (node.isEnabled()) {
              nodeResults.put(
                  node,
                  new ResourceNodeDnsResult(resourceNode, null, NodeDnsStatus.STARTING, unknownMessage)
              );
            } else {
              nodeResults.put(
                  node,
                  new ResourceNodeDnsResult(resourceNode, null, NodeDnsStatus.DISABLED, nodeDisabledMessages)
              );
            }
          }
          setDnsResult(
              new ResourceDnsResult(
                  resource,
                  currentTime,
                  currentTime,
                  null,
                  MasterDnsStatus.STARTING,
                  unknownMessage,
                  nodeResults
              )
          );
          final ExecutorService executorService = resource.getCluster().getExecutorService();
          thread = new Thread(
              () -> {
                final Thread currentThread = Thread.currentThread();
                final Set<? extends Name> masterRecords = resource.getMasterRecords();
                final int masterRecordsTtl = resource.getMasterRecordsTtl();
                final boolean allowMultiMaster = resource.getAllowMultiMaster();
                final Nameserver[] enabledNameservers = resource.getEnabledNameservers().toArray(new Nameserver[resource.getEnabledNameservers().size()]);

                final ResourceNode<?, ?>[] _resourceNodes = resource.getResourceNodes().toArray(new ResourceNode<?, ?>[resource.getResourceNodes().size()]);

                // Find all the unique hostnames and nameservers that will be queried
                final Name[] allHostnames;
                  {
                    final Set<Name> allHostnamesSet = new HashSet<>();
                    allHostnamesSet.addAll(masterRecords);
                    for (ResourceNode<?, ?> resourceNode : _resourceNodes) {
                      if (resourceNode.getNode().isEnabled()) {
                        allHostnamesSet.addAll(resourceNode.getNodeRecords());
                      }
                    }
                    allHostnames = allHostnamesSet.toArray(new Name[allHostnamesSet.size()]);
                  }

                while (!Thread.currentThread().isInterrupted()) {
                  synchronized (threadLock) {
                    if (currentThread != thread) {
                      break;
                    }
                  }
                  try {
                    long startTime = System.currentTimeMillis();

                    // Query all enabled nameservers for all involved dns entries in parallel, getting all A records
                    final Map<Name, Map<Nameserver, Future<DnsLookupResult>>> allHostnameFutures = AoCollections.newHashMap(allHostnames.length);
                    for (final Name hostname : allHostnames) {
                      Map<Nameserver, Future<DnsLookupResult>> hostnameFutures = AoCollections.newHashMap(enabledNameservers.length);
                      allHostnameFutures.put(hostname, hostnameFutures);
                      for (final Nameserver nameserver : enabledNameservers) {
                        hostnameFutures.put(
                            nameserver,
                            executorService.submit(() -> {
                              try {
                                for (int attempt = 0;
                                    attempt < DNS_ATTEMPTS && !Thread.currentThread().isInterrupted();
                                    attempt++
                                ) {
                                  Lookup lookup = new Lookup(hostname, Type.A);
                                  lookup.setCache(null);
                                  lookup.setResolver(getSimpleResolver(nameserver));
                                  lookup.setSearchPath(emptySearchPath);
                                  Record[] records = lookup.run();
                                  int result = lookup.getResult();
                                  switch (result) {
                                    case Lookup.SUCCESSFUL:
                                      if (records == null || records.length == 0) {
                                        return new DnsLookupResult(
                                            hostname,
                                            DnsLookupStatus.HOST_NOT_FOUND,
                                            null,
                                            null
                                        );
                                      }
                                      String[] addresses = new String[records.length];
                                      Collection<String> statusMessages = null;
                                      for (int c = 0; c < records.length; c++) {
                                        ARecord arecord = (ARecord) records[c];
                                        // Verify masterDomain TTL settings match expected values, issue as a warning
                                        if (masterRecords.contains(hostname)) {
                                          long ttl = arecord.getTTL();
                                          if (nameserver.isStrictTtl()) {
                                            if (ttl != masterRecordsTtl) {
                                              if (statusMessages == null) {
                                                statusMessages = new ArrayList<>();
                                              }
                                              statusMessages.add(RESOURCES.getMessage("lookup.unexpectedTtl.strictTtl", masterRecordsTtl, ttl));
                                            }
                                          } else if (ttl <= 0 || ttl > masterRecordsTtl) {
                                            if (statusMessages == null) {
                                              statusMessages = new ArrayList<>();
                                            }
                                            statusMessages.add(RESOURCES.getMessage("lookup.unexpectedTtl", masterRecordsTtl, ttl));
                                          }
                                        }
                                        addresses[c] = arecord.getAddress().getHostAddress();
                                      }
                                      return new DnsLookupResult(
                                          hostname,
                                          statusMessages == null ? DnsLookupStatus.SUCCESSFUL : DnsLookupStatus.WARNING,
                                          statusMessages,
                                          addresses
                                      );
                                    case Lookup.UNRECOVERABLE:
                                      return new DnsLookupResult(
                                          hostname,
                                          DnsLookupStatus.UNRECOVERABLE,
                                          null,
                                          null
                                      );
                                    case Lookup.TRY_AGAIN:
                                      // fall-through to try again loop
                                      break;
                                    case Lookup.HOST_NOT_FOUND:
                                      return new DnsLookupResult(
                                          hostname,
                                          DnsLookupStatus.HOST_NOT_FOUND,
                                          null,
                                          null
                                      );
                                    case Lookup.TYPE_NOT_FOUND:
                                      return new DnsLookupResult(
                                          hostname,
                                          DnsLookupStatus.TYPE_NOT_FOUND,
                                          null,
                                          null
                                      );
                                    default:
                                      return new DnsLookupResult(
                                          hostname,
                                          DnsLookupStatus.ERROR,
                                          Collections.singleton(RESOURCES.getMessage("lookup.unexpectedResultCode", result)),
                                          null
                                      );
                                  }
                                }
                                return new DnsLookupResult(
                                    hostname,
                                    DnsLookupStatus.TRY_AGAIN,
                                    null,
                                    null
                                );
                              } catch (ThreadDeath td) {
                                throw td;
                              } catch (Throwable t) {
                                return new DnsLookupResult(
                                    hostname,
                                    DnsLookupStatus.ERROR,
                                    Collections.singleton(ErrorPrinter.getStackTraces(t)),
                                    null
                                );
                              }
                            })
                        );
                      }
                    }

                    // Get all the masterRecord results
                    Map<Name, Map<Nameserver, DnsLookupResult>> masterRecordLookups = AoCollections.newHashMap(masterRecords.size());
                    MasterDnsStatus masterStatus = MasterDnsStatus.CONSISTENT;
                    List<String> masterStatusMessages = new ArrayList<>();
                    Nameserver firstMasterNameserver = null;
                    Name firstMasterRecord = null;
                    Set<String> firstMasterAddresses = null;
                    for (Name masterRecord : masterRecords) {
                      Map<Nameserver, DnsLookupResult> masterLookups = AoCollections.newHashMap(enabledNameservers.length);
                      masterRecordLookups.put(masterRecord, masterLookups);
                      Map<Nameserver, Future<DnsLookupResult>> masterFutures = allHostnameFutures.get(masterRecord);
                      boolean foundSuccessful = false;
                      for (Nameserver enabledNameserver : enabledNameservers) {
                        try {
                          DnsLookupResult result = masterFutures.get(enabledNameserver).get();
                          masterLookups.put(enabledNameserver, result);
                          if (result.getStatus() == DnsLookupStatus.SUCCESSFUL || result.getStatus() == DnsLookupStatus.WARNING) {
                            if (result.getStatus() == DnsLookupStatus.WARNING) {
                              masterStatus = AppCluster.max(masterStatus, MasterDnsStatus.WARNING);
                            }
                            foundSuccessful = true;
                            Set<String> addresses = result.getAddresses();
                            // Check for multi-master violation
                            if (addresses.size() > 1 && !allowMultiMaster) {
                              masterStatus = AppCluster.max(masterStatus, MasterDnsStatus.INCONSISTENT);
                              masterStatusMessages.add(
                                  RESOURCES.getMessage(
                                      "masterRecord.multiMasterNotAllowed",
                                      enabledNameserver,
                                      Strings.join(addresses, ", ")
                                  )
                              );
                            }
                            if (firstMasterAddresses == null) {
                              firstMasterNameserver = enabledNameserver;
                              firstMasterRecord = masterRecord;
                              firstMasterAddresses = addresses;
                            } else {
                              // All multi-record masters must have the same IP address(es) within a single node (like for domain aliases)
                              if (!firstMasterAddresses.equals(addresses)) {
                                masterStatus = AppCluster.max(masterStatus, MasterDnsStatus.INCONSISTENT);
                                masterStatusMessages.add(
                                    RESOURCES.getMessage(
                                        "multiRecordMaster.mismatch",
                                        firstMasterNameserver,
                                        firstMasterRecord,
                                        Strings.join(firstMasterAddresses, ", "),
                                        enabledNameserver,
                                        masterRecord,
                                        Strings.join(addresses, ", ")
                                    )
                                );
                              }
                            }
                          }
                        } catch (ThreadDeath td) {
                          throw td;
                        } catch (Throwable t) {
                          masterLookups.put(
                              enabledNameserver,
                              new DnsLookupResult(
                                  masterRecord,
                                  DnsLookupStatus.UNRECOVERABLE,
                                  Collections.singleton(ErrorPrinter.getStackTraces(t)),
                                  null
                              )
                          );
                        }
                      }
                      // Make sure we got at least one response for every master
                      if (!foundSuccessful) {
                        masterStatus = AppCluster.max(masterStatus, MasterDnsStatus.INCONSISTENT);
                        masterStatusMessages.add(RESOURCES.getMessage("masterRecord.missing", masterRecord));
                      }
                    }
                    assert firstMasterAddresses != null;

                    // Get the results for each node
                    Map<Node, ResourceNodeDnsResult> myNodeResults = AoCollections.newHashMap(_resourceNodes.length);
                    Set<String> allNodeAddresses = AoCollections.newHashSet(_resourceNodes.length);
                    for (ResourceNode<?, ?> resourceNode :  _resourceNodes) {
                      Node node = resourceNode.getNode();
                      if (node.isEnabled()) {
                        Set<? extends Name> nodeRecords = resourceNode.getNodeRecords();
                        Map<Name, Map<Nameserver, DnsLookupResult>> nodeRecordLookups = AoCollections.newHashMap(nodeRecords.size());
                        NodeDnsStatus nodeStatus = NodeDnsStatus.SLAVE;
                        List<String> nodeStatusMessages = new ArrayList<>();
                        Nameserver firstNodeNameserver = null;
                        Name firstNodeRecord = null;
                        Set<String> firstNodeAddresses = null;
                        for (Name nodeRecord : resourceNode.getNodeRecords()) {
                          Map<Nameserver, DnsLookupResult> nodeLookups = AoCollections.newHashMap(enabledNameservers.length);
                          nodeRecordLookups.put(nodeRecord, nodeLookups);
                          Map<Nameserver, Future<DnsLookupResult>> nodeFutures = allHostnameFutures.get(nodeRecord);
                          boolean foundSuccessful = false;
                          for (Nameserver enabledNameserver : enabledNameservers) {
                            try {
                              DnsLookupResult result = nodeFutures.get(enabledNameserver).get();
                              nodeLookups.put(enabledNameserver, result);
                              if (result.getStatus() == DnsLookupStatus.SUCCESSFUL || result.getStatus() == DnsLookupStatus.WARNING) {
                                foundSuccessful = true;
                                Set<String> addresses = result.getAddresses();
                                allNodeAddresses.addAll(addresses);
                                // Must be only one A record
                                if (addresses.size() > 1) {
                                  nodeStatus = NodeDnsStatus.INCONSISTENT;
                                  nodeStatusMessages.add(
                                      RESOURCES.getMessage(
                                          "nodeRecord.onlyOneAllowed",
                                          Strings.join(addresses, ", ")
                                      )
                                  );
                                } else {
                                  // Each node must have a different A record
                                  String address = addresses.iterator().next();
                                  for (ResourceNodeDnsResult previousNodeResult :  myNodeResults.values()) {
                                    Map<? extends Name, ? extends Map<? extends Nameserver, ? extends DnsLookupResult>> previousNodeRecordLookups = previousNodeResult.getNodeRecordLookups();
                                    if (previousNodeRecordLookups != null) {
                                      boolean foundMatch = false;
                                      MATCH_LOOP:
                                      for (Map<? extends Nameserver, ? extends DnsLookupResult> previousLookups : previousNodeRecordLookups.values()) {
                                        for (DnsLookupResult previousResult : previousLookups.values()) {
                                          if (previousResult.getAddresses().contains(address)) {
                                            foundMatch = true;
                                            break MATCH_LOOP;
                                          }
                                        }
                                      }
                                      if (foundMatch) {
                                        Node previousNode = previousNodeResult.getResourceNode().getNode();
                                        nodeStatus = NodeDnsStatus.INCONSISTENT;
                                        nodeStatusMessages.add(
                                            RESOURCES.getMessage(
                                                "nodeRecord.duplicateA",
                                                previousNode,
                                                nodeRecord,
                                                address
                                            )
                                        );
                                        // Re-add the previous with inconsistent state and additional message
                                        List<String> newNodeStatusMessages = new ArrayList<>(previousNodeResult.getNodeStatusMessages());
                                        newNodeStatusMessages.add(
                                            RESOURCES.getMessage(
                                                "nodeRecord.duplicateA",
                                                nodeRecord,
                                                previousNode,
                                                address
                                            )
                                        );
                                        myNodeResults.put(
                                            previousNode,
                                            new ResourceNodeDnsResult(
                                                previousNodeResult.getResourceNode(),
                                                previousNodeResult.getNodeRecordLookups(),
                                                NodeDnsStatus.INCONSISTENT,
                                                newNodeStatusMessages
                                            )
                                        );
                                      }
                                    }
                                  }
                                }
                                if (firstNodeAddresses == null) {
                                  firstNodeNameserver = enabledNameserver;
                                  firstNodeRecord = nodeRecord;
                                  firstNodeAddresses = addresses;
                                } else {
                                  // All multi-record nodes must have the same IP address within a single node (like for domain aliases)
                                  if (!firstNodeAddresses.equals(addresses)) {
                                    nodeStatus = NodeDnsStatus.INCONSISTENT;
                                    nodeStatusMessages.add(
                                        RESOURCES.getMessage(
                                            "multiRecordNode.mismatch",
                                            firstNodeNameserver,
                                            firstNodeRecord,
                                            Strings.join(firstNodeAddresses, ", "),
                                            enabledNameserver,
                                            nodeRecord,
                                            Strings.join(addresses, ", ")
                                        )
                                    );
                                  }
                                }
                              }
                            } catch (ThreadDeath td) {
                              throw td;
                            } catch (Throwable t) {
                              nodeLookups.put(
                                  enabledNameserver,
                                  new DnsLookupResult(
                                      nodeRecord,
                                      DnsLookupStatus.UNRECOVERABLE,
                                      Collections.singleton(ErrorPrinter.getStackTraces(t)),
                                      null
                                  )
                              );
                            }
                          }
                          // Make sure we got at least one response for every node
                          if (!foundSuccessful) {
                            nodeStatus = NodeDnsStatus.INCONSISTENT;
                            nodeStatusMessages.add(RESOURCES.getMessage("nodeRecord.missing", nodeRecord));
                          }
                        }
                        // If master and node are both consistent and matches any master A record, promote to master
                        if ((masterStatus == MasterDnsStatus.CONSISTENT || masterStatus == MasterDnsStatus.WARNING) && nodeStatus == NodeDnsStatus.SLAVE) {
                          if (firstMasterAddresses.containsAll(firstNodeAddresses)) {
                            nodeStatus = NodeDnsStatus.MASTER;
                          }
                        }
                        myNodeResults.put(
                            node,
                            new ResourceNodeDnsResult(
                                resourceNode,
                                nodeRecordLookups,
                                nodeStatus,
                                nodeStatusMessages
                            )
                        );
                      } else {
                        // Node disabled
                        myNodeResults.put(
                            node,
                            new ResourceNodeDnsResult(
                                resourceNode,
                                null,
                                NodeDnsStatus.DISABLED,
                                Collections.singleton(RESOURCES.getMessage("nodeDisabled"))
                            )
                        );
                      }
                    }

                    // Inconsistent if any master A record is outside the expected nodeDomains
                    for (Name masterRecord : masterRecords) {
                      for (Map<Nameserver, DnsLookupResult> masterLookups : masterRecordLookups.values()) {
                        for (DnsLookupResult masterResult : masterLookups.values()) {
                          for (String masterAddress : masterResult.getAddresses()) {
                            if (!allNodeAddresses.contains(masterAddress)) {
                              masterStatus = AppCluster.max(masterStatus, MasterDnsStatus.INCONSISTENT);
                              masterStatusMessages.add(
                                  RESOURCES.getMessage(
                                      "masterARecordDoesntMatchNode",
                                      masterRecord,
                                      masterAddress
                                  )
                              );
                            }
                          }
                        }
                      }
                    }

                    synchronized (threadLock) {
                      if (currentThread != thread) {
                        break;
                      }
                      setDnsResult(
                          new ResourceDnsResult(
                              resource,
                              startTime,
                              System.currentTimeMillis(),
                              masterRecordLookups,
                              masterStatus,
                              masterStatusMessages,
                              myNodeResults
                          )
                      );
                    }
                  } catch (RejectedExecutionException exc) {
                    // Normal during shutdown
                    boolean needsLogged;
                    synchronized (threadLock) {
                      needsLogged = currentThread == thread;
                    }
                    if (needsLogged) {
                      logger.log(Level.SEVERE, null, exc);
                    }
                  } catch (ThreadDeath td) {
                    throw td;
                  } catch (Throwable t) {
                    logger.log(Level.SEVERE, null, t);
                  }
                  try {
                    Thread.sleep(DNS_CHECK_INTERVAL.toMillis(), DNS_CHECK_INTERVAL.getNano() % 1000_000);
                  } catch (InterruptedException exc) {
                    logger.log(Level.WARNING, null, exc);
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                  }
                }
              },
              "PropertiesConfiguration.fileMonitorThread"
          );
          thread.setPriority(THREAD_PRIORITY);
          thread.start();
        }
      }
    }
  }

  /**
   * Stops this resource DNS monitor.
   */
  void stop() {
    long currentTime = System.currentTimeMillis();
    synchronized (threadLock) {
      thread = null;
      setDnsResult(
          new ResourceDnsResult(
              resource,
              currentTime,
              currentTime,
              null,
              MasterDnsStatus.STOPPED,
              null,
              getNodeResults(resource, null, NodeDnsStatus.STOPPED, null)
          )
      );
    }
  }
}
