package com.hospital.triage.modules.visit.service;

import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class VisitStatusSnapshotSyncService {

    private final PatientInfoMapper patientInfoMapper;

    public VisitStatusSnapshotSyncService(PatientInfoMapper patientInfoMapper) {
        this.patientInfoMapper = patientInfoMapper;
    }

    public void syncFromVisit(VisitRecord visitRecord) {
        syncFromVisit(visitRecord, LocalDateTime.now());
    }

    public void syncFromVisit(VisitRecord visitRecord, LocalDateTime statusUpdatedTime) {
        if (visitRecord == null || visitRecord.getPatientId() == null) {
            return;
        }
        PatientInfo patientInfo = patientInfoMapper.selectById(visitRecord.getPatientId());
        if (patientInfo == null) {
            return;
        }
        patientInfo.setCurrentStatus(visitRecord.getStatus());
        patientInfo.setCurrentVisitId(visitRecord.getId());
        patientInfo.setCurrentVisitNo(visitRecord.getVisitNo());
        patientInfo.setCurrentDeptId(visitRecord.getCurrentDeptId());
        patientInfo.setCurrentRoomId(visitRecord.getCurrentRoomId());
        patientInfo.setStatusUpdatedTime(statusUpdatedTime);
        patientInfoMapper.updateById(patientInfo);
    }
}
