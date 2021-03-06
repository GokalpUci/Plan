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
package com.djrapitops.plan.command.commands;

import com.djrapitops.plan.command.commands.manage.*;
import com.djrapitops.plan.system.locale.Locale;
import com.djrapitops.plan.system.locale.lang.CmdHelpLang;
import com.djrapitops.plan.system.locale.lang.DeepHelpLang;
import com.djrapitops.plan.system.settings.Permissions;
import com.djrapitops.plugin.command.ColorScheme;
import com.djrapitops.plugin.command.CommandNode;
import com.djrapitops.plugin.command.CommandType;
import com.djrapitops.plugin.command.TreeCmdNode;
import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * This SubCommand is used to manage the the plugin's database and components.
 *
 * @author Rsl1122
 */
public class ManageCommand extends TreeCmdNode {

    @Inject
    public ManageCommand(ColorScheme colorScheme, Locale locale, @Named("mainCommand") Lazy<CommandNode> parent,
                         // Group 1
                         ManageRawDataCommand rawDataCommand,
                         ManageMoveCommand moveCommand,
                         ManageBackupCommand backupCommand,
                         ManageRemoveCommand removeCommand,
                         ManageRestoreCommand restoreCommand,
                         ManageHotSwapCommand hotSwapCommand,
                         ManageClearCommand clearCommand,
                         // Group 2
                         ManageSetupCommand setupCommand,
                         ManageConDebugCommand conDebugCommand,
                         ManageImportCommand importCommand,
                         ManageExportCommand exportCommand,
                         ManageDisableCommand disableCommand,
                         ManageUninstalledCommand uninstalledCommand
    ) {
        super("manage|m", Permissions.MANAGE.getPermission(), CommandType.CONSOLE, parent.get());
        super.setColorScheme(colorScheme);

        setShortHelp(locale.getString(CmdHelpLang.MANAGE));
        setInDepthHelp(locale.getArray(DeepHelpLang.MANAGE));
        CommandNode[] databaseGroup = {
                rawDataCommand,
                moveCommand,
                backupCommand,
                restoreCommand,
                hotSwapCommand,
                removeCommand,
                clearCommand,
        };
        CommandNode[] pluginGroup = {
                setupCommand,
                conDebugCommand,
                importCommand,
                exportCommand,
                disableCommand,
                uninstalledCommand
        };
        setNodeGroups(databaseGroup, pluginGroup);
    }
}
