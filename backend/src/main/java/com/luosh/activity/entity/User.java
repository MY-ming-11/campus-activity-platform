package com.luosh.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码密文。加 @JsonIgnore 是防止哪天有人直接把这个实体返回给前端，把哈希漏出去。 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String phone;

    /** USER / ADMIN */
    private String role;

    private LocalDateTime createTime;
}
