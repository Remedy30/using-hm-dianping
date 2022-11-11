package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
       // 缓存穿透
       // Shop shop =  queryWithPassThrough(id);

       //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }


    //设置空对象，解决缓存击穿
    public Shop queryWithMutex(Long id){
        //从redis查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);

        }

        //命中是否是空值
        if(shopJson != null){
            //返回错误信息
            return null;
        }

        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {

            boolean isLock = tryLock(lockKey);
            //判断是否成功
            if(!isLock){
                //失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //成功，根据id查询数据库

            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //db不存在id，返回错误
            if(shop == null){

                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

                //返回错误信息
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }

        //返回
        return shop;
    }

//    @Override
//    public Result queryWithPassThrough(Long id) {
//        // 解决缓存穿透
//        String key = "cache : shop:" + id;// 1.从redis查询商铺缓存
//        //1.从redis查询商铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StringUtils.isNotBlank(shopJson)){
//            //3.存在直接返回
//            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
//            return Result.ok(shop);
//        }
//
//        //4.不存在查询数据库
//        Shop shop = this.getById(id);
//
//        //5.不存在报错
//        if(shop==null){
//            return Result.fail("查找不到店铺");
//        }
//
//        //6.缓存写入redis，设置超时时间
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 7.返回
//        return Result.ok(shop);
//
//    }


    private boolean tryLock(String key){

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){

        stringRedisTemplate.delete(key);

    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空！");
        }
        //更新db
        updateById(shop);
        //删除缓存

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
