local roomKey = KEYS[1]
local deptKey = KEYS[2]
local callingKey = KEYS[3]
local callingTtlSeconds = ARGV[1]
local allowDeptFallback = ARGV[2] == 'true'

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

if allowDeptFallback then
    local deptItem = redis.call('ZRANGE', deptKey, 0, 0)
    if deptItem ~= nil and #deptItem > 0 then
        local ticketNo = deptItem[1]
        redis.call('ZREM', deptKey, ticketNo)
        redis.call('SET', callingKey, ticketNo, 'EX', callingTtlSeconds)
        return ticketNo
    end
end

return nil
