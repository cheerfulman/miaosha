package org.example.controller;

import org.example.controller.viewObject.ItemVO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.response.CommentResponseType;
import org.example.service.CacheService;
import org.example.service.ItemService;
import org.example.service.PromoService;
import org.example.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller("item")
@RequestMapping("item")
@CrossOrigin(origins = "*",allowCredentials = "true", allowedHeaders = "*")
public class ItemController extends BaseController{
    @Autowired(required = false)
    private ItemService itemService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private PromoService promoService;
    @RequestMapping(value = "/publishpromo", method = {RequestMethod.GET})
    @ResponseBody
    public CommentResponseType publishPromo(@RequestParam(name = "id") Integer id) {
        promoService.publishPromo(id);
        return CommentResponseType.create(null);
    }

    @ResponseBody
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType createItem(@RequestParam(value = "title")String title,
                                          @RequestParam(value = "description")String description,
                                          @RequestParam(value = "price") BigDecimal price,
                                          @RequestParam(value = "stock")Integer stock,
                                          @RequestParam(value = "imgUrl")String imgUrl) throws BusinessException {
        // 封装service层来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setStock(stock);
        itemModel.setPrice(price);
        itemModel.setDescription(description);
        itemModel.setImgUrl(imgUrl);
        itemModel.setTitle(title);

        ItemModel item = itemService.createItem(itemModel);
        ItemVO itemVo = this.convertVoFromModel(item);
        return CommentResponseType.create(itemVo);
    }


    // get id 必传，否则出发 url绑定路由的问题
    @RequestMapping(value = "/get",method = {RequestMethod.GET})
    @ResponseBody
    public CommentResponseType getItem(@RequestParam(name = "id")Integer id) throws BusinessException {
        ItemModel itemModel = null;
        // 先取本地缓存
        itemModel = (ItemModel) cacheService.getFromCommonCache("item_"+id);
        // 多级缓存的格式，先取本地 然后取redis 然后 数据库
        if(itemModel == null){
            // 根据redis 获取商品 Model
            itemModel = (ItemModel) redisTemplate.opsForValue().get("item_" + id);
            // 若不存在
            if(itemModel == null){
                itemModel=itemService.getItemById(id);
                // 将其放入Redis
                redisTemplate.opsForValue().set("item_" + id,itemModel);
                redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
            }
            // 填充本地缓存
            cacheService.setCommonCache("item_"+id,itemModel);
        }

        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"参数不合法");
        }

        ItemVO itemVO = convertVoFromModel(itemModel);
        return CommentResponseType.create(itemVO);
    }

    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommentResponseType listItem(){
        List<ItemModel> itemModels = itemService.listItem();
        List<ItemVO> list = itemModels.stream().map(itemModel -> {
            return this.convertVoFromModel(itemModel);
        }).collect(Collectors.toList());
        return CommentResponseType.create(list);
    }



    private ItemVO convertVoFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemVO itemVo = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVo);
        if(itemModel.getPromoModel() != null){
            itemVo.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVo.setPromoId(itemModel.getPromoModel().getId());
            itemVo.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVo.setPromoPrice(itemModel.getPromoModel().getPromoPrice());
        }else{
            itemVo.setPromoStatus(0);
        }
        return itemVo;
    }

}
