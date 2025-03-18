package top.tonybee;

import cn.hutool.json.JSONUtil;
import top.tonybee.dao.UserMapper;

import java.sql.*;

public class App {
    public static void main(String[] args) throws Exception {
        MyCustomSqlSessionFactory myCustomSqlSessionFactory = new MyCustomSqlSessionFactory();
        UserMapper mapper = myCustomSqlSessionFactory.getMapper(UserMapper.class);
        System.out.println("查询结果：" + mapper.selectById(1) + "\n");
        System.out.println("查询结果：" + mapper.selectByName("lily") + "\n");
        System.out.println("查询结果：" + mapper.selectByAgeAndName(17, "lily") + "\n");
        // 返回集合
        System.out.println("查询结果：" + mapper.selectBySex("男") + "\n");
        // 返回Map
        System.out.println("查询结果：" + JSONUtil.toJsonStr(mapper.selectByIdIntoMap(1)) + "\n");
        // 返回List<Map>
        System.out.println("查询结果：" + JSONUtil.toJsonStr(mapper.selectBySexIntoMap("男")) + "\n");
    }

    public static void initDB() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:/develop/duckdb/my_test_db1");
        Statement statement = connection.createStatement();
        statement.execute("create or replace sequence user_id_seq start with 1");
        statement.execute("create or replace table user (id int primary key default nextval('user_id_seq'), name varchar, age int, sex varchar)");
        statement.execute("insert into user (name, age, sex) values ('tony', 18, '男'), ('lily', 17, '女'), ('van', 16, '男')");

        ResultSet rs = statement.executeQuery("select * from user");
        while (rs.next()) {
            System.out.println("查询结果：" + rs.getInt(1) + ", " + rs.getString(2) + ", " + rs.getInt(3) + ", " + rs.getString(4) + "\n");
        }
        statement.close();
    }
}
