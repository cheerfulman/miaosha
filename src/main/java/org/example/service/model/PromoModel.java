package org.example.service.model;

import lombok.Data;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PromoModel implements Serializable {
    private Integer id;
    // 秒杀活动状态 1未开始 2 进行中 3 已结束
    private Integer status;

    // 秒杀获得名称
    private String promoName;

    // 秒杀活动开始时间
    private DateTime startDate;
    private DateTime endDate;

    // 秒杀活动适用商品
    private Integer itemId;

    // 秒杀活动的价格
    private BigDecimal promoPrice;
}
