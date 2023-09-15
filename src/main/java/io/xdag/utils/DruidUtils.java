/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
        if (statement != null){
            try {
                statement.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }

        if (connection != null){
            try {
                connection.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    public static void close(Connection connection, Statement statement, ResultSet resultSet){
        if(resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }

        if(statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }

        if(connection != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public static void close(Connection connection, PreparedStatement statement, ResultSet resultSet){
        if(resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }

        if(statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }

        if(connection != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
