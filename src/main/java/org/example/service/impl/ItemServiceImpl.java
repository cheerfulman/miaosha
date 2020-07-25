package org.example.service.impl;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.example.dao.ItemDOMapper;
import org.example.dao.ItemStockDOMapper;
import org.example.dao.StockLogDOMapper;
import org.example.dataobject.ItemDO;
import org.example.dataobject.ItemStockDO;
import org.example.dataobject.StockLogDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.mq.MqProducer;
import org.example.service.ItemService;
import org.example.service.PromoService;
import org.example.service.model.ItemModel;
import org.example.service.model.PromoModel;
import org.example.validator.ValidationImpl;
import org.example.validator.ValidationResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidationImpl validator;

    @Autowired(required = false)
    private ItemDOMapper itemDOMapper;

    @Autowired(required = false)
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired(required = false)
    private PromoService promoService;

    @Autowired(required = false)
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired(required = false)
    private StockLogDOMapper stockLogDOMapper;
    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        // 校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        // 转换Item ---> dataObject
        ItemDO itemDO = this.convertItemDOItemModel(itemModel);

        // 写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());
        ItemStockDO itemStockDO = this.convertItemStockDoItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);
        // 返回创建完成的实体

        return this.getItemById(itemModel.getId());
    }
    private ItemStockDO convertItemStockDoItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }

    private ItemDO convertItemDOItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }


    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOS = itemDOMapper.listItem();
        List<ItemModel> collect = itemDOS.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            return this.convertModelFromDataObject(itemDO, itemStockDO);
        }).collect(Collectors.toList());
        return collect;
    }
    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }
        // 获得库存
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(id);

        // 将dataObject --> Model
        ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);

        PromoModel promo = promoService.getPromoById(itemModel.getId());

        if(promo != null && promo.getStatus() != 3){
            itemModel.setPromoModel(promo);
        }
        return itemModel;
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_validate_" + id);
        if(itemModel == null){
            itemModel = this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
            redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
//        int result = itemStockDOMapper.decreaseStock(itemId, amount);
        Long result = redisTemplate.opsForValue().decrement("promo_item_stock_" + itemId, amount.intValue());
        if(result > 0){
//            boolean bool = mqProducer.asyncReduceStock(itemId, amount);
//            if(bool == false){
//                redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
//                return false;
//            }
            return true;
        }else if(result == 0){
            // 标志其已售罄
            redisTemplate.opsForValue().set("promo_item_stock_invalid" + itemId,"true");
            return true;
        }
        else{
            // 更新Reids失败，回补库存
            this.increaseSales(itemId,amount);
            return false;
        }
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
        return true;
    }

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        boolean bool = mqProducer.asyncReduceStock(itemId, amount);
        return bool;
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId,amount);
    }

    // 初始化对应流水
    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        stockLogDO.setStatus(1);

        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO){
        ItemModel itemModel = new ItemModel();
        if(itemDO != null){
            BeanUtils.copyProperties(itemDO,itemModel);
            itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        }
        if(itemStockDO != null){
            itemModel.setStock(itemStockDO.getStock());
        }

        return itemModel;
    }
}
