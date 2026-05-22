package org.poc.claudecodepoc.service;

import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.poc.claudecodepoc.dto.response.SlotResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface SlotService {

    SlotResponse createSlot(SlotRequest request, String userId);

    SlotResponse getSlotById(Long id);

    List<SlotResponse> getSlotsByDoctor(String doctorId, LocalDate date);

    SlotResponse updateSlot(Long id, SlotRequest request, String userId);

    void deleteSlot(Long id, String userId);

    Page<SlotResponse> filterPaged(FilterRequest request);
}