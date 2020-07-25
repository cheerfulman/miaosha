package org.example.controller.viewObject;

import lombok.Data;
import org.joda.time.DateTime;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ItemVO {
    // 商品id
    private Integer id;
    // 商品标题
    private String title;
    // 商品价格
    private BigDecimal price;
    // 商品库存
    private Integer stock;
    // 商品描述
    private String description;
    // 商品销量
    private Integer sales;
    // 商品图片url
    private String imgUrl;

    // 秒杀活动状态
    private Integer promoStatus;
    // 秒杀活动价格
    private BigDecimal promoPrice;
    // 秒杀活动id
    private Integer promoId;

    // 秒杀活动开始时间
    private String startDate;
}
