## 项目各层介绍

项目架构：

![](/img/image-20200717114243234.png)

![image-20200717113141077](/img/image-20200717113141077.png)

![image-20200717113346776](/img/image-20200717113346776-1595682039936.png)

![image-20200717115218323](/img/image-20200717115218323.png)

## 秒杀项目并发优化

首先在之前的项目中，我们用`BaseController`基类配合`@ExceptionHandler`来拦截抛出的异常，但是相对于一些无法进入`Controller`的错误就无法通过`BaseController`处理，比如**404或者405**的错误。

我们通过`@ControllerAdvice`利用切片的原理配配合`@ExceptionHandler`来处理异常。

```java
// 利用切面的方式处理全局异常，摒弃BaseController + @ExceptionHandler的方式（只能处理经过Controller的异常无法处理 页面路径等404，405的错误）
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommentResponseType doError(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        ex.printStackTrace();
        Map<String, Object> responseData = new HashMap<>();
        if (ex instanceof BusinessException) {
            BusinessException bizException = (BusinessException) ex;
            responseData.put("errCode", bizException.getErrCode());
            responseData.put("errMsg", bizException.getErrMsg());
        } else if (ex instanceof ServletRequestBindingException) {
            // @RequestParam是必传的，如果没传，就会触发这个异常
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", "URL绑定路由问题");
        } else if (ex instanceof NoHandlerFoundException) {
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", "没有找到对应的访问路径");
        } else {
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", EmBusinessError.UNKNOWN_ERROR.getErrMsg());
        }
        return CommentResponseType.create(responseData, "fail");
    }
}
```

并且在spring配置文件中添加 ：

```properties
spring.mvc.throw-exception-if-no-handler-found=true
spring.resources.add-mappings=false
```

**详情请见** : https://www.jianshu.com/p/ced4eb2edddf

### 将项目部署入阿里云

通过`navicat`将数据库形成脚本然后再云端执行改脚本则直接复制出本机的数据。

打包项目时要注意配置一些属性。

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>2.2.6.RELEASE</version> <!--注意添加版本号否则可能无法更新依赖-->
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

```text
最后 mvn clean install -Dmaven.test.skip 进行打包生产jar文件
```

### 利用deploy启动脚本

如果我们直接在服务器上启动项目，当我们退出终端时，项目则停止，我们可以利用**deploy**启动脚本

> 很多时候我们本机的配置和线上的配置不一样，我们可以在jar文件同一目录下创建一个`application.properties`来修改一些配置。

新建一个sh文件，`vim deploy.sh`

```sh
nohup java -Xms400m -Xmx400m -XX:NewSize=200m -XX:MaxNewSize=200m -jar miaosha.jar --spring.config.additional-location=/root/App/miaosha/application.properties(application.properties该文件地址)
```

最后使用`./deploy.sh &`即可在后台运行，允许后可以用tail 等命令查看

### 利用Screen执行

同deploy作用都是防止关闭终端时项目停掉。

```text
我们先 yum install screen 命令安装工具
screen -S [$Name]   创建窗口 
screen -ls  列出screen窗口
然后使用 Ctrl 和 a 键，再按下 d 键，就可以退出SSH登录，但不会影响screen程序的运行。
screen -r -d   恢复会话
```

官方文档学习 ----- https://help.aliyun.com/knowledge_detail/42523.html?spm=5176.2000002.0.0.616d7b0fuIjmqa

### jmeter性能压测

在官网下载号jmeter 解压后，配置环境变量 即可 使用 该工具 简单使用即是  创建线程组 -- http 请求 --- 结果树 ----  聚合报告

### 单机服务器并发容量问题和优化

`pstree -p pid` 查看该线程即其子线程形成的线程树 利用`| wc -l` 查看行数 故 `pstree -p pid | wc -l` 即可查看有多少线程

```text
top -H 查看cpu使用情况
cpu us ---- 用户空间cpu使用情况
cpu sy ----  内核空间cpu使用情况
load average ---- 0 代表通常，1 打满， 1+ 阻塞
```

在使用`jmeter`进行压测时发现性能低下；

#### 更改Spring Boot内嵌Tomcat线程优化

首先就是修改Tomcat的默认值`spring-configuration-metadata.json`中可查看。

```proper
# 内嵌Tomcat默认配置 
server.tomcat.accept-count : 等待队列长度，默认100
server.tomcat.max-connetion : 最大可连接长度，默认10000
server.tomcat.max-threads:最大工作线程数，默认200
server.tomcat.min-spare-threads: 最小工作线程数，默认10
```

> 在默认配置下 连接超过10000后出现 拒绝连接情况
>
> 触发请求超过200 + 100 后拒绝

```properties
server.tomcat.accept-count=1000 ---- 等待队列不是越大越好，一是受到内存的限制，二是大量的出队入队操作耗费CPU性能。
server.tomcat.max-threads=800 ---- 线程数不是越多越好，线程之间的来回切换是消耗时间的，在4核8G中经验值是800
serevr.tomcat.min-spare-threads=100  ---  可应付突发情况
```

