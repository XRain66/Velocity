/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.config;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.Expose;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.proxy.util.AddressUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Velocity's configuration.
 */
@ConfigSerializable
public class VelocityConfiguration implements ProxyConfig {

  private static final Logger logger = LogManager.getLogger(VelocityConfiguration.class);

  @Expose
  private String bind = "0.0.0.0:25565";
  @Expose
  private String motd = "<aqua>A Velocity Server";
  @Expose
  private int showMaxPlayers = 500;
  @Expose
  private boolean onlineMode = true;
  @Expose
  private boolean preventClientProxyConnections = false;
  @Expose
  private PlayerInfoForwarding playerInfoForwardingMode = PlayerInfoForwarding.NONE;
  private byte[] forwardingSecret = generateRandomString(12).getBytes(StandardCharsets.UTF_8);
  @Expose
  private boolean announceForge = false;
  @Expose
  private boolean onlineModeKickExistingPlayers = false;
  @Expose
  private PingPassthroughMode pingPassthrough = PingPassthroughMode.DISABLED;
  private final Servers servers;
  private final ForcedHosts forcedHosts;
  @Expose
  private final Advanced advanced;
  @Expose
  private final Query query;
  private final Metrics metrics;
  @Expose
  private boolean enablePlayerAddressLogging = true;
  private net.kyori.adventure.text.@MonotonicNonNull Component motdAsComponent;
  private @Nullable Favicon favicon;
  @Expose
  private boolean forceKeyAuthentication = true; // Added in 1.19

  private VelocityConfiguration(Servers servers, ForcedHosts forcedHosts, Advanced advanced,
                                Query query, Metrics metrics) {
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
  }

  VelocityConfiguration(String bind, String motd, int showMaxPlayers, boolean onlineMode,
                        boolean preventClientProxyConnections, boolean announceForge,
                        PlayerInfoForwarding playerInfoForwardingMode, byte[] forwardingSecret,
                        boolean onlineModeKickExistingPlayers, PingPassthroughMode pingPassthrough,
                        boolean enablePlayerAddressLogging, Servers servers,
                        ForcedHosts forcedHosts,
                        Advanced advanced, Query query, Metrics metrics,
                        boolean forceKeyAuthentication) {
    this.bind = bind;
    this.motd = motd;
    this.showMaxPlayers = showMaxPlayers;
    this.onlineMode = onlineMode;
    this.preventClientProxyConnections = preventClientProxyConnections;
    this.announceForge = announceForge;
    this.playerInfoForwardingMode = playerInfoForwardingMode;
    this.forwardingSecret = forwardingSecret;
    this.onlineModeKickExistingPlayers = onlineModeKickExistingPlayers;
    this.pingPassthrough = pingPassthrough;
    this.enablePlayerAddressLogging = enablePlayerAddressLogging;
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
    this.forceKeyAuthentication = forceKeyAuthentication;
  }

