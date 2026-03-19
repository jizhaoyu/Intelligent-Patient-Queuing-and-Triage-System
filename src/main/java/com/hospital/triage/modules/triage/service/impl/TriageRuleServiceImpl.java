package com.hospital.triage.modules.triage.service.impl;

import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.triage.entity.dto.TriageRuleUpdateDTO;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.entity.vo.TriageRuleVO;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.TriageRuleService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TriageRuleServiceImpl implements TriageRuleService {

    private final TriageRuleMapper triageRuleMapper;

    public TriageRuleServiceImpl(TriageRuleMapper triageRuleMapper) {
        this.triageRuleMapper = triageRuleMapper;
    }

    @Override
    public List<TriageRuleVO> list() {
        return triageRuleMapper.selectList(null).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageRuleVO update(Long id, TriageRuleUpdateDTO dto) {
        TriageRule triageRule = triageRuleMapper.selectById(id);
        if (triageRule == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊规则不存在");
        }
        BeanUtils.copyProperties(dto, triageRule);
        triageRuleMapper.updateById(triageRule);
        return toVO(triageRule);
    }

    private TriageRuleVO toVO(TriageRule triageRule) {
        TriageRuleVO vo = new TriageRuleVO();
        BeanUtils.copyProperties(triageRule, vo);
        return vo;
    }
}