**上述优化后发现性能有明显提升，`tps`和响应速度大概翻一倍；**

> 为了防止恶意发起攻击如DDos发送无脑的包，故我们要定制化tomcat配置。主要如下两个参数

```text
KeepAliveTimeOut : 多少毫秒不响应断开keepalive(长连接)

maxKeepAliveRequest :多少次请求后keppAlive断开失效
```

由于**Spring Boot**内嵌Tomcat默认使用`HTTP 1.0`的**短连接**，**Spring Boot**并没有把所有**Tomcat**配置都暴露出来，所以需要编写一个配置类使用`HTTP 1.1`的**长连接**。

```java
// 当spring容器内没有TomcatEmbeddedServletContainerFactory这个bean时，会把此bean加载进spring容器
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // 使用对应工厂类提供给我们的接口定制化我们的tomcat connect
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocolHandler = (Http11NioProtocol) connector.getProtocolHandler();
                // 定制化keepalivetimeout,设置30秒没有请求则服务器自动断开keepalive连接
                protocolHandler.setKeepAliveTimeout(30000);
                // 当客户端发送超过10000个请求则自动断开keepalive连接
                protocolHandler.setMaxKeepAliveRequests(10000);
            }
        });
    }
}
```

#### 优化后效果

（2核CPU），200*50个请求，TPS在**120左右，平均响应**1000毫秒，优化后TPS在**210左右**，平均**300毫秒**

## 分布式扩展优化

### 配置环境

配置两台运行`java`的服务器和一台`mysql`服务器。

```text
两个服务器之间传输文件尽量使用内网，内网更快。
scp -r //文件夹 root@(远程服务器内网ip):(传输到哪个文件)
传输完mysql后，发现不能直接访问：（数据库设置不是所有人只要知道用户密码就能直接访问的）
mysql更改权限（设置白名单）：grant all privileges on *.*(所有的数据库中所有的表*.*) to root@'%'(所有的Host -- %) identified by 'root'
filush privileges 即可成功
```

> 至此两台`java`服务器可以连接到`mysql`服务器后，我们配置`java`环境

```text
传输文件时，java服务器中已有java安装包
我们直接rpm -ivh rpm包名
-i 安装软件包
-v 可视化，也就是查看更多详细输出
-h 显示安装的进度
当然我们要修改application.properties中 spring.datasource.url中的ip地址更改成对应mysql服务器的ip即可
然后./deploy.sh & 即可运行jar包
tail -f nohup.out 观察启动日志(deploy 脚本启动项目方式)
```

### nginx

未扩展时：

![image-20200720114528938](/img/image-20200720114528938.png)

扩展后：

![image-20200720155114100](/img/image-20200720155114100.png)

本项目中的使用：

+ 使用nginx作为web服务器（放入静态资源文件暴露出去）
+ 动态分离服务器
+ 反向代理服务器（将动态请求反向代理给后端）

**1、正向代理**

 在客户端（浏览器）配置代理服务器，通过代理服务器进行互联网访问。

**2、反向代理**

 客户端只需要将请求发送到反向代理服务器，由反向代理服务器去选择目标服务器获取数据后，再返回给客户端。此时反向代理服务器和目标服务器对外就是一个服务器，暴露的是代理服务器地址，隐藏真实服务器IP地址。

> 将静态资源部署到nginx服务器resources中

```text
我们使用OpenResty框架作为nginx开发框架
yum install -y wget 安装wget下载工具
通过wget 命令下载OpenResty后
tar -xvzf进行解压 （-cjvf 压缩）
-x  --extract, --get 解压文件
-v --verbose 显示详细的tar处理的文件信息
-z --gzip, --gunzip, --ungzip      通过 gzip 来进行归档压缩
-f --file 要操作的文件名

下载后都是源代码，将源码配置编译操作掉
yum install pcre-devel openssl-devel gc curl 安装前置条件
./configure 
make 编译
make install 安装

sbin/nginx -c conf/nginx.conf 启动nginx
```

查看`nginx`配置文件 `vim nginx.conf`

```text
worker_processes 1; 工作进程
events {
	worker_connections 1024; 工作连接
}
可以配置多个 Server节点
server{
	location / resources/{
		alias /usr/local/openresty/nginx/html/resources/;(将上面的resources 替换 下面的路径)
	}
}
sbin/nginx -s reload 无缝重启（nginx服务器可以做到）
```

### **nginx 分配服务器策略**

**第一种 轮询（默认）**
每个请求按时间顺序逐一分配到不同的后端服务器，如果后端服务器 down 掉，能自动剔除。
**第二种 weight**
weight 代表权重默认为 1,权重越高被分配的客户端越多
**第三种 ip_hash**
每个请求按访问 ip 的 hash 结果分配，这样每个访客固定访问一个后端服务器
**第四种 fair（第三方）**
按后端服务器的响应时间来分配请求，响应时间短的优先分配。

