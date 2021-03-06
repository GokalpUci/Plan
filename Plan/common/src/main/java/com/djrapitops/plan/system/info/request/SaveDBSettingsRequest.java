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
package com.djrapitops.plan.system.info.request;

import com.djrapitops.plan.PlanPlugin;
import com.djrapitops.plan.api.exceptions.connection.BadRequestException;
import com.djrapitops.plan.api.exceptions.connection.InternalErrorException;
import com.djrapitops.plan.api.exceptions.connection.WebException;
import com.djrapitops.plan.system.info.server.ServerInfo;
import com.djrapitops.plan.system.settings.config.PlanConfig;
import com.djrapitops.plan.system.settings.paths.DatabaseSettings;
import com.djrapitops.plan.system.settings.paths.PluginSettings;
import com.djrapitops.plan.system.webserver.response.DefaultResponses;
import com.djrapitops.plan.system.webserver.response.Response;
import com.djrapitops.plan.system.webserver.response.errors.BadRequestResponse;
import com.djrapitops.plugin.api.TimeAmount;
import com.djrapitops.plugin.logging.console.PluginLogger;
import com.djrapitops.plugin.task.AbsRunnable;
import com.djrapitops.plugin.task.RunnableFactory;
import com.djrapitops.plugin.utilities.Verify;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * InfoRequest for sending Database config settings to Bukkit servers.
 *
 * @author Rsl1122
 */
public class SaveDBSettingsRequest extends InfoRequestWithVariables implements SetupRequest {

    private final PlanPlugin plugin;
    private final PlanConfig config;
    private final ServerInfo serverInfo;
    private final PluginLogger logger;
    private final RunnableFactory runnableFactory;

    SaveDBSettingsRequest(
            PlanPlugin plugin,
            PlanConfig config,
            ServerInfo serverInfo, PluginLogger logger,
            RunnableFactory runnableFactory
    ) {
        this.plugin = plugin;
        this.config = config;
        this.serverInfo = serverInfo;
        this.logger = logger;
        this.runnableFactory = runnableFactory;

        variables.put("DB_TYPE", "mysql"); // DatabaseSettings.TYPE
        variables.put("DB_HOST", config.get(DatabaseSettings.MYSQL_HOST));
        variables.put("DB_USER", config.get(DatabaseSettings.MYSQL_USER));
        variables.put("DB_PASS", config.get(DatabaseSettings.MYSQL_PASS));
        variables.put("DB_DATABASE", config.get(DatabaseSettings.MYSQL_DATABASE));
        variables.put("DB_PORT", config.get(DatabaseSettings.MYSQL_PORT));
    }

    @Override
    public void runLocally() {
        /* Won't be run */
    }

    @Override
    public Response handleRequest(Map<String, String> variables) throws WebException {
        if (serverInfo.getServer().isProxy()) {
            return new BadRequestResponse("Not supposed to be called on a proxy server");
        }
        if (config.isFalse(PluginSettings.BUNGEE_COPY_CONFIG)) {
            return new BadRequestResponse("Bungee config settings overridden on this server.");
        }

        try {
            setSettings(variables);
            logger.info("----------------------------------");
            logger.info("The Received Bungee Database Settings, restarting Plan..");
            logger.info("----------------------------------");
            return DefaultResponses.SUCCESS.get();
        } finally {
            runnableFactory.create("Bungee Setup Restart Task", new AbsRunnable() {
                @Override
                public void run() {
                    plugin.reloadPlugin(true);
                }
            }).runTaskLater(TimeAmount.toTicks(2L, TimeUnit.SECONDS));
        }
    }

    private void setSettings(Map<String, String> variables) throws BadRequestException, InternalErrorException {
        String type = variables.get("DB_TYPE");
        String host = variables.get("DB_HOST");
        String user = variables.get("DB_USER");
        String pass = variables.get("DB_PASS");
        String database = variables.get("DB_DATABASE");
        String portS = variables.get("DB_PORT");

        Verify.nullCheck(type, () -> new BadRequestException("DB_TYPE not specified in the request."));
        Verify.nullCheck(host, () -> new BadRequestException("DB_HOST not specified in the request."));
        Verify.nullCheck(user, () -> new BadRequestException("DB_USER not specified in the request."));
        Verify.nullCheck(pass, () -> new BadRequestException("DB_PASS not specified in the request."));
        Verify.nullCheck(database, () -> new BadRequestException("DB_DATABASE not specified in the request."));
        Verify.nullCheck(portS, () -> new BadRequestException("DB_PORT not specified in the request."));

        config.set(DatabaseSettings.MYSQL_PORT, portS);
        config.set(DatabaseSettings.TYPE, type);
        config.set(DatabaseSettings.MYSQL_HOST, host);
        config.set(DatabaseSettings.MYSQL_USER, user);
        config.set(DatabaseSettings.MYSQL_PASS, pass);
        config.set(DatabaseSettings.MYSQL_DATABASE, database);
        try {
            config.save();
        } catch (IOException e) {
            throw new InternalErrorException("Failed to Save Config", e);
        }
    }
}
