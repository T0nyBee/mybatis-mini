package top.tonybee;

import top.tonybee.annotation.Param;
import top.tonybee.annotation.Table;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class MyCustomSqlSessionFactory {

    private static final String JDBC_URL = "jdbc:duckdb:/develop/duckdb/my_test_db1";

    @SuppressWarnings({"unchecked"})
    public <T> T getMapper(Class<T> clz) {
        // 通过动态代理拿到一个clz的实例
        return (T) Proxy.newProxyInstance(clz.getClassLoader(), new Class[]{clz}, new MapperSelectInvocationHandler());
    }

    static class MapperSelectInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (methodName.equals("toString")) {
                return proxy.toString();
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == args[0];
            }
            // TODO 其它Object类的核心方法如果用到，都需要判断、排除

            // 只代理select前缀的方法
            if (methodName.startsWith("select")) {
                System.out.println("代理执行的方法：" + methodName);
                return invokeSelectSQL(method, args);
            } else {
                throw new RuntimeException("Unsupported method: " + methodName + ". Use select* as method name prefix");
            }
        }

        private Object invokeSelectSQL(Method method, Object[] args) {
            try {
                Connection connection = DriverManager.getConnection(JDBC_URL);  // TODO 池化管理

                // 对返回集合的情况特别处理
                Class<?> wrapperReturnClz = method.getReturnType();
                Class<?> returnClz = null;
                boolean collectionFlag = false;
                boolean mapFlag = false;
                if (Collection.class.isAssignableFrom(wrapperReturnClz)) {
                    collectionFlag = true;
                    Type genericReturnType = method.getGenericReturnType();
                    if (genericReturnType instanceof ParameterizedType) {
                        Type[] actualTypeArguments = ((ParameterizedType) genericReturnType).getActualTypeArguments();
                        if (actualTypeArguments.length != 1) {
                            throw new RuntimeException("Unsupported return type: " + "Raw use of a Collection. " + "Try sign a type param to the collection as a return type.");
                        }

                        Type typeArg0 = actualTypeArguments[0];
                        // 看看泛型参数是不是Map<String, Object>，做相应处理
                        mapFlag = judgeSpecificMap(typeArg0);
                        if (!mapFlag) {  // 不是List<Map>，而是List<SomeEntity>
                            returnClz = (Class<?>) typeArg0;  // we know that Collection has only one type param

                            // 防一下Raw use of Map
                            if (returnClz == Map.class) {
                                throw new RuntimeException("Unsupported return type: " + "Raw use of Map in a Collection.");
                            }
                        }

                    }
                    // 分支：返回类型可能是Map<String, Object>
                } else if (Map.class.isAssignableFrom(wrapperReturnClz)) {
                    mapFlag = judgeSpecificMap(method.getGenericReturnType());
                } else {
                    returnClz = wrapperReturnClz;
                }

                if (returnClz == null && !mapFlag) {
                    throw new RuntimeException("Unsupported return type: " + wrapperReturnClz);
                }

                String sql = createSelectSQL(method, returnClz, mapFlag);
                System.out.println("预执行的sql是：" + sql);
                System.out.println("传入的参数列表：" + Arrays.toString(args));
                PreparedStatement statement = connection.prepareStatement(sql);
                setSQLParam(statement, args);
                ResultSet rs = statement.executeQuery();
                if (collectionFlag) {
                    Collection resultCollection = getDefaultCollection(wrapperReturnClz);
                    if (resultCollection == null) {
                        throw new RuntimeException("Unsupported return type: " + "Unknown Collection return type.");
                    }
                    while (rs.next()) {
                        Object resultItem = mapFlag ? parseResultToMap(rs) : parseResult(rs, returnClz);
                        resultCollection.add(resultItem);
                    }
                    return resultCollection;
                }

                if (mapFlag) {
                    if (rs.next()) {
                        return parseResultToMap(rs);
                    }
                }

                // 单个对象返回的情况
                if (rs.next()) {
                    return parseResult(rs, returnClz);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }


        private Object parseResult(ResultSet rs, Class<?> returnClz) throws Exception {
            Constructor<?> constructor = returnClz.getConstructor();
            constructor.setAccessible(true);
            Object resultObj = constructor.newInstance();
            Field[] fields = returnClz.getDeclaredFields();

            for (Field field : fields) {
                Object colVal = null;
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive()) {
                    colVal = rs.getObject(fieldName);
                } else if (fieldType == String.class) {
                    colVal = rs.getString(fieldName);
                } else if (fieldType == Integer.class) {
                    colVal = rs.getInt(fieldName);
                }
                field.setAccessible(true);
                field.set(resultObj, colVal);
            }
            return resultObj;
        }

        private Object parseResultToMap(ResultSet rs) throws Exception {
            Map<String, Object> result = new HashMap<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 0; i < columnCount; i++) {
                String columnName = metaData.getColumnName(i + 1);
                result.put(columnName, rs.getObject(columnName));
            }
            return result;
        }

        private String createSelectSQL(Method method, Class<?> returnClz) {
            return createSelectSQL(method, returnClz, false);
        }

        private String createSelectSQL(Method method, Class<?> returnClz, boolean selectAllFlag) {
            StringBuilder sql = new StringBuilder();
            sql.append("select ");
            List<String> cols = selectAllFlag ? Collections.singletonList("*") : getSelectColumns(returnClz);
            sql.append(String.join(", ", cols));
            sql.append(" from ");
            // 获取表名
            sql.append(getSelectTableName(returnClz, method));
            // 获取where条件
            String whereClause = getWhereClause(method);
            if (!whereClause.isEmpty()) {  // 可能没有查询参数，那也就意味着没有where条件
                sql.append(" where ");
                sql.append(whereClause);
            }

            return sql.toString();
        }

        private List<String> getSelectColumns(Class<?> returnClz) {
            Field[] declaredFields = returnClz.getDeclaredFields();
            return Arrays.stream(declaredFields).map(Field::getName).collect(Collectors.toList());
        }

        private String getSelectTableName(Class<?> returnClz, Method method) {
            if (returnClz == null) {
                // 尝试使用method找寻@Table注解信息
                Table tblAnno = method.getAnnotation(Table.class);
                if (tblAnno != null) {
                    return tblAnno.value();
                }
            }

            Table tableAnno = returnClz.getAnnotation(Table.class);
            if (tableAnno == null) {
                throw new RuntimeException("@Table annotation is required");
            }
            return tableAnno.value();
        }

        private String getWhereClause(Method method) {
            Parameter[] parameters = method.getParameters();
            List<String> whereSeg = new ArrayList<>();
            for (Parameter parameter : parameters) {
                if (parameter.isAnnotationPresent(Param.class)) {
                    Param paramAnno = parameter.getAnnotation(Param.class);
                    whereSeg.add(paramAnno.value() + " = ?");
                }
            }
            // 使用and拼接
            return String.join(" and ", whereSeg);
        }

        private void setSQLParam(PreparedStatement statement, Object[] args) throws SQLException {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof String) {
                    statement.setString(i + 1, (String) arg);
                } else if (arg instanceof Integer) {
                    statement.setInt(i + 1, (Integer) arg);
                }
                // TODO 支持更多类型
            }
        }

        private Collection<?> getDefaultCollection(Class<?> collectionClz) {
            if (List.class.isAssignableFrom(collectionClz)) {
                return new ArrayList<>();
            } else if (Set.class.isAssignableFrom(collectionClz)) {
                return new HashSet<>();
            }
            return null;
        }

        /**
         * 判断指定的clz是否为Map< String, Object >
         *
         * @param clz
         * @return
         */
        private boolean judgeSpecificMap(Type clz) {
            if (clz == null) return false;
            if (!(clz instanceof ParameterizedType)) {
                return false;
            }

            ParameterizedType pClz = (ParameterizedType) clz;
            if (pClz.getRawType() != Map.class) {
                return false;
            }

            Type[] actualTypeArguments = pClz.getActualTypeArguments();
            if (actualTypeArguments.length != 2) {
                return false;
            }
            if (actualTypeArguments[0] != String.class || actualTypeArguments[1] != Object.class) {
                throw new RuntimeException("Unsupported return type: " + "unsupported Map type param(s). " + "Only Map<String, Object> supported.");
            }

            return true;
        }
    }
}
