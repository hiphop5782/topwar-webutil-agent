package com.hacademy.twagent.analyze.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ServerJsonSummaryService {

    private static final int MAX_MEMBER_PER_ALLIANCE = 170;

    @Autowired
    private ObjectMapper objectMapper;

    public String summarizeForAi(String rawString) {
        try {
            JsonNode root = extractDeepJsonPayload(rawString);

            ObjectNode aiInput = buildAiInput(root);

            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(aiInput);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 분석용 서버 요약 생성 실패", e);
        }
    }

    private ObjectNode buildAiInput(JsonNode root) {
        JsonNode summary = root.path("summary");
        JsonNode players = root.path("players");
        JsonNode alliances = root.path("alliances");

        int serverId = root.path("serverId").asInt(0);
        String exportedAt = root.path("exportedAt").asText("");

        int totalMapPlayers = intValue(
                summary.path("serverActivitySummary").path("totalMapPlayers"),
                summary.path("mapPlayers").asInt(arraySize(players))
        );

        int activeUsers = intValue(
                summary.path("serverActivitySummary").path("activeUsers"),
                summary.path("userStatus").path("activeUsers").asInt(countUsersByStatus(players, "ACTIVE")
                        + countUsersByStatus(players, "RECENT")
                        + countUsersByStatus(players, "SLEEPY"))
        );

        int coreUsers = intValue(
                summary.path("serverActivitySummary").path("coreUsers"),
                countUsersByActivityGrade(players, "CORE")
        );

        int recentUsers = intValue(
                summary.path("userStatus").path("recentUsers"),
                countUsersByStatus(players, "RECENT")
        );

        int sleepyUsers = intValue(
                summary.path("userStatus").path("sleepyUsers"),
                countUsersByStatus(players, "SLEEPY")
        );

        int inactiveUsers = intValue(
                summary.path("userStatus").path("inactiveUsers"),
                countUsersByStatus(players, "INACTIVE")
        );

        int quitLikelyUsers = intValue(
                summary.path("userStatus").path("quitLikelyUsers"),
                countUsersByStatus(players, "QUIT_LIKELY")
        );

        int unknownStatusUsers = intValue(
                summary.path("userStatus").path("unknownStatusUsers"),
                countUsersByStatus(players, "UNKNOWN")
        );

        int activeAllianceCountForWar = intValue(
                summary.path("allianceActivitySummary").path("activeAllianceCountForWar"),
                summary.path("activeAllianceCountForWar").asInt(0)
        );

        int meaningfulAllianceCount = intValue(
                summary.path("allianceActivitySummary").path("meaningfulAllianceCount"),
                summary.path("meaningfulAllianceCount").asInt(0)
        );

        int coreAllianceCount = summary.path("allianceActivitySummary").path("coreAllianceCount").asInt(0);
        int activeAllianceCount = summary.path("allianceActivitySummary").path("activeAllianceCount").asInt(0);
        int watchAllianceCount = summary.path("allianceActivitySummary").path("watchAllianceCount").asInt(0);
        int lowAllianceCount = summary.path("allianceActivitySummary").path("lowAllianceCount").asInt(0);
        int insufficientAllianceCount = summary.path("allianceActivitySummary").path("insufficientAllianceCount").asInt(0);

        double activeUserRate = rate(activeUsers, totalMapPlayers);
        double quitLikelyRate = rate(quitLikelyUsers, totalMapPlayers);

        long serverPowerTotal = longValue(
                summary.path("serverActivitySummary").path("serverPowerTotal"),
                sumPlayerPower(players)
        );

        long activePowerTotal = longValue(
                summary.path("serverActivitySummary").path("activePowerTotal"),
                sumActivePlayerPower(players)
        );

        double activePowerRate = doubleValue(
                summary.path("serverActivitySummary").path("activePowerRate"),
                rate(activePowerTotal, serverPowerTotal)
        );

        double topAlliancePowerRate = doubleValue(
                summary.path("serverActivitySummary").path("topAlliancePowerRate"),
                calculateTopAlliancePowerRate(alliances, serverPowerTotal, 1)
        );

        double top3AlliancePowerRate = doubleValue(
                summary.path("serverActivitySummary").path("top3AlliancePowerRate"),
                calculateTopAlliancePowerRate(alliances, serverPowerTotal, 3)
        );

        double activeEquivalentAllianceCount = round2(activeUsers / (double) MAX_MEMBER_PER_ALLIANCE);
        double coreEquivalentAllianceCount = round2(coreUsers / (double) MAX_MEMBER_PER_ALLIANCE);
        double filledAllianceEquivalentCount = round2(totalMapPlayers / (double) MAX_MEMBER_PER_ALLIANCE);

        int dataQualityScore = calculateDataQualityScore(summary, players, alliances);
        int operationalScaleScore = calculateOperationalScaleScore(
                activeEquivalentAllianceCount,
                activeAllianceCountForWar,
                activeUserRate,
                meaningfulAllianceCount
        );
        int elitePowerScore = calculateElitePowerScore(
                coreUsers,
                coreEquivalentAllianceCount,
                activePowerRate,
                top3AlliancePowerRate
        );
        int activityScore = calculateActivityScore(
                activeEquivalentAllianceCount,
                activeAllianceCountForWar,
                activeUserRate,
                meaningfulAllianceCount
        );
        int warPotentialScore = calculateWarPotentialScore(
                activeAllianceCountForWar,
                activePowerRate,
                coreEquivalentAllianceCount,
                topAlliancePowerRate,
                top3AlliancePowerRate
        );
        int riskScore = calculateRiskScore(
                activeEquivalentAllianceCount,
                quitLikelyRate,
                activeAllianceCountForWar,
                insufficientAllianceCount,
                inactiveUsers + quitLikelyUsers
        );

        String serverType = resolveServerType(
                activeEquivalentAllianceCount,
                activeAllianceCountForWar,
                elitePowerScore,
                activePowerRate,
                coreUsers,
                activeUsers,
                topAlliancePowerRate
        );

        int serverStrengthScore = calculateServerStrengthScore(
                operationalScaleScore,
                elitePowerScore,
                activityScore,
                dataQualityScore,
                riskScore,
                serverType
        );

        String overallGrade = resolveOverallGrade(serverStrengthScore, dataQualityScore);
        String confidence = resolveConfidence(dataQualityScore);
        String powerConcentrationGrade = resolvePowerConcentrationGrade(
                topAlliancePowerRate,
                top3AlliancePowerRate,
                activeAllianceCountForWar,
                activeUsers
        );

        ObjectNode result = objectMapper.createObjectNode();

        result.put("serverId", serverId);
        result.put("exportedAt", exportedAt);

        ObjectNode scores = result.putObject("scores");
        scores.put("serverStrengthScore", serverStrengthScore);
        scores.put("operationalScaleScore", operationalScaleScore);
        scores.put("elitePowerScore", elitePowerScore);
        scores.put("activityScore", activityScore);
        scores.put("warPotentialScore", warPotentialScore);
        scores.put("riskScore", riskScore);
        scores.put("dataQualityScore", dataQualityScore);

        result.put("serverTypeHint", serverType);
        result.put("overallGradeHint", overallGrade);
        result.put("confidenceHint", confidence);

        ObjectNode operationalAlliance = result.putObject("operationalAlliance");
        operationalAlliance.put("maxMemberPerAlliance", MAX_MEMBER_PER_ALLIANCE);
        operationalAlliance.put("activeEquivalentAllianceCount", activeEquivalentAllianceCount);
        operationalAlliance.put("coreEquivalentAllianceCount", coreEquivalentAllianceCount);
        operationalAlliance.put("filledAllianceEquivalentCount", filledAllianceEquivalentCount);
        operationalAlliance.put("activeAllianceCountForWar", activeAllianceCountForWar);
        operationalAlliance.put("meaningfulAllianceCount", meaningfulAllianceCount);

        ObjectNode activity = result.putObject("activity");
        activity.put("totalMapPlayers", totalMapPlayers);
        activity.put("activeUsers", activeUsers);
        activity.put("coreUsers", coreUsers);
        activity.put("recentUsers", recentUsers);
        activity.put("sleepyUsers", sleepyUsers);
        activity.put("inactiveUsers", inactiveUsers);
        activity.put("quitLikelyUsers", quitLikelyUsers);
        activity.put("unknownStatusUsers", unknownStatusUsers);
        activity.put("activeUserRate", activeUserRate);
        activity.put("quitLikelyRate", quitLikelyRate);

        ObjectNode power = result.putObject("power");
        power.put("serverPowerTotal", serverPowerTotal);
        power.put("activePowerTotal", activePowerTotal);
        power.put("activePowerRate", activePowerRate);
        power.put("topAlliancePowerRate", topAlliancePowerRate);
        power.put("top3AlliancePowerRate", top3AlliancePowerRate);
        power.put("powerConcentrationGrade", powerConcentrationGrade);

        ObjectNode allianceSummary = result.putObject("alliances");
        allianceSummary.put("totalAllianceCount", arraySize(alliances));
        allianceSummary.put("coreAllianceCount", coreAllianceCount);
        allianceSummary.put("activeAllianceCount", activeAllianceCount);
        allianceSummary.put("watchAllianceCount", watchAllianceCount);
        allianceSummary.put("lowAllianceCount", lowAllianceCount);
        allianceSummary.put("insufficientAllianceCount", insufficientAllianceCount);

        result.set("topAlliances", buildTopAlliancesForAi(alliances, 5));
        result.set("warnings", buildWarnings(
                dataQualityScore,
                activeEquivalentAllianceCount,
                activeAllianceCountForWar,
                topAlliancePowerRate,
                top3AlliancePowerRate,
                insufficientAllianceCount,
                unknownStatusUsers,
                totalMapPlayers
        ));

        return result;
    }

    private JsonNode extractDeepJsonPayload(String rawString) throws Exception {
        if (rawString == null || rawString.isBlank()) {
            throw new IllegalArgumentException("json 값이 비어 있습니다.");
        }

        JsonNode node = objectMapper.readTree(rawString);

        for (int depth = 0; depth < 10; depth++) {
            if (node == null || node.isNull()) {
                throw new IllegalArgumentException("유효하지 않은 JSON입니다.");
            }

            if (node.isObject() && node.has("json")) {
                node = node.get("json");
                continue;
            }

            if (node.isTextual()) {
                String text = node.asText();

                if (!looksLikeJson(text)) {
                    return node;
                }

                node = objectMapper.readTree(text);
                continue;
            }

            return node;
        }

        throw new IllegalStateException("JSON wrapper depth exceeded");
    }

    private boolean looksLikeJson(String text) {
        if (text == null) return false;

        String trimmed = text.trim();

        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private int calculateActivityScore(
            double activeEquivalentAllianceCount,
            int activeAllianceCountForWar,
            double activeUserRate,
            int meaningfulAllianceCount
    ) {
        int score = 0;

        if (activeEquivalentAllianceCount >= 4.0) score += 40;
        else if (activeEquivalentAllianceCount >= 3.0) score += 32;
        else if (activeEquivalentAllianceCount >= 2.0) score += 24;
        else if (activeEquivalentAllianceCount >= 1.0) score += 14;
        else if (activeEquivalentAllianceCount >= 0.5) score += 7;

        if (activeAllianceCountForWar >= 4) score += 30;
        else if (activeAllianceCountForWar >= 3) score += 24;
        else if (activeAllianceCountForWar >= 2) score += 16;
        else if (activeAllianceCountForWar >= 1) score += 8;

        if (activeUserRate >= 0.50) score += 15;
        else if (activeUserRate >= 0.35) score += 10;
        else if (activeUserRate >= 0.20) score += 5;

        if (meaningfulAllianceCount >= 8) score += 15;
        else if (meaningfulAllianceCount >= 5) score += 10;
        else if (meaningfulAllianceCount >= 3) score += 5;

        return clamp(score);
    }

    private int calculateOperationalScaleScore(
            double activeEquivalentAllianceCount,
            int activeAllianceCountForWar,
            double activeUserRate,
            int meaningfulAllianceCount
    ) {
        return calculateActivityScore(
                activeEquivalentAllianceCount,
                activeAllianceCountForWar,
                activeUserRate,
                meaningfulAllianceCount
        );
    }

    private int calculateElitePowerScore(
            int coreUsers,
            double coreEquivalentAllianceCount,
            double activePowerRate,
            double top3AlliancePowerRate
    ) {
        int score = 0;

        if (coreUsers >= 120) score += 30;
        else if (coreUsers >= 80) score += 24;
        else if (coreUsers >= 40) score += 16;
        else if (coreUsers >= 20) score += 8;

        if (coreEquivalentAllianceCount >= 1.0) score += 25;
        else if (coreEquivalentAllianceCount >= 0.5) score += 15;
        else if (coreEquivalentAllianceCount >= 0.25) score += 8;

        if (activePowerRate >= 0.75) score += 25;
        else if (activePowerRate >= 0.60) score += 18;
        else if (activePowerRate >= 0.45) score += 10;

        if (top3AlliancePowerRate >= 0.65) score += 10;

        return clamp(score);
    }

    private int calculateWarPotentialScore(
            int activeAllianceCountForWar,
            double activePowerRate,
            double coreEquivalentAllianceCount,
            double topAlliancePowerRate,
            double top3AlliancePowerRate
    ) {
        int score = 0;

        if (activeAllianceCountForWar >= 4) score += 35;
        else if (activeAllianceCountForWar >= 3) score += 28;
        else if (activeAllianceCountForWar >= 2) score += 20;
        else if (activeAllianceCountForWar >= 1) score += 10;

        if (activePowerRate >= 0.75) score += 30;
        else if (activePowerRate >= 0.60) score += 22;
        else if (activePowerRate >= 0.45) score += 14;

        if (coreEquivalentAllianceCount >= 1.0) score += 20;
        else if (coreEquivalentAllianceCount >= 0.5) score += 12;
        else if (coreEquivalentAllianceCount >= 0.25) score += 6;

        if (topAlliancePowerRate >= 0.60) score -= 15;
        if (top3AlliancePowerRate >= 0.85) score -= 10;

        return clamp(score);
    }

    private int calculateRiskScore(
            double activeEquivalentAllianceCount,
            double quitLikelyRate,
            int activeAllianceCountForWar,
            int insufficientAllianceCount,
            int inactiveOrQuitUsers
    ) {
        int score = 0;

        if (activeEquivalentAllianceCount < 0.5) score += 35;
        else if (activeEquivalentAllianceCount < 1.0) score += 25;
        else if (activeEquivalentAllianceCount < 2.0) score += 12;

        if (quitLikelyRate >= 0.60) score += 30;
        else if (quitLikelyRate >= 0.45) score += 22;
        else if (quitLikelyRate >= 0.30) score += 14;

        if (activeAllianceCountForWar <= 0) score += 25;
        else if (activeAllianceCountForWar <= 1) score += 15;
        else if (activeAllianceCountForWar <= 2) score += 8;

        if (insufficientAllianceCount >= 5) score += 10;

        if (inactiveOrQuitUsers >= 200) score += 15;
        else if (inactiveOrQuitUsers >= 120) score += 10;
        else if (inactiveOrQuitUsers >= 60) score += 5;

        return clamp(score);
    }

    private int calculateDataQualityScore(JsonNode summary, JsonNode players, JsonNode alliances) {
        int score = 0;

        int mapPlayerCount = summary.path("mapPlayers").asInt(arraySize(players));
        int allianceCount = summary.path("alliances").asInt(arraySize(alliances));
        int allianceDetails = summary.path("allianceDetails").asInt(0);
        int allianceMemberMergedPlayers = summary.path("allianceMemberMergedPlayers").asInt(0);

        double allianceDetailCoverageRate = allianceCount > 0
                ? allianceDetails / (double) allianceCount
                : 0;

        double memberDataCoverageRate = mapPlayerCount > 0
                ? allianceMemberMergedPlayers / (double) mapPlayerCount
                : 0;

        double loginDataCoverageRate = mapPlayerCount > 0
                ? countPlayersWithLoginData(players) / (double) mapPlayerCount
                : 0;

        if (mapPlayerCount >= 300) score += 25;
        else if (mapPlayerCount >= 150) score += 18;
        else if (mapPlayerCount >= 80) score += 10;

        if (allianceDetailCoverageRate >= 0.70) score += 25;
        else if (allianceDetailCoverageRate >= 0.50) score += 15;
        else if (allianceDetailCoverageRate >= 0.30) score += 8;

        if (loginDataCoverageRate >= 0.70) score += 25;
        else if (loginDataCoverageRate >= 0.50) score += 15;
        else if (loginDataCoverageRate >= 0.30) score += 8;

        if (memberDataCoverageRate >= 0.60) score += 25;
        else if (memberDataCoverageRate >= 0.40) score += 15;
        else if (memberDataCoverageRate >= 0.20) score += 8;

        return clamp(score);
    }

    private int calculateServerStrengthScore(
            int operationalScaleScore,
            int elitePowerScore,
            int activityScore,
            int dataQualityScore,
            int riskScore,
            String serverType
    ) {
        double raw =
                operationalScaleScore * 0.35
                        + elitePowerScore * 0.35
                        + activityScore * 0.20
                        + dataQualityScore * 0.10
                        - riskScore * 0.15;

        int score = clamp((int) Math.round(raw));

        if ("ELITE_POWER".equals(serverType)) {
            score = Math.max(score, clamp((int) Math.round(elitePowerScore * 0.85)));
        }

        return score;
    }

    private String resolveServerType(
            double activeEquivalentAllianceCount,
            int activeAllianceCountForWar,
            int elitePowerScore,
            double activePowerRate,
            int coreUsers,
            int activeUsers,
            double topAlliancePowerRate
    ) {
        if (topAlliancePowerRate >= 0.65 && activeAllianceCountForWar <= 1 && activeUsers < MAX_MEMBER_PER_ALLIANCE) {
            return "WHALE_DEPENDENT";
        }

        if (activeEquivalentAllianceCount >= 2.0 && activeAllianceCountForWar >= 2) {
            return "LARGE_SCALE_POWER";
        }

        if (
                activeEquivalentAllianceCount < 2.0
                        && elitePowerScore >= 70
                        && activePowerRate >= 0.60
                        && coreUsers >= 40
        ) {
            return "ELITE_POWER";
        }

        if (activeEquivalentAllianceCount >= 1.0 && elitePowerScore >= 50 && activeAllianceCountForWar >= 1) {
            return "BALANCED_POWER";
        }

        if (activeEquivalentAllianceCount >= 1.0 && elitePowerScore < 40) {
            return "ACTIVE_BUT_WEAK";
        }

        if (activeEquivalentAllianceCount >= 0.5) {
            return "QUIET_SERVER";
        }

        return "DEAD_SERVER";
    }

    private String resolveOverallGrade(int serverStrengthScore, int dataQualityScore) {
        String grade;

        if (serverStrengthScore >= 85) grade = "VERY_ACTIVE";
        else if (serverStrengthScore >= 70) grade = "ACTIVE";
        else if (serverStrengthScore >= 50) grade = "NORMAL";
        else if (serverStrengthScore >= 30) grade = "QUIET";
        else grade = "DEAD";

        if (dataQualityScore < 40) {
            if ("VERY_ACTIVE".equals(grade) || "ACTIVE".equals(grade)) {
                return "NORMAL";
            }
        }

        return grade;
    }

    private String resolveConfidence(int dataQualityScore) {
        if (dataQualityScore >= 70) return "HIGH";
        if (dataQualityScore >= 40) return "MEDIUM";
        return "LOW";
    }

    private String resolvePowerConcentrationGrade(
            double topAlliancePowerRate,
            double top3AlliancePowerRate,
            int activeAllianceCountForWar,
            int activeUsers
    ) {
        if (topAlliancePowerRate >= 0.65 && activeAllianceCountForWar <= 1 && activeUsers < MAX_MEMBER_PER_ALLIANCE) {
            return "WHALE_DEPENDENT";
        }

        if (top3AlliancePowerRate >= 0.85) {
            return "ELITE_CONCENTRATED";
        }

        if (topAlliancePowerRate >= 0.60) {
            return "TOP_ALLIANCE_CONCENTRATED";
        }

        return "NORMAL";
    }

    private ArrayNode buildTopAlliancesForAi(JsonNode alliances, int limit) {
        ArrayNode result = objectMapper.createArrayNode();

        if (!alliances.isArray()) {
            return result;
        }

        List<JsonNode> sorted = new ArrayList<>();

        alliances.forEach(sorted::add);

        sorted.sort(
                Comparator.comparingLong(this::alliancePowerForSort)
                        .reversed()
        );

        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            JsonNode alliance = sorted.get(i);

            ObjectNode item = objectMapper.createObjectNode();

            item.put("allianceId", alliance.path("allianceId").asText(""));
            item.put("tag", alliance.path("allianceTag").asText(""));
            item.put("name", alliance.path("allianceName").asText(""));
            item.put("memberCount", alliance.path("memberCount").asInt(0));
            item.put("shownMemberCount", alliance.path("allianceMemberCountShown").asInt(0));
            item.put("shownMemberMax", alliance.path("allianceMemberMax").asInt(0));
            item.put("activeAllianceGrade", alliance.path("activeAllianceGrade").asText("UNKNOWN"));
            item.put("activeAllianceScore", alliance.path("activeAllianceScore").asInt(0));
            item.put("coverageRate", alliance.path("activitySummary").path("coverageRate").asDouble(
                    alliance.path("coverageRate").asDouble(0)
            ));
            item.put("coreCount", alliance.path("activitySummary").path("coreCount").asInt(0));
            item.put("activeCount", alliance.path("activitySummary").path("activeCount").asInt(0));
            item.put("activePowerRate", alliance.path("activitySummary").path("activePowerRate").asDouble(0));
            item.put("corePowerRate", alliance.path("activitySummary").path("corePowerRate").asDouble(0));
            item.put("totalPower", alliance.path("activitySummary").path("totalPower").asLong(
                    alliance.path("alliancePower").asLong(0)
            ));

            result.add(item);
        }

        return result;
    }

    private ArrayNode buildWarnings(
            int dataQualityScore,
            double activeEquivalentAllianceCount,
            int activeAllianceCountForWar,
            double topAlliancePowerRate,
            double top3AlliancePowerRate,
            int insufficientAllianceCount,
            int unknownStatusUsers,
            int totalMapPlayers
    ) {
        ArrayNode warnings = objectMapper.createArrayNode();

        if (dataQualityScore < 40) {
            warnings.add("data_quality_low");
        } else if (dataQualityScore < 70) {
            warnings.add("data_quality_medium");
        }

        if (activeEquivalentAllianceCount < 1.0) {
            warnings.add("active_users_less_than_one_full_alliance");
        }

        if (activeAllianceCountForWar <= 1) {
            warnings.add("few_operational_alliances");
        }

        if (topAlliancePowerRate >= 0.65) {
            warnings.add("top_alliance_power_concentrated");
        }

        if (top3AlliancePowerRate >= 0.85) {
            warnings.add("top3_alliance_power_concentrated");
        }

        if (insufficientAllianceCount >= 3) {
            warnings.add("many_insufficient_sample_alliances");
        }

        if (totalMapPlayers > 0 && unknownStatusUsers / (double) totalMapPlayers >= 0.30) {
            warnings.add("many_unknown_login_status_users");
        }

        return warnings;
    }

    private int countUsersByActivityGrade(JsonNode players, String grade) {
        if (!players.isArray()) return 0;

        int count = 0;

        for (JsonNode player : players) {
            if (grade.equals(player.path("activityGrade").asText())) {
                count++;
            }
        }

        return count;
    }

    private int countUsersByStatus(JsonNode players, String status) {
        if (!players.isArray()) return 0;

        int count = 0;

        for (JsonNode player : players) {
            if (status.equals(player.path("userStatus").asText())) {
                count++;
            }
        }

        return count;
    }

    private int countPlayersWithLoginData(JsonNode players) {
        if (!players.isArray()) return 0;

        int count = 0;

        for (JsonNode player : players) {
            if (!player.path("lastLogin").isMissingNode() || !player.path("lastShowTime").isMissingNode()) {
                count++;
            }
        }

        return count;
    }

    private long sumPlayerPower(JsonNode players) {
        if (!players.isArray()) return 0L;

        long total = 0L;

        for (JsonNode player : players) {
            total += player.path("power").asLong(0L);
        }

        return total;
    }

    private long sumActivePlayerPower(JsonNode players) {
        if (!players.isArray()) return 0L;

        long total = 0L;

        for (JsonNode player : players) {
            String status = player.path("userStatus").asText("");

            if ("ACTIVE".equals(status) || "RECENT".equals(status) || "SLEEPY".equals(status)) {
                total += player.path("power").asLong(0L);
            }
        }

        return total;
    }

    private double calculateTopAlliancePowerRate(JsonNode alliances, long serverPowerTotal, int topN) {
        if (!alliances.isArray() || serverPowerTotal <= 0) {
            return 0;
        }

        List<Long> powers = new ArrayList<>();

        for (JsonNode alliance : alliances) {
            powers.add(alliancePowerForSort(alliance));
        }

        powers.sort(Comparator.reverseOrder());

        long sum = 0L;

        for (int i = 0; i < Math.min(topN, powers.size()); i++) {
            sum += powers.get(i);
        }

        return round6(sum / (double) serverPowerTotal);
    }

    private long alliancePowerForSort(JsonNode alliance) {
        long fromActivitySummary = alliance.path("activitySummary").path("totalPower").asLong(0L);

        if (fromActivitySummary > 0) {
            return fromActivitySummary;
        }

        return alliance.path("alliancePower").asLong(0L);
    }

    private int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private int intValue(JsonNode node, int fallback) {
        return node != null && node.isNumber() ? node.asInt() : fallback;
    }

    private long longValue(JsonNode node, long fallback) {
        return node != null && node.isNumber() ? node.asLong() : fallback;
    }

    private double doubleValue(JsonNode node, double fallback) {
        return node != null && node.isNumber() ? node.asDouble() : fallback;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return round6(numerator / (double) denominator);
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) return 0;
        return round6(numerator / (double) denominator);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}