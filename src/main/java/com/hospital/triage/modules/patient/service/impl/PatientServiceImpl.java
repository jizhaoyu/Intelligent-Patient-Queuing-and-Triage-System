package com.hospital.triage.modules.patient.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.patient.entity.dto.PatientCreateDTO;
import com.hospital.triage.modules.patient.entity.dto.PatientUpdateDTO;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.entity.vo.PatientVO;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.patient.service.PatientService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PatientServiceImpl implements PatientService {

    private final PatientInfoMapper patientInfoMapper;

    public PatientServiceImpl(PatientInfoMapper patientInfoMapper) {
        this.patientInfoMapper = patientInfoMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO create(PatientCreateDTO dto) {
        PatientInfo patientInfo = new PatientInfo();
        BeanUtils.copyProperties(dto, patientInfo);
        patientInfo.setPatientNo("P" + RandomUtil.randomNumbers(10));
        patientInfoMapper.insert(patientInfo);
        return toVO(patientInfo);
    }

    @Override
    public PatientVO getById(Long id) {
        PatientInfo patientInfo = patientInfoMapper.selectById(id);
        if (patientInfo == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "患者档案不存在");
        }
        return toVO(patientInfo);
    }

    @Override
    public List<PatientVO> list(String keyword) {
        return patientInfoMapper.selectList(new LambdaQueryWrapper<PatientInfo>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(PatientInfo::getPatientName, keyword)
                                .or().like(PatientInfo::getPatientNo, keyword)
                                .or().like(PatientInfo::getPhone, keyword))
                        .orderByDesc(PatientInfo::getCreatedTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO update(Long id, PatientUpdateDTO dto) {
        PatientInfo patientInfo = patientInfoMapper.selectById(id);
        if (patientInfo == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "患者档案不存在");
        }
        BeanUtils.copyProperties(dto, patientInfo);
        patientInfoMapper.updateById(patientInfo);
        return toVO(patientInfo);
    }

    private PatientVO toVO(PatientInfo patientInfo) {
        PatientVO patientVO = new PatientVO();
        BeanUtils.copyProperties(patientInfo, patientVO);
        return patientVO;
    }
}
