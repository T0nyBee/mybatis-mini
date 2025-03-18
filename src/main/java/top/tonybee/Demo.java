package top.tonybee;

import lombok.Data;
import top.tonybee.annotation.Param;
import top.tonybee.annotation.Table;

import java.lang.reflect.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Demo {
    public static void main(String[] args) throws Exception {
        CustomSqlSessionFactory customSqlSessionFactory = new CustomSqlSessionFactory();
        StudentMapper studentMapper = customSqlSessionFactory.getMapper(StudentMapper.class);
        Student student1 = studentMapper.selectById(1);
        System.out.println(student1);

        Student student2 = studentMapper.selectByName("小李");
        System.out.println(student2);
    }

    public static void initDB() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:/develop/duckdb/my_test_db1");
        Statement statement = connection.createStatement();
        statement.execute("create or replace sequence student_id_seq start with 1");
        statement.execute("create or replace table student (id int primary key default nextval('student_id_seq'), name varchar, grade int, classNo int)");
        statement.execute("insert into student (name, grade, classNo) values ('小明', 3, 1), ('小李', 3, 2), ('小王', 2, 1)");

        ResultSet rs = statement.executeQuery("select * from student");
        while (rs.next()) {
            System.out.println("查询结果：" + rs.getInt(1) + ", " + rs.getString(2) + ", " + rs.getInt(3) + ", " + rs.getString(4) + "\n");
        }
        statement.close();
    }

    interface StudentMapper {
        Student selectById(@Param("id") int id);
        Student selectByName(@Param("name") String name);
    }

    @Data
    @Table("student")
    static class Student {
        private int id;
        private String name;
        private int grade;  // 年级
        private int classNo;
    }

    @SuppressWarnings("all")
    static class CustomSqlSessionFactory {
        public <T> T getMapper(Class<T> clz) {
            return (T) Proxy.newProxyInstance(clz.getClassLoader(), new Class[]{clz}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String methodName = method.getName();
                    if (methodName.startsWith("select")) {
                        // select开头，就执行sql查询
                        Connection connection = DriverManager.getConnection("jdbc:duckdb:/develop/duckdb/my_test_db1");  // 这里有张student表，结构与Student实体一致；里面有一些数据。

                        // 获取select字段名列表
                        List<String> fieldList = Arrays.stream(method.getReturnType().getDeclaredFields()).map(item -> item.getName()).collect(Collectors.toList());

                        // 获取表名
                        String tblName = method.getReturnType().getAnnotation(Table.class).value();

                        // 获取查询字段列表
                        List<String> whereSegList = new ArrayList<>();
                        for (Parameter parameter : method.getParameters()) {
                            if (parameter.isAnnotationPresent(Param.class)) {
                                Param param = parameter.getAnnotation(Param.class);
                                whereSegList.add(param.value() + " = ?");
                            }
                        }

                        // 拼装SQL
                        String sql = "select " + String.join(", ", fieldList) + " from " + tblName + " where " + String.join(" and ", whereSegList);
                        PreparedStatement pstmt = connection.prepareStatement(sql);

                        // 把参数列表set进去
                        for (int i = 0; i < args.length; i++) {
                            Object arg = args[i];
                            if (arg instanceof String) {
                                pstmt.setString(i + 1, (String) arg);
                            } else if (arg instanceof Integer) {
                                pstmt.setInt(i + 1, (Integer) arg);
                            }
                            // TODO 支持更多类型
                        }

                        // 执行
                        ResultSet rs = pstmt.executeQuery();

                        // 组装返回
                        Object result = method.getReturnType().getConstructor().newInstance();
                        if (rs.next()) {
                            Field[] fields = method.getReturnType().getDeclaredFields();
                            for (int i = 0; i < fields.length; i++) {
                                Object colVal = null;
                                Field field = fields[i];
                                if (field.getType().isPrimitive()) {
                                    colVal = rs.getObject(field.getName());
                                } else if (field.getType() == String.class) {
                                    colVal = rs.getString(field.getName());
                                } else if (field.getType() == Integer.class) {
                                    colVal = rs.getInt(field.getName());
                                }
                                field.setAccessible(true);
                                field.set(result, colVal);
                            }
                        }

                        return result;
                    }
                    return null;
                }
            });
        }
    }
}
