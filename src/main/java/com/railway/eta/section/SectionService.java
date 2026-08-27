package com.railway.eta.section;

import com.railway.eta.section.dto.SectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;

    @Transactional(readOnly = true)
    public List<SectionResponse> getAllSections() {

        return sectionRepository.findAll()
                .stream()
                .map(section -> new SectionResponse(
                        section.getId(),
                        section.getSectionCode(),
                        section.getFromStation().getCode(),
                        section.getToStation().getCode(),
                        section.getDistanceKm()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse getSection(String sectionCode) {

        Section section = sectionRepository
                .findBySectionCode(sectionCode)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Section not found: " + sectionCode
                        ));

        return new SectionResponse(
                section.getId(),
                section.getSectionCode(),
                section.getFromStation().getCode(),
                section.getToStation().getCode(),
                section.getDistanceKm()
        );
    }
}