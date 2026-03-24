local roomKey = KEYS[1]
local overflowKey = KEYS[2]
local callingKey = KEYS[3]
local callingTtlSeconds = ARGV[1]
local allowOverflowFallback = ARGV[2] == 'true'

if redis.call('EXISTS', callingKey) == 1 then
    return nil
end

local roomItem = redis.call('ZRANGE', roomKey, 0, 0)
if roomItem ~= nil and #roomItem > 0 then
    local ticketNo = roomItem[1]
    redis.call('ZREM', roomKey, ticketNo)
    redis.call('SET', callingKey, ticketNo, 'EX', callingTtlSeconds)
    return ticketNo
end

if allowOverflowFallback then
    local overflowItem = redis.call('ZRANGE', overflowKey, 0, 0)
    if overflowItem ~= nil and #overflowItem > 0 then
        local ticketNo = overflowItem[1]
        redis.call('ZREM', overflowKey, ticketNo)
        redis.call('SET', callingKey, ticketNo, 'EX', callingTtlSeconds)
        return 'overflow:' .. ticketNo
    end
end

return nil
