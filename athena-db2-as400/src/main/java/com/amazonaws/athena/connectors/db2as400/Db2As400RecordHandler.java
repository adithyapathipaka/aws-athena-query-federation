/*-
 * #%L
 * athena-db2-as400
 * %%
 * Copyright (C) 2019 - 2022 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.amazonaws.athena.connectors.db2as400;

import com.amazonaws.athena.connector.lambda.domain.Split;
import com.amazonaws.athena.connector.lambda.domain.TableName;
import com.amazonaws.athena.connector.lambda.domain.predicate.Constraints;
import com.amazonaws.athena.connectors.jdbc.connection.DatabaseConnectionConfig;
import com.amazonaws.athena.connectors.jdbc.connection.DatabaseConnectionInfo;
import com.amazonaws.athena.connectors.jdbc.connection.GenericJdbcConnectionFactory;
import com.amazonaws.athena.connectors.jdbc.connection.JdbcConnectionFactory;
import com.amazonaws.athena.connectors.jdbc.manager.JDBCUtil;
import com.amazonaws.athena.connectors.jdbc.manager.JdbcRecordHandler;
import com.amazonaws.athena.connectors.jdbc.manager.JdbcSplitQueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.commons.lang3.Validate;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Db2As400RecordHandler extends JdbcRecordHandler
{
    private static final String QUOTE_CHARACTER = "\"";
    private static final int FETCH_SIZE = 1000;
    private final JdbcSplitQueryBuilder jdbcSplitQueryBuilder;
    public Db2As400RecordHandler(java.util.Map<String, String> configOptions)
    {
        this(JDBCUtil.getSingleDatabaseConfigFromEnv(Db2As400Constants.NAME, configOptions), configOptions);
    }

    /**
     * Constructs object of type JdbcRecordHandler.
     *
     * @param databaseConnectionConfig
     */
    public Db2As400RecordHandler(DatabaseConnectionConfig databaseConnectionConfig, java.util.Map<String, String> configOptions)
    {
        this(databaseConnectionConfig, S3Client.create(), SecretsManagerClient.create(), AthenaClient.create(),
                new GenericJdbcConnectionFactory(databaseConnectionConfig, null,
                        new DatabaseConnectionInfo(Db2As400Constants.DRIVER_CLASS, Db2As400Constants.DEFAULT_PORT)), new Db2As400QueryStringBuilder(QUOTE_CHARACTER), configOptions);
    }

    @VisibleForTesting
    Db2As400RecordHandler(DatabaseConnectionConfig databaseConnectionConfig, S3Client amazonS3, SecretsManagerClient secretsManager, AthenaClient athena, JdbcConnectionFactory jdbcConnectionFactory, JdbcSplitQueryBuilder jdbcSplitQueryBuilder, java.util.Map<String, String> configOptions)
    {
        super(amazonS3, secretsManager, athena, databaseConnectionConfig, jdbcConnectionFactory, configOptions);
        this.jdbcSplitQueryBuilder = Validate.notNull(jdbcSplitQueryBuilder, "query builder must not be null");
    }

    /**
     * Builds split SQL string and returns prepared statement.
     *
     * @param jdbcConnection jdbc connection. See {@link Connection}
     * @param catalogName Athena provided catalog name.
     * @param tableName database table name.
     * @param schema table schema.
     * @param constraints constraints to push down to the database.
     * @param split table split.
     * @return
     * @throws SQLException
     */
    @Override
    public PreparedStatement buildSplitSql(Connection jdbcConnection, String catalogName, TableName tableName, Schema schema, Constraints constraints, Split split) throws SQLException
    {
        PreparedStatement preparedStatement;

        if (constraints.isQueryPassThrough()) {
            preparedStatement = buildQueryPassthroughSql(jdbcConnection, constraints);
        }
        else {
            preparedStatement = jdbcSplitQueryBuilder.buildSql(jdbcConnection, null, tableName.getSchemaName(), tableName.getTableName(), schema, constraints, split);
        }
        // Disable fetching all rows.
        preparedStatement.setFetchSize(FETCH_SIZE);
        return preparedStatement;
    }
}