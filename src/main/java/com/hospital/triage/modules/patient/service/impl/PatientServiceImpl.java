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
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PatientServiceImpl implements PatientService {

    private final PatientInfoMapper patientInfoMapper;
    private final VisitRecordMapper visitRecordMapper;

    public PatientServiceImpl(PatientInfoMapper patientInfoMapper,
                              VisitRecordMapper visitRecordMapper) {
        this.patientInfoMapper = patientInfoMapper;
        this.visitRecordMapper = visitRecordMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO create(PatientCreateDTO dto) {
        PatientInfo patientInfo = new PatientInfo();
        BeanUtils.copyProperties(dto, patientInfo);
        patientInfo.setPatientNo("P" + RandomUtil.randomNumbers(10));
        patientInfoMapper.insert(patientInfo);
        return toVO(patientInfo, null);
    }

    @Override
    public PatientVO getById(Long id) {
        PatientInfo patientInfo = patientInfoMapper.selectById(id);
        if (patientInfo == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "患者档案不存在");
        }
        Map<Long, VisitRecord> latestVisits = loadLatestVisits(List.of(patientInfo.getId()));
        return toVO(patientInfo, latestVisits.get(patientInfo.getId()));
    }

    @Override
    public List<PatientVO> list(String keyword) {
        List<PatientInfo> patients = patientInfoMapper.selectList(new LambdaQueryWrapper<PatientInfo>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(PatientInfo::getPatientName, keyword)
                                .or().like(PatientInfo::getPatientNo, keyword)
                                .or().like(PatientInfo::getPhone, keyword))
                        .orderByDesc(PatientInfo::getCreatedTime));
        Map<Long, VisitRecord> latestVisits = loadLatestVisits(patients.stream().map(PatientInfo::getId).toList());
        return patients.stream()
                .map(patient -> toVO(patient, latestVisits.get(patient.getId())))
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
        Map<Long, VisitRecord> latestVisits = loadLatestVisits(List.of(patientInfo.getId()));
        return toVO(patientInfo, latestVisits.get(patientInfo.getId()));
    }

    private PatientVO toVO(PatientInfo patientInfo, VisitRecord latestVisit) {
        PatientVO patientVO = new PatientVO();
        BeanUtils.copyProperties(patientInfo, patientVO);
        if (patientInfo.getCurrentStatus() == null && latestVisit != null) {
            patientVO.setCurrentVisitId(latestVisit.getId());
            patientVO.setCurrentVisitNo(latestVisit.getVisitNo());
            patientVO.setCurrentStatus(latestVisit.getStatus());
            patientVO.setCurrentDeptId(latestVisit.getCurrentDeptId());
            patientVO.setCurrentRoomId(latestVisit.getCurrentRoomId());
        }
        return patientVO;
    }

    private Map<Long, VisitRecord> loadLatestVisits(Collection<Long> patientIds) {
        List<Long> normalizedIds = patientIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return visitRecordMapper.selectList(new LambdaQueryWrapper<VisitRecord>()
                        .in(VisitRecord::getPatientId, normalizedIds)
                        .orderByDesc(VisitRecord::getUpdatedTime, VisitRecord::getId))
                .stream()
                .collect(Collectors.toMap(VisitRecord::getPatientId, Function.identity(), (current, ignored) -> current));
    }
}
