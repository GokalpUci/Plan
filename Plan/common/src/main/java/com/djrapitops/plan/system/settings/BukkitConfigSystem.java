/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.system.settings;

import com.djrapitops.plan.api.exceptions.EnableException;
import com.djrapitops.plan.system.file.PlanFiles;
import com.djrapitops.plan.system.settings.changes.ConfigUpdater;
import com.djrapitops.plan.system.settings.config.ConfigReader;
import com.djrapitops.plan.system.settings.config.PlanConfig;
import com.djrapitops.plan.system.settings.network.ServerSettingsManager;
import com.djrapitops.plan.system.settings.paths.PluginSettings;
import com.djrapitops.plan.system.settings.theme.Theme;
import com.djrapitops.plugin.logging.console.PluginLogger;
import com.djrapitops.plugin.logging.error.ErrorHandler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * ConfigSystem for Bukkit.
 * <p>
 * Bukkit and Bungee have different default config file inside the jar.
 *
 * @author Rsl1122
 */
@Singleton
public class BukkitConfigSystem extends ConfigSystem {

    private final ConfigUpdater configUpdater;
    private final ServerSettingsManager serverSettingsManager;

    @Inject
    public BukkitConfigSystem(
            PlanFiles files,
            PlanConfig config,
            ConfigUpdater configUpdater,
            ServerSettingsManager serverSettingsManager,
            Theme theme,
            PluginLogger logger,
            ErrorHandler errorHandler
    ) {
        super(files, config, theme, logger, errorHandler);
        this.configUpdater = configUpdater;
        this.serverSettingsManager = serverSettingsManager;
    }

    @Override
    public void enable() throws EnableException {
        super.enable();
        if (config.isTrue(PluginSettings.BUNGEE_COPY_CONFIG)) {
            serverSettingsManager.enable();
        }
    }

    @Override
    public void disable() {
        serverSettingsManager.disable();
        super.disable();
    }

    @Override
    protected void copyDefaults() throws IOException {
        configUpdater.applyConfigUpdate(config);
        try (ConfigReader reader = new ConfigReader(files.getResourceFromJar("config.yml").asInputStream())) {
            config.copyMissing(reader.read());
        }
    }
}
