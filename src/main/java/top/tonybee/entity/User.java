package top.tonybee.entity;

import lombok.Data;
import top.tonybee.annotation.Table;

@Data
@Table("user")
public class User {
    private int id;
    private String name;
    private int age;
    private String sex;
}
