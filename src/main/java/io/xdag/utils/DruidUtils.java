package io.xdag.utils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DruidUtils {
    private static DataSource dataSource;

    static {
        try {

            Properties p = new Properties();
            InputStream inputStream = DruidUtils.class.getClassLoader().getResourceAsStream("druid.properties");
            p.load(inputStream);
            dataSource = DruidDataSourceFactory.createDataSource(p);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
    public static Connection getConnection(){
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void close(Connection connection, Statement statement){
        if (connection != null && statement != null){
            try {
                statement.close();
                connection.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    public static void close(Connection connection, Statement statement, ResultSet resultSet){
        if (connection != null && statement != null && resultSet != null){
            try {
                resultSet.close();
                statement.close();
                connection.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public static void close(Connection connection, PreparedStatement statement, ResultSet resultSet){
        if (connection != null && statement != null && resultSet != null){
            try {
                resultSet.close();
                statement.close();
                connection.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