  /**
   * Attempts to validate the configuration.
   *
   * @return {@code true} if the configuration is sound, {@code false} if not
   */
  public boolean validate() {
    boolean valid = true;

    if (bind.isEmpty()) {
      logger.error("'bind' option is empty.");
      valid = false;
    } else {
      try {
        AddressUtil.parseAddress(bind);
      } catch (IllegalArgumentException e) {
        logger.error("'bind' option does not specify a valid IP address.", e);
        valid = false;
      }
    }

    if (!onlineMode) {
      logger.warn("The proxy is running in offline mode! This is a security risk and you will NOT "
          + "receive any support!");
    }

    switch (playerInfoForwardingMode) {
      case NONE:
        logger.warn("Player info forwarding is disabled! All players will appear to be connecting "
            + "from the proxy and will have offline-mode UUIDs.");
        break;
      case MODERN:
      case BUNGEEGUARD:
        if (forwardingSecret == null || forwardingSecret.length == 0) {
          logger.error("You don't have a forwarding secret set. This is required for security.");
          valid = false;
        }
        break;
      default:
        break;
    }

    if (servers.getServers().isEmpty()) {
      logger.warn("You don't have any servers configured.");
    }

    for (Map.Entry<String, String> entry : servers.getServers().entrySet()) {
      try {
        AddressUtil.parseAddress(entry.getValue());
      } catch (IllegalArgumentException e) {
        logger.error("Server {} does not have a valid IP address.", entry.getKey(), e);
        valid = false;
      }
    }

    for (String s : servers.getAttemptConnectionOrder()) {
      if (!servers.getServers().containsKey(s)) {
        logger.error("Fallback server " + s + " is not registered in your configuration!");
        valid = false;
      }
    }

    for (Map.Entry<String, List<String>> entry : forcedHosts.getForcedHosts().entrySet()) {
      if (entry.getValue().isEmpty()) {
        logger.error("Forced host '{}' does not contain any servers", entry.getKey());
        valid = false;
        continue;
      }

      for (String server : entry.getValue()) {
        if (!servers.getServers().containsKey(server)) {
          logger.error("Server '{}' for forced host '{}' does not exist", server, entry.getKey());
          valid = false;
        }
      }
    }

    try {
      getMotd();
    } catch (Exception e) {
      logger.error("Can't parse your MOTD", e);
      valid = false;
    }

    if (advanced.compressionLevel < -1 || advanced.compressionLevel > 9) {
      logger.error("Invalid compression level {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionLevel == 0) {
      logger.warn("ALL packets going through the proxy will be uncompressed. This will increase "
          + "bandwidth usage.");
    }

    if (advanced.compressionThreshold < -1) {
      logger.error("Invalid compression threshold {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionThreshold == 0) {
      logger.warn("ALL packets going through the proxy will be compressed. This will compromise "
          + "throughput and increase CPU usage!");
    }

    if (advanced.loginRatelimit < 0) {
      logger.error("Invalid login ratelimit {}ms", advanced.loginRatelimit);
      valid = false;
    }

    loadFavicon();

    return valid;
  }

  private void loadFavicon() {
    Path faviconPath = Path.of("server-icon.png");
    if (Files.exists(faviconPath)) {
      try {
        this.favicon = Favicon.create(faviconPath);
      } catch (Exception e) {
        logger.info("Unable to load your server-icon.png, continuing without it.", e);
      }
    }
  }

  public InetSocketAddress getBind() {
    return AddressUtil.parseAndResolveAddress(bind);
  }

  @Override
  public boolean isQueryEnabled() {
    return query.isQueryEnabled();
  }

  @Override
  public int getQueryPort() {
    return query.getQueryPort();
  }

  @Override
  public String getQueryMap() {
    return query.getQueryMap();
  }

  @Override
  public boolean shouldQueryShowPlugins() {
    return query.shouldQueryShowPlugins();
  }

  @Override
  public net.kyori.adventure.text.Component getMotd() {
    if (motdAsComponent == null) {
      motdAsComponent = MiniMessage.miniMessage().deserialize(motd);
    }
    return motdAsComponent;
  }

  @Override
  public int getShowMaxPlayers() {
    return showMaxPlayers;
  }

  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  @Override
  public boolean shouldPreventClientProxyConnections() {
    return preventClientProxyConnections;
  }

  public PlayerInfoForwarding getPlayerInfoForwardingMode() {
    return playerInfoForwardingMode;
  }

  public byte[] getForwardingSecret() {
    return forwardingSecret.clone();
  }

  @Override
  public Map<String, String> getServers() {
    return servers.getServers();
  }

  @Override
  public List<String> getAttemptConnectionOrder() {
    return servers.getAttemptConnectionOrder();
  }

  @Override
  public Map<String, List<String>> getForcedHosts() {
    return forcedHosts.getForcedHosts();
  }

  @Override
  public int getCompressionThreshold() {
    return advanced.getCompressionThreshold();
  }

  @Override
  public int getCompressionLevel() {
    return advanced.getCompressionLevel();
  }

  @Override
  public int getLoginRatelimit() {
    return advanced.getLoginRatelimit();
  }

  @Override
  public Optional<Favicon> getFavicon() {
    return Optional.ofNullable(favicon);
  }

  @Override
  public boolean isAnnounceForge() {
    return announceForge;
  }

  @Override
  public int getConnectTimeout() {
    return advanced.getConnectionTimeout();
  }

  @Override
  public int getReadTimeout() {
    return advanced.getReadTimeout();
  }

  public boolean isProxyProtocol() {
    return advanced.isProxyProtocol();
  }

  public void setProxyProtocol(boolean proxyProtocol) {
    advanced.setProxyProtocol(proxyProtocol);
  }

  public boolean useTcpFastOpen() {
    return advanced.isTcpFastOpen();
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public PingPassthroughMode getPingPassthrough() {
    return pingPassthrough;
  }

  public boolean isPlayerAddressLoggingEnabled() {
    return enablePlayerAddressLogging;
  }

  public boolean isBungeePluginChannelEnabled() {
    return advanced.isBungeePluginMessageChannel();
  }

  public boolean isShowPingRequests() {
    return advanced.isShowPingRequests();
  }

  public boolean isFailoverOnUnexpectedServerDisconnect() {
    return advanced.isFailoverOnUnexpectedServerDisconnect();
  }

  public boolean isAnnounceProxyCommands() {
    return advanced.isAnnounceProxyCommands();
  }

  public boolean isLogCommandExecutions() {
    return advanced.isLogCommandExecutions();
  }

  public boolean isLogPlayerConnections() {
    return advanced.isLogPlayerConnections();
  }

  public boolean isAcceptTransfers() {
    return this.advanced.isAcceptTransfers();
  }

  public boolean isForceKeyAuthentication() {
    return forceKeyAuthentication;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("bind", bind)
        .add("motd", motd)
        .add("showMaxPlayers", showMaxPlayers)
        .add("onlineMode", onlineMode)
        .add("playerInfoForwardingMode", playerInfoForwardingMode)
        .add("forwardingSecret", forwardingSecret)
        .add("announceForge", announceForge)
        .add("servers", servers)
        .add("forcedHosts", forcedHosts)
        .add("advanced", advanced)
        .add("query", query)
        .add("favicon", favicon)
        .add("enablePlayerAddressLogging", enablePlayerAddressLogging)
        .add("forceKeyAuthentication", forceKeyAuthentication)
        .toString();
  }

  /**
   * Generates a Random String.
   *
   * @param length the required string size.
   * @return a new random string.
   */
  public static String generateRandomString(int length) {
    final String chars = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz1234567890";
    final StringBuilder builder = new StringBuilder();
    final Random rnd = new SecureRandom();
    for (int i = 0; i < length; i++) {
      builder.append(chars.charAt(rnd.nextInt(chars.length())));
    }
    return builder.toString();
  }

  public boolean isOnlineModeKickExistingPlayers() {
    return onlineModeKickExistingPlayers;
  }

  static class Servers {

    private Map<String, String> servers = ImmutableMap.of(
        "lobby", "127.0.0.1:30066",
        "factions", "127.0.0.1:30067",
        "minigames", "127.0.0.1:30068"
    );
    private List<String> attemptConnectionOrder = ImmutableList.of("lobby");

    Servers() {
    }

    Servers(Map<String, String> servers, List<String> attemptConnectionOrder) {
      this.servers = servers;
      this.attemptConnectionOrder = attemptConnectionOrder;
    }

    private Map<String, String> getServers() {
      return servers;
    }

    public void setServers(Map<String, String> servers) {
      this.servers = servers;
    }

    public List<String> getAttemptConnectionOrder() {
      return attemptConnectionOrder;
    }

    public void setAttemptConnectionOrder(List<String> attemptConnectionOrder) {
      this.attemptConnectionOrder = attemptConnectionOrder;
    }

    /**
     * TOML requires keys to match a regex of {@code [A-Za-z0-9_-]} unless it is wrapped in quotes;
     * however, the TOML parser returns the key with the quotes so we need to clean the server name
     * before we pass it onto server registration to keep proper server name behavior.
     *
     * @param name the server name to clean
     * @return the cleaned server name
     */
    static String cleanServerName(String name) {
      return name.replace("\"", "");
    }

    @Override
    public String toString() {
      return "Servers{"
          + "servers=" + servers
          + ", attemptConnectionOrder=" + attemptConnectionOrder
          + '}';
    }
  }

  static class ForcedHosts {

    private Map<String, List<String>> forcedHosts = ImmutableMap.of(
        "lobby.example.com", ImmutableList.of("lobby"),
        "factions.example.com", ImmutableList.of("factions"),
        "minigames.example.com", ImmutableList.of("minigames")
    );

    ForcedHosts() {
    }

    ForcedHosts(Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    private Map<String, List<String>> getForcedHosts() {
      return forcedHosts;
    }

    private void setForcedHosts(Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    @Override
    public String toString() {
      return "ForcedHosts{"
          + "forcedHosts=" + forcedHosts
          + '}';
    }
  }

  static class Advanced {

    @Expose
    private int compressionThreshold = 256;
    @Expose
    private int compressionLevel = -1;
    @Expose
    private int loginRatelimit = 3000;
    @Expose
    private int connectionTimeout = 5000;
    @Expose
    private int readTimeout = 30000;
    @Expose
    private boolean proxyProtocol = false;
    @Expose
    private boolean tcpFastOpen = false;
    @Expose
    private boolean bungeePluginMessageChannel = true;
    @Expose
    private boolean showPingRequests = false;
    @Expose
    private boolean failoverOnUnexpectedServerDisconnect = true;
    @Expose
    private boolean announceProxyCommands = true;
    @Expose
    private boolean logCommandExecutions = false;
    @Expose
    private boolean logPlayerConnections = true;
    @Expose
    private boolean acceptTransfers = false;

    Advanced() {
    }

    Advanced(int compressionThreshold, int compressionLevel, int loginRatelimit,
             int connectionTimeout, int readTimeout, boolean proxyProtocol, boolean tcpFastOpen,
             boolean bungeePluginMessageChannel, boolean showPingRequests,
             boolean failoverOnUnexpectedServerDisconnect, boolean announceProxyCommands,
             boolean logCommandExecutions, boolean logPlayerConnections, boolean acceptTransfers) {
      this.compressionThreshold = compressionThreshold;
      this.compressionLevel = compressionLevel;
      this.loginRatelimit = loginRatelimit;
      this.connectionTimeout = connectionTimeout;
      this.readTimeout = readTimeout;
      this.proxyProtocol = proxyProtocol;
      this.tcpFastOpen = tcpFastOpen;
      this.bungeePluginMessageChannel = bungeePluginMessageChannel;
      this.showPingRequests = showPingRequests;
      this.failoverOnUnexpectedServerDisconnect = failoverOnUnexpectedServerDisconnect;
      this.announceProxyCommands = announceProxyCommands;
      this.logCommandExecutions = logCommandExecutions;
      this.logPlayerConnections = logPlayerConnections;
      this.acceptTransfers = acceptTransfers;
    }


    public int getCompressionThreshold() {
      return compressionThreshold;
    }

    public int getCompressionLevel() {
      return compressionLevel;
    }

    public int getLoginRatelimit() {
      return loginRatelimit;
    }

    public int getConnectionTimeout() {
      return connectionTimeout;
    }

    public int getReadTimeout() {
      return readTimeout;
    }

    public boolean isProxyProtocol() {
      return proxyProtocol;
    }

    public void setProxyProtocol(boolean proxyProtocol) {
      this.proxyProtocol = proxyProtocol;
    }

    public boolean isTcpFastOpen() {
      return tcpFastOpen;
    }

    public boolean isBungeePluginMessageChannel() {
      return bungeePluginMessageChannel;
    }

    public boolean isShowPingRequests() {
      return showPingRequests;
    }

    public boolean isFailoverOnUnexpectedServerDisconnect() {
      return failoverOnUnexpectedServerDisconnect;
    }

    public boolean isAnnounceProxyCommands() {
      return announceProxyCommands;
    }

    public boolean isLogCommandExecutions() {
      return logCommandExecutions;
    }

    public boolean isLogPlayerConnections() {
      return logPlayerConnections;
    }

    public boolean isAcceptTransfers() {
      return this.acceptTransfers;
    }

    @Override
    public String toString() {
      return "Advanced{"
          + "compressionThreshold=" + compressionThreshold
          + ", compressionLevel=" + compressionLevel
          + ", loginRatelimit=" + loginRatelimit
          + ", connectionTimeout=" + connectionTimeout
          + ", readTimeout=" + readTimeout
          + ", proxyProtocol=" + proxyProtocol
          + ", tcpFastOpen=" + tcpFastOpen
          + ", bungeePluginMessageChannel=" + bungeePluginMessageChannel
          + ", showPingRequests=" + showPingRequests
          + ", failoverOnUnexpectedServerDisconnect=" + failoverOnUnexpectedServerDisconnect
          + ", announceProxyCommands=" + announceProxyCommands
          + ", logCommandExecutions=" + logCommandExecutions
          + ", logPlayerConnections=" + logPlayerConnections
          + ", acceptTransfers=" + acceptTransfers
          + '}';
    }
  }

  static class Query {

    @Expose
    private boolean queryEnabled = false;
    @Expose
    private int queryPort = 25565;
    @Expose
    private String queryMap = "Velocity";
    @Expose
    private boolean showPlugins = false;

    Query() {
    }

    Query(boolean queryEnabled, int queryPort, String queryMap, boolean showPlugins) {
      this.queryEnabled = queryEnabled;
      this.queryPort = queryPort;
      this.queryMap = queryMap;
      this.showPlugins = showPlugins;
    }

    public boolean isQueryEnabled() {
      return queryEnabled;
    }

    public int getQueryPort() {
      return queryPort;
    }

    public String getQueryMap() {
      return queryMap;
    }

    public boolean shouldQueryShowPlugins() {
      return showPlugins;
    }

    @Override
    public String toString() {
      return "Query{"
          + "queryEnabled=" + queryEnabled
          + ", queryPort=" + queryPort
          + ", queryMap='" + queryMap + '\''
          + ", showPlugins=" + showPlugins
          + '}';
    }
  }

  /**
   * Configuration for metrics.
   */
  public static class Metrics {

    private boolean enabled = true;

    Metrics() {
    }

    Metrics(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isEnabled() {
      return enabled;
    }
  }
}