> 动态资源的配置

```text
配置java服务器
upstream backend_server{
	server 局域网ip地址 weight = 1;
	server 局域网ip地址 weight = 1;
	keepalive 30; // 使nginx服务与两台java服务器之间保持长连接
}
server{
	// 其他资源进入后端java服务器
	location /{
		proxy_pass http://backend_server;
		proxy_set_header Host $http_hosti:$proxy_port;
		proxy_set_header X-Real-IP $remote_addr;
		proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
		proxy_http-version 1.1; // 只有1.1才有长连接
		proxy_set_header Connection "";//如果是空则长连接 
	}
}
// 反向代理生效
```

> 开启tomcat_access_log验证

```text
配置一台java服务器
mkdir tomcat
chmod -R 777 tomcat/
vim tomcat
server.tomcat.accesslog.enabled=true
server.tomcat.accesslog.directory=tomcat文件知道
server.tomcat.accesslog.pattern=%h %l %u %t "%r" %s %b %D
```

**配置完成后tps可以达到1600 平均耗时400ms**

### 分布式会话

由于我们原本生产是session是各自的服务器上，那么如果在`nginx`服务器进行轮询时，你在后端服务器1登录，在后端服务器2进入登录后页面，则会显示未登录，**所以我们要把生产的`SessionId`存储到`redis`服务器上**

首先我们引入两个Jar包

```xml
<!--      redis-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <version>2.3.1.RELEASE</version>
</dependency>
<!--    将session 放入redis-->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
    <version>2.0.5.RELEASE</version>
</dependency>
```

并且新建一个`RedisConfig`类，暂时不进行任何操作只添加两个注解

```java
@Component
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
```

并在properties中添加redis相关配置

```xml
# redis配置
spring.redis.host=（对应服务器主机ip）
spring.redis.port=6379
spring.redis.database=10

# 设置jedis 连接池
spring.redis.jedis.pool.max-active=50
spring.redis.jedis.pool.min-idle=20
```

由于登录时我们将`UserModel`存在session中，现在存储在`redis`中，我们要给`UserModel`配置序列化操作。

**登录时，我们生产Token，将其代表`sessionId`与`UserModel`以键值对的形式存入`Redis`中**

```java
String uuidToken = UUID.randomUUID().toString().replaceAll("-","");
// 建立token和用户登录态之间的联系
redisTemplate.opsForValue().set(uuidToken,userModel);
redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);
// 返回给前端
return CommonReturnType.create(uuidToken);
```

```js
// 如果登录成功，则将后端返回的token存放到localStorage里面。
if (data.status == "success") {
    alter("登录成功");
    var token = data.data;
    window.localStorage["token"] = token;
    window.location.href = "listitem.html";
}
```

前端要下单时

```js
var token = window.localStorage["token"];
if (token == null) {
    alter("没有登录，不能下单");
    window.location.href = "login.html";
    return false;
}
// 下单时，将其带上方便后端获取Token来校验是否登录
$.ajax({
    type: "POST",
    url: "http://" + g_host + "/order/createorder?token=" + token,
    ···
});
```

```java
String token=httpServletRequest.getParameterMap().get("token")[0];
if(StringUtils.isEmpty(token)){
    throw new BizException(EmBizError.USER_NOT_LOGIN,"用户还未登录，不能下单");
}
UserModel userModel= (UserModel) redisTemplate.opsForValue().get(token);
if(userModel==null){
    throw new BizException(EmBizError.USER_NOT_LOGIN,"登录过期，请重新登录");
}
```

### 小结

本节引入了分布式会话，有两种常见的实现方式：

1. 第一种是通过Spring提供的API，将Tomcat的`SessionId`和`UserModel`存到Redis服务器上。
2. 第二种是通过UUID生成登录`token`，将`token`和`UserModel`存到Redis服务器上。

## 优化查询

对查询的优化：首先我们可以用到缓存。

1. redis缓存
2. 热点内存本地缓存
3. nginx proxy cache缓存
4. nginx lua 缓存

### Redis缓存

+ 单机版
+ sentinal哨兵模式
+ 集群cluster模式

我们采用单机版对查询进行缓存。

item商品信息，会查询三张表，首先是**item基础信息表**，然后是**库存表**，再者是**promo活动信息表**（是否参加秒杀活动）故很消耗**Mysql的性能**，我们采取缓存的方式如果其id相同，则返回相同的Model即可。

我们采用item_id，表示Item商品+商品id 代表其key；

当我们访问商品时，我们先从redis中获取，如果redis中没有，则访问数据库获取，并且写回到redis；

```java
// itemController代码
// 根据redis 获取商品 Model
ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_" + id);
// 若不存在
if(itemModel == null){
    itemModel=itemService.getItemById(id);
    // 将其放入Redis
    redisTemplate.opsForValue().set("item_" + id,itemModel);
    redisTemplate.expire("ietm_"+id,10, TimeUnit.MINUTES);
}
```

