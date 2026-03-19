package com.hospital.triage.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.system.entity.po.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
