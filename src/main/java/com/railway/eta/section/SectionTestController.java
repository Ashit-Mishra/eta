package com.railway.eta.section;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/sections")
public class SectionTestController {

    private final SectionService sectionService;

    public SectionTestController(
            SectionService sectionService
    ) {
        this.sectionService = sectionService;
    }

    @PostMapping("/{trainNo}")
    public String createSections(
            @PathVariable String trainNo
    ) {

        sectionService.createSectionsForTrain(trainNo);

        return "Sections created for train " + trainNo;
    }
}