> 然后此时我们查看redis中的数据时是乱码的形式，原因为我们没有配置其序列化格式，其采用自己的一种编码方式

```java
public class RedisAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(
        name = {"redisTemplate"}// 此注解代表 当我们spring容器中没有redistemplate Bean时，其自动给我们配置此编码格式的Bean
    )
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) throws UnknownHostException {
        RedisTemplate<Object, Object> template = new RedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
}
```

此时我们自己配置一个Bean并且解决Reids序列化的问题

```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
    RedisTemplate redisTemplate = new RedisTemplate();
    redisTemplate.setConnectionFactory(redisConnectionFactory);

    //首先解决key的序列化格式
    StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
    redisTemplate.setKeySerializer(stringRedisSerializer);

    //解决value的序列化格式
    Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);

    //解决日期的序列化格式
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleModule simpleModule = new SimpleModule();
    simpleModule.addSerializer(DateTime.class, new JodaDateTimeJSONSerializer());
    simpleModule.addDeserializer(DateTime.class, new JodaDateTimeDeserializer());

    objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    objectMapper.registerModule(simpleModule);

    jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
    redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
    return redisTemplate;
}

// 对于日期而言，序列化后是一个很长的毫秒数。我们希望是yyyy-MM-dd HH:mm:ss的格式，还需要进一步处理。新建serializer包，里面新建两个类。
public class JodaDateTimeJSONSerializer extends JsonSerializer<DateTime> {
    @Override
    public void serialize(DateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(dateTime.toString("yyyy-MM-dd HH:mm:ss"));
    }
}
public class JodaDateTimeDeserializer extends JsonDeserializer<DateTime> {
    @Override
    public DateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String dateString = jsonParser.readValueAs(String.class);
        DateTimeFormatter formatter=DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        return DateTime.parse(dateString,formatter);
    }
}
```

### 使用本地缓存优化

虽然Redis很好，但是还是设计网络的I/O,没有本地缓存快，我们在Redis之前添加一层**”本地热点“**所谓**本地**，就是利用**本地JVM的内存**。所谓**热点**，由于JVM内存有限，仅存放**多次查询**的数据（一般是一秒内上千甚至上万的访问）。

每个本地缓存也就是存储在各自服务器的，所以我们对脏数据也不是特别敏感，但是一定要有过期时间，并且要设置得比较短。

这里本地缓存我们可以使用`HashMap`,但是其不能支持并发修改，故我们可以选取`ConcurrentHashMap`但是无法高效处理**过期时限**、没有**淘汰机制**等问题，所以这里使用了`Google`的`Guava Cache`方案。

```xml
// 配置Guava依赖
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>19.0</version>
</dependency>
```

封装本地缓存的Service接口

```java
// 封装本地缓存操作类
public interface CacheService {
    // 存方法
    void setCommonCache(String key,Object value);
    // 取
    Object getFromCommonCache(String key);
}
```

```java
// 其实现类
@Service
public class CacheServiceImpl implements CacheService {
    private Cache<String,Object> commonCache = null;

    @PostConstruct
    public void init(){
        commonCache = CacheBuilder.newBuilder()
                // 初始容量为10
                .initialCapacity(10)
                // 设置缓存中最大可存储100个key，超过后按照LRU策略移除
                .maximumSize(100)
                // 设置写缓存后多少秒过期
                .expireAfterWrite(60,TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
```

在获取商品id详情时，先走本地缓存 ---  Redis缓存 --  mysql服务器

```java
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
```

至此**tps**能最高到达**4000**，并且保持在**50ms**以内，因为大部分重复的访问全部走向了**本地缓存**，对服务器没有一点压力

#### 本地缓存缺点

1. 更新特别麻烦，容易产生脏缓存。
2. 受到`JVM`容量的限制。

### Nginx Proxy Cache缓存

通过**Redis缓存**减少了**MySQL的重复查询**，通过**本地缓存**，减少了Redis的**网络I/O**，大大的提高了效率，但是客户端请求Nginx服务器也有分发过程，需要去请求后面的两台应用服务器，有一定网络I/O，我们可以直接把**热点数据**存放到**Nginx服务器**上。

通过`Nginx Proxy Cache`作为文件存放在指定目录下。

```text
# 申明一个cache缓存节点 evels 表示以二级目录存放
    proxy_cache_path /usr/local/openresty/nginx/tmp_cache levels=1:2 keys_zone=tmp_cache:100m inactive=7d max_size=10g;
...
server{
    location / {
        #proxy_cache 目录
        proxy_cache tmp_cache;
        proxy_cache_key $uri;
        #只有后端返回以下状态码才缓存
        proxy_cache_valid 200 206 304 302 7d;
    }
}
```

这样在tmp_cache/下生成了对应的文件为JSON格式。

但是不好用，它是放在本地磁盘上的，有磁盘I/O，而且一般企业级都是用到的NAS。 一般很少使用。

### nginx lua脚本

协程机制：

+ 依附于线程的内存模型，切换开销小
+ 遇阻塞及归还执行权，代码同步
+ 无需加锁

