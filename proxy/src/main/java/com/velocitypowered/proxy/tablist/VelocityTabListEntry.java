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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Generic tab list entry implementation.
 */
public class VelocityTabListEntry implements TabListEntry {

  private static final Logger logger = LogManager.getLogger(VelocityTabListEntry.class);

  private final VelocityTabList tabList;
  private final GameProfile profile;
  private Component displayName;
  private int latency;
  private int gameMode;
  private boolean listed;
  private int listOrder;
  private @Nullable ChatSession session;

  /**
   * Constructs the instance.
   */
  public VelocityTabListEntry(VelocityTabList tabList, GameProfile profile, Component displayName,
                              int latency,
                              int gameMode, @Nullable ChatSession session, boolean listed, int listOrder) {
    this.tabList = tabList;
    this.profile = profile;
    this.displayName = displayName;
    this.latency = latency;
    this.gameMode = gameMode;
    this.session = session;
    this.listed = listed;
    this.listOrder = listOrder;
  }

  @Override
  public @Nullable ChatSession getChatSession() {
    return this.session;
  }

  @Override
  public TabList getTabList() {
    return this.tabList;
  }

  @Override
  public GameProfile getProfile() {
    return this.profile;
  }

  @Override
  public Optional<Component> getDisplayNameComponent() {
    return Optional.ofNullable(displayName);
  }

  @Override
  public TabListEntry setDisplayName(@Nullable Component displayName) {
    this.displayName = displayName;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setDisplayName(
            displayName == null
                    ?
                    null :
                    new ComponentHolder(this.tabList.getPlayer().getProtocolVersion(), displayName)
    );
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, upsertEntry);
    return this;
  }

  void setDisplayNameWithoutUpdate(@Nullable Component displayName) {
    this.displayName = displayName;
  }

  @Override
  public int getLatency() {
    return this.latency;
  }

  @Override
  public TabListEntry setLatency(int latency) {
    this.latency = latency;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setLatency(latency);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY, upsertEntry);
    return this;
  }

  void setLatencyWithoutUpdate(int latency) {
    this.latency = latency;
  }

  @Override
  public int getGameMode() {
    return this.gameMode;
  }

  @Override
  public TabListEntry setGameMode(int gameMode) {
    int oldGameMode = this.gameMode;
    logger.info("[GameMode Debug] Setting gamemode for player {}: {} -> {}", 
        this.profile.getName(), 
        getGameModeName(oldGameMode), 
        getGameModeName(gameMode));
    
    this.gameMode = gameMode;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setGameMode(gameMode);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE, upsertEntry);
    
    logger.info("[GameMode] Player {} gamemode changed from {} to {}", 
        this.profile.getName(), 
        getGameModeName(oldGameMode), 
        getGameModeName(gameMode));
    return this;
  }

  void setGameModeWithoutUpdate(int gameMode) {
    int oldGameMode = this.gameMode;
    logger.info("[GameMode Debug] Setting gamemode without update for player {}: {} -> {}", 
        this.profile.getName(), 
        getGameModeName(oldGameMode), 
        getGameModeName(gameMode));
    this.gameMode = gameMode;
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
      default:
        return "UNKNOWN(" + gameMode + ")";
    }
  }

  protected void setChatSession(@Nullable ChatSession session) {
    this.session = session;
  }

  @Override
  public boolean isListed() {
    return listed;
  }

  @Override
  public VelocityTabListEntry setListed(boolean listed) {
    this.listed = listed;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setListed(listed);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LISTED, upsertEntry);
    return this;
  }

  void setListedWithoutUpdate(boolean listed) {
    this.listed = listed;
  }

  @Override
  public int getListOrder() {
    return listOrder;
  }

  @Override
  public VelocityTabListEntry setListOrder(int listOrder) {
    this.listOrder = listOrder;
    if (tabList.getPlayer().getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
      upsertEntry.setListOrder(listOrder);
      tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER, upsertEntry);
    }
    return this;
  }

  void setListOrderWithoutUpdate(int listOrder) {
    this.listOrder = listOrder;
  }
}