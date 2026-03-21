package com.hospital.triage.modules.triage.service.support;

import com.hospital.triage.modules.triage.entity.po.TriageRule;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TriageRuleMatchSupport {

    private static final Pattern KEYWORD_SPLITTER = Pattern.compile("[,，;；/、\\s]+");

    private TriageRuleMatchSupport() {
    }

    public static TriageRule bestMatch(List<TriageRule> rules, String... texts) {
        return matchRules(rules, texts).stream()
                .findFirst()
                .map(MatchedRule::getRule)
                .orElse(null);
    }

    public static List<MatchedRule> matchRules(List<TriageRule> rules, String... texts) {
        String normalizedInput = normalizeInput(texts);
        if (!StringUtils.hasText(normalizedInput) || rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .filter(rule -> rule != null && Integer.valueOf(1).equals(rule.getEnabled()))
                .map(rule -> toMatchedRule(rule, normalizedInput))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((MatchedRule matchedRule) -> normalizedLevel(matchedRule.getRule().getTriageLevel()))
                        .thenComparing(Comparator.comparingInt((MatchedRule matchedRule) -> matchedRule.getMatchedKeyword().length()).reversed())
                        .thenComparing(Comparator.comparingInt((MatchedRule matchedRule) -> normalizedWeight(matchedRule.getRule().getSpecialWeight())).reversed())
                        .thenComparingLong(matchedRule -> matchedRule.getRule().getId() == null ? Long.MAX_VALUE : matchedRule.getRule().getId()))
                .toList();
    }

    public static String normalizeInput(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }
        return Arrays.stream(texts)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(text -> text.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static MatchedRule toMatchedRule(TriageRule rule, String normalizedInput) {
        if (!StringUtils.hasText(rule.getSymptomKeyword())) {
            return null;
        }
        return KEYWORD_SPLITTER.splitAsStream(rule.getSymptomKeyword())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .filter(normalizedInput::contains)
                .max(Comparator.comparingInt(String::length))
                .map(keyword -> new MatchedRule(rule, keyword))
                .orElse(null);
    }

    private static int normalizedLevel(Integer triageLevel) {
        if (triageLevel == null) {
            return 4;
        }
        return Math.max(1, Math.min(triageLevel, 4));
    }

    private static int normalizedWeight(Integer specialWeight) {
        return specialWeight == null ? 0 : specialWeight;
    }

    public static final class MatchedRule {

        private final TriageRule rule;
        private final String matchedKeyword;

        public MatchedRule(TriageRule rule, String matchedKeyword) {
            this.rule = rule;
            this.matchedKeyword = matchedKeyword;
        }

        public TriageRule getRule() {
            return rule;
        }

        public String getMatchedKeyword() {
            return matchedKeyword;
        }
    }
}
