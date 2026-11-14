package org.example.grailedmldemo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
    private static HikariConfig config = new HikariConfig();
    private static HikariDataSource ds;
    private static final Properties props = new Properties();

    static {
        try {
            props.load(DatabaseConfig.class.getClassLoader()
                    .getResourceAsStream("application.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties", e);
        }
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.username"));
        config.setPassword(props.getProperty("db.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);
    }

    private void DataSource() {}

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
    public static HikariDataSource getDataSource() {
        return ds;
    }
}
