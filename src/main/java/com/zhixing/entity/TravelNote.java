package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog")
public class TravelNote implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商户id
     */
    private Long shopId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户图标
     */
    @TableField(exist = false) // 非数据库字段
    private String icon;

    /**
     * 用户姓名
     */
    @TableField(exist = false) // 非数据库字段
    private String name;

    /**
     * 是否点赞过了
     */
    @TableField(exist = false) // 非数据库字段
    private Boolean isLike;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     * 注意：这里移除了exist=false，因为这是数据库实际存在的字段
     */
    @TableLogic // MyBatis-Plus逻辑删除注解，自动处理删除查询条件
    private Integer isDeleted; // 建议用Integer类型，对应数据库tinyint

    /**
     * 标题
     */
    private String title;

    /**
     * 探店的照片，最多9张，多张以','隔开
     */
    private String images;

    /**
     * 探店的文字描述
     */
    private String content;

    /**
     * 点赞数量
     */
    private Integer liked;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}










