/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.cdc.postgres.table;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.factories.DynamicTableSourceFactory;
import org.apache.flink.table.factories.FactoryUtil;

import java.util.HashSet;
import java.util.Set;

import static com.ververica.cdc.debezium.table.DebeziumOptions.DEBEZIUM_OPTIONS_PREFIX;
import static com.ververica.cdc.debezium.table.DebeziumOptions.getDebeziumProperties;
import static org.apache.inlong.sort.base.Constants.AUDIT_KEYS;
import static org.apache.inlong.sort.base.Constants.INLONG_AUDIT;
import static org.apache.inlong.sort.base.Constants.INLONG_METRIC;
import static org.apache.inlong.sort.base.Constants.SOURCE_MULTIPLE_ENABLE;

/**
 * Factory for creating configured instance of
 * {@link com.ververica.cdc.connectors.postgres.table.PostgreSQLTableSource}.
 */
public class PostgreSQLTableFactory implements DynamicTableSourceFactory {

    private static final String IDENTIFIER = "postgres-cdc-inlong";

    private static final ConfigOption<String> HOSTNAME =
            ConfigOptions.key("hostname")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("IP address or hostname of the PostgreSQL database server.");

    private static final ConfigOption<Integer> PORT =
            ConfigOptions.key("port")
                    .intType()
                    .defaultValue(5432)
                    .withDescription("Integer port number of the PostgreSQL database server.");

    private static final ConfigOption<String> USERNAME =
            ConfigOptions.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Name of the PostgreSQL database to use when connecting to the PostgreSQL database server"
                                    + ".");

    private static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Password to use when connecting to the PostgreSQL database server.");

    private static final ConfigOption<String> DATABASE_NAME =
            ConfigOptions.key("database-name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Database name of the PostgreSQL server to monitor.");

    private static final ConfigOption<String> SCHEMA_NAME =
            ConfigOptions.key("schema-name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Schema name of the PostgreSQL database to monitor.");

    private static final ConfigOption<String> TABLE_NAME =
            ConfigOptions.key("table-name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Table name of the PostgreSQL database to monitor.");

    private static final ConfigOption<String> DECODING_PLUGIN_NAME =
            ConfigOptions.key("decoding.plugin.name")
                    .stringType()
                    .defaultValue("decoderbufs")
                    .withDescription(
                            "The name of the Postgres logical decoding plug-in installed on the server.\n"
                                    + "Supported values are decoderbufs, wal2json, wal2json_rds, wal2json_streaming,\n"
                                    + "wal2json_rds_streaming and pgoutput.");

    private static final ConfigOption<String> SLOT_NAME =
            ConfigOptions.key("slot.name")
                    .stringType()
                    .defaultValue("flink")
                    .withDescription(
                            "The name of the PostgreSQL logical decoding slot that was created for streaming changes "
                                    + "from a particular plug-in for a particular database/schema. The server uses "
                                    + "this slot "
                                    + "to stream events to the connector that you are configuring. Default is "
                                    + "\"flink\".");

    /**
     * The session time zone in database server.
     */
    public static final ConfigOption<String> SERVER_TIME_ZONE =
            ConfigOptions.key("server-time-zone")
                    .stringType()
                    .defaultValue("UTC")
                    .withDescription("The session time zone in database server.");

    /**
     * row kinds to be filtered
     */
    public static final ConfigOption<String> ROW_KINDS_FILTERED =
            ConfigOptions.key("row-kinds-filtered")
                    .stringType()
                    .defaultValue("+I&-U&+U&-D")
                    .withDescription("row kinds to be filtered,"
                            + " here filtered means keep the data of certain row kind"
                            + "the format follows rowKind1&rowKind2, supported row kinds are "
                            + "\"+I\" represents INSERT.\n"
                            + "\"-U\" represents UPDATE_BEFORE.\n"
                            + "\"+U\" represents UPDATE_AFTER.\n"
                            + "\"-D\" represents DELETE.");

    /**
     * Whether works as append source.
     */
    public static final ConfigOption<Boolean> APPEND_MODE =
            ConfigOptions.key("append-mode")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether works as append source.");

    @Override
    public DynamicTableSource createDynamicTableSource(Context context) {
        final FactoryUtil.TableFactoryHelper helper =
                FactoryUtil.createTableFactoryHelper(this, context);
        helper.validateExcept(DEBEZIUM_OPTIONS_PREFIX);

        final ReadableConfig config = helper.getOptions();
        String hostname = config.get(HOSTNAME);
        String username = config.get(USERNAME);
        String password = config.get(PASSWORD);
        String databaseName = config.get(DATABASE_NAME);
        String schemaName = config.get(SCHEMA_NAME);
        String tableName = config.get(TABLE_NAME);
        int port = config.get(PORT);
        String pluginName = config.get(DECODING_PLUGIN_NAME);
        String slotName = config.get(SLOT_NAME);
        String serverTimeZone = config.get(SERVER_TIME_ZONE);
        ResolvedSchema physicalSchema = context.getCatalogTable().getResolvedSchema();
        final boolean sourceMultipleEnable = config.get(SOURCE_MULTIPLE_ENABLE);
        String inlongMetric = config.getOptional(INLONG_METRIC).orElse(null);
        String inlongAudit = config.get(INLONG_AUDIT);
        final boolean appendSource = config.get(APPEND_MODE);
        final String rowKindFiltered = config.get(ROW_KINDS_FILTERED).isEmpty() ? ROW_KINDS_FILTERED.defaultValue()
                : config.get(ROW_KINDS_FILTERED);

        return new PostgreSQLTableSource(
                physicalSchema,
                port,
                hostname,
                databaseName,
                schemaName,
                tableName,
                username,
                password,
                pluginName,
                slotName,
                serverTimeZone,
                getDebeziumProperties(context.getCatalogTable().getOptions()),
                appendSource,
                rowKindFiltered,
                sourceMultipleEnable,
                inlongMetric,
                inlongAudit);
    }

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        Set<ConfigOption<?>> options = new HashSet<>();
        options.add(HOSTNAME);
        options.add(USERNAME);
        options.add(PASSWORD);
        options.add(DATABASE_NAME);
        options.add(SCHEMA_NAME);
        options.add(TABLE_NAME);
        return options;
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        Set<ConfigOption<?>> options = new HashSet<>();
        options.add(PORT);
        options.add(DECODING_PLUGIN_NAME);
        options.add(SLOT_NAME);
        options.add(SERVER_TIME_ZONE);
        options.add(SOURCE_MULTIPLE_ENABLE);
        options.add(INLONG_METRIC);
        options.add(INLONG_AUDIT);
        options.add(AUDIT_KEYS);
        options.add(APPEND_MODE);
        options.add(ROW_KINDS_FILTERED);
        return options;
    }
}
