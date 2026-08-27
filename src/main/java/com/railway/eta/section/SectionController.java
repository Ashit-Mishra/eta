package com.railway.eta.section;

import com.railway.eta.section.dto.SectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;

    @GetMapping
    public List<SectionResponse> getAllSections() {
        return sectionService.getAllSections();
    }

    @GetMapping("/{sectionCode}")
    public SectionResponse getSection(
            @PathVariable String sectionCode) {

        return sectionService.getSection(sectionCode);
    }
}