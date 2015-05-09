/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.pires.hazelcast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SSLConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Controller;

/**
 * Read from Kubernetes API all Hazelcast service bound pods, get their IP and connect to them.
 */
@Controller
public class HazelcastDiscoveryController implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(
      HazelcastDiscoveryController.class);

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Address {

    public String IP;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Subset {

    public List<Address> addresses;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Endpoints {

    public List<Subset> subsets;
  }

  private static String getEnvOrDefault(String var, String def) {
    final String val = System.getenv(var);
    return (val == null || val.isEmpty())
        ? def
        : val;
  }

  @Override
  public void run(String... args) {
    final String hostName = getEnvOrDefault("KUBERNETES_RO_SERVICE_HOST",
        "localhost");
    final String hostPort = getEnvOrDefault("KUBERNETES_RO_SERVICE_PORT",
        "8080");
    String serviceName = getEnvOrDefault("HAZELCAST_SERVICE", "hazelcast");
    String path = "/api/v1beta3/namespaces/default/endpoints/";
    final String host = "http://" + hostName + ":" + hostPort;
    log.info("Asking k8s registry at {}..", host);

    final List<String> hazelcastEndpoints = new CopyOnWriteArrayList<>();

    try {
      URL url = new URL(host + path + serviceName);
      ObjectMapper mapper = new ObjectMapper();
      Endpoints endpoints = mapper.readValue(url, Endpoints.class);
      if (endpoints != null) {
        // Here is a problem point, endpoints.endpoints can be null in first node cases.
        if (endpoints.subsets != null && !endpoints.subsets.isEmpty()) {
          endpoints.subsets.parallelStream().forEach(subset -> {
            subset.addresses.parallelStream().forEach(
                addr -> hazelcastEndpoints.add(addr.IP));
          });
        }
      }
    } catch (IOException ex) {
      log.warn("Request to Kubernetes API failed", ex);
    }

    log.info("Found {} pods running Hazelcast.", hazelcastEndpoints.size());

    runHazelcast(hazelcastEndpoints);
  }

  private void runHazelcast(final List<String> nodes) {
    // configure Hazelcast instance
    final Config cfg = new Config();
    cfg.setInstanceName(UUID.randomUUID().toString());
    // group configuration
    final String HC_GROUP_NAME = getEnvOrDefault("HC_GROUP_NAME", "someGroup");
    final String HC_GROUP_PASSWORD = getEnvOrDefault("HC_GROUP_PASSWORD",
        "someSecret");
    final int HC_PORT = Integer.parseInt(getEnvOrDefault("HC_PORT", "5701"));
    cfg.setGroupConfig(new GroupConfig(HC_GROUP_NAME, HC_GROUP_PASSWORD));
    // network configuration initialization
    final NetworkConfig netCfg = new NetworkConfig();
    netCfg.setPortAutoIncrement(false);
    netCfg.setPort(HC_PORT);
    // multicast
    final MulticastConfig mcCfg = new MulticastConfig();
    mcCfg.setEnabled(false);
    // tcp
    final TcpIpConfig tcpCfg = new TcpIpConfig();
    nodes.parallelStream().forEach(tcpCfg::addMember);
    tcpCfg.setEnabled(true);
    // network join configuration
    final JoinConfig joinCfg = new JoinConfig();
    joinCfg.setMulticastConfig(mcCfg);
    joinCfg.setTcpIpConfig(tcpCfg);
    netCfg.setJoin(joinCfg);
    // ssl
    netCfg.setSSLConfig(new SSLConfig().setEnabled(false));
    // set it all
    cfg.setNetworkConfig(netCfg);
    // run
    Hazelcast.newHazelcastInstance(cfg);
  }

}
