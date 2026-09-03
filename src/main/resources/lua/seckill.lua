-- 秒杀 Lua 脚本：原子完成 库存校验 + 一人一单校验 + 库存扣减 + 用户记录
-- KEYS[1]: 库存 key  (seckill:stock:{voucherId})
-- KEYS[2]: 已购用户 key (seckill:user:{voucherId})
-- ARGV[1]: 用户 id
-- 返回: 1=扣减成功, 0=库存不足, -1=重复下单
if (redis.call('sismember', KEYS[2], ARGV[1]) == 1) then
    return -1
end
local stock = tonumber(redis.call('get', KEYS[1]))
if (stock == nil or stock <= 0) then
    return 0
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 1
