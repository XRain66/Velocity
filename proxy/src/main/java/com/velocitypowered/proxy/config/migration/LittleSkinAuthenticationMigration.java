/*
 * Copyright (C) 2024 Velocity Contributors
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

package com.velocitypowered.proxy.config.migration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.Logger;
import java.util.List;

/**
 * Creation of the configuration options for LittleSkin authentication.
 */
public class LittleSkinAuthenticationMigration implements ConfigurationMigration {

  @Override
  public boolean shouldMigrate(final CommentedFileConfig config) {
    return configVersion(config) < 2.8;
  }

  @Override
  public void migrate(final CommentedFileConfig config, final Logger logger) {
    // Add enable-littleskin option
    config.set("authentication.enable-littleskin", true);
    config.setComment("authentication.enable-littleskin", """
            Whether to enable LittleSkin authentication as a fallback when Mojang authentication fails.
            When enabled, the proxy will first try to authenticate with Mojang's servers.
            If that fails, it will attempt to authenticate with LittleSkin.
            This is useful for players who use LittleSkin authentication service.""");

    // Add whitelist option
    List<String> defaultWhitelist = ImmutableList.of("example_player1", "example_player2");
    config.set("authentication.littleskin-whitelist", defaultWhitelist);
    config.setComment("authentication.littleskin-whitelist", """
            玩家白名单列表，只有在此列表中的玩家才能使用 LittleSkin 验证。
            如果玩家不在白名单中，将无法使用 LittleSkin 验证登录。
            留空列表 [] 表示禁用白名单，允许所有玩家使用 LittleSkin 验证。""");

    config.set("config-version", "2.8");
  }
}
