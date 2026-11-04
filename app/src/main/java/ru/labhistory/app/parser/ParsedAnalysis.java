package ru.labhistory.app.parser;

import java.util.Collections;
import java.util.List;

public class ParsedAnalysis {
    public final String labName;
    public final List<ParsedResult> results;
    public final List<String> warnings;

    public ParsedAnalysis(String labName, List<ParsedResult> results, List<String> warnings) {
        this.labName = labName;
        this.results = Collections.unmodifiableList(results);
        this.warnings = Collections.unmodifiableList(warnings);
    }
}
