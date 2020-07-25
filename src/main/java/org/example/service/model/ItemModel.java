package org.example.service.model;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ItemModel implements Serializable {
    // 商品id
    private Integer id;
    // 商品标题
    @NotBlank(message = "商品名称不能为空")
    private String title;
    // 商品价格
    @NotNull(message = "商品价格不能为空")
    @Min(value = 0,message = "商品价格必须大于0")
    private BigDecimal price;
    // 商品库存
    @NotNull(message = "商品库存不能为空")
    private Integer stock;
    // 商品描述
    @NotNull(message = "商品描述信息不能为空")
    private String description;
    // 商品销量
    private Integer sales;
    // 聚合 秒杀活动
    private PromoModel promoModel;
    // 商品图片url
    @NotNull(message = "图片信息不能为空")
    private String imgUrl;
}
