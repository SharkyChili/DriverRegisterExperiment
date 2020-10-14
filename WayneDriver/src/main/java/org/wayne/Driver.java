package org.wayne;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class Driver implements java.sql.Driver {
    static
    {
        try
        {
            // moved the registerDriver from the constructor to here
            // because some clients call the driver themselves (I know, as
            // my early jdbc work did - and that was based on other examples).
            // Placing it here, means that the driver is registered once only.
            Driver driver = new Driver();
            System.out.println("WayneDriver register " + driver.getClass().getClassLoader());
            java.sql.DriverManager.registerDriver(driver);
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }



    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        Properties properties = new Properties();
        return makeConnection(url,properties);
    }

    private static Connection makeConnection(String url, Properties props) throws SQLException {
        if(url.contains("wayne")){
            return new WayneConnection();
        }
        return null;
    }


    @Override
    public boolean acceptsURL(String url) throws SQLException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return false;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return 0;
    }

    @Override
    public int getMinorVersion() {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        return null;
    }
}
