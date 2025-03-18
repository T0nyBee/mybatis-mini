package top.tonybee.dao;

import top.tonybee.annotation.Param;
import top.tonybee.annotation.Table;
import top.tonybee.entity.User;

import java.util.List;
import java.util.Map;

public interface UserMapper {
    User selectById(@Param("id") int id);

    User selectByName(@Param("name") String name);

    User selectByAgeAndName(@Param("age") Integer age, @Param("name") String name);

    List<User> selectBySex(@Param("sex") String sex);

    @Table("user")  // 无法根据返回值推断查询哪张表，因此我们选择加上@Table注解来补全这个信息
    Map<String, Object> selectByIdIntoMap(@Param("id") int id);

    @Table("user")
    List<Map<String, Object>> selectBySexIntoMap(@Param("sex") String sex);
}
