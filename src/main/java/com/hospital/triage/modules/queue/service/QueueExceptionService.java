package com.hospital.triage.modules.queue.service;

import com.hospital.triage.modules.queue.entity.vo.QueueExceptionVO;

import java.util.List;

public interface QueueExceptionService {

    List<QueueExceptionVO> listUnqueuedTriaged(Long deptId);

    long countUnqueuedTriaged(Long deptId);
}