**暂是跳过 ....**

## 静态资源之CDN

### CDN

CDN是内容分发网络，一般用来存储（缓存）项目的静态资源。访问静态资源，会从离用户**最近**的CDN服务器上返回静态资源。如果该CDN服务器上没有静态资源，则会执行**回源**操作，从Nginx服务器上获取静态资源。

---

之前静态资源是直接从**Nginx服务器**上获取，而现在会先去**CDN服务器**上获取，如果没有则回源到**Nginx服务器**上获取。

#### CDN的使用

1. 在CDN服务器上，选择要加速的域名，和回源的IP(Nginx服务器)。

2. 配置DNS解析规则，一般A类型则是普通类型，我们选择CNAME类型即可

#### cache control 响应头

+ private : 客户端可以缓存
+ public ：客户端代理服务器都可以缓存
+ max-age = xxx: 缓存内容将在多少秒后失效
+ no-cache : 强制向服务端再验证一次
+ no-store ： 不缓存请求的任何返回内容

#### 有效性验证

验证当前缓存是否还有效，有没有被更改，服务器资源是否被更新，客户端就需要及时的刷新缓存，主要使用两个字段 **Last-Modified** 和 **etag**。

> 判断修改时间是否一致

If-Modified-Since和If-Unmodified-Since的区别是：
**If-Modified-Since**：告诉服务器如果时间一致，返回状态码304
**If-Unmodified-Since**：告诉服务器如果时间不一致，返回状态码412

> 弥补modified只判断时间的缺陷

**ETag**：第一次请求资源的时候，服务器会根据**资源内容**生成一个**唯一标示（md5）ETag**，并返回给浏览器。浏览器下一次请求，会把**ETag**（If-None-Match）发送给服务器，与服务器的ETag进行对比。如果一致，就返回一个**304**响应，即**Not Modify**，**表示浏览器缓存的资源文件依然是可用的**，直接使用就行了，不用重新请求。

> 请求资源的流程

![image-20200720204317719](/img/image-20200720204317719.png)

**a标签/回车刷新**：查看`max-age`是否有效，有效直接从缓存中获取，无效进入缓存协商逻辑。

**F5刷新**：取消`max-age`或者将其设置为0，直接进入缓存协商逻辑。

**CTRL+F5强制刷新**：直接去掉`cache-control`和协商头，重新请求资源。

## 交易流程优化

在交易中流程中，我们首先要对**用户信息表**和**密码**得到`UserModel`,然后是**商品信息**、**商品库存**、**商品活动表**进行数据库查询得到`ItemModel`来进行**用户风控策略**和**活动校验策略**，最后对商品库存执行**-1** `update`操作，创建**订单信息操作**和**增加改商品销量**等多次**数据库的I/O**,并且，减库存操作还存在**行锁阻塞**下单接口并发性能很低。

### 用户和活动缓存优化

### 用户校验缓存优化

思路很简单，就是先从Redis里面获取用户信息，没有再去数据库里查，并存到Redis里面。`UserService`新开一个`getUserByIdInCache`的方法。

```java
public UserModel getUserByIdInCache(Integer id) {
    UserModel userModel= (UserModel) redisTemplate.opsForValue().get("user_validate_"+id);
    if(userModel==null){
        userModel=this.getUserById(id);
        redisTemplate.opsForValue().set("user_validate_"+id,userModel);
        redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
    }
    return userModel;
}
```

### 活动校验缓存优化

跟用户校验类似，`ItemService`新开一个`getItemByIdInCache`方法。

```java
public ItemModel getItemByIdInCache(Integer id) {
    ItemModel itemModel=(ItemModel)redisTemplate.opsForValue().get("item_validate_"+id);
    if(itemModel==null){
        itemModel=this.getItemById(id);
        redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
         redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
    }
    return itemModel;
}
```

## 库存扣减优化

### 索引优化

对于之前下单减库存的操作，我们是直接执行`update stock set stock = stock -#{amount} where item_id = #{itemId} and stock >= #{amount}`这条`SQL`语句的，但是此**where条件item_id是没有索引**的，那么就会直接**锁表**，降低了性能，故我们对其添加唯一索引`alter table item_stock add unque index item_stock_index(item_id);`此时为**锁行**。

### 库存缓存优化

在进行秒杀活动时，在短时间内有大量的请求打在MySQL上，因此我们使用Redis缓存技术减少同一时间MySQL服务器的压力，在发布活动时，我们将MySQL中的库存同步到Redis中，但是下单后，我们要将缓存的库存信息同步到数据库中，这就需要**异步消息队列**——也就是**RocketMQ**。

### RocketMQ配置

> windows版本

去官网下载安装包 https://www.apache.org/dyn/closer.cgi?path=rocketmq/4.7.1/rocketmq-all-4.7.1-bin-release.zip

解压后配置环境变量即可

如果启动nameserver出现问题的话，可以看我的这篇博客https://blog.csdn.net/foolishpichao/article/details/107539872

