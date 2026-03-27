package com.stopforfuel.backend.config;

import com.stopforfuel.backend.entity.Party;
import com.stopforfuel.backend.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartyDataSeeder implements ApplicationRunner {

    private final PartyRepository partyRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (partyRepository.count() == 0) {
            Party local = new Party();
            local.setPartyType("Local");
            partyRepository.save(local);

            Party statement = new Party();
            statement.setPartyType("Statement");
            partyRepository.save(statement);

            log.info("Seeded default party types: Local, Statement");
        }
    }
}
