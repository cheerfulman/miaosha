package org.example.service;

import org.example.error.BusinessException;
import org.example.service.model.ItemModel;

import java.util.List;

public interface ItemService {
    // 创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;
    // 商品列表浏览
    List<ItemModel> listItem();
    // 商品详情浏览
    ItemModel getItemById(Integer id);

    ItemModel getItemByIdInCache(Integer id);

    // 库存减扣
    boolean decreaseStock(Integer itemId,Integer amount);
    // 库存回补
    boolean increaseStock(Integer itemId,Integer amount);

    // 异步更新库存
    boolean asyncDecreaseStock(Integer itemId,Integer amount);

    // 销量增加
    void increaseSales(Integer itemId,Integer amount);

    // 初始化库存流水
    String initStockLog(Integer itemId,Integer amount);


}