### RocketMQ使用

RocketMQ是一个异步消息队列。

主要可以用来**解耦**、**异步**、**削峰**

本项目中主要应用到的是异步和削峰，本项目是一个秒杀项目，考虑到在活动开启时，大量用户购买商品对服务器造成巨大的压力，我们使用RocketMQ进行**异步**处理，和**削峰**操作。

**异步**：在Redis上库存更改后，我们不直接更改MySQL而是以异步消息队列的方式进行缓存，后续也能应用到增加商品销量上，对于一些时序性要求不高的，我们进行一个功能的拆分。

**削峰**：以异步消息队列的方式进行缓存，当秒杀活动结束，用户请求大幅度降低，我们的服务器压力减少的时候，再按照数据库承受量对消息进行消费。

#### 同步库存

>  当我们发布活动时，将其库存同步至缓存

```java
public void publishPromo(Integer promoId) {
    //通过活动id获取活动
    PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
    if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0){
        return;
    }
    ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
    //库存同步到Redis
    redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());
}
```

> 每次下单都从缓存中扣取库存，暂时没有同步入数据库

```java
// 在生成订单前，我们先落单锁库存调用itemService.decreaseStock(itemId, amount);
public boolean decreaseStock(Integer itemId, Integer amount) {
  affectedRow=itemStockDOMapper.decreaseStock(itemId,amount);
    long affectedRow=redisTemplate.opsForValue().
     increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    return (affectedRow >= 0);
}
```

> 此时没有跟数据库同步，我们引用RocketMQ

新建一个`MQProducer` 生产者类，通过`@PostContruct`初始化生产者。

**`@PostConstruct`该注解被用来修饰一个非静态的`void（）`方法。被`@PostConstruct`修饰的方法会在服务器加载`Servlet`的时候运行，并且只会被服务器执行一次。`PostConstruct`在构造函数之后执行，`init（）`方法之前执行。**

```java
public class MqProducer {
    private DefaultMQProducer producer;
    //即是IP:9867
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    //即是stock
    @Value("${mq.topicname}")
    private String topicName;
   
    @PostConstruct
    public void init() throws MQClientException {
        //Producer初始化，Group对于生产者没有意义，但是消费者有意义
        producer=new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();
    }
}
```

通过`asyncReduceStock`方法实现异步扣减库存。

```java
public boolean asyncReduceStock(Integer itemId, Integer amount) {
    Map<String, Object> bodyMap = new HashMap<>();
    bodyMap.put("itemId", itemId);
    bodyMap.put("amount", amount);
    Message message = new Message(topicName, "increase",
                                  JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
    try {
        producer.send(message);
    } catch (MQClientException e) {
        e.printStackTrace();
        return false;
    } catch (RemotingException e) {
        e.printStackTrace();
        return false;
    } catch (MQBrokerException e) {
        e.printStackTrace();
        return false;
    } catch (InterruptedException e) {
        e.printStackTrace();
        return false;
    }
    return true;
}
```

新建`MqConsumer`类，与`MqProducer`类类似，也有一个`init`方法，实现**异步扣减库存**的逻辑。

```java
public class MqConsumer {
    private DefaultMQPushConsumer consumer;
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    @PostConstruct
    public void init() throws MQClientException {
        consumer = new DefaultMQPushConsumer("stock_consumer_group");
        // 监听名为topicName的话题
        consumer.setNamesrvAddr(nameAddr);
        // 监听topicName话题下的所有(*)消息
        consumer.subscribe(topicName, "*");
        // 这个匿名类会监听消息队列中的消息
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                // 实现缓存数据真正到数据库扣减的逻辑
                // 实现缓存数据真正到数据库扣减的逻辑
                // 从消息队列中获取消息
                Message message = list.get(0);
                // 反序列化消息
                String jsonString = new String(message.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                // 去数据库扣减库存
                itemStockDOMapper.decreaseStock(itemId, amount);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
    }
}
```

`ItemService.decreaseStock`方法也要做更改：

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    long affectedRow=redisTemplate.opsForValue().
        increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    //>0，表示Redis扣减成功
    if(affectedRow>=0){
        //发送消息到消息队列，准备异步扣减
        boolean mqResult = mqProducer.asyncReduceStock(itemId,amount);
        if (!mqResult){
            //消息发送失败，需要回滚Redis
            redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
            return false;
        }
        return true;
    } else {
        //Redis扣减失败，回滚
        redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
        return false;
    }
}
```

### 存在的问题

1. 如果发送消息失败，只能回滚Redis。
2. 对于发出去的消息，直接成功，没有回滚
3. 下单过程中出现异常无法回滚数据库

**故引入事务型消息**

> 将上述decreaseStock 中Redis回补 和消息的发送 功能抽离开来，使订单成功之后再进行异步消息的发送。

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    long affectedRow=redisTemplate.opsForValue().
                increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    // >0，表示Redis扣减成功
    if(affectedRow>=0){
        // 抽离了发送异步消息的逻辑
        return true;
    } else {
        // Redis扣减失败，回滚
        increaseStock(itemId, amount)
        return false;
    }
}

public boolean increaseStock(Integer itemId, Integer amount) {
    redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
    return true;
}

// ItemService
public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
    return mqProducer.asyncReduceStock(itemId, amount);
}


// 在下单service中，等执行完最后一步才发送异步消息
boolean mqResult=itemService.asyncDecreaseStock(itemId,amount);
    if(!mqResult){
        //回滚redis库存
        itemService.increaseStock(itemId,amount);
        throw new BizException(EmBizError.MQ_SEND_FAIL);
    }
```

