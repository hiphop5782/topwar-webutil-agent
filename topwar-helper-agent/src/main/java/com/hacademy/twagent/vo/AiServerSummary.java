package com.hacademy.twagent.vo;

import java.util.List;

public record AiServerSummary(
    Integer serverId,
    Integer playerCount,
    Integer allianceCount,
    Integer noAlliancePlayers,
    Integer coreCount,
    Integer activeCount,
    Integer watchCount,
    Integer lowCount,
    Integer activeAllianceCountForWar,
    Integer meaningfulAllianceCount,
    List<AiAllianceSummary> alliances,
    List<AiPlayerSummary> topPlayers
) {
}