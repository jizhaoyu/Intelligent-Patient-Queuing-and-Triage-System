package com.hospital.triage.modules.visit.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.visit.entity.dto.VisitCreateDTO;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.entity.vo.VisitVO;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VisitServiceImpl implements VisitService {

    private final VisitRecordMapper visitRecordMapper;
    private final PatientInfoMapper patientInfoMapper;

    public VisitServiceImpl(VisitRecordMapper visitRecordMapper, PatientInfoMapper patientInfoMapper) {
        this.visitRecordMapper = visitRecordMapper;
        this.patientInfoMapper = patientInfoMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitVO create(VisitCreateDTO dto) {
        if (patientInfoMapper.selectById(dto.getPatientId()) == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "患者不存在");
        }
        VisitRecord record = new VisitRecord();
        record.setPatientId(dto.getPatientId());
        record.setChiefComplaint(dto.getChiefComplaint());
        record.setVisitNo("V" + RandomUtil.randomNumbers(12));
        record.setRegisterTime(LocalDateTime.now());
        record.setStatus(VisitStatusEnum.REGISTERED.name());
        visitRecordMapper.insert(record);
        return toVO(record);
    }

    @Override
    public VisitVO getById(Long id) {
        VisitRecord record = visitRecordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "到诊记录不存在");
        }
        return toVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitVO arrive(Long id) {
        VisitRecord record = visitRecordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "到诊记录不存在");
        }
        record.setStatus(VisitStatusEnum.ARRIVED.name());
        record.setArrivalTime(LocalDateTime.now());
        visitRecordMapper.updateById(record);
        return toVO(record);
    }

    private VisitVO toVO(VisitRecord record) {
        VisitVO visitVO = new VisitVO();
        BeanUtils.copyProperties(record, visitVO);
        return visitVO;
    }
}