**问题**：Spring的`@Transactional`标签，会在**事务方法返回后才提交**，如果提交的过程中，发生了异常，则数据库回滚，但是Redis库存已扣，还是无法保证一致性。我们需要在**事务提交成功后**，**再发送异步消息**。

**借助**Spring给我们提供了`TransactionSynchronizationManager.registerSynchronization`方法，这个方法的传入一个`TransactionSynchronizationAdapter`的匿名类，通过`afterCommit`方法，在**事务提交成功后**，执行**发送消息操作**。

---

**Spring**给我们提供了`TransactionSynchronizationManager.registerSynchronization`方法，这个方法的传入一个`TransactionSynchronizationAdapter`的匿名类，通过`afterCommit`方法，在**事务提交成功后**，执行**发送消息操作**。

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
    @Override
    public void afterCommit() {
    boolean mqResult=itemService.asyncDecreaseStock(itemId,amount);
    if(!mqResult){
        itemService.increaseStock(itemId,amount);
        throw new BizException(EmBizError.MQ_SEND_FAIL);
    }
}
```

### 事物型消息

当`afterCommit()`执行出错则会出现问题，又不能回滚，故引用事务型消息。

发送到消息队列的消息先处于`prepared`状态，当`broker`接受到时，是**不允许消费者消费**的。要先执行`TransactionListener`的`executeLocalTransaction`方法，根据执行结果，**改变事务型消息的状态**，根据这三种状态判断能否消费`COMMIT,ROLLBACK UNKNOWN`。

```java
// 事务型消息同步库存扣减消息
public boolean transactionAsyncReduceStock(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) {
    // 发送的消息
    Map<String, Object> bodyMap = new HashMap<>();
    bodyMap.put("itemId", itemId);
    bodyMap.put("amount", amount);
    bodyMap.put("stockLogId", stockLogId);
    // 作为参数检查消息是否能被消费，也就是执行orderService.createOrder的传参，根据orderService.createOrder返回结果判断
    Map<String, Object> argsMap = new HashMap<>();
    argsMap.put("itemId", itemId);
    argsMap.put("amount", amount);
    argsMap.put("userId", userId);
    argsMap.put("promoId", promoId);
    argsMap.put("stockLogId", stockLogId);

    Message message = new Message(topicName, "increase",
                                  JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
    TransactionSendResult sendResult = null;
    try {
        sendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);

    } catch (MQClientException e) {
        e.printStackTrace();
        return false;
    }
    if(sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE){
        return false;
    }else if(sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
        return true;
    }else{
        return false;
    }
}
```

broker收到`prepare`消息后，会执行`TransactionListener`的`executeLocalTransaction`方法，来判断能否被消费：

```java
transactionMQProducer.setTransactionListener(new TransactionListener() {
    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object args) {
    //在事务型消息中去进行下单
    Integer itemId = (Integer) ((Map) args).get("itemId");
    Integer promoId = (Integer) ((Map) args).get("promoId");
    Integer userId = (Integer) ((Map) args).get("userId");
    Integer amount = (Integer) ((Map) args).get("amount");
    try {
        //调用下单接口
        orderService.createOrder(userId, itemId, promoId, amount);
    } catch (BizException e) {
        e.printStackTrace();
        //发生异常就回滚消息
        return LocalTransactionState.ROLLBACK_MESSAGE;
    }
    return LocalTransactionState.COMMIT_MESSAGE;
}
```

**问题**：当执行`orderService.createOrder`后，突然**又宕机了**，根本没有返回，这个时候事务型消息就会进入`UNKNOWN`状态，我们需要处理这个状态。

在匿名类`TransactionListener`里面，还需要覆写`checkLocalTransaction`方法，这个方法就是用来处理`UNKNOWN`状态的。应该怎么处理？这就需要引入**库存流水**，通过流水可以查看和进行相应的操作。

## 库存流水

数据库新建一张`stock_log`的表，用来记录库存流水，添加一个`ItemService.initStockLog`方法。

```text
流水状态 1 表示Prepare 2 表示 成功 3 表示需要回滚
1. 在发送异步消息队列前，生成一个流水
2. 如果Redis扣减库存、订单入库、销量增加的操作完成则更新流水的状态  
3. 根据status状态判断
```

### 小结

事务型消息提交后，会在`broker`里面处于`prepare`状态，也即是`UNKNOWN`状态，等待被消费端消费，或者是回滚。`prepare`状态下，会执行`OrderService.createOrder`方法。

此时有两种情况：

1. `createOrder`执行完**没有宕机**，要么**执行成功**，要么**抛出异常**。**执行成功**，那么就说明下单成功了，订单入库了，Redis里的库存扣了，销量增加了，**等待着异步扣减库存**，所以将事务型消息的状态，从`UNKNOWN`变为`COMMIT`，这样消费端就会消费这条消息，异步扣减库存。抛出异常，那么订单入库、Redis库存、销量增加，就会被数据库回滚，此时去异步扣减的消息，就应该“丢弃”，所以发回`ROLLBACK`，进行回滚。
2. `createOrder`执行完**宕机**了，那么这条消息会是`UNKNOWN`状态，这个时候就需要在`checkLocalTransaction`进行处理。如果`createOrder`执行完毕，此时`stockLog.status==2`，就说明下单成功，需要去异步扣减库存，所以返回`COMMIT`。如果`status==1`，说明下单还未完成，还需要继续执行下单操作，所以返回`UNKNOWN`。如果`status==3`，说明下单失败，需要回滚，不需要异步扣减库存，所以返回`ROLLBACK`。

## 流量削峰

### 秒杀令牌

+ 秒杀下单接口容易被脚本不停的刷 ---  只要知道它自己的token 和 秒杀地址即可
+ 秒杀验证的逻辑和秒杀下单强关联，代码冗余高
+ 秒杀验证逻辑复杂

秒杀令牌使用：

+ 秒杀接口需要秒杀令牌才能进入
+ 秒杀令牌由秒杀活动模块生成
+ 秒杀活动模块对秒杀令牌生成全权处理，逻辑收口
+ 秒杀下单前需要先获得秒杀令牌

> 秒杀代码实现
```java
// 生产秒杀令牌
String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);

