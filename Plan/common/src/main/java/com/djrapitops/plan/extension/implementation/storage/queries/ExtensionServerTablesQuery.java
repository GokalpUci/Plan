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
package com.djrapitops.plan.extension.implementation.storage.queries;

import com.djrapitops.plan.db.SQLDB;
import com.djrapitops.plan.db.access.Query;
import com.djrapitops.plan.db.access.QueryStatement;
import com.djrapitops.plan.db.sql.tables.ExtensionIconTable;
import com.djrapitops.plan.db.sql.tables.ExtensionServerTableValueTable;
import com.djrapitops.plan.db.sql.tables.ExtensionTabTable;
import com.djrapitops.plan.db.sql.tables.ExtensionTableProviderTable;
import com.djrapitops.plan.extension.ElementOrder;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.implementation.TabInformation;
import com.djrapitops.plan.extension.implementation.results.ExtensionTabData;
import com.djrapitops.plan.extension.implementation.results.ExtensionTableData;
import com.djrapitops.plan.extension.implementation.results.server.ExtensionServerData;
import com.djrapitops.plan.extension.table.Table;
import com.djrapitops.plan.extension.table.TableAccessor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.djrapitops.plan.db.sql.parsing.Sql.*;

/**
 * Query server tables from tableprovider table.
 * <p>
 * Returns Map: PluginID - ExtensionServerData.Factory.
 * <p>
 * How it is done:
 * - TableProviders are queried and formed into Table.Factory objects sorted into a multi-map: PluginID - TableID - Table.Factory
 * - Table values are queried and merged into the above multimap
 * - Data query is sorted into a multi-map: PluginID - Tab Name - Tab Data
 * - (Tab Name can be empty.)
 * - Multi-map is sorted into ExtensionServerData objects by PluginID, one per ID
 * <p>
 * There are multiple data extraction methods to make extracting the value query easier.
 *
 * @author Rsl1122
 */
public class ExtensionServerTablesQuery implements Query<Map<Integer, ExtensionServerData.Factory>> {

    private final UUID serverUUID;

    public ExtensionServerTablesQuery(UUID serverUUID) {
        this.serverUUID = serverUUID;
    }

    @Override
    public Map<Integer, ExtensionServerData.Factory> executeQuery(SQLDB db) {
        Map<Integer, Map<Integer, Table.Factory>> tablesByPluginIDAndTableID = db.query(queryTableProviders());
        Map<Integer, Map<Integer, Table.Factory>> tablesWithValues = db.query(queryTableValues(tablesByPluginIDAndTableID));

        Map<Integer, Map<String, ExtensionTabData.Factory>> tabDataByPluginID = mapToTabsByPluginID(tablesWithValues);
        return ExtensionServerDataQuery.flatMapToServerData(tabDataByPluginID);
    }

    /**
     * @param tablesByPluginIDAndTableID {@code <Plugin ID - <Table ID - Table.Factory>>}
     * @return {@code <Plugin ID - <Tab name - ExtensionTabData.Factory>}
     */
    private Map<Integer, Map<String, ExtensionTabData.Factory>> mapToTabsByPluginID(Map<Integer, Map<Integer, Table.Factory>> tablesByPluginIDAndTableID) {
        Map<Integer, Map<String, ExtensionTabData.Factory>> byPluginID = new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, Table.Factory>> entry : tablesByPluginIDAndTableID.entrySet()) {
            Integer pluginID = entry.getKey();
            Map<String, ExtensionTabData.Factory> byTabName = byPluginID.getOrDefault(pluginID, new HashMap<>());
            for (Table.Factory table : entry.getValue().values()) {
                // Extra Table information
                String tableName = TableAccessor.getTableName(table);
                Color tableColor = TableAccessor.getColor(table);

                // Extra tab information
                String tabName = TableAccessor.getTabName(table);
                int tabPriority = TableAccessor.getTabPriority(table);
                ElementOrder[] tabOrder = TableAccessor.getTabOrder(table);
                Icon tabIcon = TableAccessor.getTabIcon(table);

                ExtensionTabData.Factory tab = byTabName.getOrDefault(tabName, new ExtensionTabData.Factory(new TabInformation(tabName, tabIcon, tabOrder, tabPriority)));
                tab.putTableData(new ExtensionTableData(
                        tableName, table.build(), tableColor
                ));
                byTabName.put(tabName, tab);
            }
            byPluginID.put(pluginID, byTabName);
        }

