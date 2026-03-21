package com.hospital.triage.modules.clinic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import com.hospital.triage.modules.clinic.entity.vo.ClinicDeptOptionVO;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.clinic.mapper.ClinicRoomMapper;
import com.hospital.triage.modules.clinic.service.ClinicDeptService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClinicDeptServiceImpl implements ClinicDeptService {

    private final ClinicDeptMapper clinicDeptMapper;
    private final ClinicRoomMapper clinicRoomMapper;

    public ClinicDeptServiceImpl(ClinicDeptMapper clinicDeptMapper,
                                 ClinicRoomMapper clinicRoomMapper) {
        this.clinicDeptMapper = clinicDeptMapper;
        this.clinicRoomMapper = clinicRoomMapper;
    }

    @Override
    public List<ClinicDeptOptionVO> listOptions() {
        return clinicDeptMapper.selectList(new LambdaQueryWrapper<ClinicDept>()
                        .eq(ClinicDept::getEnabled, 1)
                        .orderByAsc(ClinicDept::getDeptCode, ClinicDept::getId))
                .stream()
                .map(this::toOption)
                .toList();
    }

    @Override
    public Long getDeptIdByRoomId(Long roomId) {
        if (roomId == null) {
            return null;
        }
        ClinicRoom room = clinicRoomMapper.selectOne(new LambdaQueryWrapper<ClinicRoom>()
                .eq(ClinicRoom::getId, roomId)
                .eq(ClinicRoom::getEnabled, 1)
                .last("limit 1"));
        return room == null ? null : room.getDeptId();
    }

    private ClinicDeptOptionVO toOption(ClinicDept dept) {
        ClinicDeptOptionVO option = new ClinicDeptOptionVO();
        BeanUtils.copyProperties(dept, option);
        return option;
    }
}
