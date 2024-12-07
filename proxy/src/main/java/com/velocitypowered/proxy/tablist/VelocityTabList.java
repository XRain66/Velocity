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

package com.velocitypowered.proxy.tablist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.console.VelocityConsole;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Base class for handling tab lists.
 */
public class VelocityTabList implements InternalTabList {

  private static final Logger logger = LogManager.getLogger(VelocityTabList.class);
  private final ConnectedPlayer player;
  private final MinecraftConnection connection;
  private final Map<UUID, VelocityTabListEntry> entries;

  /**
   * Constructs the instance.
   *
   * @param player player associated with this tab list
   */
  public VelocityTabList(ConnectedPlayer player) {
    this.player = player;
    this.connection = player.getConnection();
    this.entries = Maps.newConcurrentMap();
  }

  @Override
  public Player getPlayer() {
    return player;
  }

  @Override
  public void setHeaderAndFooter(Component header, Component footer) {
    Preconditions.checkNotNull(header, "header");
    Preconditions.checkNotNull(footer, "footer");
    this.player.sendPlayerListHeaderAndFooter(header, footer);
  }

  @Override
  public void clearHeaderAndFooter() {
    this.player.clearPlayerListHeaderAndFooter();
  }

  @Override
  public void addEntry(TabListEntry entry1) {
    VelocityTabListEntry entry;
    if (entry1 instanceof VelocityTabListEntry) {
      entry = (VelocityTabListEntry) entry1;
    } else {
      entry = new VelocityTabListEntry(this, entry1.getProfile(),
          entry1.getDisplayNameComponent().orElse(null),
          entry1.getLatency(), entry1.getGameMode(), entry1.getChatSession(), entry1.isListed(), entry1.getListOrder());
    }

    EnumSet<UpsertPlayerInfoPacket.Action> actions = EnumSet
            .noneOf(UpsertPlayerInfoPacket.Action.class);
    UpsertPlayerInfoPacket.Entry playerInfoEntry = new UpsertPlayerInfoPacket
            .Entry(entry.getProfile().getId());

    Preconditions.checkNotNull(entry.getProfile(), "Profile cannot be null");
    Preconditions.checkNotNull(entry.getProfile().getId(), "Profile ID cannot be null");

    this.entries.compute(entry.getProfile().getId(), (uuid, previousEntry) -> {
      if (previousEntry != null) {
        // we should merge entries here
        if (previousEntry.equals(entry)) {
          return previousEntry; // nothing else to do, this entry is perfect
        }
        if (!Objects.equals(previousEntry.getDisplayNameComponent().orElse(null),
                entry.getDisplayNameComponent().orElse(null))) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
          playerInfoEntry.setDisplayName(entry.getDisplayNameComponent().isEmpty()
                  ?
                  null :
                  new ComponentHolder(player.getProtocolVersion(),
                          entry.getDisplayNameComponent().get())
          );
        }
        if (!Objects.equals(previousEntry.getLatency(), entry.getLatency())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY);
          playerInfoEntry.setLatency(entry.getLatency());
        }
        if (!Objects.equals(previousEntry.getGameMode(), entry.getGameMode())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE);
          playerInfoEntry.setGameMode(entry.getGameMode());
        }
        if (!Objects.equals(previousEntry.isListed(), entry.isListed())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LISTED);
          playerInfoEntry.setListed(entry.isListed());
        }
        if (!Objects.equals(previousEntry.getListOrder(), entry.getListOrder())
            && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER);
          playerInfoEntry.setListOrder(entry.getListOrder());
        }
        if (!Objects.equals(previousEntry.getChatSession(), entry.getChatSession())) {
          ChatSession from = entry.getChatSession();
          if (from != null) {
            actions.add(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT);
            playerInfoEntry.setChatSession(
                    new RemoteChatSession(from.getSessionId(), from.getIdentifiedKey()));
          }
        }
      } else {
        actions.addAll(EnumSet.of(UpsertPlayerInfoPacket.Action.ADD_PLAYER,
                UpsertPlayerInfoPacket.Action.UPDATE_LATENCY,
                UpsertPlayerInfoPacket.Action.UPDATE_LISTED));
        playerInfoEntry.setProfile(entry.getProfile());
        if (entry.getDisplayNameComponent().isPresent()) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
          playerInfoEntry.setDisplayName(entry.getDisplayNameComponent().isEmpty()
                  ?
                  null :
                  new ComponentHolder(player.getProtocolVersion(),
                          entry.getDisplayNameComponent().get())
          );
        }
        if (entry.getChatSession() != null) {
          actions.add(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT);
          ChatSession from = entry.getChatSession();
          playerInfoEntry.setChatSession(
                  new RemoteChatSession(from.getSessionId(), from.getIdentifiedKey()));
        }
        if (entry.getGameMode() != -1 && entry.getGameMode() != 256) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE);
          playerInfoEntry.setGameMode(entry.getGameMode());
        }
        playerInfoEntry.setLatency(entry.getLatency());
        playerInfoEntry.setListed(entry.isListed());
        if (entry.getListOrder() != 0
            && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER);
          playerInfoEntry.setListOrder(entry.getListOrder());
        }
      }
      return entry;
    });

    if (!actions.isEmpty()) {
      this.connection.write(new UpsertPlayerInfoPacket(actions, List.of(playerInfoEntry)));
    }
  }

  @Override
  public Optional<TabListEntry> removeEntry(UUID uuid) {
    this.connection.write(new RemovePlayerInfoPacket(List.of(uuid)));
    return Optional.ofNullable(this.entries.remove(uuid));
  }

  @Override
  public boolean containsEntry(UUID uuid) {
    return this.entries.containsKey(uuid);
  }

  @Override
  public Optional<TabListEntry> getEntry(UUID uuid) {
    return Optional.ofNullable(this.entries.get(uuid));
  }

  @Override
  public Collection<TabListEntry> getEntries() {
    return List.copyOf(this.entries.values());
  }

  @Override
  public void clearAll() {
    this.connection.delayedWrite(new RemovePlayerInfoPacket(
            new ArrayList<>(this.entries.keySet())));
    clearAllSilent();
  }

  @Override
  public void clearAllSilent() {
    this.entries.clear();
  }

  @Override
  public TabListEntry buildEntry(GameProfile profile, @Nullable Component displayName, int latency,
      int gameMode,
      @Nullable ChatSession chatSession, boolean listed, int listOrder) {
    return new VelocityTabListEntry(this, profile, displayName, latency, gameMode, chatSession,
        listed, listOrder);
  }

  @Override
  public void processUpdate(UpsertPlayerInfoPacket infoPacket) {
    logger.info("[GameMode Debug] Processing UpsertPlayerInfoPacket with actions: {}", infoPacket.getActions());
    
    // 检查是否有游戏模式更新
    boolean hasGameModeUpdate = infoPacket.getActions().contains(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE);
    if (hasGameModeUpdate) {
      logger.info("[GameMode Debug] Packet contains game mode updates");
    }

    for (UpsertPlayerInfoPacket.Entry entry : infoPacket.getEntries()) {
      // 检查是否是当前玩家
      boolean isCurrentPlayer = entry.getProfileId().equals(player.getUniqueId());
      if (isCurrentPlayer && hasGameModeUpdate) {
        logger.info("[GameMode Debug] Found game mode update for current player: {}", 
            getGameModeName(entry.getGameMode()));
      }
      
      processUpsert(infoPacket.getActions(), entry);
    }
  }

  private void processUpsert(EnumSet<UpsertPlayerInfoPacket.Action> actions,
      UpsertPlayerInfoPacket.Entry entry) {
    Preconditions.checkNotNull(entry.getProfileId(), "Profile ID cannot be null");
    UUID profileId = entry.getProfileId();
    VelocityTabListEntry currentEntry = this.entries.get(profileId);
    
    logger.info("[GameMode Debug] Processing UpsertPlayerInfoPacket for {} with actions: {}", 
        entry.getProfile() != null ? entry.getProfile().getName() : profileId, 
        actions);

    if (actions.contains(UpsertPlayerInfoPacket.Action.ADD_PLAYER)) {
      logger.info("[GameMode Debug] Processing ADD_PLAYER action for {}", 
          entry.getProfile() != null ? entry.getProfile().getName() : profileId);
      if (currentEntry == null) {
        this.entries.put(profileId,
            currentEntry = new VelocityTabListEntry(
                this,
                entry.getProfile(),
                null,
                0,
                entry.getGameMode(), // 使用数据包中的游戏模式
                null,
                false,
                0
            )
        );
        logger.info("[GameMode Debug] Created new tab list entry for {} with gameMode {}", 
            entry.getProfile() != null ? entry.getProfile().getName() : profileId,
            getGameModeName(entry.getGameMode()));
      }
    } else if (currentEntry == null) {
      logger.debug("[GameMode Debug] Received a partial player update before ADD_PLAYER action");
      return;
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE)) {
      int oldGameMode = currentEntry.getGameMode();
      int newGameMode = entry.getGameMode();
      
      // 检查是否是当前玩家
      boolean isCurrentPlayer = entry.getProfileId().equals(player.getUniqueId());
      
      logger.info("[GameMode Debug] Processing game mode update for {}: {} -> {} (isCurrentPlayer: {})", 
          entry.getProfile() != null ? entry.getProfile().getName() : profileId,
          getGameModeName(oldGameMode),
          getGameModeName(newGameMode),
          isCurrentPlayer);
      
      currentEntry.setGameModeWithoutUpdate(newGameMode);
      
      // 如果是当前玩家，确保游戏模式更新被正确处理
      if (isCurrentPlayer) {
        logger.info("[GameMode] Current player {} gamemode changed from {} to {}", 
            entry.getProfile() != null ? entry.getProfile().getName() : profileId,
            getGameModeName(oldGameMode),
            getGameModeName(newGameMode));
      }
    }
    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY)) {
      currentEntry.setLatencyWithoutUpdate(entry.getLatency());
    }
    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
      currentEntry.setDisplayNameWithoutUpdate(entry.getDisplayName() != null
          ? entry.getDisplayName().getComponent() : null);
    }
    if (actions.contains(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT)) {
      currentEntry.setChatSession(entry.getChatSession());
    }
    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LISTED)) {
      currentEntry.setListedWithoutUpdate(entry.isListed());
    }
    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER)) {
      currentEntry.setListOrderWithoutUpdate(entry.getListOrder());
    }
  }

  protected UpsertPlayerInfoPacket.Entry createRawEntry(VelocityTabListEntry entry) {
    Preconditions.checkNotNull(entry, "entry");
    Preconditions.checkNotNull(entry.getProfile(), "Profile cannot be null");
    Preconditions.checkNotNull(entry.getProfile().getId(), "Profile ID cannot be null");
    return new UpsertPlayerInfoPacket.Entry(entry.getProfile().getId());
  }

  protected void emitActionRaw(UpsertPlayerInfoPacket.Action action,
                               UpsertPlayerInfoPacket.Entry entry) {
    logger.info("[GameMode Debug] Emitting raw action {} for player {}", 
        action, 
        entry.getProfile() != null ? entry.getProfile().getName() : entry.getProfileId());
    this.connection.write(new UpsertPlayerInfoPacket(EnumSet.of(action), List.of(entry)));
  }

  private String getGameModeName(int gameMode) {
    switch (gameMode) {
      case 0:
        return "SURVIVAL";
      case 1:
        return "CREATIVE";
      case 2:
        return "ADVENTURE";
      case 3:
        return "SPECTATOR";
      case -1:
        return "NOT_SET";
      default:
        return "UNKNOWN(" + gameMode + ")";
    }
  }

  @Override
  public void processRemove(RemovePlayerInfoPacket infoPacket) {
    for (UUID uuid : infoPacket.getProfilesToRemove()) {
      this.entries.remove(uuid);
    }
  }
}