        return byPluginID;
    }

    // Map: <Plugin ID - <Table ID - Table.Factory>>
    private Query<Map<Integer, Map<Integer, Table.Factory>>> queryTableValues(Map<Integer, Map<Integer, Table.Factory>> tables) {
        String selectTableValues = SELECT +
                ExtensionTableProviderTable.PLUGIN_ID + ',' +
                ExtensionServerTableValueTable.TABLE_ID + ',' +
                ExtensionServerTableValueTable.VALUE_1 + ',' +
                ExtensionServerTableValueTable.VALUE_2 + ',' +
                ExtensionServerTableValueTable.VALUE_3 + ',' +
                ExtensionServerTableValueTable.VALUE_4 + ',' +
                ExtensionServerTableValueTable.VALUE_5 +
                FROM + ExtensionServerTableValueTable.TABLE_NAME +
                INNER_JOIN + ExtensionTableProviderTable.TABLE_NAME + " on " + ExtensionTableProviderTable.TABLE_NAME + '.' + ExtensionTableProviderTable.ID + '=' + ExtensionServerTableValueTable.TABLE_NAME + '.' + ExtensionServerTableValueTable.TABLE_ID +
                WHERE + ExtensionServerTableValueTable.SERVER_UUID + "=?";

        return new QueryStatement<Map<Integer, Map<Integer, Table.Factory>>>(selectTableValues, 10000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public Map<Integer, Map<Integer, Table.Factory>> processResults(ResultSet set) throws SQLException {
                while (set.next()) {
                    Table.Factory table = getTable(set);
                    if (table == null) {
                        continue;
                    }

                    Object[] row = extractTableRow(set);
                    if (row.length > 0) {
                        table.addRow(row);
                    }
                }
                return tables;
            }

            private Table.Factory getTable(ResultSet set) throws SQLException {
                int pluginID = set.getInt(ExtensionTableProviderTable.PLUGIN_ID);
                Map<Integer, Table.Factory> byTableID = tables.get(pluginID);
                if (byTableID == null) {
                    return null;
                }
                int tableID = set.getInt(ExtensionServerTableValueTable.TABLE_ID);
                return byTableID.get(tableID);
            }
        };
    }

    private Object[] extractTableRow(ResultSet set) throws SQLException {
        List<Object> row = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String columnName = "col_" + i + "_value"; // See ExtensionServerTableValueTable.VALUE_1
            String value = set.getString(columnName);
            if (value == null) {
                return row.toArray(new Object[0]);
            }
            row.add(value);
        }
        return row.toArray(new Object[0]);
    }

    // Map: <Plugin ID - <Table ID - Table.Factory>>
    private Query<Map<Integer, Map<Integer, Table.Factory>>> queryTableProviders() {
        String selectTables = SELECT +
                "p1." + ExtensionTableProviderTable.ID + " as table_id," +
                "p1." + ExtensionTableProviderTable.PLUGIN_ID + " as plugin_id," +
                "p1." + ExtensionTableProviderTable.PROVIDER_NAME + " as table_name," +
                "p1." + ExtensionTableProviderTable.COLOR + " as table_color," +
                ExtensionTableProviderTable.COL_1 + ',' +
                ExtensionTableProviderTable.COL_2 + ',' +
                ExtensionTableProviderTable.COL_3 + ',' +
                ExtensionTableProviderTable.COL_4 + ',' +
                ExtensionTableProviderTable.COL_5 + ',' +
                "t1." + ExtensionTabTable.TAB_NAME + " as tab_name," +
                "t1." + ExtensionTabTable.TAB_PRIORITY + " as tab_priority," +
                "t1." + ExtensionTabTable.ELEMENT_ORDER + " as element_order," +
                "i1." + ExtensionIconTable.ICON_NAME + " as i1_name," +
                "i1." + ExtensionIconTable.FAMILY + " as i1_family," +
                "i1." + ExtensionIconTable.COLOR + " as i1_color," +
                "i2." + ExtensionIconTable.ICON_NAME + " as i2_name," +
                "i2." + ExtensionIconTable.FAMILY + " as i2_family," +
                "i2." + ExtensionIconTable.COLOR + " as i2_color," +
                "i3." + ExtensionIconTable.ICON_NAME + " as i3_name," +
                "i3." + ExtensionIconTable.FAMILY + " as i3_family," +
                "i3." + ExtensionIconTable.COLOR + " as i3_color," +
                "i4." + ExtensionIconTable.ICON_NAME + " as i4_name," +
                "i4." + ExtensionIconTable.FAMILY + " as i4_family," +
                "i4." + ExtensionIconTable.COLOR + " as i4_color," +
                "i5." + ExtensionIconTable.ICON_NAME + " as i5_name," +
                "i5." + ExtensionIconTable.FAMILY + " as i5_family," +
                "i5." + ExtensionIconTable.COLOR + " as i5_color," +
                "i6." + ExtensionIconTable.ICON_NAME + " as tab_icon_name," +
                "i6." + ExtensionIconTable.FAMILY + " as tab_icon_family," +
                "i6." + ExtensionIconTable.COLOR + " as tab_icon_color" +
                FROM + ExtensionTableProviderTable.TABLE_NAME + " p1" +
                INNER_JOIN + ExtensionServerTableValueTable.TABLE_NAME + " v1 on v1." + ExtensionServerTableValueTable.TABLE_ID + "=p1." + ExtensionTableProviderTable.ID +
                LEFT_JOIN + ExtensionTabTable.TABLE_NAME + " t1 on t1." + ExtensionTabTable.ID + "=p1." + ExtensionTableProviderTable.TAB_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i1 on i1." + ExtensionIconTable.ID + "=p1." + ExtensionTableProviderTable.ICON_1_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i2 on i2." + ExtensionIconTable.ID + "=p1." + ExtensionTableProviderTable.ICON_2_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i3 on i3." + ExtensionIconTable.ID + "=p1." + ExtensionTableProviderTable.ICON_3_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i4 on i4." + ExtensionIconTable.ID + "=p1." + ExtensionTableProviderTable.ICON_4_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i5 on i5." + ExtensionIconTable.ID + "=p1." + ExtensionTableProviderTable.ICON_5_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i6 on i6." + ExtensionIconTable.ID + "=t1." + ExtensionTabTable.ICON_ID +
                WHERE + "v1." + ExtensionServerTableValueTable.SERVER_UUID + "=?";

        return new QueryStatement<Map<Integer, Map<Integer, Table.Factory>>>(selectTables, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public Map<Integer, Map<Integer, Table.Factory>> processResults(ResultSet set) throws SQLException {
                Map<Integer, Map<Integer, Table.Factory>> byPluginID = new HashMap<>();

                while (set.next()) {
                    int pluginID = set.getInt("plugin_id");
                    Map<Integer, Table.Factory> byTableID = byPluginID.getOrDefault(pluginID, new HashMap<>());

                    int tableID = set.getInt("table_id");
                    Table.Factory table = Table.builder();

                    extractColumns(set, table);

                    TableAccessor.setColor(table, Color.getByName(set.getString("table_color")).orElse(Color.NONE));
                    TableAccessor.setTableName(table, set.getString("table_name"));
                    TableAccessor.setTabName(table, Optional.ofNullable(set.getString("tab_name")).orElse(""));
                    TableAccessor.setTabPriority(table, Optional.of(set.getInt("tab_priority")).orElse(100));
                    TableAccessor.setTabOrder(table, Optional.ofNullable(set.getString(ExtensionTabTable.ELEMENT_ORDER)).map(ElementOrder::deserialize).orElse(ElementOrder.values()));
                    TableAccessor.setTabIcon(table, extractIcon(set, "tab_icon"));

                    byTableID.put(tableID, table);
                    byPluginID.put(pluginID, byTableID);
                }

                return byPluginID;
            }
        };
    }

    private void extractColumns(ResultSet set, Table.Factory table) throws SQLException {
        String col1 = set.getString(ExtensionTableProviderTable.COL_1);
        if (col1 != null) {
            table.columnOne(col1, extractIcon(set, "i1"));
        }
        String col2 = set.getString(ExtensionTableProviderTable.COL_2);
        if (col2 != null) {
            table.columnTwo(col2, extractIcon(set, "i2"));
        }
        String col3 = set.getString(ExtensionTableProviderTable.COL_3);
        if (col3 != null) {
            table.columnThree(col3, extractIcon(set, "i3"));
        }
        String col4 = set.getString(ExtensionTableProviderTable.COL_4);
        if (col4 != null) {
            table.columnFour(col4, extractIcon(set, "i4"));
        }
        String col5 = set.getString(ExtensionTableProviderTable.COL_5);
        if (col5 != null) {
            table.columnFive(col5, extractIcon(set, "i5"));
        }
    }

    private Icon extractIcon(ResultSet set, String iconColumnName) throws SQLException {
        String iconName = set.getString(iconColumnName + "_name");
        if (iconName == null) {
            return null;
        }
        return new Icon(
                Family.getByName(set.getString(iconColumnName + "_family")).orElse(Family.SOLID),
                iconName,
                Color.getByName(set.getString(iconColumnName + "_color")).orElse(Color.NONE)
        );
    }
}