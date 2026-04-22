package com.stopforfuel.backend.dto.bank;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class BankStatementParseResponse {
    private List<BankStatementRow> rows = new ArrayList<>();
    private Map<Integer, List<BankMatchCandidate>> matchesByRow = new HashMap<>();
    private int unparsedLineCount;
    private List<String> warnings = new ArrayList<>();
}
