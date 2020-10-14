package org.wayne;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class TestWithoutCfn {
    public void test(){
        try {
            //Class.forName("org.wayne.Driver");
            Connection conn = DriverManager.getConnection("jdbc:wayne://172.19.1.49:7300/dwtmppdb");
            Statement statement = conn.createStatement();
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
