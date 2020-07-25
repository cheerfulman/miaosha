package org.example.service;

import org.example.error.BusinessException;
import org.example.service.model.OrderModel;
import org.springframework.stereotype.Service;

@Service
public interface OrderService {
    // 1.通过前端传过来的 秒杀活动id，在下单接口内校验id是否属于对应商品且活动开始
    // 2.直接在下单接口内 判断对应商品是否存在下单秒杀活动，如果存在则直接秒杀下单
    OrderModel createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount, String stockLogId) throws BusinessException;
}
