package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Party;
import com.stopforfuel.backend.repository.PartyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parties")
public class PartyController {

    @Autowired
    private PartyRepository partyRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<Party> getAllParties() {
        return partyRepository.findAll();
    }
}
