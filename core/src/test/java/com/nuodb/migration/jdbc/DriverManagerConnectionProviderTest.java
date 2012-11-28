package com.nuodb.migration.jdbc;


import com.nuodb.migration.jdbc.connection.JdbcConnectionProvider;
import com.nuodb.migration.spec.JdbcConnectionSpec;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Properties;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DriverManagerConnectionProviderTest {

    private static final int TRANSACTION_ISOLATION = Connection.TRANSACTION_READ_COMMITTED;
    private static final String URL = "jdbc:test";

    @Mock
    private JdbcConnectionSpec connectionSpec;
    @Mock
    private Driver driver;
    @Mock
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        when(driver.connect(anyString(), any(Properties.class))).thenReturn(connection);
        when(driver.acceptsURL(URL)).thenReturn(true);
        DriverManager.registerDriver(driver);

        connectionSpec = mock(JdbcConnectionSpec.class);
        when(connectionSpec.getDriver()).thenReturn(driver);
        when(connectionSpec.getUrl()).thenReturn(URL);
        when(connectionSpec.getUsername()).thenReturn("user");
        when(connectionSpec.getPassword()).thenReturn("pass");
    }

    @Test
    public void testGetConnection() throws Exception {
        JdbcConnectionProvider connectionProvider =
                new JdbcConnectionProvider(connectionSpec);
        connectionProvider.setTransactionIsolation(TRANSACTION_ISOLATION);
        connectionProvider.setAutoCommit(false);
        Assert.assertNotNull(connectionProvider.getConnection());
        verify(connectionSpec).getDriver();
        verify(connectionSpec).getUsername();
        verify(connectionSpec).getUrl();
        verify(connectionSpec).getPassword();
        verify(connection).setTransactionIsolation(anyInt());
        verify(connection).setAutoCommit(false);
    }

    @After
    public void tearDown() throws Exception {
        DriverManager.deregisterDriver(driver);
    }
}
