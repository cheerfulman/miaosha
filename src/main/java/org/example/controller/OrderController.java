package org.example.controller;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.mq.MqProducer;
import org.example.response.CommentResponseType;
import org.example.service.ItemService;
import org.example.service.OrderService;
import org.example.service.PromoService;
import org.example.service.model.UserModel;
import org.example.util.CodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = "*",allowCredentials = "true", allowedHeaders = "*")
public class OrderController extends BaseController{
    @Autowired
    private OrderService orderService;

    @Autowired
    private MqProducer mqProducer;
    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    @Autowired(required = false)
    private ExecutorService executorService;

    private RateLimiter orderCreateLimiter;
    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(20);
        orderCreateLimiter = RateLimiter.create(10);
    }

    //生成验证码
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public void generateVerifyCode(HttpServletResponse response) throws BusinessException, IOException {
        // 获取根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        // 获取用户登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "登录过期，不能生成验证");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("verify_code_" + userModel.getId(), map.get("code"));
        redisTemplate.expire("verify_code_" + userModel.getId(), 10, TimeUnit.MINUTES);

        System.out.println("验证码为： " + map.get("code"));
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }

    @ResponseBody
    @RequestMapping(value = "/generatetoken",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                           @RequestParam(name = "promoId")Integer promoId,
                                           @RequestParam(name = "verifyCode") String verifyCode) throws BusinessException {

        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户未登录，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登录，不能下单");
        }
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        if (StringUtils.isEmpty(redisVerifyCode)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "请求非法");
        }
        if (!redisVerifyCode.equalsIgnoreCase(verifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "请求非法");
        }

        // 获取秒杀令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if(promoToken == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"生产令牌失败");
        }

        // 返回对应结果
        return CommentResponseType.create(promoToken);

    }

    @ResponseBody
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                           @RequestParam(name = "amount")Integer amount,
                                           @RequestParam(name = "promoId",required = false)Integer promoId,
                                           @RequestParam(name = "promoToken",required = false)String promoToken) throws BusinessException {
        /*
            非分布式处理方法，放入session中
        Boolean is_login = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
       if(is_login == null || !is_login.booleanValue()){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
          }
        UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
         */

        if(!orderCreateLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.RATELIMIT);
        }

        // 分布式会话处理，将token 和 session 放入 redis服务器中
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户未登录，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登录，不能下单");
        }
//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId,promoId, amount);
        if(promoId != null){
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId + "_userid" + userModel.getId() + "_itemid"+itemId);
            if(inRedisPromoToken == null || !StringUtils.equals(promoToken,inRedisPromoToken)){
                // 令牌校验失败
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"令牌校验失败");
            }
        }
        // 同步调用线程池的submit方法
        // 拥塞窗口为20的等待队列，用来队列化泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                // 加入库存流水init状态  通过流水追踪异步消息
                String stockLogId = itemService.initStockLog(itemId, amount);

                // 完成对应下单事物消息
                boolean bool = mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId);
                if (bool == false) {
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }

        return CommentResponseType.create(null);
    }
}
