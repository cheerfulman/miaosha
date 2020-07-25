package org.example.service.impl;

import org.example.dao.OrderDOMapper;
import org.example.dao.SequenceDOMapper;
import org.example.dao.StockLogDOMapper;
import org.example.dataobject.OrderDO;
import org.example.dataobject.SequenceDO;
import org.example.dataobject.StockLogDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.ItemService;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.example.service.model.ItemModel;
import org.example.service.model.OrderModel;
import org.example.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;

    @Autowired(required =  false)
    private OrderDOMapper orderDOMapper;

    @Autowired(required =  false)
    private SequenceDOMapper sequenceDOMapper;

    @Autowired(required =  false)
    private StockLogDOMapper stockLogDOMapper;
    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId,Integer promoId, Integer amount,String stockLogId) throws BusinessException {
        // 1.校验下单状态，下单商品是否存在，用户是否合法，购买数量是否正确
//        ItemModel itemModel = itemService.getItemById(itemId); // 三次访问数据库, 商品信息，和 库存 和活动 信息表

        ItemModel itemModel = itemService.getItemByIdInCache(itemId);  // 通过Redis 进行优化
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }

//
////        UserModel userById = userService.getUserById(userId); // 两次，用户信息， 用户密码
//
//        UserModel userById = userService.getUserByIdInCache(userId);// 通过Redis 进行优化
//        if(userById == null){
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
//        }

        if(amount < 0 || amount > 900){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不存在");
        }
//        // 校验 是否有秒杀活动
//        if(promoId != null){
//            if(promoId.intValue() != itemModel.getPromoModel().getId()){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
//            }else if(itemModel.getPromoModel().getStatus().intValue() != 2){ // 校验活动是否正在进行中
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动未开始");
//            }
//        }
        // 2.落单减库存，支付减库存
        boolean result = itemService.decreaseStock(itemId, amount); // 一次 减库存操作

        if(result == false){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        // 3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        orderModel.setPromoId(promoId);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoPrice());
            orderModel.setOrderPrice(itemModel.getPromoModel().getPromoPrice().multiply(new BigDecimal(amount)));
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
            orderModel.setOrderPrice(itemModel.getPrice().multiply(new BigDecimal(amount)));
        }


        // 生产交易流水号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = this.convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO); // 一次 插入订单信息表

        // 销售量++
        itemService.increaseSales(itemId,amount); // 一次 销量表

        // 设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

        // 当订单提交后再 异步同步Redis跟MySQL
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                // 异步更新库存  问题1.如果消息发送失败则拥有失去这条信息
//                boolean bool = itemService.asyncDecreaseStock(itemId, amount);
////                if(bool == false){
////                    itemService.increaseStock(itemId,amount);
////                    throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
////                }
//            }
//        });


        // 4.返回前端
        return orderModel;
    }
    // 就算订单创建失败，ID还是继续生成
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo(){
        // 16位
        StringBuilder stringBuilder = new StringBuilder();

        // 前8为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);
        // 中间六位为 自增序列
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        Integer sequence = sequenceDO.getCurrentValue();

        sequenceDO.setCurrentValue(sequence + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKey(sequenceDO);


        String str = String.valueOf(sequence);
        for(int i = 0; i < 6 - str.length(); i ++){
            stringBuilder.append("0");
        }
        stringBuilder.append(str);

        // 后两位为分库分表位,暂是写死
        stringBuilder.append("00");
        return stringBuilder.toString();

    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();

        BeanUtils.copyProperties(orderModel,orderDO);
//
//        orderDO.setId(orderModel.getId());
//        orderDO.setAmount(orderModel.getAmount());
//        orderDO.setUserId(orderModel.getUserId());
//        orderDO.setItemId(orderModel.getItemId());

        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }

}
