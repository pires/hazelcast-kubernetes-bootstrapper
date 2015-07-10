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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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
    public String ip;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Subset {
    public List<Address> addresses;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Endpoints {
    public List<Subset> subsets;
  }

  private static String getServiceAccountToken() throws IOException {
    String file = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    return new String(Files.readAllBytes(Paths.get(file)));
  }

  private static String getEnvOrDefault(String var, String def) {
    final String val = System.getenv(var);
    return (val == null || val.isEmpty())
        ? def
        : val;
  }

  // TODO: Load the CA cert when it is available on all platforms.
  private static TrustManager[] trustAll = new TrustManager[] {
    new X509TrustManager() {
      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      public void checkClientTrusted(X509Certificate[] certs, String authType) {}
      public X509Certificate[] getAcceptedIssuers() { return null; }
    }
  };
  private static HostnameVerifier trustAllHosts = new HostnameVerifier() {
    public boolean verify(String hostname, SSLSession session) {
      return true;
    }
  };

  @Override
  public void run(String... args) {
    final String serviceName = getEnvOrDefault("HAZELCAST_SERVICE", "hazelcast");
    final String namespace = getEnvOrDefault("POD_NAMESPACE", "default");
    final String path = String.format("/api/v1/namespaces/%s/endpoints/", namespace);
    final String domain = getEnvOrDefault("DNS_DOMAIN", "cluster.local");
    final String host = getEnvOrDefault("KUBERNETES_MASTER", "https://kubernetes.default.svc.".concat(domain));
    log.info("Asking k8s registry at {}..", host);

    final List<String> hazelcastEndpoints = new CopyOnWriteArrayList<>();

    try {
      String token = getServiceAccountToken();

      SSLContext ctx = SSLContext.getInstance("SSL");
      ctx.init(null, trustAll, new SecureRandom());

      URL url = new URL(host + path + serviceName);
      HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
      // TODO: remove this when and replace with CA cert loading, when the CA is propogated
      // to all nodes on all platforms.
      conn.setSSLSocketFactory(ctx.getSocketFactory());
      conn.setHostnameVerifier(trustAllHosts);
      conn.addRequestProperty("Authorization", "Bearer " + token);

      ObjectMapper mapper = new ObjectMapper();
      Endpoints endpoints = mapper.readValue(conn.getInputStream(), Endpoints.class);
      if (endpoints != null) {
        if (endpoints.subsets != null && !endpoints.subsets.isEmpty()) {
          endpoints.subsets.parallelStream().forEach(subset -> {
            subset.addresses.parallelStream().forEach(
                addr -> hazelcastEndpoints.add(addr.ip));
          });
        }
      }
    } catch (IOException | NoSuchAlgorithmException | KeyManagementException ex) {
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
