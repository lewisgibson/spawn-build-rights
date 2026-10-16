package com.lewisgibson.spawnbuildrights.storage;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/** A JDBC driver whose connect is a lambda, for the storage tests. Any other call is refused. */
final class FakeDriver {

    /** What the driver does on connect. */
    interface Connect {
        Connection connect(String url, Properties properties) throws SQLException;
    }

    private FakeDriver() {}

    static Driver of(Connect connect) {
        return (Driver)
                Proxy.newProxyInstance(
                        Driver.class.getClassLoader(),
                        new Class<?>[] {Driver.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("connect")) {
                                return connect.connect((String) args[0], (Properties) args[1]);
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }
}
