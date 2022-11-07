package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getByIconList() {

        //在redis中查询
        String key = CACHE_SHOP_TYPE_KEY;
        List<String> shopTypeList = new ArrayList<>();
        // range()中的 -1 表示最后一位
        // shopTypeList中存放的数据是[{...},{...},{...}...] 一个列表中有一个个json对象
        shopTypeList = stringRedisTemplate.opsForList().range(key,0,-1);

        //存在，直接返回
        if(!shopTypeList.isEmpty()){
            List<ShopType> typeList = new ArrayList<>();
            for (String str:
                 shopTypeList) {
                ShopType shopType = JSONUtil.toBean(str, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //不存在，从db获取

            //根据ShopType对象的sort属性排序后存入typeList
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //不存在，返回错误
        if(typeList.isEmpty()){
            return Result.fail("不存在分类！");
        }

        for(ShopType shopType : typeList){
            String s = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(s);
        }

        //存在，写入redis
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);

        return Result.ok(typeList);
    }
}
