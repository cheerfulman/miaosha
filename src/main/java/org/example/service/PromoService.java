package org.example.service;

import org.example.service.model.PromoModel;

public interface PromoService {
    // 根据itemId 获取即将进行 和 真在进行的秒杀活动
    PromoModel getPromoById(Integer itemId);

    // 发布活动时，将其库存同步至Redis
    void publishPromo(Integer promoId);

    // 生产秒杀令牌
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);
}