// 在poromoServiceImpl下
1. 先校验活动商品
2. 商品是否在活动状态下，在秒杀则能生成，否则不生成
3. 对用户和商品信息的校验
4. 根据秒杀活动id，商品id，用户id，生成秒杀令牌token
    
// 在OrderController中定义/generatetoken 路径下生成秒杀令牌，调用在poromoServiceImpl下的generateSecondKillToken()

// 之后在下单界面可以省去用户商品和活动的校验，直接获取秒杀令牌如果成功即可
```

**问题**：如果下单人数过多，令牌发送过多。

**解决 --- 秒杀大闸**   ： 通过库存的多少来限制令牌的数量。(令牌数量为库存五倍)

### 秒杀大闸

```text
1. 在发布活动时，将库存数量 * 5 存入Redis 代表其令牌可发放的数量
2. 在发布令牌之前，进行一个大闸的校验
```

**问题**：

+ 浪涌流量涌入后系统无法应对
+ 多库存多商品等令牌限制能力弱

### 队列泄洪

+ 排队有时候比并发更加高效
  + 并发时遇到行锁或者互斥锁的竞争，如果一个线程在运行中遇到锁，那么我就要退出执行，由cpu调度另一个线程，然后又遇到锁，又要进行上下文的切换，还要mysql innodb mutex key ，**在 update where 后面会加行锁，那么也有cpu上下文切换消耗**， 阿里巴巴对其进行了一个优化，就是利用**队列**（支付宝和银行网关）那么，**如果我知道会遇到锁，我不如将其排队，等它执行完，下一个再进行执行**  --- 典型代表：**Redis**，它的set和get都是单线程的，没有cpu切换上下文的开销，然后是基于内存的，故快。
+ 依靠排队限制并发流量
+ 排队和下游拥塞窗口成都调整队列释放
+ 支付宝银行网关队列

> 对创建订单的操作，调用线程池做一个队列限洪（本地队列）

```java
@ResponseBody
@RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
public CommentResponseType createOrder() {
    // 1. 分布式校验session 对用户进行风控
    // 2. 拿到对应秒杀令牌，对令牌进行校验

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
```

### 防刷限流

分为**集群限流**和**单机限流**，集群限流顾名思义就是限制整个集群的流量，需要用Redis或者其它中间件技术来做统一计数器，往往会产生性能瓶颈。单机限流在负载均衡的前提下效果更好。

## RateLimiter限流实现

`google.guava.RateLimiter`就是令牌桶算法的一个实现类，`OrderController`引入这个类，在`init`方法里面，初始令牌数量为200。

```
@PostConstruct
    public void init() {
    //20个线程的线程池
    executorService = Executors.newFixedThreadPool(20);
    //200个令牌，即200TPS
    orderCreateRateLimiter = RateLimiter.create(200);
}
```

请求`createOrder`接口之前，会调用`RateLimiter.tryAcquire`方法，看当前令牌是否足够，不够直接抛出异常。

```java
if (!orderCreateRateLimiter.tryAcquire())
     throw new BizException(EmBizError.RATELIMIT);
```