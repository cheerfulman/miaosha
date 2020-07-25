package org.example.service.impl;

import org.example.dao.PromoDOMapper;
import org.example.dataobject.PromoDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.ItemService;
import org.example.service.PromoService;
import org.example.service.UserService;
import org.example.service.model.ItemModel;
import org.example.service.model.PromoModel;
import org.example.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired(required = false)
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public void publishPromo(Integer promoId) {
        // 通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        // 库存同步到Redis
        redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());
        // 大闸限制数量设置到redis内
        redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock().intValue() * 5);
    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
        // 商品是否售罄
        if(redisTemplate.hasKey("promo_item_stock_invalid"+itemId)){
            return null;
        }
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);

        // dataObject --> model
        PromoModel promoModel = convertFromDataObject(promoDO);

        if(promoModel == null){
            return null;
        }
        // 判断当前时间是否在秒杀
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        // 判断活动是否进行
        if(promoModel.getStatus().intValue() != 2){
            return null;
        }

        // 商品是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);  // 通过Redis 进行优化
        if(itemModel == null){
            return null;
        }
        // 用户是否存在
        UserModel userById = userService.getUserByIdInCache(userId);// 通过Redis 进行优化
        if(userById == null){
            return null;
        }
        // 获取秒杀大闸数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId,-1);

        if(result < 0){
            return null;
        }
        // 生产token
        String token = UUID.randomUUID().toString().replaceAll("-","");
        redisTemplate.opsForValue().set("promo_token_"+promoId + "_userid" + userId + "_itemid"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId,5, TimeUnit.MINUTES);
        return token;
    }

    @Override
    public PromoModel getPromoById(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        // dataObject --> model
        PromoModel promoModel = convertFromDataObject(promoDO);

        if(promoModel == null){
            return null;
        }
        // 判断当前时间是否在秒杀
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);

        promoModel.setPromoPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
